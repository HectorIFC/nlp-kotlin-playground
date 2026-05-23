package dev.nlpplayground.pipeline

import dev.mosaic.EmbeddingTable
import dev.mosaic.Initializer
import dev.tessera.Trainer
import dev.tessera.TrainingConfig

/**
 * Builds a [Pipeline] from a user-provided corpus by training a fresh Tessera
 * BPE tokenizer and a freshly-initialized Mosaic embedding table sized to the
 * resulting vocabulary.
 *
 * No persistence: this is for uploaded corpora that live only in the session
 * cache. Pre-trained corpora go through [PretrainedLoader] instead.
 */
internal object CorpusTrainer {

    private const val DEFAULT_NUM_MERGES = 2000
    private const val DEFAULT_EMBEDDING_DIM = 128
    private const val DEFAULT_SEED = 42L
    private const val MIN_SENTENCE_LENGTH = 10

    fun train(
        name: String,
        corpus: String,
        numMerges: Int = DEFAULT_NUM_MERGES,
        embeddingDim: Int = DEFAULT_EMBEDDING_DIM,
        seed: Long = DEFAULT_SEED,
    ): Pipeline {
        require(corpus.isNotBlank()) { "Corpus must not be empty." }

        val tokenizer = Trainer(
            TrainingConfig(
                numMerges = numMerges,
                verbose = false,
            ),
        ).train(corpus)

        val embeddings = EmbeddingTable.create(
            vocabSize = tokenizer.vocabSize,
            embeddingDim = embeddingDim,
            initializer = Initializer.uniformDefault(seed = seed),
        )

        val sentences = splitIntoSentences(corpus)

        return Pipeline(
            name = name,
            tokenizer = tokenizer,
            embeddings = embeddings,
            sentences = sentences,
        )
    }

    private val SENTENCE_BOUNDARY = Regex("[.!?\\n]+")

    private fun splitIntoSentences(corpus: String): List<String> = corpus.split(SENTENCE_BOUNDARY)
        .map { it.trim() }
        .filter { it.length > MIN_SENTENCE_LENGTH }
}
