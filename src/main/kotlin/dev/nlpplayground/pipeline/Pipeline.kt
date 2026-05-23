package dev.nlpplayground.pipeline

import dev.mosaic.EmbeddingTable
import dev.tessera.BpeTokenizer

/**
 * A ready-to-explore NLP pipeline: a Tessera tokenizer paired with a Mosaic
 * embedding table over the same vocabulary, plus the corpus sentences that
 * semantic search will index against.
 *
 * Pipelines are immutable; replacing one means evicting the old session.
 */
internal data class Pipeline(
    val name: String,
    val tokenizer: BpeTokenizer,
    val embeddings: EmbeddingTable,
    val sentences: List<String>,
)

internal enum class PipelineState {
    TRAINING,
    READY,
    ERROR,
}
