package dev.nlpplayground.routes

import dev.nlpplayground.AppContext
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.UUID

private const val MAX_UPLOAD_BYTES = 2L * 1024L * 1024L // 2 MB, per PRD §6.2
private const val SHORT_UUID_LENGTH = 8
private const val UTF8_BOM_LENGTH = 3
private const val UTF8_BOM_BYTE_0 = 0xEF
private const val UTF8_BOM_BYTE_1 = 0xBB
private const val UTF8_BOM_BYTE_2 = 0xBF

internal fun Route.uploadRoute(ctx: AppContext, scope: CoroutineScope) {
    val log = LoggerFactory.getLogger("uploadRoute")

    post("/upload") {
        val multipart = call.receiveMultipart()

        var rawBytes: ByteArray? = null
        var tooLarge = false

        // Iterate all parts; we only care about the first file part. Other fields are ignored.
        multipart.forEachPart { part ->
            if (rawBytes == null && part is PartData.FileItem && !tooLarge) {
                val bytes = part.provider().toInputStream().use { stream ->
                    // Read up to MAX + 1: lets us detect "too large" without buffering everything.
                    stream.readNBytes((MAX_UPLOAD_BYTES + 1).toInt())
                }
                if (bytes.size.toLong() > MAX_UPLOAD_BYTES) {
                    tooLarge = true
                } else {
                    rawBytes = bytes
                }
            }
            part.dispose()
        }

        if (tooLarge) {
            return@post call.respond(
                HttpStatusCode.PayloadTooLarge,
                ErrorResponse("Upload exceeds 2 MB limit", "Reduce the corpus size or split into multiple uploads."),
            )
        }

        val bytes = rawBytes ?: return@post call.respond(
            HttpStatusCode.BadRequest,
            ErrorResponse("No file part in multipart request"),
        )

        val corpus = decodeUtf8Strict(bytes) ?: return@post call.respond(
            HttpStatusCode.BadRequest,
            ErrorResponse("File must be UTF-8 encoded"),
        )

        if (corpus.isBlank()) {
            return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Uploaded file is empty"))
        }

        // Use an opaque server-generated name (PRD §6.11 — never trust upload filenames).
        val internalName = "upload-${UUID.randomUUID().toString().substring(0, SHORT_UUID_LENGTH)}"
        val sessionId = ctx.sessions.createTraining()

        scope.launch(Dispatchers.Default) {
            runCatching { ctx.pipelineService.trainFromCorpus(internalName, corpus) }
                .onSuccess { pipeline -> ctx.sessions.markReady(sessionId, pipeline) }
                .onFailure { e ->
                    log.warn("Training failed for session {}", sessionId, e)
                    ctx.sessions.markError(sessionId, e.message ?: e::class.simpleName.orEmpty())
                }
        }

        call.respond(
            HttpStatusCode.Accepted,
            UploadResponse(sessionId = sessionId, state = "training"),
        )
    }
}

/**
 * Returns the decoded string if [bytes] is valid UTF-8, or null otherwise.
 * Strips a BOM if present (PRD §6.4 mentions BOM removal).
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
