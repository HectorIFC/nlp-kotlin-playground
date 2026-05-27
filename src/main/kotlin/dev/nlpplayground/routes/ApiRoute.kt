package dev.nlpplayground.routes

import dev.nlpplayground.AppContext
import dev.nlpplayground.pipeline.Pipeline
import dev.nlpplayground.pipeline.SemanticSearch
import dev.nlpplayground.training.TrainingStatus
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.SerializationException

/**
 * Search / Tokenize / Compare endpoints, scoped to a training_id.
 *
 * The path param flipped from `{sessionId}` (v0.0.x) to `{trainingId}` —
 * this is part of the breaking change announced in the v0.1.0 README. The
 * training must be in `READY` state (or `EXPIRED`, which we reject with a
 * helpful 410 so the dashboard can hint at "re-upload"). All other states
 * yield 409.
 *
 * Pipeline objects are pulled from MinIO once and cached locally by
 * [dev.nlpplayground.training.TrainingPipelineLoader] — we never re-download
 * for every request.
 */
internal fun Route.apiRoutes(ctx: AppContext) {
    route("/api") {
        searchEndpoint(ctx)
        tokenizeEndpoint(ctx)
        similarityEndpoint(ctx)
    }
}

private fun Route.searchEndpoint(ctx: AppContext) {
    post("/search/{trainingId}") {
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
    post("/tokenize/{trainingId}") {
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
    post("/similarity/{trainingId}") {
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
    respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid JSON body", e.message))
    null
} catch (e: SerializationException) {
    respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid JSON body", e.message))
    null
}

@Suppress("ReturnCount")
private suspend fun ApplicationCall.requireReadyPipeline(ctx: AppContext): Pipeline? {
    val id = parameters["trainingId"]
    if (id.isNullOrBlank()) {
        respond(HttpStatusCode.BadRequest, ErrorResponse("Missing trainingId"))
        return null
    }
    val training = ctx.trainings.findById(id) ?: run {
        respond(HttpStatusCode.NotFound, ErrorResponse("Unknown training"))
        return null
    }
    return when (training.status) {
        TrainingStatus.READY -> ctx.pipelineLoader.resolve(training)
        TrainingStatus.EXPIRED -> {
            respond(HttpStatusCode.Gone, ErrorResponse("Training expired", "Upload the corpus again to retrain."))
            null
        }
        TrainingStatus.FAILED -> {
            respond(HttpStatusCode.Conflict, ErrorResponse("Training failed", training.errorMessage))
            null
        }
        else -> {
            respond(
                HttpStatusCode.Conflict,
                ErrorResponse("Training still in progress", "Poll /api/training/$id to wait."),
            )
            null
        }
    }
}
