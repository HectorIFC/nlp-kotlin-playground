package dev.nlpplayground.persistence

import dev.nlpplayground.testConfig
import dev.nlpplayground.training.TrainingStatus
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.time.Instant

private data class TestSetup(val db: Database, val events: TrainingEventRepository)

private fun freshSetup(): TestSetup {
    val path = File.createTempFile("playground-events-", ".db").also { it.deleteOnExit() }
    val db = PlaygroundDatabase(testConfig().copy(sqlitePath = path.absolutePath))
    return TestSetup(db.handle, TrainingEventRepository(db.handle))
}

class TrainingEventRepositoryTest :
    StringSpec({

        "events for a training are returned in insertion order" {
            val (db, repo) = freshSetup()
            val tid = "training-1"
            // We must seed a row in `trainings` first because of the FK constraint.
            // Use direct INSERT to bypass the higher-level repository in this isolated test.
            transaction(db) {
                Trainings.insert {
                    it[Trainings.id] = tid
                    it[Trainings.status] = TrainingStatus.QUEUED
                    it[Trainings.createdAt] = 0L
                    it[Trainings.updatedAt] = 0L
                }
            }
            val base = Instant.parse("2026-01-01T00:00:00Z")
            repo.record(tid, null, TrainingStatus.QUEUED, "uploaded", base)
            repo.record(tid, TrainingStatus.QUEUED, TrainingStatus.DOWNLOADING, null, base.plusSeconds(1))
            repo.record(tid, TrainingStatus.DOWNLOADING, TrainingStatus.TOKENIZING, null, base.plusSeconds(2))

            val all = repo.findByTrainingId(tid)
            all shouldHaveSize 3
            all.map { it.toStatus } shouldBe listOf(
                TrainingStatus.QUEUED,
                TrainingStatus.DOWNLOADING,
                TrainingStatus.TOKENIZING,
            )
            all[0].fromStatus shouldBe null
            all[0].detail shouldBe "uploaded"
        }
    })
