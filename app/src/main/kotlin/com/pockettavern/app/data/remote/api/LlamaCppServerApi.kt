package com.pockettavern.app.data.remote.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

/** llama.cpp built-in HTTP server API */
interface LlamaCppServerApi {

    @POST("completion")
    @Streaming
    suspend fun completionStream(@Body request: LlamaCppRequest): Response<ResponseBody>

    @POST("completion")
    suspend fun completion(@Body request: LlamaCppRequest): Response<LlamaCppResponse>

    @GET("props")
    suspend fun getProps(): Response<LlamaCppProps>
}

@Serializable
data class LlamaCppRequest(
    val prompt: String,
    @SerialName("n_predict") val nPredict: Int = 200,
    val temperature: Float = 0.8f,
    @SerialName("top_p") val topP: Float = 0.95f,
    @SerialName("top_k") val topK: Int = 40,
    @SerialName("min_p") val minP: Float = 0.05f,
    @SerialName("repeat_penalty") val repeatPenalty: Float = 1.1f,
    @SerialName("repeat_last_n") val repeatLastN: Int = 64,
    @SerialName("frequency_penalty") val frequencyPenalty: Float = 0f,
    @SerialName("presence_penalty") val presencePenalty: Float = 0f,
    @SerialName("typical_p") val typicalP: Float = 1.0f,
    @SerialName("dry_multiplier") val dryMultiplier: Float = 0f,
    @SerialName("dry_base") val dryBase: Float = 1.75f,
    @SerialName("dry_allowed_length") val dryAllowedLength: Int = 2,
    @SerialName("dry_penalty_last_n") val dryPenaltyLastN: Int = 0,
    @SerialName("xtc_threshold") val xtcThreshold: Float = 0.1f,
    @SerialName("xtc_probability") val xtcProbability: Float = 0f,
    val mirostat: Int = 0,
    @SerialName("mirostat_tau") val mirostatTau: Float = 5f,
    @SerialName("mirostat_eta") val mirostatEta: Float = 0.1f,
    val stop: List<String> = emptyList(),
    val stream: Boolean = true,
    val seed: Int = -1,
    @SerialName("cache_prompt") val cachePrompt: Boolean = true
)

@Serializable
data class LlamaCppResponse(
    val content: String = "",
    @SerialName("stop_type") val stopType: String = "",
    @SerialName("tokens_predicted") val tokensPredicted: Int = 0
)

@Serializable
data class LlamaCppStreamChunk(
    val content: String = "",
    val stop: Boolean = false,
    @SerialName("tokens_predicted") val tokensPredicted: Int = 0
)

@Serializable
data class LlamaCppProps(
    @SerialName("default_generation_settings") val defaultSettings: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap()
)
