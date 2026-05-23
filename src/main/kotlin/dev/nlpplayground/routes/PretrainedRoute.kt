package dev.nlpplayground.routes

import dev.nlpplayground.AppContext
import dev.nlpplayground.pipeline.NoSuchPretrainedException
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

internal fun Route.pretrainedRoutes(ctx: AppContext) {
    route("/pretrained") {
        get {
            call.respond(PretrainedListResponse(ctx.pipelineService.availablePretrained()))
        }

        post("{name}") {
            val name = call.parameters["name"]
                ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing path parameter: name"))

            val pipeline = try {
                ctx.pipelineService.loadPretrained(name)
            } catch (e: IllegalArgumentException) {
                return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("Unknown corpus", e.message))
            } catch (e: NoSuchPretrainedException) {
                return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("Resources missing", e.message))
            }

            val sessionId = ctx.sessions.createReady(pipeline)
            call.respond(
                HttpStatusCode.Created,
                StartSessionResponse(sessionId = sessionId, name = pipeline.name),
            )
        }
    }
}
