package dev.nlpplayground.routes

import dev.nlpplayground.AppContext
import io.ktor.http.ContentType
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

private val helpText: Map<String, String> = mapOf(
    "playground_trainings_queued_total" to "Total trainings ever enqueued by the upload endpoint.",
    "playground_trainings_completed_total" to "Total trainings that reached READY successfully.",
    "playground_trainings_failed_total" to "Total trainings that ended in FAILED (any pipeline step).",
    "playground_trainings_expired_total" to "Total READY trainings swept to EXPIRED by the TTL scheduler.",
)

/**
 * `GET /metrics` — Prometheus text format, no scrape-protocol negotiation.
 * Bare minimum to demonstrate observability scaffolding without pulling in
 * the Prometheus client lib.
 */
internal fun Route.metricsRoute(ctx: AppContext) {
    get("/metrics") {
        val snapshot = ctx.metrics.snapshot()
        val body = buildString {
            for ((name, value) in snapshot) {
                appendLine("# HELP $name ${helpText[name] ?: ""}")
                appendLine("# TYPE $name counter")
                appendLine("$name $value")
            }
        }
        call.respondText(body, ContentType.parse("text/plain; version=0.0.4"))
    }
}
