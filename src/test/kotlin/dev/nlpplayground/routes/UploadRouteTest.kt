package dev.nlpplayground.routes

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.delay

private suspend fun waitForState(
    client: HttpClient,
    sessionId: String,
    target: String,
    timeoutMs: Long = 30_000L,
): StatusResponse {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
        val response = client.get("/api/status/$sessionId")
        if (response.status == HttpStatusCode.OK) {
            val body: StatusResponse = response.body()
            if (body.state == target || body.state == "error") return body
        }
        delay(100)
    }
    error("Timed out waiting for session $sessionId to reach state '$target'.")
}

private fun multipartBody(bytes: ByteArray, filename: String = "anything.txt"): MultiPartFormDataContent =
    MultiPartFormDataContent(
        formData {
            append(
                "file",
                bytes,
                Headers.build {
                    append(HttpHeaders.ContentType, "text/plain")
                    append(HttpHeaders.ContentDisposition, "filename=\"$filename\"")
                },
            )
        },
    )

class UploadRouteTest :
    StringSpec({

        "valid UTF-8 upload returns 202 and the session eventually becomes READY" {
            testApplication {
                installApp()
                val client = testClient()
                val corpus = """
                    The fox jumps over the lazy dog.
                    The dog sleeps in the warm afternoon sun.
                    The fox returns to the den at dusk.
                """.trimIndent().toByteArray(Charsets.UTF_8)

                val response = client.post("/upload") { setBody(multipartBody(corpus)) }
                response.status shouldBe HttpStatusCode.Accepted
                val accepted: UploadResponse = response.body()
                accepted.state shouldBe "training"

                val finalState = waitForState(client, accepted.sessionId, target = "ready")
                finalState.state shouldBe "ready"
                finalState.name.shouldNotBeNull()
                finalState.name!! shouldStartWith "upload-"
            }
        }

        "upload of non-UTF-8 bytes returns 400" {
            testApplication {
                installApp()
                // Stray 0xFF byte is not a valid UTF-8 start byte.
                val badBytes = byteArrayOf(0x48, 0x69, 0xFF.toByte(), 0x21)
                val response = testClient().post("/upload") { setBody(multipartBody(badBytes)) }
                response.status shouldBe HttpStatusCode.BadRequest
                val err: ErrorResponse = response.body()
                err.error shouldContain "UTF-8"
            }
        }

        "upload larger than 2 MB returns 413" {
            testApplication {
                installApp()
                // 2 MB + 1 byte of valid ASCII (also valid UTF-8) — should be rejected.
                val oversized = ByteArray(2 * 1024 * 1024 + 1) { 'a'.code.toByte() }
                val response = testClient().post("/upload") { setBody(multipartBody(oversized)) }
                response.status shouldBe HttpStatusCode.PayloadTooLarge
            }
        }

        "upload with no file part returns 400" {
            testApplication {
                installApp()
                val response = testClient().post("/upload") {
                    setBody(
                        MultiPartFormDataContent(
                            formData {
                                // Only a plain form field — no file part.
                                append("note", "no file attached")
                            },
                        ),
                    )
                }
                response.status shouldBe HttpStatusCode.BadRequest
            }
        }
    })
