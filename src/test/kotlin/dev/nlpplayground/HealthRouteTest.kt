package dev.nlpplayground

import dev.nlpplayground.routes.HealthResponse
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication

class HealthRouteTest :
    StringSpec({

        "/health returns 200 with all dependencies healthy" {
            testApplication {
                val ctx = testAppContext()
                application { moduleWith(ctx) }
                val client = createClient { install(ContentNegotiation) { json() } }
                val response = client.get("/health")
                response.status shouldBe HttpStatusCode.OK
                val body: HealthResponse = response.body()
                body.status shouldBe "ok"
                body.database shouldBe true
                body.storage shouldBe true
                body.rabbit shouldBe true
            }
        }
    })
