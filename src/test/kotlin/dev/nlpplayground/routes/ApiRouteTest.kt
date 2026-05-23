package dev.nlpplayground.routes

import dev.nlpplayground.AppContext
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication

class ApiRouteTest :
    StringSpec({

        "GET /api/status/{id} returns 404 for unknown session" {
            testApplication {
                installApp()
                val response = testClient().get("/api/status/does-not-exist")
                response.status shouldBe HttpStatusCode.NotFound
            }
        }

        "POST /api/search/{id} returns top-K hits sorted descending" {
            testApplication {
                val ctx = AppContext()
                val sessionId = ctx.sessions.createReady(tinyPipeline())
                installApp(ctx)

                val response = testClient().post("/api/search/$sessionId") {
                    contentType(ContentType.Application.Json)
                    setBody(SearchRequest(query = "Alice went into wonderland", topK = 3))
                }
                response.status shouldBe HttpStatusCode.OK
                val body: SearchResponse = response.body()
                body.results.shouldNotBeEmpty()
                val scores = body.results.map { it.score }
                scores shouldBe scores.sortedDescending()
                body.results.forEach { hit ->
                    hit.score shouldBeGreaterThanOrEqualTo -1f
                    hit.score shouldBeLessThanOrEqualTo 1f
                }
            }
        }

        "POST /api/search rejects blank query with 400" {
            testApplication {
                val ctx = AppContext()
                val sessionId = ctx.sessions.createReady(tinyPipeline())
                installApp(ctx)
                val response = testClient().post("/api/search/$sessionId") {
                    contentType(ContentType.Application.Json)
                    setBody(SearchRequest(query = "   ", topK = 3))
                }
                response.status shouldBe HttpStatusCode.BadRequest
            }
        }

        "POST /api/search rejects non-positive topK with 400" {
            testApplication {
                val ctx = AppContext()
                val sessionId = ctx.sessions.createReady(tinyPipeline())
                installApp(ctx)
                val response = testClient().post("/api/search/$sessionId") {
                    contentType(ContentType.Application.Json)
                    setBody(SearchRequest(query = "alice", topK = 0))
                }
                response.status shouldBe HttpStatusCode.BadRequest
            }
        }

        "POST /api/tokenize returns one entry per token" {
            testApplication {
                val ctx = AppContext()
                val sessionId = ctx.sessions.createReady(tinyPipeline())
                installApp(ctx)
                val response = testClient().post("/api/tokenize/$sessionId") {
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
                val ctx = AppContext()
                val sessionId = ctx.sessions.createReady(tinyPipeline())
                installApp(ctx)
                val response = testClient().post("/api/similarity/$sessionId") {
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

        "POST /api/search on a session that is still TRAINING returns 409" {
            testApplication {
                val ctx = AppContext()
                val sessionId = ctx.sessions.createTraining()
                installApp(ctx)
                val response = testClient().post("/api/search/$sessionId") {
                    contentType(ContentType.Application.Json)
                    setBody(SearchRequest(query = "alice"))
                }
                response.status shouldBe HttpStatusCode.Conflict
            }
        }

        "GET /api/status reports the current pipeline state" {
            testApplication {
                val ctx = AppContext()
                val sessionId = ctx.sessions.createReady(tinyPipeline())
                installApp(ctx)
                val response = testClient().get("/api/status/$sessionId")
                response.status shouldBe HttpStatusCode.OK
                val body: StatusResponse = response.body()
                body.sessionId shouldBe sessionId
                body.state shouldBe "ready"
            }
        }
    })
