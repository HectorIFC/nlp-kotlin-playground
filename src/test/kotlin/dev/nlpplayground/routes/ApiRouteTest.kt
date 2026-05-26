package dev.nlpplayground.routes

import dev.nlpplayground.AppContext
import dev.nlpplayground.testAppContext
import dev.nlpplayground.training.BUNDLED_PREFIX
import dev.nlpplayground.training.TrainingStatus
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication

/**
 * Seed a READY training row backed by a bundled corpus so the pipeline loader
 * can resolve it via the classpath without touching MinIO.
 */
private fun AppContext.seedBundled(name: String = "alice-in-wonderland"): String {
    val training = trainings.create(
        corpusBlobKey = "$BUNDLED_PREFIX$name",
        corpusSizeBytes = 0,
        corpusFilename = name,
        initialStatus = TrainingStatus.READY,
        modelBlobPrefix = "$BUNDLED_PREFIX$name",
    )
    return training.id
}

class ApiRouteTest :
    StringSpec({

        "POST /api/search/{trainingId} returns 404 for unknown id" {
            testApplication {
                installApp()
                val response = testClient().post("/api/search/does-not-exist") {
                    contentType(ContentType.Application.Json)
                    setBody(SearchRequest(query = "alice"))
                }
                response.status shouldBe HttpStatusCode.NotFound
            }
        }

        "POST /api/search returns top-K hits sorted descending" {
            testApplication {
                val ctx = testAppContext()
                val trainingId = ctx.seedBundled()
                installApp(ctx)

                val response = testClient().post("/api/search/$trainingId") {
                    contentType(ContentType.Application.Json)
                    setBody(SearchRequest(query = "Alice went into wonderland", topK = 3))
                }
                response.status shouldBe HttpStatusCode.OK
                val body: SearchResponse = response.body()
                body.results.shouldNotBeEmpty()
                body.results.size shouldBe 3
                val scores = body.results.map { it.score }
                scores shouldBe scores.sortedDescending()
                body.results.forEach { hit ->
                    hit.score shouldBeGreaterThanOrEqualTo -1f
                    hit.score shouldBeLessThanOrEqualTo 1f
                }
            }
        }

        "POST /api/search caps results by topK" {
            testApplication {
                val ctx = testAppContext()
                val trainingId = ctx.seedBundled()
                installApp(ctx)
                val response = testClient().post("/api/search/$trainingId") {
                    contentType(ContentType.Application.Json)
                    setBody(SearchRequest(query = "rabbit hole", topK = 1))
                }
                response.status shouldBe HttpStatusCode.OK
                val body: SearchResponse = response.body()
                body.results.size shouldBe 1
            }
        }

        "POST /api/search rejects blank query with 400" {
            testApplication {
                val ctx = testAppContext()
                val trainingId = ctx.seedBundled()
                installApp(ctx)
                val response = testClient().post("/api/search/$trainingId") {
                    contentType(ContentType.Application.Json)
                    setBody(SearchRequest(query = "   ", topK = 3))
                }
                response.status shouldBe HttpStatusCode.BadRequest
            }
        }

        "POST /api/search rejects non-positive topK with 400" {
            testApplication {
                val ctx = testAppContext()
                val trainingId = ctx.seedBundled()
                installApp(ctx)
                val response = testClient().post("/api/search/$trainingId") {
                    contentType(ContentType.Application.Json)
                    setBody(SearchRequest(query = "alice", topK = 0))
                }
                response.status shouldBe HttpStatusCode.BadRequest
            }
        }

        "POST /api/tokenize returns one entry per token" {
            testApplication {
                val ctx = testAppContext()
                val trainingId = ctx.seedBundled()
                installApp(ctx)
                val response = testClient().post("/api/tokenize/$trainingId") {
                    contentType(ContentType.Application.Json)
                    setBody(TokenizeRequest(text = "Alice"))
                }
                response.status shouldBe HttpStatusCode.OK
                val body: TokenizeResponse = response.body()
                body.text shouldBe "Alice"
                body.tokens.shouldNotBeEmpty()
            }
        }

        "POST /api/similarity returns a finite score in [-1, 1]" {
            testApplication {
                val ctx = testAppContext()
                val trainingId = ctx.seedBundled()
                installApp(ctx)
                val response = testClient().post("/api/similarity/$trainingId") {
                    contentType(ContentType.Application.Json)
                    setBody(SimilarityRequest(textA = "alice", textB = "wonderland"))
                }
                response.status shouldBe HttpStatusCode.OK
                val body: SimilarityResponse = response.body()
                body.score.isFinite() shouldBe true
                body.score shouldBeGreaterThanOrEqualTo -1f
                body.score shouldBeLessThanOrEqualTo 1f
            }
        }

        "POST /api/search on a QUEUED training returns 409" {
            testApplication {
                val ctx = testAppContext()
                val training = ctx.trainings.create("blob.txt", 100, "x.txt")
                installApp(ctx)
                val response = testClient().post("/api/search/${training.id}") {
                    contentType(ContentType.Application.Json)
                    setBody(SearchRequest(query = "alice"))
                }
                response.status shouldBe HttpStatusCode.Conflict
            }
        }
    })
