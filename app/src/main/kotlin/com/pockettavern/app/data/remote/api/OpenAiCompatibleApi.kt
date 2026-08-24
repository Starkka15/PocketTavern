package com.pockettavern.app.data.remote.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

/**
 * OpenAI-compatible chat completions API.
 * Works with: OpenAI, Anthropic (via compat), LM Studio, TabbyAPI, vLLM, Aphrodite,
 * TextGenWebUI (OpenAI mode), Mistral, Groq, DeepSeek, OpenRouter, etc.
 */
interface OpenAiCompatibleApi {

    @POST("v1/chat/completions")
    @Streaming
    suspend fun chatCompletionStream(@Body request: OaiChatRequest): Response<ResponseBody>

    @POST("v1/chat/completions")
    suspend fun chatCompletion(@Body request: OaiChatRequest): Response<OaiChatResponse>

    @GET("v1/models")
    suspend fun listModels(): Response<OaiModelsResponse>

    // Text completion endpoint (for APIs that support it)
    @POST("v1/completions")
    @Streaming
    suspend fun textCompletionStream(@Body request: OaiTextRequest): Response<ResponseBody>
}

@Serializable
data class OaiChatRequest(
    val model: String,
    val messages: List<OaiMessage>,
    val stream: Boolean = true,
    val temperature: Float? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    @SerialName("top_p") val topP: Float? = null,
    @SerialName("top_k") val topK: Int? = null,
    @SerialName("frequency_penalty") val frequencyPenalty: Float? = null,
    @SerialName("presence_penalty") val presencePenalty: Float? = null,
    @SerialName("repetition_penalty") val repetitionPenalty: Float? = null,
    @SerialName("min_p") val minP: Float? = null,
    @SerialName("top_a") val topA: Float? = null,
    val stop: List<String>? = null,
    val seed: Int? = null
)

@Serializable
data class OaiMessage(
    val role: String,       // "system", "user", "assistant"
    val content: String
)

@Serializable
data class OaiTextRequest(
    val model: String,
    val prompt: String,
    val stream: Boolean = true,
    val temperature: Float? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    @SerialName("top_p") val topP: Float? = null,
    // Extended sampler params (ooba/vLLM/Aphrodite/TabbyAPI accept these; null = omitted)
    @SerialName("top_k") val topK: Int? = null,
    @SerialName("min_p") val minP: Float? = null,
    @SerialName("repetition_penalty") val repetitionPenalty: Float? = null,
    @SerialName("frequency_penalty") val frequencyPenalty: Float? = null,
    @SerialName("presence_penalty") val presencePenalty: Float? = null,
    @SerialName("guidance_scale") val guidanceScale: Float? = null,
    @SerialName("smoothing_curve") val smoothingCurve: Float? = null,
    @SerialName("skip_special_tokens") val skipSpecialTokens: Boolean? = null,
    val stop: List<String>? = null
)

@Serializable
data class OaiChatResponse(
    val id: String = "",
    val choices: List<OaiChoice> = emptyList(),
    val model: String = ""
)

@Serializable
data class OaiChoice(
    val index: Int = 0,
    val message: OaiMessage? = null,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class OaiModelsResponse(
    val data: List<OaiModelInfo> = emptyList()
)

@Serializable
data class OaiModelInfo(
    val id: String,
    @SerialName("context_length") val contextLength: Int? = null
)

/** Streaming delta chunk from SSE */
@Serializable
data class OaiStreamChunk(
    val id: String = "",
    val choices: List<OaiStreamChoice> = emptyList()
)

@Serializable
data class OaiStreamChoice(
    val index: Int = 0,
    val delta: OaiStreamDelta? = null,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class OaiStreamDelta(
    val role: String? = null,
    val content: String? = null,
    @SerialName("reasoning_content") val reasoningContent: String? = null,
    val reasoning: String? = null
)
