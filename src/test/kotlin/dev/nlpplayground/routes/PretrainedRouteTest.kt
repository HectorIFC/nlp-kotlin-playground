package dev.nlpplayground.routes

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication

class PretrainedRouteTest :
    StringSpec({

        "GET /pretrained returns the bundled corpora list" {
            testApplication {
                installApp()
                val response = testClient().get("/pretrained")
                response.status shouldBe HttpStatusCode.OK
                val body: PretrainedListResponse = response.body()
                body.available shouldContainExactly listOf(
                    "alice-in-wonderland",
                    "shakespeare-sonnets",
                    "kotlin-stdlib-docs",
                )
            }
        }

        "POST /pretrained/{name} for an unknown corpus returns 404" {
            testApplication {
                installApp()
                val response = testClient().post("/pretrained/no-such-corpus")
                response.status shouldBe HttpStatusCode.NotFound
            }
        }

        "POST /pretrained/{name} for a bundled corpus returns 201 with the session id" {
            testApplication {
                installApp()
                val response = testClient().post("/pretrained/alice-in-wonderland")
                response.status shouldBe HttpStatusCode.Created
                val body: StartSessionResponse = response.body()
                body.name shouldBe "alice-in-wonderland"
                body.trainingId.isNotBlank() shouldBe true
            }
        }
    })
