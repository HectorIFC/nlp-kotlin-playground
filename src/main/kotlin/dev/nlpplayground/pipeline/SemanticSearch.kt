package dev.nlpplayground.pipeline

import dev.mosaic.TesseraEmbeddings
import dev.mosaic.VectorOps

internal data class SearchResult(val sentence: String, val score: Float)

internal data class TokenView(val id: Int, val text: String)

/**
 * Cosine-similarity search over mean-pooled sentence embeddings, plus a few
 * companion utilities that share the same [TesseraEmbeddings] instance per
 * pipeline.
 */
internal object SemanticSearch {

    private const val DEFAULT_TOP_K = 5

    fun search(pipeline: Pipeline, query: String, topK: Int = DEFAULT_TOP_K): List<SearchResult> {
        require(query.isNotBlank()) { "Query must not be blank." }
        require(topK > 0) { "topK must be positive." }
        if (pipeline.sentences.isEmpty()) return emptyList()

        val combo = TesseraEmbeddings(pipeline.tokenizer, pipeline.embeddings)
        val queryVector = combo.encodeMeanPooled(query)

        return pipeline.sentences
            .asSequence()
            .map { sentence ->
                val sentenceVector = combo.encodeMeanPooled(sentence)
                SearchResult(sentence, VectorOps.cosineSimilarity(queryVector, sentenceVector))
            }
            .sortedByDescending { it.score }
            .take(topK)
            .toList()
    }

    fun similarity(pipeline: Pipeline, textA: String, textB: String): Float {
        require(textA.isNotBlank() && textB.isNotBlank()) { "Both texts must be non-blank." }
        val combo = TesseraEmbeddings(pipeline.tokenizer, pipeline.embeddings)
        return VectorOps.cosineSimilarity(combo.encodeMeanPooled(textA), combo.encodeMeanPooled(textB))
    }

    fun tokenize(pipeline: Pipeline, text: String): List<TokenView> {
        require(text.isNotBlank()) { "Text must not be blank." }
        val ids = pipeline.tokenizer.encode(text)
        return ids.map { id -> TokenView(id = id, text = pipeline.tokenizer.tokenAsString(id)) }
    }
}
