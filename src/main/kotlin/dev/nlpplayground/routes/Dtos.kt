package dev.nlpplayground.routes

import kotlinx.serialization.Serializable

@Serializable
internal data class HealthResponse(
    val status: String,
    val database: Boolean,
    val storage: Boolean,
    val rabbit: Boolean,
)

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
internal data class UploadAcceptedResponse(
    val trainingId: String,
    val status: String,
    val statusUrl: String,
    val progressUrl: String,
)

@Serializable
internal data class TrainingEventDto(
    val fromStatus: String?,
    val toStatus: String,
    val detail: String?,
    val occurredAt: Long,
)

@Serializable
internal data class TrainingDetailResponse(
    val id: String,
    val status: String,
    val corpusFilename: String?,
    val corpusSizeBytes: Long?,
    val errorMessage: String?,
    val modelBlobPrefix: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val expiresAt: Long?,
    val events: List<TrainingEventDto>,
)

@Serializable
internal data class TrainingListItem(
    val id: String,
    val status: String,
    val corpusFilename: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
internal data class TrainingListResponse(val items: List<TrainingListItem>)

@Serializable
internal data class StartSessionResponse(val sessionId: String, val name: String)

@Serializable
internal data class ErrorResponse(val error: String, val detail: String? = null)
