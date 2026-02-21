package com.pockettavern.app.data.remote.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

/** Anthropic Claude API */
interface AnthropicApi {

    @POST("v1/messages")
    @Streaming
    suspend fun messagesStream(@Body request: AnthropicRequest): Response<ResponseBody>

    @POST("v1/messages")
    suspend fun messages(@Body request: AnthropicRequest): Response<AnthropicResponse>

    @GET("v1/models")
    suspend fun listModels(): Response<AnthropicModelsResponse>
}

@Serializable
data class AnthropicRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int = 2048,
    val messages: List<AnthropicMessage>,
    val system: String? = null,
    val stream: Boolean = true,
    val temperature: Float? = null,
    @SerialName("top_p") val topP: Float? = null,
    @SerialName("top_k") val topK: Int? = null,
    val stop_sequences: List<String>? = null
)

@Serializable
data class AnthropicMessage(
    val role: String,   // "user" or "assistant"
    val content: String
)

@Serializable
data class AnthropicResponse(
    val id: String = "",
    val type: String = "",
    val role: String = "",
    val content: List<AnthropicContent> = emptyList(),
    val model: String = "",
    @SerialName("stop_reason") val stopReason: String? = null
)

@Serializable
data class AnthropicContent(
    val type: String = "text",
    val text: String = ""
)

@Serializable
data class AnthropicModelsResponse(
    val data: List<AnthropicModelInfo> = emptyList()
)

@Serializable
data class AnthropicModelInfo(
    val id: String,
    @SerialName("display_name") val displayName: String = ""
)

/** Streaming SSE event types from Anthropic */
@Serializable
data class AnthropicStreamEvent(
    val type: String = "",
    val index: Int? = null,
    val delta: AnthropicDelta? = null,
    val message: AnthropicResponse? = null
)

@Serializable
data class AnthropicDelta(
    val type: String = "",
    val text: String = ""
)
