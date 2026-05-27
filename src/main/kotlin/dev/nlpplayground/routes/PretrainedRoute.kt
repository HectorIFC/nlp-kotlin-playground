package dev.nlpplayground.routes

import dev.nlpplayground.AppContext
import dev.nlpplayground.training.BUNDLED_PREFIX
import dev.nlpplayground.training.TrainingStatus
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.util.UUID

/**
 * Bundled pre-trained corpora live on the classpath, not in MinIO, but for
 * v0.1.0 we still surface them as training rows so the URL contract is
 * uniform: every Pipeline is reachable at `/api/.../{trainingId}`.
 *
 * The trick is that we persist the row directly in `READY` state with a
 * sentinel `corpus_blob_key = "bundled:{name}"`. The
 * [dev.nlpplayground.training.TrainingPipelineLoader] inspects that prefix
 * and routes to the classpath loader instead of MinIO.
 */
internal fun Route.pretrainedRoutes(ctx: AppContext) {
    route("/pretrained") {
        get {
            call.respond(PretrainedListResponse(ctx.pipelineLoader.bundled()))
        }

        post("{name}") {
            val name = call.parameters["name"]
                ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing path parameter: name"))

            if (name !in ctx.pipelineLoader.bundled()) {
                return@post call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponse("Unknown corpus", "Available: ${ctx.pipelineLoader.bundled()}"),
                )
            }

            val trainingId = UUID.randomUUID().toString()
            ctx.trainings.create(
                corpusBlobKey = "$BUNDLED_PREFIX$name",
                corpusSizeBytes = 0,
                corpusFilename = name,
                id = trainingId,
                initialStatus = TrainingStatus.READY,
                modelBlobPrefix = "$BUNDLED_PREFIX$name",
            )
            call.respond(
                HttpStatusCode.Created,
                StartSessionResponse(trainingId = trainingId, name = name),
            )
        }
    }
}
