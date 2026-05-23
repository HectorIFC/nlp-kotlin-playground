package dev.nlpplayground

import dev.nlpplayground.routes.ErrorResponse
import dev.nlpplayground.routes.apiRoutes
import dev.nlpplayground.routes.healthRoute
import dev.nlpplayground.routes.pretrainedRoutes
import dev.nlpplayground.routes.uploadRoute
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
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
        healthRoute()
        pretrainedRoutes(ctx)
        apiRoutes(ctx)
        uploadRoute(ctx, this@configureRouting)
    }
}
