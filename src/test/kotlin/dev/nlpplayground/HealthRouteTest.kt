package dev.nlpplayground

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication

class HealthRouteTest :
    StringSpec({

        "/health returns 200 ok" {
            testApplication {
                application { module() }
                val response = client.get("/health")
                response.status shouldBe HttpStatusCode.OK
                response.bodyAsText() shouldBe "ok"
            }
        }
    })
