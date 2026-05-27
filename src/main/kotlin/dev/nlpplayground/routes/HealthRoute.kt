package dev.nlpplayground.routes

import dev.nlpplayground.AppContext
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/**
 * `/health` now reflects the distributed topology (PRD §3.1 / §4.6): only
 * returns 200 when SQLite, MinIO and RabbitMQ are all reachable. The container
 * `depends_on: condition: service_healthy` in `docker-compose.yml` uses this.
 */
internal fun Route.healthRoute(ctx: AppContext) {
    get("/health") {
        val db = ctx.database.isHealthy()
        val storage = ctx.storage.isHealthy()
        val rabbit = ctx.rabbit.isHealthy()
        val allHealthy = db && storage && rabbit
        call.respond(
            status = if (allHealthy) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable,
            message = HealthResponse(
                status = if (allHealthy) "ok" else "degraded",
                database = db,
                storage = storage,
                rabbit = rabbit,
            ),
        )
    }
}
