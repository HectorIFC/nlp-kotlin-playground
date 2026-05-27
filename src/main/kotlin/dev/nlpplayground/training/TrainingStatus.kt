package dev.nlpplayground.training

/**
 * Lifecycle states of a training pipeline .
 *
 * - `QUEUED`        — message published, not yet picked up by a worker.
 * - `DOWNLOADING`   — worker is pulling the corpus blob from MinIO.
 * - `TOKENIZING`    — Tessera is training the BPE tokenizer.
 * - `EMBEDDING`     — Mosaic is creating the embedding table.
 * - `INDEXING`      — sentence vectors pre-computed; artifacts staged for upload.
 * - `READY`         — model uploaded, available for search/explore.
 * - `FAILED`        — any step blew up; message routed to the DLQ for inspection.
 * - `EXPIRED`       — `READY` training aged past TTL; model artifacts deletable.
 *
 * `FAILED` and `EXPIRED` are terminal; everything else either advances or
 * jumps to `FAILED`. The full set of allowed transitions lives in
 * [TrainingStateMachine].
 */
internal enum class TrainingStatus {
    QUEUED,
    DOWNLOADING,
    TOKENIZING,
    EMBEDDING,
    INDEXING,
    READY,
    FAILED,
    EXPIRED,
}
