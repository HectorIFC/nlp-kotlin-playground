package dev.nlpplayground.observability

import java.util.concurrent.atomic.AtomicLong

/**
 * Tiny in-process counters surfaced at `GET /metrics`. Fase 5 keeps
 * the dependency budget at zero — no Micrometer, no Prometheus client — so
 * the choice is simple `AtomicLong`s with a stable name → value contract.
 *
 * Names follow the `playground_<noun>_<verb>_total` convention so a future
 * Prometheus scrape can ingest them without translation.
 */
internal class MetricsRegistry {

    private val queued = AtomicLong()
    private val completed = AtomicLong()
    private val failed = AtomicLong()
    private val expired = AtomicLong()

    fun recordQueued() {
        queued.incrementAndGet()
    }

    fun recordCompleted() {
        completed.incrementAndGet()
    }

    fun recordFailed() {
        failed.incrementAndGet()
    }

    fun recordExpired() {
        expired.incrementAndGet()
    }

    fun snapshot(): Map<String, Long> = mapOf(
        "playground_trainings_queued_total" to queued.get(),
        "playground_trainings_completed_total" to completed.get(),
        "playground_trainings_failed_total" to failed.get(),
        "playground_trainings_expired_total" to expired.get(),
    )
}
