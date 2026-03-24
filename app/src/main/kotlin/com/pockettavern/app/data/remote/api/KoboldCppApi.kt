package com.pockettavern.app.data.remote.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

/** KoboldCpp / KoboldAI text generation API */
interface KoboldCppApi {

    @POST("api/v1/generate")
    suspend fun generate(@Body request: KoboldGenerateRequest): Response<KoboldGenerateResponse>

    @POST("api/extra/generate/stream")
    @Streaming
    suspend fun generateStream(@Body request: KoboldGenerateRequest): Response<ResponseBody>

    @POST("api/extra/abort")
    suspend fun abort(): Response<ResponseBody>

    @GET("api/v1/model")
    suspend fun getModel(): Response<KoboldModelResponse>

    @GET("api/extra/true_max_context_length")
    suspend fun getMaxContextLength(): Response<KoboldContextResponse>
}

@Serializable
data class KoboldGenerateRequest(
    val prompt: String,
    @SerialName("max_length") val maxLength: Int = 200,
    @SerialName("max_context_length") val maxContextLength: Int = 4096,
    @SerialName("min_length") val minLength: Int = 0,
    val temperature: Float = 0.7f,
    @SerialName("top_p") val topP: Float = 0.92f,
    @SerialName("top_k") val topK: Int = 0,
    @SerialName("top_a") val topA: Float = 0f,
    @SerialName("min_p") val minP: Float = 0f,
    val typical: Float = 1f,
    val tfs: Float = 1f,
    @SerialName("rep_pen") val repPen: Float = 1.0f,
    @SerialName("rep_pen_range") val repPenRange: Int = 0,
    @SerialName("rep_pen_slope") val repPenSlope: Float = 1f,
    @SerialName("dry_multiplier") val dryMultiplier: Float = 0f,
    @SerialName("dry_base") val dryBase: Float = 1.75f,
    @SerialName("dry_allowed_length") val dryAllowedLength: Int = 2,
    @SerialName("dry_penalty_last_n") val dryPenaltyLastN: Int = 0,
    @SerialName("xtc_threshold") val xtcThreshold: Float = 0.1f,
    @SerialName("xtc_probability") val xtcProbability: Float = 0f,
    @SerialName("smoothing_factor") val smoothingFactor: Float = 0f,
    val mirostat: Int = 0,
    @SerialName("mirostat_tau") val mirostatTau: Float = 5f,
    @SerialName("mirostat_eta") val mirostatEta: Float = 0.1f,
    @SerialName("stop_sequence") val stopSequence: List<String> = emptyList(),
    @SerialName("sampler_order") val samplerOrder: List<Int>? = null,
    val grammar: String? = null,
    @SerialName("trim_stop") val trimStop: Boolean = true,
    @SerialName("ban_eos_token") val banEosToken: Boolean = false,
    @SerialName("add_bos_token") val addBosToken: Boolean = true,
    val stream: Boolean = false
)

@Serializable
data class KoboldGenerateResponse(
    val results: List<KoboldResult> = emptyList()
)

@Serializable
data class KoboldResult(
    val text: String = ""
)

@Serializable
data class KoboldModelResponse(
    val result: String = ""
)

@Serializable
data class KoboldContextResponse(
    val value: Int = 4096
)
