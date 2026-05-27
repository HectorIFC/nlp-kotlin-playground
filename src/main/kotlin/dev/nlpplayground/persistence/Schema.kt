package dev.nlpplayground.persistence

import dev.nlpplayground.training.TrainingStatus
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.Table

private const val ID_LENGTH = 36 // UUID v4 string length
private const val STATUS_LENGTH = 16
private const val FILENAME_LENGTH = 255
private const val BLOB_KEY_LENGTH = 255

/**
 * `trainings` — one row per upload. The primary record of state for a
 * training pipeline.
 * Indexes match the dashboard's hot queries:
 *
 * - `status` for "show me all QUEUED/DOWNLOADING" filtering
 * - `created_at DESC` for the default newest-first listing
 */
internal object Trainings : Table("trainings") {
    val id = varchar("id", ID_LENGTH)
    val status = enumerationByName("status", STATUS_LENGTH, TrainingStatus::class)
    val corpusBlobKey = varchar("corpus_blob_key", BLOB_KEY_LENGTH).nullable()
    val corpusSizeBytes = long("corpus_size_bytes").nullable()
    val corpusFilename = varchar("corpus_filename", FILENAME_LENGTH).nullable()
    val modelBlobPrefix = varchar("model_blob_prefix", BLOB_KEY_LENGTH).nullable()
    val errorMessage = text("error_message").nullable()
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")
    val expiresAt = long("expires_at").nullable()

    override val primaryKey: PrimaryKey = PrimaryKey(id)

    init {
        index("idx_trainings_status", isUnique = false, status)
        index("idx_trainings_created_at", isUnique = false, createdAt)
    }
}

/**
 * `training_events` — append-only audit log of every status transition.
 * `from_status` is NULL for the initial QUEUED insertion; all subsequent
 * rows carry both ends of the transition so timelines can be reconstructed
 * without joining back to `trainings`.
 */
internal object TrainingEvents : LongIdTable("training_events", "id") {
    val trainingId = varchar("training_id", ID_LENGTH).references(Trainings.id)
    val fromStatus = enumerationByName("from_status", STATUS_LENGTH, TrainingStatus::class).nullable()
    val toStatus = enumerationByName("to_status", STATUS_LENGTH, TrainingStatus::class)
    val detail = text("detail").nullable()
    val occurredAt = long("occurred_at")

    init {
        index("idx_events_training_id", isUnique = false, trainingId, occurredAt)
    }
}
