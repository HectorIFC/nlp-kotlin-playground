package dev.nlpplayground.routes

import dev.nlpplayground.AppContext
import dev.nlpplayground.pipeline.Pipeline
import dev.nlpplayground.pipeline.PipelineState
import dev.nlpplayground.pipeline.SemanticSearch
import dev.nlpplayground.session.SessionEntry
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.SerializationException
import java.util.Locale

internal fun Route.apiRoutes(ctx: AppContext) {
    route("/api") {
        statusEndpoint(ctx)
        searchEndpoint(ctx)
        tokenizeEndpoint(ctx)
        similarityEndpoint(ctx)
    }
}

private fun Route.statusEndpoint(ctx: AppContext) {
    get("/status/{sessionId}") {
        val id = call.requireSessionId() ?: return@get
        val entry = ctx.sessions.get(id) ?: run {
            call.respond(HttpStatusCode.NotFound, ErrorResponse("Unknown session"))
            return@get
        }
        call.respond(
            StatusResponse(
                sessionId = entry.id,
                // Locale.ROOT: the API contract is ASCII-only — avoid Turkish-locale 'i' surprises.
                state = entry.state.name.lowercase(Locale.ROOT),
                name = entry.pipeline?.name,
                error = entry.errorMessage,
            ),
        )
    }
}

private fun Route.searchEndpoint(ctx: AppContext) {
    post("/search/{sessionId}") {
        val pipeline = call.requireReadyPipeline(ctx) ?: return@post
        val body = call.receiveJson<SearchRequest>() ?: return@post
        if (body.query.isBlank()) {
            return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("query must not be blank"))
        }
        if (body.topK <= 0) {
            return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("topK must be positive"))
        }
        val results = SemanticSearch.search(pipeline, body.query, body.topK)
            .map { SearchHit(it.sentence, it.score) }
        call.respond(SearchResponse(query = body.query, results = results))
    }
}

private fun Route.tokenizeEndpoint(ctx: AppContext) {
    post("/tokenize/{sessionId}") {
        val pipeline = call.requireReadyPipeline(ctx) ?: return@post
        val body = call.receiveJson<TokenizeRequest>() ?: return@post
        if (body.text.isBlank()) {
            return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("text must not be blank"))
        }
        val tokens = SemanticSearch.tokenize(pipeline, body.text)
            .map { TokenizedToken(it.id, it.text) }
        call.respond(TokenizeResponse(text = body.text, tokens = tokens))
    }
}

private fun Route.similarityEndpoint(ctx: AppContext) {
    post("/similarity/{sessionId}") {
        val pipeline = call.requireReadyPipeline(ctx) ?: return@post
        val body = call.receiveJson<SimilarityRequest>() ?: return@post
        if (body.textA.isBlank() || body.textB.isBlank()) {
            return@post call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("textA and textB must both be non-blank"),
            )
        }
        val score = SemanticSearch.similarity(pipeline, body.textA, body.textB)
        call.respond(SimilarityResponse(textA = body.textA, textB = body.textB, score = score))
    }
}

private suspend inline fun <reified T : Any> ApplicationCall.receiveJson(): T? = try {
    receive()
} catch (e: BadRequestException) {
    // Ktor wraps malformed/empty bodies and unsupported content-types in BadRequestException.
    respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid JSON body", e.message))
    null
} catch (e: SerializationException) {
    // kotlinx-serialization decoding errors (missing fields, wrong types).
    respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid JSON body", e.message))
    null
}

private suspend fun ApplicationCall.requireSessionId(): String? {
    val id = parameters["sessionId"]
    if (id.isNullOrBlank()) {
        respond(HttpStatusCode.BadRequest, ErrorResponse("Missing sessionId"))
        return null
    }
    return id
}

private suspend fun ApplicationCall.requireReadyPipeline(ctx: AppContext): Pipeline? {
    val id = requireSessionId() ?: return null
    val entry: SessionEntry = ctx.sessions.get(id) ?: run {
        respond(HttpStatusCode.NotFound, ErrorResponse("Unknown session"))
        return null
    }
    return when (entry.state) {
        PipelineState.READY -> entry.pipeline
        PipelineState.TRAINING -> {
            respond(HttpStatusCode.Conflict, ErrorResponse("Session still training", "Poll /api/status to wait."))
            null
        }
        PipelineState.ERROR -> {
            respond(HttpStatusCode.Conflict, ErrorResponse("Session failed to build", entry.errorMessage))
            null
        }
    }
}
