package dev.nlpplayground.messaging

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire payload published on `training.exchange` and consumed off
 * `training.queue`. Matches PRD §4.5 exactly.
 *
 * The keys are snake_case so they read naturally in management-UI inspection
 * and survive consumer rewrites in other languages, should that ever happen.
 */
@Serializable
internal data class TrainingMessage(
    @SerialName("training_id") val trainingId: String,
    @SerialName("blob_key") val blobKey: String,
    @SerialName("submitted_at") val submittedAt: Long,
)
