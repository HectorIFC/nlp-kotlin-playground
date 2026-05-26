package dev.nlpplayground.routes

import dev.nlpplayground.AppContext
import dev.nlpplayground.messaging.TrainingMessage
import dev.nlpplayground.training.TrainingStatus
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.CoroutineScope
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.UUID

private const val MAX_UPLOAD_BYTES = 2L * 1024L * 1024L // 2 MB
private const val UTF8_BOM_LENGTH = 3
private const val UTF8_BOM_BYTE_0 = 0xEF
private const val UTF8_BOM_BYTE_1 = 0xBB
private const val UTF8_BOM_BYTE_2 = 0xBF

private val log = LoggerFactory.getLogger("uploadRoute")

/**
 * Parsed multipart payload — either a file part with its (size-checked) bytes
 * and original filename, or a sentinel saying we exceeded the limit.
 */
private data class ParsedUpload(val bytes: ByteArray?, val filename: String?, val tooLarge: Boolean)

/**
 * `POST /upload` is now fully async (PRD §4.6):
 *
 * 1. Validate size + UTF-8 + non-empty.
 * 2. PUT the raw bytes into `corpus-uploads/{trainingId}.txt` (MinIO).
 * 3. INSERT a `trainings` row in `QUEUED` state (SQLite).
 * 4. Publish a `TrainingMessage` on `training.exchange` (RabbitMQ, persistent).
 * 5. Respond `202 Accepted` with the training_id + status/progress URLs.
 *
 * The `scope` parameter is kept for compatibility with the old sync path that
 * launched background training inline; it's unused now.
 */
@Suppress("UNUSED_PARAMETER")
internal fun Route.uploadRoute(ctx: AppContext, scope: CoroutineScope) {
    post("/upload") {
        val parsed = parseMultipart(call.receiveMultipart()) ?: return@post
        if (parsed.tooLarge) {
            return@post call.respond(
                HttpStatusCode.PayloadTooLarge,
                ErrorResponse(
                    "Upload exceeds 2 MB limit",
                    "Reduce the corpus size or split into multiple uploads.",
                ),
            )
        }
        val bytes = parsed.bytes ?: return@post call.respond(
            HttpStatusCode.BadRequest,
            ErrorResponse("No file part in multipart request"),
        )
        if (decodeUtf8Strict(bytes) == null) {
            return@post call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("File must be UTF-8 encoded"),
            )
        }
        if (decodeUtf8Strict(bytes)!!.isBlank()) {
            return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Uploaded file is empty"))
        }
        handleAccepted(call, ctx, bytes, parsed.filename)
    }
}

private suspend fun parseMultipart(multipart: io.ktor.http.content.MultiPartData): ParsedUpload? {
    var bytes: ByteArray? = null
    var filename: String? = null
    var tooLarge = false
    multipart.forEachPart { part ->
        if (bytes == null && part is PartData.FileItem && !tooLarge) {
            val raw = part.provider().toInputStream().use { it.readNBytes((MAX_UPLOAD_BYTES + 1).toInt()) }
            if (raw.size.toLong() > MAX_UPLOAD_BYTES) {
                tooLarge = true
            } else {
                bytes = raw
                filename = part.originalFileName
            }
        }
        part.dispose()
    }
    return ParsedUpload(bytes, filename, tooLarge)
}

@Suppress("TooGenericExceptionCaught")
private suspend fun handleAccepted(call: ApplicationCall, ctx: AppContext, bytes: ByteArray, filename: String?) {
    val trainingId = UUID.randomUUID().toString()
    val blobKey = "$trainingId.txt"

    // 1. Upload to MinIO first — the message we publish later references this blob.
    try {
        ctx.storage.upload(
            bucket = ctx.config.corpusBucket,
            key = blobKey,
            bytes = bytes,
            contentType = "text/plain; charset=utf-8",
        )
    } catch (e: Exception) {
        log.error("Upload to MinIO failed for {}", trainingId, e)
        return call.respond(
            HttpStatusCode.InternalServerError,
            ErrorResponse("Storage unavailable", e.message),
        )
    }

    // 2. Persist QUEUED row + initial event.
    ctx.trainings.create(
        corpusBlobKey = blobKey,
        corpusSizeBytes = bytes.size.toLong(),
        corpusFilename = filename,
        id = trainingId,
    )

    // 3. Publish to RabbitMQ. On failure, mark the training FAILED so the dashboard
    // surfaces it immediately rather than leaving a zombie QUEUED row.
    try {
        ctx.publisher.publish(
            TrainingMessage(
                trainingId = trainingId,
                blobKey = blobKey,
                submittedAt = System.currentTimeMillis(),
            ),
        )
    } catch (e: Exception) {
        log.error("Publish failed for {}; marking training FAILED", trainingId, e)
        ctx.trainings.updateStatus(
            id = trainingId,
            newStatus = TrainingStatus.FAILED,
            errorMessage = "Could not publish to queue: ${e.message}",
        )
        return call.respond(
            HttpStatusCode.InternalServerError,
            ErrorResponse("Queue unavailable", e.message),
        )
    }

    call.respond(
        HttpStatusCode.Accepted,
        UploadAcceptedResponse(
            trainingId = trainingId,
            status = "queued",
            statusUrl = "/api/training/$trainingId",
            progressUrl = "/training/$trainingId/progress",
        ),
    )
}

/**
 * Returns the decoded string if [bytes] is valid UTF-8, or null otherwise.
 * Strips a BOM if present.
 */
private fun decodeUtf8Strict(bytes: ByteArray): String? {
    val cleaned = if (hasUtf8Bom(bytes)) bytes.copyOfRange(UTF8_BOM_LENGTH, bytes.size) else bytes
    val decoder = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
    return try {
        decoder.decode(ByteBuffer.wrap(cleaned)).toString()
    } catch (_: CharacterCodingException) {
        null
    }
}

private fun hasUtf8Bom(bytes: ByteArray): Boolean = bytes.size >= UTF8_BOM_LENGTH &&
    bytes[0] == UTF8_BOM_BYTE_0.toByte() &&
    bytes[1] == UTF8_BOM_BYTE_1.toByte() &&
    bytes[2] == UTF8_BOM_BYTE_2.toByte()
