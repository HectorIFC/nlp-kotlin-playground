package dev.nlpplayground

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.netty.EngineMain
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation

fun main(args: Array<String>) {
    EngineMain.main(args)
}

@Suppress("unused")
fun Application.module() {
    // Building the context opens the SQLite + MinIO + RabbitMQ connections.
    // If any dependency is unreachable, this throws and Ktor never finishes
    // starting — matching the `depends_on: service_healthy` chain in compose.
    val ctx = AppContext()
    monitor.subscribe(ApplicationStarted) {
        // Order matters: start the scheduler first (cheap, daemon thread) and
        // the consumer second (allocates AMQP channels). If `consumer.start()`
        // throws, we tear the scheduler back down so we don't leak the daemon
        // when the app fails to boot.
        ctx.expirationScheduler.start()
        @Suppress("TooGenericExceptionCaught")
        try {
            ctx.consumer.start()
        } catch (e: Throwable) {
            // If the consumer pool can't start (broker down, channel errors,
            // etc.) we have to tear down what we already booted ourselves
            // because Ktor won't fire ApplicationStopped on a failed start.
            runCatching { ctx.expirationScheduler.stop() }
            runCatching { ctx.close() }
            throw e
        }
    }
    monitor.subscribe(ApplicationStopped) { ctx.close() }
    moduleWith(ctx)
}

/**
 * Test seam: wire the app with a caller-supplied context (so tests can inject
 * stubs or pre-built fakes). Production paths go through [module] above, which
 * also owns the scheduler lifecycle.
 */
internal fun Application.moduleWith(ctx: AppContext) {
    install(ContentNegotiation) {
        json()
    }
    install(CallLogging)
    configureRouting(ctx)
}
