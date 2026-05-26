package dev.nlpplayground.routes

import dev.nlpplayground.testAppContext
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication

class MetricsRouteTest :
    StringSpec({

        "GET /metrics returns Prometheus-style text with all four counters" {
            testApplication {
                val ctx = testAppContext()
                ctx.metrics.recordQueued()
                ctx.metrics.recordCompleted()
                installApp(ctx)
                val response = testClient().get("/metrics")
                response.status shouldBe HttpStatusCode.OK
                val body = response.bodyAsText()
                body shouldContain "# HELP playground_trainings_queued_total"
                body shouldContain "# TYPE playground_trainings_queued_total counter"
                body shouldContain "playground_trainings_queued_total 1"
                body shouldContain "playground_trainings_completed_total 1"
                body shouldContain "playground_trainings_failed_total 0"
                body shouldContain "playground_trainings_expired_total 0"
            }
        }
    })
