package dev.nlpplayground.training

import java.time.Instant

/**
 * Domain view of a row in the `trainings` SQLite table. Repository methods
 * return this immutable shape so callers don't have to learn Exposed's DAO
 * surface; we keep ResultRow access localized to the repository.
 *
 * Field mapping mirrors PRD §4.3 — see [dev.nlpplayground.persistence.Trainings]
 * for the column definitions.
 */
internal data class Training(
    val id: String,
    val status: TrainingStatus,
    val corpusBlobKey: String?,
    val corpusSizeBytes: Long?,
    val corpusFilename: String?,
    val modelBlobPrefix: String?,
    val errorMessage: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val expiresAt: Instant?,
)

/**
 * One row from `training_events`. Append-only; the repository never updates
 * an event in place. Ordering inside a training is by [occurredAt] then [id]
 * (auto-increment), which ties identical millis to insert order.
 */
internal data class TrainingEvent(
    val id: Long,
    val trainingId: String,
    val fromStatus: TrainingStatus?,
    val toStatus: TrainingStatus,
    val detail: String?,
    val occurredAt: Instant,
)
