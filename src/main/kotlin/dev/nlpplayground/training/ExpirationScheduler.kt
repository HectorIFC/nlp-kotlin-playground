package dev.nlpplayground.training

import dev.nlpplayground.observability.MetricsRegistry
import dev.nlpplayground.persistence.TrainingRepository
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Periodically calls [TrainingRepository.markExpired] so READY trainings past
 * their TTL move to EXPIRED without manual intervention. Runs on a single
 * daemon thread, so the JVM can shut down cleanly even if [stop] is never
 * called — but [stop] is still wired in for the application module's
 * `ApplicationStopped` hook.
 *
 * only READY transitions to EXPIRED; the state machine + repository
 * enforce that, so the scheduler can safely call `markExpired` without
 * extra guards.
 */
internal class ExpirationScheduler(
    private val trainings: TrainingRepository,
    private val metrics: MetricsRegistry = MetricsRegistry(),
    private val period: Duration = DEFAULT_PERIOD,
) {

    private val log = LoggerFactory.getLogger(ExpirationScheduler::class.java)
    private var executor: ScheduledExecutorService? = null

    fun start() {
        check(executor == null) { "Scheduler already started." }
        val svc = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "training-expiry").apply { isDaemon = true }
        }
        svc.scheduleAtFixedRate(
            {
                runCatching { trainings.markExpired() }
                    .onSuccess { count ->
                        if (count > 0) {
                            log.info("Marked {} training(s) EXPIRED", count)
                            repeat(count) { metrics.recordExpired() }
                        }
                    }
                    .onFailure { e -> log.warn("Expiration sweep failed", e) }
            },
            period.toMillis(),
            period.toMillis(),
            TimeUnit.MILLISECONDS,
        )
        executor = svc
    }

    fun stop() {
        executor?.shutdownNow()
        executor = null
    }

    internal companion object {
        val DEFAULT_PERIOD: Duration = Duration.ofMinutes(10)
    }
}
