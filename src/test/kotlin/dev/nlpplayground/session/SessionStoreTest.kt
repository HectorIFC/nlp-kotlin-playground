package dev.nlpplayground.session

import dev.mosaic.EmbeddingTable
import dev.nlpplayground.pipeline.Pipeline
import dev.nlpplayground.pipeline.PipelineState
import dev.tessera.Trainer
import dev.tessera.TrainingConfig
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

private class StepClock(start: Instant) : Clock() {
    private var now: Instant = start
    override fun getZone() = ZoneOffset.UTC
    override fun withZone(zone: java.time.ZoneId): Clock = this
    override fun instant(): Instant = now
    fun advance(by: Duration) {
        now = now.plus(by)
    }
}

private fun tinyPipeline(name: String = "tiny"): Pipeline {
    val tokenizer = Trainer(TrainingConfig(numMerges = 10, verbose = false))
        .train("alice. bob. the cat sat on the mat. another sentence here.")
    val embeddings = EmbeddingTable.create(vocabSize = tokenizer.vocabSize, embeddingDim = 16)
    return Pipeline(name = name, tokenizer = tokenizer, embeddings = embeddings, sentences = listOf("a b c"))
}

class SessionStoreTest :
    StringSpec({

        "createReady stores the pipeline and returns a UUID" {
            val store = SessionStore()
            val id = store.createReady(tinyPipeline())
            store.get(id).shouldNotBeNull()
            store.get(id)!!.state shouldBe PipelineState.READY
            store.pipeline(id).shouldNotBeNull()
        }

        "createTraining starts in TRAINING; markReady flips to READY" {
            val store = SessionStore()
            val id = store.createTraining()
            store.get(id)!!.state shouldBe PipelineState.TRAINING
            store.pipeline(id).shouldBeNull()

            store.markReady(id, tinyPipeline())
            store.get(id)!!.state shouldBe PipelineState.READY
            store.pipeline(id).shouldNotBeNull()
        }

        "markError moves the session to ERROR with a message" {
            val store = SessionStore()
            val id = store.createTraining()
            store.markError(id, "bad utf-8")
            val entry = store.get(id)!!
            entry.state shouldBe PipelineState.ERROR
            entry.errorMessage shouldBe "bad utf-8"
        }

        "evictOld removes entries older than maxAge" {
            val clock = StepClock(Instant.parse("2025-01-01T00:00:00Z"))
            val store = SessionStore(clock = clock, maxAge = Duration.ofMinutes(30))

            val staleId = store.createReady(tinyPipeline("stale"))
            clock.advance(Duration.ofMinutes(45))
            val freshId = store.createReady(tinyPipeline("fresh"))

            val removed = store.evictOld()
            removed shouldBe 1
            store.get(staleId).shouldBeNull()
            store.get(freshId).shouldNotBeNull()
        }

        "maxSessions cap drops the oldest entry when exceeded" {
            val clock = StepClock(Instant.parse("2025-01-01T00:00:00Z"))
            val store = SessionStore(clock = clock, maxSessions = 2)

            val first = store.createReady(tinyPipeline("first"))
            clock.advance(Duration.ofSeconds(1))
            val second = store.createReady(tinyPipeline("second"))
            clock.advance(Duration.ofSeconds(1))
            val third = store.createReady(tinyPipeline("third"))

            store.size() shouldBe 2
            val survivors = listOf(first, second, third).filter { store.get(it) != null }
            survivors shouldContain second
            survivors shouldContain third
            survivors shouldNotContain first
        }
    })
