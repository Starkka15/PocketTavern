package com.pockettavern.app.data.remote.imagegen

import com.pockettavern.app.data.local.SettingsDataStore
import com.pockettavern.app.domain.model.ImageGenConfig
import com.pockettavern.app.data.remote.api.ForgeApi
import com.pockettavern.app.data.remote.dto.forge.Img2ImgRequest
import com.pockettavern.app.data.remote.dto.forge.SetOptionsRequest
import com.pockettavern.app.data.remote.dto.forge.Txt2ImgRequest
import com.pockettavern.app.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class SdWebuiBackend private constructor(
    private val apiProvider: () -> ForgeApi,
    private val httpClient: OkHttpClient,
    private val configProvider: suspend () -> ImageGenConfig
) : ImageGenBackend {

    constructor(apiProvider: () -> ForgeApi, httpClient: OkHttpClient, settingsDataStore: SettingsDataStore) :
        this(apiProvider, httpClient, { settingsDataStore.getImageGenConfig() })

    internal constructor(apiProvider: () -> ForgeApi, httpClient: OkHttpClient, config: ImageGenConfig) :
        this(apiProvider, httpClient, { config })

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val api: ForgeApi get() = apiProvider()

    override val type = ImageGenBackendType.SD_WEBUI

    override val capabilities = ImageGenCapabilities(
        supportsSamplers = true,
        supportsSchedulers = false,
        supportsModels = true,
        supportsSteps = true,
        supportsCfgScale = true,
        supportsSeed = true,
        supportsNegativePrompt = true,
        supportsImg2Img = true,
        supportsClipSkip = true,
        supportsVae = true,
        supportsResolutionPresets = true,
        supportsProgress = true,
        supportsCustomWorkflow = true,
        requiresUrl = true
    )

    override suspend fun testConnection(): Result<Boolean> {
        return try {
            api.getOptions()
            Result.Success(true)
        } catch (e: Exception) {
            Result.Error(Exception("Connection failed: ${e.message}", e))
        }
    }

    override suspend fun getSamplers(): Result<List<String>> {
        return try {
            val samplers = api.getSamplers()
            Result.Success(samplers.map { it.name })
        } catch (e: Exception) {
            Result.Error(Exception("Failed to get samplers: ${e.message}", e))
        }
    }

    override suspend fun getSchedulers(): Result<List<String>> {
        return Result.Success(emptyList())
    }

    override suspend fun getModels(): Result<List<String>> {
        return try {
            val models = api.getModels()
            Result.Success(models.map { it.title })
        } catch (e: Exception) {
            Result.Error(Exception("Failed to get models: ${e.message}", e))
        }
    }

    override fun generate(params: ForgeGenerationParams): Flow<GenerationState> = flow {
        emit(GenerationState.Starting)

        try {
            val config = configProvider()
            val customTemplate = CustomImageTemplate.checkedSelected(config, params, type)

            val image = if (customTemplate != null) {
                val baseUrl = config.sdWebuiUrl.trimEnd('/')
                if (baseUrl.isBlank()) throw IllegalStateException("No SD WebUI / Forge URL configured")

                val body = CustomImageTemplate.renderJson(
                    customTemplate,
                    params,
                    config.sdModel
                )
                val endpoint = if (params.sourceImageBase64 != null) "img2img" else "txt2img"
                val request = Request.Builder()
                    .url("$baseUrl/sdapi/v1/$endpoint")
                    .addHeader("Content-Type", "application/json")
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .build()

                val responseBody = withContext(Dispatchers.IO) {
                    httpClient.newCall(request).execute().use { response ->
                        val text = response.body?.string() ?: throw Exception("Empty response")
                        if (!response.isSuccessful) throw Exception("HTTP ${response.code}: $text")
                        text
                    }
                }

                json.parseToJsonElement(responseBody)
                    .jsonObject["images"]
                    ?.jsonArray
                    ?.firstOrNull()
                    ?.jsonPrimitive
                    ?.content
            } else {
                val overrides = if (params.clipSkip > 1) mapOf(
                    "CLIP_stop_at_last_layers" to kotlinx.serialization.json.JsonPrimitive(params.clipSkip)
                ) else null
                val response = if (params.sourceImageBase64 != null) {
                    api.img2img(
                        Img2ImgRequest(
                            prompt = params.prompt,
                            overrideSettings = overrides,
                            negativePrompt = params.negativePrompt,
                            initImages = listOf(params.sourceImageBase64),
                            steps = params.steps,
                            cfgScale = params.cfgScale,
                            width = params.width,
                            height = params.height,
                            samplerName = params.sampler,
                            denoisingStrength = params.denoisingStrength,
                            seed = params.seed
                        )
                    )
                } else {
                    api.generateImage(
                        Txt2ImgRequest(
                            prompt = params.prompt,
                            overrideSettings = overrides,
                            negativePrompt = params.negativePrompt,
                            steps = params.steps,
                            cfgScale = params.cfgScale,
                            width = params.width,
                            height = params.height,
                            samplerName = params.sampler,
                            seed = params.seed
                        )
                    )
                }
                response.images?.firstOrNull()
            }
            if (image != null) {
                emit(GenerationState.Complete(imageBase64 = image))
            } else {
                emit(GenerationState.Error("No image generated"))
            }
        } catch (e: Exception) {
            emit(GenerationState.Error(e.message ?: "Generation failed"))
        }
    }

    override suspend fun interrupt(): Result<Unit> {
        return try {
            api.interrupt()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(Exception("Failed to interrupt: ${e.message}", e))
        }
    }

    override suspend fun setModel(model: String): Result<Unit> {
        return try {
            api.setOptions(SetOptionsRequest(sdModelCheckpoint = model))
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(Exception("Failed to set model: ${e.message}", e))
        }
    }
}
