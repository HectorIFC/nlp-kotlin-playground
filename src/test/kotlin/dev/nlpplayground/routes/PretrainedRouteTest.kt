package dev.nlpplayground.routes

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication

class PretrainedRouteTest :
    StringSpec({

        "GET /pretrained returns the configured list (empty before phase 4)" {
            testApplication {
                installApp()
                val response = testClient().get("/pretrained")
                response.status shouldBe HttpStatusCode.OK
                val body: PretrainedListResponse = response.body()
                body.available shouldBe emptyList<String>()
            }
        }

        "POST /pretrained/{name} for an unknown corpus returns 404" {
            testApplication {
                installApp()
                val response = testClient().post("/pretrained/no-such-corpus")
                response.status shouldBe HttpStatusCode.NotFound
            }
        }
    })
