package com.pockettavern.app.data.remote.imagegen

import android.util.Base64
import com.pockettavern.app.data.local.SettingsDataStore
import com.pockettavern.app.domain.model.ForgeGenerationParams
import com.pockettavern.app.domain.model.GenerationState
import com.pockettavern.app.domain.model.ImageGenBackendType
import com.pockettavern.app.domain.model.ImageGenCapabilities
import com.pockettavern.app.domain.model.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Serializable
private data class StabilityRequest(
    @SerialName("text_prompts")
    val textPrompts: List<StabilityPrompt>,
    val width: Int = 512,
    val height: Int = 512,
    val steps: Int = 30,
    @SerialName("cfg_scale")
    val cfgScale: Float = 7f,
    val seed: Long = 0,
    val samples: Int = 1
)

@Serializable
private data class StabilityPrompt(
    val text: String,
    val weight: Float = 1f
)

@Serializable
private data class StabilityResponse(
    val artifacts: List<StabilityArtifact>? = null,
    val message: String? = null
)

@Serializable
private data class StabilityArtifact(
    val base64: String? = null,
    @SerialName("finish_reason")
    val finishReason: String? = null
)

class StabilityBackend(
    private val httpClient: OkHttpClient,
    private val settingsDataStore: SettingsDataStore
) : ImageGenBackend {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override val type = ImageGenBackendType.STABILITY

    override val capabilities = ImageGenCapabilities(
        supportsSteps = true,
        supportsCfgScale = true,
        supportsSeed = true,
        supportsNegativePrompt = true,
        supportsResolutionPresets = true,
        requiresApiKey = true
    )

    override suspend fun testConnection(): Result<Boolean> {
        val config = settingsDataStore.getImageGenConfig()
        if (config.stabilityApiKey.isBlank()) {
            return Result.Error(Exception("No API key configured"))
        }
        return try {
            val request = Request.Builder()
                .url("https://api.stability.ai/v1/user/account")
                .addHeader("Authorization", "Bearer ${config.stabilityApiKey}")
                .get()
                .build()
            withContext(Dispatchers.IO) {
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) Result.Success(true)
                    else Result.Error(Exception("HTTP ${response.code}: ${response.message}"))
                }
            }
        } catch (e: Exception) {
            Result.Error(Exception("Connection failed: ${e.message}", e))
        }
    }

    override suspend fun getSamplers(): Result<List<String>> = Result.Success(emptyList())
    override suspend fun getSchedulers(): Result<List<String>> = Result.Success(emptyList())
    override suspend fun getModels(): Result<List<String>> {
        return Result.Success(listOf(
            "stable-diffusion-xl-1024-v1-0",
            "stable-diffusion-v1-6"
        ))
    }

    override fun generate(params: ForgeGenerationParams): Flow<GenerationState> = flow {
        emit(GenerationState.Starting)
        try {
            val config = settingsDataStore.getImageGenConfig()
            if (config.stabilityApiKey.isBlank()) {
                emit(GenerationState.Error("No Stability AI API key configured"))
                return@flow
            }

            // Stability requires dimensions to be multiples of 64
            val width = (params.width / 64) * 64
            val height = (params.height / 64) * 64

            val prompts = mutableListOf(StabilityPrompt(text = params.prompt, weight = 1f))
            if (params.negativePrompt.isNotBlank()) {
                prompts.add(StabilityPrompt(text = params.negativePrompt, weight = -1f))
            }

            val body = json.encodeToString(StabilityRequest.serializer(), StabilityRequest(
                textPrompts = prompts,
                width = width.coerceIn(512, 1024),
                height = height.coerceIn(512, 1024),
                steps = params.steps,
                cfgScale = params.cfgScale,
                seed = if (params.seed == -1) 0 else params.seed.toLong(),
                samples = 1
            ))

            val engineId = "stable-diffusion-xl-1024-v1-0"
            val request = Request.Builder()
                .url("https://api.stability.ai/v1/generation/$engineId/text-to-image")
                .addHeader("Authorization", "Bearer ${config.stabilityApiKey}")
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()

            val responseBody = withContext(Dispatchers.IO) {
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val error = response.body?.string() ?: "HTTP ${response.code}"
                        throw Exception(error)
                    }
                    response.body?.string() ?: throw Exception("Empty response")
                }
            }

            val parsed = json.decodeFromString(StabilityResponse.serializer(), responseBody)
            val imageBase64 = parsed.artifacts?.firstOrNull()?.base64
            if (imageBase64 != null) {
                emit(GenerationState.Complete(imageBase64 = imageBase64))
            } else {
                emit(GenerationState.Error(parsed.message ?: "No image returned"))
            }
        } catch (e: Exception) {
            emit(GenerationState.Error(e.message ?: "Generation failed"))
        }
    }

    override suspend fun interrupt(): Result<Unit> = Result.Success(Unit)
}
