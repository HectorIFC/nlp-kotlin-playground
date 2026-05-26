package dev.nlpplayground.routes

import dev.nlpplayground.InMemoryBlobStorage
import dev.nlpplayground.StubTrainingPublisher
import dev.nlpplayground.testAppContext
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldEndWith
import io.ktor.client.call.body
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication

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

        "valid UTF-8 upload returns 202, stores the blob, and publishes the message" {
            testApplication {
                val storage = InMemoryBlobStorage()
                val publisher = StubTrainingPublisher()
                val ctx = testAppContext(storage = storage, publisher = publisher)
                installApp(ctx)

                val corpus = "The fox jumps over the lazy dog.".toByteArray(Charsets.UTF_8)
                val response = testClient().post("/upload") { setBody(multipartBody(corpus, "fox.txt")) }

                response.status shouldBe HttpStatusCode.Accepted
                val accepted: UploadAcceptedResponse = response.body()
                accepted.status shouldBe "queued"
                accepted.statusUrl shouldEndWith "/api/training/${accepted.trainingId}"
                accepted.progressUrl shouldEndWith "/training/${accepted.trainingId}/progress"

                // Blob landed in MinIO under corpus-uploads/<trainingId>.txt
                storage.objects.size shouldBe 1
                storage.objects.keys.first() shouldBe "corpus-uploads/${accepted.trainingId}.txt"

                // Message published with the same training_id + blob_key.
                publisher.published.shouldHaveSize(1)
                val msg = publisher.published.first()
                msg.trainingId shouldBe accepted.trainingId
                msg.blobKey shouldBe "${accepted.trainingId}.txt"

                // SQLite has a row in QUEUED state with the original filename preserved (for display).
                val training = ctx.trainings.findById(accepted.trainingId)
                training.shouldNotBeNull()
                training.corpusFilename shouldBe "fox.txt"
                training.corpusSizeBytes shouldBe corpus.size.toLong()
            }
        }

        "upload of non-UTF-8 bytes returns 400 and never touches storage or queue" {
            testApplication {
                val storage = InMemoryBlobStorage()
                val publisher = StubTrainingPublisher()
                installApp(testAppContext(storage = storage, publisher = publisher))
                val badBytes = byteArrayOf(0x48, 0x69, 0xFF.toByte(), 0x21)
                val response = testClient().post("/upload") { setBody(multipartBody(badBytes)) }
                response.status shouldBe HttpStatusCode.BadRequest
                val err: ErrorResponse = response.body()
                err.error shouldContain "UTF-8"
                storage.objects.size shouldBe 0
                publisher.published.shouldHaveSize(0)
            }
        }

        "upload larger than 2 MB returns 413" {
            testApplication {
                installApp()
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
                                append("note", "no file attached")
                            },
                        ),
                    )
                }
                response.status shouldBe HttpStatusCode.BadRequest
            }
        }
    })
