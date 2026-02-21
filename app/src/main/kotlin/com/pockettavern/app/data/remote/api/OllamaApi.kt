package com.pockettavern.app.data.remote.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

/** Ollama local inference API */
interface OllamaApi {

    @POST("api/generate")
    @Streaming
    suspend fun generateStream(@Body request: OllamaGenerateRequest): Response<ResponseBody>

    @POST("api/generate")
    suspend fun generate(@Body request: OllamaGenerateRequest): Response<OllamaGenerateResponse>

    @POST("api/chat")
    @Streaming
    suspend fun chatStream(@Body request: OllamaChatRequest): Response<ResponseBody>

    @GET("api/tags")
    suspend fun listModels(): Response<OllamaTagsResponse>
}

@Serializable
data class OllamaGenerateRequest(
    val model: String,
    val prompt: String,
    val stream: Boolean = true,
    val options: OllamaOptions = OllamaOptions(),
    val system: String? = null,
    @SerialName("raw") val raw: Boolean = false
)

@Serializable
data class OllamaChatRequest(
    val model: String,
    val messages: List<OllamaChatMessage>,
    val stream: Boolean = true,
    val options: OllamaOptions = OllamaOptions()
)

@Serializable
data class OllamaChatMessage(
    val role: String,   // "system", "user", "assistant"
    val content: String
)

@Serializable
data class OllamaOptions(
    val temperature: Float? = null,
    @SerialName("top_p") val topP: Float? = null,
    @SerialName("top_k") val topK: Int? = null,
    @SerialName("repeat_penalty") val repeatPenalty: Float? = null,
    @SerialName("num_predict") val numPredict: Int? = null,
    @SerialName("num_ctx") val numCtx: Int? = null,
    val mirostat: Int? = null,
    @SerialName("mirostat_tau") val mirostatTau: Float? = null,
    @SerialName("mirostat_eta") val mirostatEta: Float? = null,
    val stop: List<String>? = null
)

@Serializable
data class OllamaGenerateResponse(
    val model: String = "",
    val response: String = "",
    val done: Boolean = false
)

@Serializable
data class OllamaTagsResponse(
    val models: List<OllamaModel> = emptyList()
)

@Serializable
data class OllamaModel(
    val name: String,
    val size: Long = 0
)

/** Streaming chunk from Ollama */
@Serializable
data class OllamaStreamChunk(
    val model: String = "",
    val response: String = "",
    val message: OllamaChatMessage? = null,
    val done: Boolean = false,
    val error: String? = null
)
