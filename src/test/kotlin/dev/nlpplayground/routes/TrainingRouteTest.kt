package dev.nlpplayground.routes

import dev.nlpplayground.testAppContext
import dev.nlpplayground.training.TrainingStatus
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication

class TrainingRouteTest :
    StringSpec({

        "GET /api/training/{id} returns 404 for unknown id" {
            testApplication {
                installApp()
                val response = testClient().get("/api/training/nope")
                response.status shouldBe HttpStatusCode.NotFound
            }
        }

        "GET /api/training/{id} returns detail + event timeline" {
            testApplication {
                val ctx = testAppContext()
                val created = ctx.trainings.create(
                    corpusBlobKey = "corpus-uploads/abc.txt",
                    corpusSizeBytes = 1024,
                    corpusFilename = "alice.txt",
                )
                ctx.trainings.updateStatus(created.id, TrainingStatus.DOWNLOADING)
                installApp(ctx)

                val response = testClient().get("/api/training/${created.id}")
                response.status shouldBe HttpStatusCode.OK
                val body: TrainingDetailResponse = response.body()
                body.id shouldBe created.id
                body.status shouldBe "downloading"
                body.corpusFilename shouldBe "alice.txt"
                body.events shouldHaveSize 2
                body.events[0].toStatus shouldBe "queued"
                body.events[1].fromStatus shouldBe "queued"
                body.events[1].toStatus shouldBe "downloading"
            }
        }

        "GET /api/trainings filters by status" {
            testApplication {
                val ctx = testAppContext()
                val a = ctx.trainings.create("a.txt", 1, null).id
                val b = ctx.trainings.create("b.txt", 1, null).id
                val c = ctx.trainings.create("c.txt", 1, null).id
                ctx.trainings.updateStatus(a, TrainingStatus.FAILED, errorMessage = "boom")
                ctx.trainings.updateStatus(b, TrainingStatus.DOWNLOADING)
                installApp(ctx)

                val failedResp = testClient().get("/api/trainings?status=failed")
                val failedBody: TrainingListResponse = failedResp.body()
                failedBody.items shouldHaveSize 1
                failedBody.items.first().id shouldBe a

                val activeResp = testClient().get("/api/trainings/active")
                val activeBody: TrainingListResponse = activeResp.body()
                // c is QUEUED, b is DOWNLOADING — both are "active". a is FAILED → excluded.
                activeBody.items.map { it.id }.toSet() shouldBe setOf(b, c)
            }
        }

        "GET /api/trainings caps to limit param" {
            testApplication {
                val ctx = testAppContext()
                repeat(5) { ctx.trainings.create("blob-$it", 1, null) }
                installApp(ctx)

                val response = testClient().get("/api/trainings?limit=2")
                val body: TrainingListResponse = response.body()
                body.items shouldHaveSize 2
            }
        }
    })
