package dev.nlpplayground.persistence

import dev.nlpplayground.Config
import dev.nlpplayground.testConfig
import dev.nlpplayground.training.TrainingStatus
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.util.UUID

private fun freshDatabase(): PlaygroundDatabase {
    // Each test gets its own on-disk SQLite file under /tmp so WAL is exercised
    // and the schema lives in a sandboxed location that the test cleans up.
    val path = java.io.File.createTempFile("playground-test-", ".db").also { it.deleteOnExit() }
    val config: Config = testConfig().copy(sqlitePath = path.absolutePath)
    return PlaygroundDatabase(config)
}

private fun freshRepos(): Pair<TrainingRepository, TrainingEventRepository> {
    val db = freshDatabase()
    val events = TrainingEventRepository(db.handle)
    val repo = TrainingRepository(db.handle, events)
    return repo to events
}

class TrainingRepositoryTest :
    StringSpec({

        "create persists a QUEUED training and emits the initial event" {
            val (repo, events) = freshRepos()
            val id = UUID.randomUUID().toString()
            val training = repo.create(
                corpusBlobKey = "corpus-uploads/$id.txt",
                corpusSizeBytes = 1024,
                corpusFilename = "alice.txt",
                id = id,
            )
            training.status shouldBe TrainingStatus.QUEUED
            training.corpusBlobKey shouldBe "corpus-uploads/$id.txt"
            repo.findById(id).shouldNotBeNull()
            val history = events.findByTrainingId(id)
            history shouldHaveSize 1
            history[0].fromStatus shouldBe null
            history[0].toStatus shouldBe TrainingStatus.QUEUED
        }

        "happy-path chain produces 6 events (QUEUED + 5 transitions to READY)" {
            val (repo, events) = freshRepos()
            val id = UUID.randomUUID().toString()
            repo.create("corpus/$id.txt", 100, "shakespeare.txt", id)
            for (target in listOf(
                TrainingStatus.DOWNLOADING,
                TrainingStatus.TOKENIZING,
                TrainingStatus.EMBEDDING,
                TrainingStatus.INDEXING,
                TrainingStatus.READY,
            )) {
                repo.updateStatus(id, target).shouldNotBeNull()
            }
            repo.findById(id)!!.status shouldBe TrainingStatus.READY
            events.findByTrainingId(id) shouldHaveSize 6
        }

        "updateStatus rejects an invalid transition" {
            val (repo, _) = freshRepos()
            val id = UUID.randomUUID().toString()
            repo.create("corpus/$id.txt", 100, null, id)
            // QUEUED → READY is not a valid jump.
            shouldThrow<IllegalArgumentException> {
                repo.updateStatus(id, TrainingStatus.READY)
            }
        }

        "updateStatus for unknown id returns null without inserting events" {
            val (repo, events) = freshRepos()
            repo.updateStatus("nope", TrainingStatus.DOWNLOADING) shouldBe null
            events.findByTrainingId("nope") shouldHaveSize 0
        }

        "findAll filters by status and respects newest-first ordering" {
            val (repo, _) = freshRepos()
            val a = repo.create("a", 1, null).id
            Thread.sleep(2)
            val b = repo.create("b", 1, null).id
            Thread.sleep(2)
            val c = repo.create("c", 1, null).id
            repo.updateStatus(a, TrainingStatus.FAILED, errorMessage = "boom")

            val queued = repo.findAll(TrainingFilter(statuses = setOf(TrainingStatus.QUEUED)))
            queued.map { it.id } shouldBe listOf(c, b)

            val failed = repo.findAll(TrainingFilter(statuses = setOf(TrainingStatus.FAILED)))
            failed.map { it.id } shouldBe listOf(a)
        }

        "markExpired moves READY trainings past TTL to EXPIRED" {
            val (repo, _) = freshRepos()
            val id = UUID.randomUUID().toString()
            repo.create("blob/$id.txt", 100, null, id)
            for (target in listOf(
                TrainingStatus.DOWNLOADING,
                TrainingStatus.TOKENIZING,
                TrainingStatus.EMBEDDING,
                TrainingStatus.INDEXING,
            )) {
                repo.updateStatus(id, target)
            }
            // Mark READY with an expiry already in the past.
            repo.updateStatus(id, TrainingStatus.READY, expiresAt = Instant.now().minusSeconds(10))

            val expired = repo.markExpired()
            expired shouldBe 1
            repo.findById(id)!!.status shouldBe TrainingStatus.EXPIRED
        }
    })
