package dev.nlpplayground

import dev.nlpplayground.observability.CorrelationId
import dev.nlpplayground.routes.ErrorResponse
import dev.nlpplayground.routes.apiRoutes
import dev.nlpplayground.routes.healthRoute
import dev.nlpplayground.routes.metricsRoute
import dev.nlpplayground.routes.pretrainedRoutes
import dev.nlpplayground.routes.trainingRoutes
import dev.nlpplayground.routes.uploadRoute
import dev.nlpplayground.routes.webRoutes
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.http.content.staticResources
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import org.slf4j.LoggerFactory

internal fun Application.configureRouting(ctx: AppContext) {
    val log = LoggerFactory.getLogger("Routing")

    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(error = "Invalid request", detail = cause.message),
            )
        }
        exception<Throwable> { call, cause ->
            log.error("Unhandled error on {} {}", call.request.local.method.value, call.request.local.uri, cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse(error = "Internal server error"),
            )
        }
    }

    routing {
        // Propagate {trainingId}/{id} path params into the SLF4J MDC so logs
        // emitted by route handlers are correlated automatically.
        install(CorrelationId)
        healthRoute(ctx)
        metricsRoute(ctx)
        pretrainedRoutes(ctx)
        apiRoutes(ctx)
        trainingRoutes(ctx)
        uploadRoute(ctx, this@configureRouting)
        webRoutes()
        // Static assets (HTML home, CSS, JS, images) under `resources/static`.
        // Listed last so dynamic routes win on path conflicts.
        staticResources("/", "static")
    }
}
