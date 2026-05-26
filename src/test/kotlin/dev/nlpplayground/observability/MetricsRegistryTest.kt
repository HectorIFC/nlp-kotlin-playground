package dev.nlpplayground.observability

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class MetricsRegistryTest :
    StringSpec({

        "fresh registry reports zeros for all four counters" {
            val r = MetricsRegistry()
            r.snapshot() shouldBe mapOf(
                "playground_trainings_queued_total" to 0L,
                "playground_trainings_completed_total" to 0L,
                "playground_trainings_failed_total" to 0L,
                "playground_trainings_expired_total" to 0L,
            )
        }

        "each recorder bumps exactly its counter" {
            val r = MetricsRegistry()
            r.recordQueued()
            r.recordQueued()
            r.recordCompleted()
            r.recordFailed()
            r.recordExpired()
            r.recordExpired()
            r.recordExpired()
            r.snapshot() shouldBe mapOf(
                "playground_trainings_queued_total" to 2L,
                "playground_trainings_completed_total" to 1L,
                "playground_trainings_failed_total" to 1L,
                "playground_trainings_expired_total" to 3L,
            )
        }
    })
