package com.pockettavern.app.data.remote.imagegen

import com.pockettavern.app.data.local.SettingsDataStore
import com.pockettavern.app.util.DebugLogger
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private const val BASE_URL = "https://nano-gpt.com"

@Serializable
private data class NanoGptGenerateRequest(
    val prompt: String,
    val model: String
)

@Serializable
private data class NanoGptGenerateResponse(
    val data: List<NanoGptImage>? = null,
    val error: String? = null
)

@Serializable
private data class NanoGptImage(
    @SerialName("b64_json")
    val b64Json: String? = null
)

class NanoGptBackend(
    private val httpClient: OkHttpClient,
    private val settingsDataStore: SettingsDataStore
) : ImageGenBackend {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override val type = ImageGenBackendType.NANO_GPT

    override val capabilities = ImageGenCapabilities(
        supportsResolutionPresets = false,
        requiresApiKey = true
    )

    override suspend fun testConnection(): Result<Boolean> {
        val config = settingsDataStore.getImageGenConfig()
        if (config.nanoGptApiKey.isBlank()) {
            return Result.Error(Exception("No API key configured"))
        }
        return try {
            val request = Request.Builder()
                .url("$BASE_URL/api/models")
                .addHeader("x-api-key", config.nanoGptApiKey)
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
        val config = settingsDataStore.getImageGenConfig()
        if (config.nanoGptApiKey.isBlank()) {
            return Result.Error(Exception("No API key configured"))
        }
        return try {
            val request = Request.Builder()
                .url("$BASE_URL/api/models")
                .addHeader("x-api-key", config.nanoGptApiKey)
                .get()
                .build()
            val body = withContext(Dispatchers.IO) {
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
                    response.body?.string() ?: throw Exception("Empty response")
                }
            }
            val root = json.parseToJsonElement(body).jsonObject
            val imageModels = root["models"]?.jsonObject?.get("image")?.jsonObject
                ?: return Result.Success(emptyList())
            val modelIds = imageModels.keys.sorted()
            Result.Success(modelIds)
        } catch (e: Exception) {
            Result.Error(Exception("Failed to fetch models: ${e.message}", e))
        }
    }

    override fun generate(params: ForgeGenerationParams): Flow<GenerationState> = flow {
        DebugLogger.logSection("NanoGpt Generate")
        emit(GenerationState.Starting)
        try {
            val config = settingsDataStore.getImageGenConfig()
            if (config.nanoGptApiKey.isBlank()) {
                emit(GenerationState.Error("No nano-gpt API key configured"))
                return@flow
            }
            val model = config.nanoGptModel.ifBlank { "chroma" }

            DebugLogger.logKeyValue("model", model)
            DebugLogger.logKeyValue("apiKeyPrefix", config.nanoGptApiKey.take(12) + "...")
            DebugLogger.logKeyValue("prompt", params.prompt.take(80))

            val reqBody = json.encodeToString(
                NanoGptGenerateRequest.serializer(),
                NanoGptGenerateRequest(prompt = params.prompt, model = model)
            )
            DebugLogger.logKeyValue("requestBody", reqBody)

            val request = Request.Builder()
                .url("$BASE_URL/v1/images/generations")
                .addHeader("Authorization", "Bearer ${config.nanoGptApiKey}")
                .addHeader("Content-Type", "application/json")
                .post(reqBody.toRequestBody("application/json".toMediaType()))
                .build()

            val responseBody = withContext(Dispatchers.IO) {
                httpClient.newCall(request).execute().use { response ->
                    val text = response.body?.string() ?: throw Exception("Empty response")
                    DebugLogger.logKeyValue("responseCode", response.code)
                    DebugLogger.logKeyValue("responseBody", text.take(300))
                    if (!response.isSuccessful) throw Exception("HTTP ${response.code}: $text")
                    text
                }
            }

            val parsed = json.decodeFromString(NanoGptGenerateResponse.serializer(), responseBody)
            if (parsed.error != null) {
                emit(GenerationState.Error(parsed.error))
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
}
