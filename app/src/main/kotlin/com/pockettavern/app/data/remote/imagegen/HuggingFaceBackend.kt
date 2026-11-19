package com.pockettavern.app.data.remote.imagegen

import java.util.Base64
import com.pockettavern.app.data.local.SettingsDataStore
import com.pockettavern.app.domain.model.ImageGenConfig
import com.pockettavern.app.domain.model.ForgeGenerationParams
import com.pockettavern.app.domain.model.GenerationState
import com.pockettavern.app.domain.model.ImageGenBackendType
import com.pockettavern.app.domain.model.ImageGenCapabilities
import com.pockettavern.app.domain.model.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Serializable
private data class HfRequest(
    val inputs: String,
    val parameters: HfParameters? = null
)

@Serializable
private data class HfParameters(
    val negative_prompt: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val num_inference_steps: Int? = null,
    val guidance_scale: Float? = null,
    val seed: Int? = null
)

class HuggingFaceBackend private constructor(
    private val httpClient: OkHttpClient,
    private val configProvider: suspend () -> ImageGenConfig
) : ImageGenBackend {

    constructor(httpClient: OkHttpClient, settingsDataStore: SettingsDataStore) :
        this(httpClient, { settingsDataStore.getImageGenConfig() })

    internal constructor(httpClient: OkHttpClient, config: ImageGenConfig) :
        this(httpClient, { config })

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = false; explicitNulls = false }

    override val type = ImageGenBackendType.HUGGINGFACE

    override val capabilities = ImageGenCapabilities(
        supportsSteps = true,
        supportsCfgScale = true,
        supportsSeed = true,
        supportsNegativePrompt = true,
        supportsResolutionPresets = true,
        supportsCustomWorkflow = true,
        requiresApiKey = true
    )

    override suspend fun testConnection(): Result<Boolean> {
        val config = configProvider()
        if (config.huggingfaceApiKey.isBlank()) {
            return Result.Error(Exception("No API key configured"))
        }
        return try {
            val request = Request.Builder()
                .url("https://huggingface.co/api/whoami-v2")
                .addHeader("Authorization", "Bearer ${config.huggingfaceApiKey}")
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
            "stabilityai/stable-diffusion-xl-base-1.0",
            "runwayml/stable-diffusion-v1-5",
            "CompVis/stable-diffusion-v1-4",
            "stabilityai/stable-diffusion-2-1"
        ))
    }

    override fun generate(params: ForgeGenerationParams): Flow<GenerationState> = flow {
        emit(GenerationState.Starting)
        try {
            val config = configProvider()
            if (config.huggingfaceApiKey.isBlank()) {
                emit(GenerationState.Error("No HuggingFace API key configured"))
                return@flow
            }

            val hfParams = HfParameters(
                negative_prompt = params.negativePrompt.ifBlank { null },
                width = params.width,
                height = params.height,
                num_inference_steps = params.steps,
                guidance_scale = params.cfgScale,
                seed = if (params.seed == -1) null else params.seed
            )

            val modelId = config.huggingfaceModel.ifBlank { "stabilityai/stable-diffusion-xl-base-1.0" }
            val customTemplate = CustomImageTemplate.checkedSelected(config, params, type)
            val body = if (customTemplate != null) {
                CustomImageTemplate.renderJson(customTemplate, params, modelId)
            } else {
                json.encodeToString(HfRequest.serializer(), HfRequest(
                    inputs = params.prompt,
                    parameters = hfParams
                ))
            }
            val request = Request.Builder()
                .url("https://api-inference.huggingface.co/models/$modelId")
                .addHeader("Authorization", "Bearer ${config.huggingfaceApiKey}")
                .addHeader("Content-Type", "application/json")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()

            val imageBytes = withContext(Dispatchers.IO) {
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val error = response.body?.string() ?: "HTTP ${response.code}"
                        throw Exception(error)
                    }
                    val responseBody = response.body ?: throw Exception("Empty response")
                    if (responseBody.contentType()?.subtype?.contains("json", ignoreCase = true) == true) {
                        throw Exception("Expected image data but received JSON")
                    }
                    responseBody.bytes().takeIf { it.isNotEmpty() } ?: throw Exception("Empty image response")
                }
            }

            val base64 = Base64.getEncoder().encodeToString(imageBytes)
            emit(GenerationState.Complete(imageBase64 = base64))
        } catch (e: Exception) {
            emit(GenerationState.Error(e.message ?: "Generation failed"))
        }
    }

    override suspend fun interrupt(): Result<Unit> = Result.Success(Unit)
}
