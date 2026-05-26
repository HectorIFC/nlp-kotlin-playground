package dev.nlpplayground.training

import dev.nlpplayground.Config
import dev.nlpplayground.messaging.TrainingMessage
import dev.nlpplayground.persistence.TrainingRepository
import dev.nlpplayground.pipeline.CorpusTrainer
import dev.nlpplayground.pipeline.Pipeline
import dev.nlpplayground.storage.BlobStorage
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant

/**
 * Outcome of a single message-processing attempt. The consumer uses it to
 * decide between ack (success / known-no-op) and nack (terminal failure).
 */
internal enum class ProcessOutcome {
    /** Pipeline ran to READY (or the training was already in a terminal state). Ack. */
    SUCCESS,

    /** Training id unknown or already terminal. Ack and discard. */
    SKIPPED,

    /** Pipeline failed; training was marked FAILED. Nack without requeue → DLQ. */
    FAILED,
}

/**
 * Orchestrates the full training pipeline for a single message.
 *
 * Idempotency contract (PRD §4.8):
 * - unknown training id → ack, discard.
 * - terminal state (READY/FAILED/EXPIRED) → ack, no-op.
 * - intermediate state (DOWNLOADING/TOKENIZING/...) → reprocess from scratch.
 *   The repository's `updateStatus` is now idempotent on same-state, so the
 *   replay walks through `DOWNLOADING → ... → READY` cleanly without
 *   tripping state-machine validation.
 *
 * Resource cleanup (PRD §4.9 / §6.7):
 * - the downloaded blob is written to a tempfile that is *always* deleted
 *   in `finally`, even on `SIGKILL`-style crashes is best-effort; this
 *   covers the normal path.
 * - the original `corpus-uploads/{id}.txt` blob is deleted only on success.
 */
internal class TrainingService(
    private val config: Config,
    private val storage: BlobStorage,
    private val trainings: TrainingRepository,
    private val trainer: PipelineTrainer = DefaultPipelineTrainer,
    private val clock: () -> Instant = Instant::now,
) {

    private val log = LoggerFactory.getLogger(TrainingService::class.java)

    fun process(message: TrainingMessage): ProcessOutcome {
        val current = trainings.findById(message.trainingId)
        if (current == null) {
            log.warn("Received message for unknown training {}, discarding", message.trainingId)
            return ProcessOutcome.SKIPPED
        }
        when (current.status) {
            TrainingStatus.READY, TrainingStatus.FAILED, TrainingStatus.EXPIRED -> {
                log.info(
                    "Training {} already in terminal state {}, skipping",
                    message.trainingId,
                    current.status,
                )
                return ProcessOutcome.SUCCESS
            }
            TrainingStatus.QUEUED -> Unit
            else -> log.warn(
                "Training {} found in intermediate state {}, reprocessing",
                message.trainingId,
                current.status,
            )
        }
        return runPipeline(message)
    }

    @Suppress("TooGenericExceptionCaught")
    private fun runPipeline(message: TrainingMessage): ProcessOutcome {
        val id = message.trainingId
        val tempFile: Path = Files.createTempFile("corpus-$id-", ".txt")
        return try {
            trainings.updateStatus(id, TrainingStatus.DOWNLOADING, detail = "downloading from MinIO")
            Files.newOutputStream(tempFile).use { sink ->
                storage.download(config.corpusBucket, message.blobKey, sink)
            }

            val corpus = Files.readString(tempFile, Charsets.UTF_8)

            trainings.updateStatus(id, TrainingStatus.TOKENIZING, detail = "training BPE")
            val pipeline = trainer.train(id, corpus)

            trainings.updateStatus(id, TrainingStatus.EMBEDDING, detail = "creating embedding table")
            // (Already done inside trainer.train; status flipped here purely for the dashboard timeline.)

            trainings.updateStatus(id, TrainingStatus.INDEXING, detail = "uploading model artifacts")
            uploadModel(id, pipeline)

            val ttl = Duration.ofHours(config.trainingTtlHours)
            trainings.updateStatus(
                id = id,
                newStatus = TrainingStatus.READY,
                detail = "model published",
                modelBlobPrefix = "$id/",
                expiresAt = clock().plus(ttl),
            )

            // Delete the source blob only after the model is durably published.
            runCatching { storage.delete(config.corpusBucket, message.blobKey) }
                .onFailure { log.warn("Failed to delete source blob {}", message.blobKey, it) }

            log.info("Training {} READY", id)
            ProcessOutcome.SUCCESS
        } catch (e: Exception) {
            log.error("Pipeline failed for training {}", id, e)
            trainings.updateStatus(
                id = id,
                newStatus = TrainingStatus.FAILED,
                errorMessage = e.message ?: e::class.java.simpleName,
            )
            ProcessOutcome.FAILED
        } finally {
            runCatching { Files.deleteIfExists(tempFile) }
                .onFailure { log.warn("Failed to delete tempfile {}", tempFile, it) }
        }
    }

    private fun uploadModel(id: String, pipeline: Pipeline) {
        val prefix = "$id/"
        val tessJson = withTempFile("tessera-", ".json") { tmp ->
            pipeline.tokenizer.save(tmp.toString())
            Files.readAllBytes(tmp)
        }
        // Mosaic's EmbeddingTable.save() writes the binary AND a sidecar
        // `<path>.meta.json` (PRD §4.5 / Mosaic EmbeddingFormat.METADATA_EXTENSION).
        // We need to capture both files; the loader on the other side reads the
        // sidecar to verify the SHA-256 checksum before decoding the .bin payload.
        val (mosaicBin, mosaicMeta) = withTempFile("mosaic-", ".bin") { tmp ->
            pipeline.embeddings.save(tmp.toString())
            val metaPath = Path.of("$tmp.meta.json")
            try {
                Files.readAllBytes(tmp) to Files.readAllBytes(metaPath)
            } finally {
                runCatching { Files.deleteIfExists(metaPath) }
            }
        }
        storage.upload(config.modelsBucket, "${prefix}tessera.json", tessJson, "application/json")
        storage.upload(config.modelsBucket, "${prefix}mosaic.bin", mosaicBin, "application/octet-stream")
        storage.upload(config.modelsBucket, "${prefix}mosaic.bin.meta.json", mosaicMeta, "application/json")
        // Pipeline sentences are the *cleaned* corpus split — persist them so
        // the search endpoint can rebuild the Pipeline later from MinIO without
        // re-running the splitter (which would need the original raw bytes).
        val sentenceBlob = pipeline.sentences.joinToString("\n").toByteArray(Charsets.UTF_8)
        storage.upload(config.modelsBucket, "${prefix}corpus.txt", sentenceBlob, "text/plain; charset=utf-8")
    }

    private inline fun <T> withTempFile(prefix: String, suffix: String, block: (Path) -> T): T {
        val tmp = Files.createTempFile(prefix, suffix)
        return try {
            block(tmp)
        } finally {
            runCatching { Files.deleteIfExists(tmp) }
        }
    }
}

/**
 * Indirection over [CorpusTrainer.train] so tests can swap in a fake that
 * skips the (slow) BPE training step.
 */
internal fun interface PipelineTrainer {
    fun train(id: String, corpus: String): Pipeline
}

internal val DefaultPipelineTrainer = PipelineTrainer { id, corpus -> CorpusTrainer.train(id, corpus) }
