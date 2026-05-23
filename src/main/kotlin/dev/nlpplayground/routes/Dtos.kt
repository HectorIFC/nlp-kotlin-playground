package dev.nlpplayground.routes

import kotlinx.serialization.Serializable

@Serializable
internal data class PretrainedListResponse(val available: List<String>)

@Serializable
internal data class SearchRequest(val query: String, val topK: Int = 5)

@Serializable
internal data class SearchHit(val sentence: String, val score: Float)

@Serializable
internal data class SearchResponse(val query: String, val results: List<SearchHit>)

@Serializable
internal data class TokenizeRequest(val text: String)

@Serializable
internal data class TokenizedToken(val id: Int, val text: String)

@Serializable
internal data class TokenizeResponse(val text: String, val tokens: List<TokenizedToken>)

@Serializable
internal data class SimilarityRequest(val textA: String, val textB: String)

@Serializable
internal data class SimilarityResponse(val textA: String, val textB: String, val score: Float)

@Serializable
internal data class StatusResponse(
    val sessionId: String,
    val state: String,
    val name: String? = null,
    val error: String? = null,
)

@Serializable
internal data class UploadResponse(val sessionId: String, val state: String)

@Serializable
internal data class StartSessionResponse(val sessionId: String, val name: String)

@Serializable
internal data class ErrorResponse(val error: String, val detail: String? = null)
