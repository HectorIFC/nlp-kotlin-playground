package dev.nlpplayground.training

import dev.nlpplayground.InMemoryBlobStorage
import dev.nlpplayground.messaging.TrainingMessage
import dev.nlpplayground.persistence.PlaygroundDatabase
import dev.nlpplayground.persistence.TrainingEventRepository
import dev.nlpplayground.persistence.TrainingRepository
import dev.nlpplayground.pipeline.CorpusTrainer
import dev.nlpplayground.testConfig
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

private data class Harness(
    val storage: InMemoryBlobStorage,
    val trainings: TrainingRepository,
    val events: TrainingEventRepository,
    val service: TrainingService,
)

/**
 * Tiny trainer that skips the real BPE step — keeps service tests under a
 * second by reusing a pre-built Pipeline with a handful of sentences.
 */
private val fastTrainer = PipelineTrainer { id, corpus ->
    val mini = (corpus.lines() + listOf("alice", "rabbit", "wonderland")).filter { it.isNotBlank() }
        .joinToString(". ")
    CorpusTrainer.train(name = id, corpus = mini, numMerges = 50)
}

private fun harness(): Harness {
    val config = testConfig()
    val db = PlaygroundDatabase(config)
    val events = TrainingEventRepository(db.handle)
    val repo = TrainingRepository(db.handle, events)
    val storage = InMemoryBlobStorage()
    val service = TrainingService(config, storage, repo, trainer = fastTrainer)
    return Harness(storage, repo, events, service)
}

private const val CORPUS_BUCKET = "corpus-uploads"

private fun seedQueued(
    h: Harness,
    id: String,
    body: String = "alice goes through the looking glass",
): TrainingMessage {
    val blobKey = "$id.txt"
    h.storage.upload(CORPUS_BUCKET, blobKey, body.toByteArray())
    h.trainings.create(
        corpusBlobKey = blobKey,
        corpusSizeBytes = body.length.toLong(),
        corpusFilename = "x.txt",
        id = id,
    )
    return TrainingMessage(trainingId = id, blobKey = blobKey, submittedAt = 0L)
}

class TrainingServiceTest :
    StringSpec({

        "happy path: QUEUED → READY with model + corpus uploaded and source blob deleted" {
            val h = harness()
            val msg = seedQueued(h, "happy-1")

            val outcome = h.service.process(msg)
            outcome shouldBe ProcessOutcome.SUCCESS

            val final = h.trainings.findById(msg.trainingId).shouldNotBeNull()
            final.status shouldBe TrainingStatus.READY
            final.modelBlobPrefix shouldBe "${msg.trainingId}/"
            final.expiresAt.shouldNotBeNull()

            // Model artifacts landed in trained-models, source blob deleted from corpus-uploads.
            h.storage.objects.keys.filter { it.startsWith("trained-models/") }
                .map { it.removePrefix("trained-models/") }
                .sorted() shouldContainExactly listOf(
                "${msg.trainingId}/corpus.txt",
                "${msg.trainingId}/mosaic.bin",
                "${msg.trainingId}/mosaic.bin.meta.json",
                "${msg.trainingId}/tessera.json",
            )
            h.storage.objects.keys.any { it == "corpus-uploads/${msg.blobKey}" } shouldBe false

            // Event timeline includes the full pipeline walk.
            val states = h.events.findByTrainingId(msg.trainingId).map { it.toStatus }
            states shouldContainExactly listOf(
                TrainingStatus.QUEUED,
                TrainingStatus.DOWNLOADING,
                TrainingStatus.TOKENIZING,
                TrainingStatus.EMBEDDING,
                TrainingStatus.INDEXING,
                TrainingStatus.READY,
            )
        }

        "unknown training id is acked and discarded" {
            val h = harness()
            val outcome = h.service.process(TrainingMessage("nope", "nowhere.txt", 0L))
            outcome shouldBe ProcessOutcome.SKIPPED
        }

        "already-READY training short-circuits with SUCCESS (idempotency)" {
            val h = harness()
            val msg = seedQueued(h, "idem-1")
            h.service.process(msg) // first pass: real run
            h.storage.objects.clear() // pretend MinIO lost everything; second pass shouldn't try to repeat
            val outcome = h.service.process(msg)
            outcome shouldBe ProcessOutcome.SUCCESS
            h.trainings.findById(msg.trainingId)!!.status shouldBe TrainingStatus.READY
        }

        "trainer failure marks FAILED and returns FAILED outcome" {
            val h = harness()
            val msg = seedQueued(h, "fail-1")
            val throwing = TrainingService(
                config = testConfig(),
                storage = h.storage,
                trainings = h.trainings,
                trainer = { _, _ -> error("simulated trainer crash") },
            )
            val outcome = throwing.process(msg)
            outcome shouldBe ProcessOutcome.FAILED
            val final = h.trainings.findById(msg.trainingId).shouldNotBeNull()
            final.status shouldBe TrainingStatus.FAILED
            (final.errorMessage?.contains("simulated") == true) shouldBe true
        }

        "missing source blob marks FAILED" {
            val h = harness()
            val id = "no-blob"
            // Create training row but skip the blob upload.
            h.trainings.create(corpusBlobKey = "$id.txt", corpusSizeBytes = 0, corpusFilename = null, id = id)
            val outcome = h.service.process(TrainingMessage(id, "$id.txt", 0L))
            outcome shouldBe ProcessOutcome.FAILED
            h.trainings.findById(id)!!.status shouldBe TrainingStatus.FAILED
        }
    })
