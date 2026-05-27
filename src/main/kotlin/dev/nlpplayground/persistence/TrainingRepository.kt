package dev.nlpplayground.persistence

import dev.nlpplayground.training.Training
import dev.nlpplayground.training.TrainingStateMachine
import dev.nlpplayground.training.TrainingStatus
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

private const val DEFAULT_PAGE_SIZE = 50

/**
 * Filter parameters for [TrainingRepository.findAll]. All nullable —
 * unspecified means "don't filter on this dimension".
 */
internal data class TrainingFilter(
    val statuses: Set<TrainingStatus>? = null,
    val createdSince: Instant? = null,
    val limit: Int = DEFAULT_PAGE_SIZE,
)

/**
 * Repository for the `trainings` table. Every mutation goes through
 * `transaction(db) { ... }` so callers don't accidentally run statements
 * outside a transaction (Exposed throws "no transaction in context" otherwise).
 *
 * State transitions are validated by [TrainingStateMachine] before the row is
 * updated, and every transition writes a row to `training_events` in the same
 * transaction — keeping the audit log and the state column atomically in sync.
 */
internal class TrainingRepository(
    private val db: Database,
    private val events: TrainingEventRepository,
    private val clock: () -> Instant = Instant::now,
) {

    private val log = LoggerFactory.getLogger(TrainingRepository::class.java)

    /**
     * Persist a brand-new training and emit the initial event.
     *
     * Defaults to the [TrainingStatus.QUEUED] starting state for the normal
     * upload flow. Pretrained corpora pass [initialStatus] = [TrainingStatus.READY]
     * because they don't go through the queue — the artifacts already exist
     * on the classpath, so the consumer pipeline is bypassed.
     */
    @Suppress("LongParameterList")
    fun create(
        corpusBlobKey: String,
        corpusSizeBytes: Long,
        corpusFilename: String?,
        id: String = UUID.randomUUID().toString(),
        initialStatus: TrainingStatus = TrainingStatus.QUEUED,
        modelBlobPrefix: String? = null,
    ): Training {
        val now = clock()
        return transaction(db) {
            Trainings.insert {
                it[Trainings.id] = id
                it[status] = initialStatus
                it[Trainings.corpusBlobKey] = corpusBlobKey
                it[Trainings.corpusSizeBytes] = corpusSizeBytes
                it[Trainings.corpusFilename] = corpusFilename
                it[Trainings.modelBlobPrefix] = modelBlobPrefix
                it[createdAt] = now.toEpochMilli()
                it[updatedAt] = now.toEpochMilli()
            }
            events.record(
                trainingId = id,
                fromStatus = null,
                toStatus = initialStatus,
                detail = corpusFilename,
                occurredAt = now,
            )
            Training(
                id = id,
                status = initialStatus,
                corpusBlobKey = corpusBlobKey,
                corpusSizeBytes = corpusSizeBytes,
                corpusFilename = corpusFilename,
                modelBlobPrefix = modelBlobPrefix,
                errorMessage = null,
                createdAt = now,
                updatedAt = now,
                expiresAt = null,
            )
        }
    }

    fun findById(id: String): Training? = transaction(db) {
        Trainings.selectAll()
            .where { Trainings.id eq id }
            .limit(1)
            .firstOrNull()
            ?.toTraining()
    }

    fun findAll(filter: TrainingFilter = TrainingFilter()): List<Training> = transaction(db) {
        Trainings.selectAll().apply {
            filter.statuses?.let { andWhere { Trainings.status inList it } }
            filter.createdSince?.let { andWhere { Trainings.createdAt greaterEq it.toEpochMilli() } }
        }
            .orderBy(Trainings.createdAt, SortOrder.DESC)
            .limit(filter.limit)
            .map { it.toTraining() }
    }

    /**
     * Atomically validate the transition, update the row, and append an event.
     * Returns the new training snapshot, or null if the id is unknown.
     *
     * When [newStatus] equals the current status the call is a **no-op**: no
     * event is recorded and no row update happens. This makes consumer-side
     * pipeline replays idempotent (if a worker crashed mid-step
     * and the message is redelivered, the new worker can safely re-execute
     * `updateStatus(DOWNLOADING)` even when the row already says DOWNLOADING.
     */
    @Suppress("LongParameterList")
    fun updateStatus(
        id: String,
        newStatus: TrainingStatus,
        detail: String? = null,
        errorMessage: String? = null,
        modelBlobPrefix: String? = null,
        expiresAt: Instant? = null,
    ): Training? = transaction(db) {
        val current = Trainings.selectAll().where { Trainings.id eq id }.firstOrNull()
            ?: return@transaction null
        val from = current[Trainings.status]
        if (from == newStatus) {
            return@transaction current.toTraining()
        }
        TrainingStateMachine.assertValidTransition(from, newStatus)

        val now = clock()
        Trainings.update({ Trainings.id eq id }) {
            it[status] = newStatus
            it[updatedAt] = now.toEpochMilli()
            if (errorMessage != null) it[Trainings.errorMessage] = errorMessage
            if (modelBlobPrefix != null) it[Trainings.modelBlobPrefix] = modelBlobPrefix
            if (expiresAt != null) it[Trainings.expiresAt] = expiresAt.toEpochMilli()
        }
        events.record(
            trainingId = id,
            fromStatus = from,
            toStatus = newStatus,
            detail = detail ?: errorMessage,
            occurredAt = now,
        )
        Trainings.selectAll().where { Trainings.id eq id }.first().toTraining()
    }

    /**
     * Sweep READY trainings whose `expires_at` is past. only READY
     * trainings get marked EXPIRED; in-progress states are left alone to avoid
     * racing with the consumer.
     */
    fun markExpired(now: Instant = clock()): Int = transaction(db) {
        val targets = Trainings.selectAll()
            .where {
                (Trainings.status eq TrainingStatus.READY) and
                    (Trainings.expiresAt lessEq now.toEpochMilli())
            }
            .map { it[Trainings.id] }

        var count = 0
        for (id in targets) {
            runCatching { updateStatus(id, TrainingStatus.EXPIRED, detail = "TTL reached") }
                .onSuccess { count++ }
                .onFailure { e -> log.warn("Failed to expire training {}", id, e) }
        }
        count
    }

    private fun ResultRow.toTraining(): Training = Training(
        id = this[Trainings.id],
        status = this[Trainings.status],
        corpusBlobKey = this[Trainings.corpusBlobKey],
        corpusSizeBytes = this[Trainings.corpusSizeBytes],
        corpusFilename = this[Trainings.corpusFilename],
        modelBlobPrefix = this[Trainings.modelBlobPrefix],
        errorMessage = this[Trainings.errorMessage],
        createdAt = Instant.ofEpochMilli(this[Trainings.createdAt]),
        updatedAt = Instant.ofEpochMilli(this[Trainings.updatedAt]),
        expiresAt = this[Trainings.expiresAt]?.let(Instant::ofEpochMilli),
    )
}

// Small helper to keep selectAll filters readable when chaining optional filters.
private fun org.jetbrains.exposed.sql.Query.andWhere(
    op: org.jetbrains.exposed.sql.SqlExpressionBuilder.() -> org.jetbrains.exposed.sql.Op<Boolean>,
): org.jetbrains.exposed.sql.Query {
    adjustWhere {
        val existing = this
        org.jetbrains.exposed.sql.SqlExpressionBuilder.run {
            existing?.let { it and op() } ?: op()
        }
    }
    return this
}
