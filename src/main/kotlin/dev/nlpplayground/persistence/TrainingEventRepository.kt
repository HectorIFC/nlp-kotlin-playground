package dev.nlpplayground.persistence

import dev.nlpplayground.training.TrainingEvent
import dev.nlpplayground.training.TrainingStatus
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

/**
 * Audit log for status changes. Mostly used by the dashboard ("show me the
 * timeline of this training") and by ops debug.
 *
 * Writes are append-only; we never UPDATE or DELETE events. The repository
 * deliberately exposes no API to mutate existing rows.
 *
 * [record] is called from inside [TrainingRepository.updateStatus], which is
 * already running in a transaction. To make that nesting safe, [record]
 * piggybacks on whatever transaction is current rather than opening a new one.
 */
internal class TrainingEventRepository(private val db: Database) {

    fun record(
        trainingId: String,
        fromStatus: TrainingStatus?,
        toStatus: TrainingStatus,
        detail: String?,
        occurredAt: Instant,
    ) {
        val insert: () -> Unit = {
            TrainingEvents.insert {
                it[TrainingEvents.trainingId] = trainingId
                it[TrainingEvents.fromStatus] = fromStatus
                it[TrainingEvents.toStatus] = toStatus
                it[TrainingEvents.detail] = detail
                it[TrainingEvents.occurredAt] = occurredAt.toEpochMilli()
            }
        }
        // If a transaction is already in flight (we were called from updateStatus),
        // reuse it so the status update and the event row commit together.
        if (TransactionManager.currentOrNull() != null) {
            insert()
        } else {
            transaction(db) { insert() }
        }
    }

    fun findByTrainingId(trainingId: String): List<TrainingEvent> = transaction(db) {
        TrainingEvents.selectAll()
            .where { TrainingEvents.trainingId eq trainingId }
            .orderBy(TrainingEvents.occurredAt to SortOrder.ASC, TrainingEvents.id to SortOrder.ASC)
            .map { it.toEvent() }
    }

    private fun ResultRow.toEvent(): TrainingEvent = TrainingEvent(
        id = this[TrainingEvents.id].value,
        trainingId = this[TrainingEvents.trainingId],
        fromStatus = this[TrainingEvents.fromStatus],
        toStatus = this[TrainingEvents.toStatus],
        detail = this[TrainingEvents.detail],
        occurredAt = Instant.ofEpochMilli(this[TrainingEvents.occurredAt]),
    )
}
