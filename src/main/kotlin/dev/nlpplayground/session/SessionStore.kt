package dev.nlpplayground.session

import dev.nlpplayground.pipeline.Pipeline
import dev.nlpplayground.pipeline.PipelineState
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

internal data class SessionEntry(
    val id: String,
    val state: PipelineState,
    val pipeline: Pipeline?,
    val errorMessage: String?,
    val createdAt: Instant,
)

/**
 * In-memory cache of active pipelines, keyed by an opaque session UUID.
 * Survives only as long as the JVM does — restarting Docker discards all
 * sessions, which is acceptable for a playground.
 *
 * Three guarantees this class makes:
 *
 * - Concurrent access is safe via [ConcurrentHashMap].
 * - Old sessions are pruned periodically via [evictOld]; PRD §6.3.
 * - The total number of live sessions never exceeds [maxSessions]; when the
 *   cap is reached, the oldest session is evicted to make room (also §6.3).
 *
 * The eviction policy is intentionally simple: this is a single-instance
 * playground, not a multi-tenant service.
 */
internal class SessionStore(
    private val clock: Clock = Clock.systemUTC(),
    private val maxSessions: Int = DEFAULT_MAX_SESSIONS,
    private val maxAge: Duration = DEFAULT_MAX_AGE,
) {

    private val entries = ConcurrentHashMap<String, SessionEntry>()

    fun createTraining(): String {
        val id = UUID.randomUUID().toString()
        entries[id] = SessionEntry(
            id = id,
            state = PipelineState.TRAINING,
            pipeline = null,
            errorMessage = null,
            createdAt = Instant.now(clock),
        )
        enforceCap()
        return id
    }

    fun createReady(pipeline: Pipeline): String {
        val id = UUID.randomUUID().toString()
        entries[id] = SessionEntry(
            id = id,
            state = PipelineState.READY,
            pipeline = pipeline,
            errorMessage = null,
            createdAt = Instant.now(clock),
        )
        enforceCap()
        return id
    }

    fun markReady(id: String, pipeline: Pipeline) {
        entries.computeIfPresent(id) { _, prev ->
            prev.copy(state = PipelineState.READY, pipeline = pipeline, errorMessage = null)
        }
    }

    fun markError(id: String, message: String) {
        entries.computeIfPresent(id) { _, prev ->
            prev.copy(state = PipelineState.ERROR, pipeline = null, errorMessage = message)
        }
    }

    fun get(id: String): SessionEntry? = entries[id]

    /** Pipeline only — convenience for hot-path code that already checked state. */
    fun pipeline(id: String): Pipeline? = entries[id]?.pipeline

    fun size(): Int = entries.size

    /**
     * Removes entries older than [maxAge]. Returns the number of entries removed.
     * Safe to call from any thread.
     */
    fun evictOld(): Int {
        val cutoff = Instant.now(clock).minus(maxAge)
        var removed = 0
        val iterator = entries.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next().value
            if (entry.createdAt.isBefore(cutoff)) {
                iterator.remove()
                removed++
            }
        }
        return removed
    }

    private fun enforceCap() {
        if (entries.size <= maxSessions) return
        // Sort by age and drop the oldest until we are back under the cap.
        val toDrop = entries.values
            .sortedBy { it.createdAt }
            .take(entries.size - maxSessions)
        for (entry in toDrop) {
            entries.remove(entry.id)
        }
    }

    internal companion object {
        const val DEFAULT_MAX_SESSIONS: Int = 50
        val DEFAULT_MAX_AGE: Duration = Duration.ofHours(1)
    }
}
