package dev.nlpplayground.routes

import dev.nlpplayground.AppContext
import dev.nlpplayground.persistence.TrainingFilter
import dev.nlpplayground.training.Training
import dev.nlpplayground.training.TrainingEvent
import dev.nlpplayground.training.TrainingStatus
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import java.time.Instant
import java.util.Locale

private const val MIN_LIMIT = 1
private const val MAX_LIMIT = 200
private const val DEFAULT_LIMIT = 50

/**
 * Read endpoints for the dashboard and the progress page:
 *
 * - `GET /api/training/{id}`         — single training + full event timeline
 * - `GET /api/trainings`             — paginated list, filter by status/since
 * - `GET /api/trainings/active`      — convenience: anything not yet terminal
 *
 * Writes happen only through the upload route and the consumer; this file is
 * read-only, so no state-machine concerns leak in here.
 */
internal fun Route.trainingRoutes(ctx: AppContext) {
    route("/api") {
        get("/training/{id}") {
            val id = call.parameters["id"]
            if (id.isNullOrBlank()) {
                return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing path parameter: id"))
            }
            val training = ctx.trainings.findById(id)
                ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("Unknown training"))
            val events = ctx.events.findByTrainingId(id)
            call.respond(training.toDetail(events))
        }

        get("/trainings") {
            val filter = parseFilter(call) ?: return@get
            val items = ctx.trainings.findAll(filter).map { it.toListItem() }
            call.respond(TrainingListResponse(items = items))
        }

        get("/trainings/active") {
            val activeStatuses = setOf(
                TrainingStatus.QUEUED,
                TrainingStatus.DOWNLOADING,
                TrainingStatus.TOKENIZING,
                TrainingStatus.EMBEDDING,
                TrainingStatus.INDEXING,
            )
            val items = ctx.trainings.findAll(TrainingFilter(statuses = activeStatuses, limit = MAX_LIMIT))
                .map { it.toListItem() }
            call.respond(TrainingListResponse(items = items))
        }
    }
}

private suspend fun parseFilter(call: ApplicationCall): TrainingFilter? {
    val statuses = call.request.queryParameters["status"]?.let { raw ->
        raw.split(",").mapNotNull { token ->
            runCatching { TrainingStatus.valueOf(token.trim().uppercase(Locale.ROOT)) }.getOrNull()
        }.toSet()
    }?.takeIf { it.isNotEmpty() }

    val sinceRaw = call.request.queryParameters["since"]
    val since = sinceRaw?.let {
        runCatching { Instant.ofEpochMilli(it.toLong()) }
            .getOrElse { _ -> runCatching { Instant.parse(it) }.getOrNull() }
    }
    if (sinceRaw != null && since == null) {
        call.respond(
            HttpStatusCode.BadRequest,
            ErrorResponse("Invalid `since` parameter", "Expected epoch millis or ISO-8601 instant"),
        )
        return null
    }

    val limit = call.request.queryParameters["limit"]?.toIntOrNull()
        ?.coerceIn(MIN_LIMIT, MAX_LIMIT)
        ?: DEFAULT_LIMIT

    return TrainingFilter(statuses = statuses, createdSince = since, limit = limit)
}

private fun Training.toListItem(): TrainingListItem = TrainingListItem(
    id = id,
    status = status.name.lowercase(Locale.ROOT),
    corpusFilename = corpusFilename,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
)

private fun Training.toDetail(events: List<TrainingEvent>): TrainingDetailResponse = TrainingDetailResponse(
    id = id,
    status = status.name.lowercase(Locale.ROOT),
    corpusFilename = corpusFilename,
    corpusSizeBytes = corpusSizeBytes,
    errorMessage = errorMessage,
    modelBlobPrefix = modelBlobPrefix,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
    expiresAt = expiresAt?.toEpochMilli(),
    events = events.map { it.toDto() },
)

private fun TrainingEvent.toDto(): TrainingEventDto = TrainingEventDto(
    fromStatus = fromStatus?.name?.lowercase(Locale.ROOT),
    toStatus = toStatus.name.lowercase(Locale.ROOT),
    detail = detail,
    occurredAt = occurredAt.toEpochMilli(),
)
