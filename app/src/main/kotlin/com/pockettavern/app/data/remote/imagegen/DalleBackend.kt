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
private data class DalleRequest(
    val model: String,
    val prompt: String,
    val n: Int = 1,
    val size: String,
    @SerialName("response_format")
    val responseFormat: String = "b64_json"
)

@Serializable
private data class DalleResponse(
    val data: List<DalleImage>? = null,
    val error: DalleError? = null
)

@Serializable
private data class DalleImage(
    @SerialName("b64_json")
    val b64Json: String? = null,
    val url: String? = null
)

@Serializable
private data class DalleError(
    val message: String? = null
)

class DalleBackend(
    private val httpClient: OkHttpClient,
    private val settingsDataStore: SettingsDataStore
) : ImageGenBackend {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override val type = ImageGenBackendType.DALLE

    override val capabilities = ImageGenCapabilities(
        supportsResolutionPresets = true,
        requiresApiKey = true
    )

    override suspend fun testConnection(): Result<Boolean> {
        val config = settingsDataStore.getImageGenConfig()
        if (config.dalleApiKey.isBlank()) {
            return Result.Error(Exception("No API key configured"))
        }
        return try {
            val request = Request.Builder()
                .url("https://api.openai.com/v1/models")
                .addHeader("Authorization", "Bearer ${config.dalleApiKey}")
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
        return Result.Success(listOf("dall-e-3", "dall-e-2"))
    }

    override fun generate(params: ForgeGenerationParams): Flow<GenerationState> = flow {
        emit(GenerationState.Starting)
        try {
            val config = settingsDataStore.getImageGenConfig()
            if (config.dalleApiKey.isBlank()) {
                emit(GenerationState.Error("No OpenAI API key configured"))
                return@flow
            }

            // DALL-E supports specific sizes
            val size = pickDalleSize(config.dalleModel, params.width, params.height)

            val body = json.encodeToString(DalleRequest.serializer(), DalleRequest(
                model = config.dalleModel,
                prompt = params.prompt,
                size = size,
                responseFormat = "b64_json"
            ))

            val request = Request.Builder()
                .url("https://api.openai.com/v1/images/generations")
                .addHeader("Authorization", "Bearer ${config.dalleApiKey}")
                .addHeader("Content-Type", "application/json")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()

            val responseBody = withContext(Dispatchers.IO) {
                httpClient.newCall(request).execute().use { response ->
                    response.body?.string() ?: throw Exception("Empty response")
                }
            }

            val parsed = json.decodeFromString(DalleResponse.serializer(), responseBody)
            if (parsed.error != null) {
                emit(GenerationState.Error(parsed.error.message ?: "DALL-E error"))
                return@flow
            }

            val imageBase64 = parsed.data?.firstOrNull()?.b64Json
            if (imageBase64 != null) {
                emit(GenerationState.Complete(imageBase64 = imageBase64))
            } else {
                emit(GenerationState.Error("No image returned"))
            }
        } catch (e: Exception) {
            emit(GenerationState.Error(e.message ?: "Generation failed"))
        }
    }

    override suspend fun interrupt(): Result<Unit> = Result.Success(Unit)

    private fun pickDalleSize(model: String, width: Int, height: Int): String {
        return when (model) {
            "dall-e-3" -> when {
                width > height -> "1792x1024"
                height > width -> "1024x1792"
                else -> "1024x1024"
            }
            else -> when {
                width <= 256 && height <= 256 -> "256x256"
                width <= 512 && height <= 512 -> "512x512"
                else -> "1024x1024"
            }
        }
    }
}
