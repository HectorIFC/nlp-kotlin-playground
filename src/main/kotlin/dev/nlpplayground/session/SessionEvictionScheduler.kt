package dev.nlpplayground.session

import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Periodically calls [SessionStore.evictOld]. Runs on a single daemon thread
 * so the JVM can shut down cleanly without an explicit cancel from callers
 * — but [stop] is still wired in for [Application] to call on shutdown.
 */
internal class SessionEvictionScheduler(
    private val store: SessionStore,
    private val period: Duration = DEFAULT_PERIOD,
) {

    private val log = LoggerFactory.getLogger(SessionEvictionScheduler::class.java)
    private var executor: ScheduledExecutorService? = null

    fun start() {
        check(executor == null) { "Scheduler already started." }
        val svc = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "session-eviction").apply { isDaemon = true }
        }
        svc.scheduleAtFixedRate(
            {
                runCatching { store.evictOld() }
                    .onSuccess { removed -> if (removed > 0) log.info("Evicted {} old session(s).", removed) }
                    .onFailure { e -> log.warn("Session eviction failed", e) }
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
