package com.pockettavern.app.data.remote.imagegen

import android.util.Base64
import com.pockettavern.app.data.local.SettingsDataStore
import com.pockettavern.app.domain.model.ForgeGenerationParams
import com.pockettavern.app.domain.model.GenerationState
import com.pockettavern.app.domain.model.ImageGenBackendType
import com.pockettavern.app.domain.model.ImageGenCapabilities
import com.pockettavern.app.domain.model.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class ComfyUiBackend(
    private val httpClient: OkHttpClient,
    private val settingsDataStore: SettingsDataStore
) : ImageGenBackend {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override val type = ImageGenBackendType.COMFYUI

    override val capabilities = ImageGenCapabilities(
        supportsSamplers = true,
        supportsSteps = true,
        supportsCfgScale = true,
        supportsSeed = true,
        supportsNegativePrompt = true,
        supportsResolutionPresets = true,
        supportsProgress = true,
        requiresUrl = true
    )

    private suspend fun getBaseUrl(): String {
        val config = settingsDataStore.getImageGenConfig()
        return config.comfyuiUrl.trimEnd('/')
    }

    override suspend fun testConnection(): Result<Boolean> {
        return try {
            val baseUrl = getBaseUrl()
            if (baseUrl.isBlank()) return Result.Error(Exception("No ComfyUI URL configured"))
            val request = Request.Builder().url("$baseUrl/system_stats").get().build()
            withContext(Dispatchers.IO) {
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) Result.Success(true)
                    else Result.Error(Exception("HTTP ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.Error(Exception("Connection failed: ${e.message}", e))
        }
    }

    override suspend fun getSamplers(): Result<List<String>> {
        return try {
            val baseUrl = getBaseUrl()
            val request = Request.Builder().url("$baseUrl/object_info/KSampler").get().build()
            val body = withContext(Dispatchers.IO) {
                httpClient.newCall(request).execute().use { it.body?.string() }
            } ?: return Result.Success(emptyList())

            val root = json.parseToJsonElement(body).jsonObject
            val samplerNames = root["KSampler"]?.jsonObject
                ?.get("input")?.jsonObject
                ?.get("required")?.jsonObject
                ?.get("sampler_name")?.jsonArray
                ?.firstOrNull()?.jsonArray
                ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                ?: emptyList()
            Result.Success(samplerNames)
        } catch (e: Exception) {
            Result.Success(listOf("euler", "euler_ancestral", "dpmpp_2m", "dpmpp_sde", "ddim", "uni_pc"))
        }
    }

    override suspend fun getSchedulers(): Result<List<String>> {
        return try {
            val baseUrl = getBaseUrl()
            val request = Request.Builder().url("$baseUrl/object_info/KSampler").get().build()
            val body = withContext(Dispatchers.IO) {
                httpClient.newCall(request).execute().use { it.body?.string() }
            } ?: return Result.Success(emptyList())

            val root = json.parseToJsonElement(body).jsonObject
            val schedulerNames = root["KSampler"]?.jsonObject
                ?.get("input")?.jsonObject
                ?.get("required")?.jsonObject
                ?.get("scheduler")?.jsonArray
                ?.firstOrNull()?.jsonArray
                ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                ?: emptyList()
            Result.Success(schedulerNames)
        } catch (e: Exception) {
            Result.Success(listOf("normal", "karras", "exponential", "sgm_uniform"))
        }
    }

    override suspend fun getModels(): Result<List<String>> {
        return try {
            val baseUrl = getBaseUrl()
            val request = Request.Builder().url("$baseUrl/object_info/CheckpointLoaderSimple").get().build()
            val body = withContext(Dispatchers.IO) {
                httpClient.newCall(request).execute().use { it.body?.string() }
            } ?: return Result.Success(emptyList())

            val root = json.parseToJsonElement(body).jsonObject
            val modelNames = root["CheckpointLoaderSimple"]?.jsonObject
                ?.get("input")?.jsonObject
                ?.get("required")?.jsonObject
                ?.get("ckpt_name")?.jsonArray
                ?.firstOrNull()?.jsonArray
                ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                ?: emptyList()
            Result.Success(modelNames)
        } catch (e: Exception) {
            Result.Success(emptyList())
        }
    }

    override fun generate(params: ForgeGenerationParams): Flow<GenerationState> = flow {
        emit(GenerationState.Starting)
        try {
            val baseUrl = getBaseUrl()
            if (baseUrl.isBlank()) {
                emit(GenerationState.Error("No ComfyUI URL configured"))
                return@flow
            }

            val config = settingsDataStore.getImageGenConfig()
            val seed = if (params.seed == -1) (0..Int.MAX_VALUE).random().toLong() else params.seed.toLong()

            // Build a simple txt2img workflow
            val workflow = buildDefaultWorkflow(
                prompt = params.prompt,
                negativePrompt = params.negativePrompt,
                width = params.width,
                height = params.height,
                steps = params.steps,
                cfgScale = params.cfgScale,
                sampler = config.sampler.lowercase().replace(" ", "_"),
                seed = seed
            )

            val promptPayload = buildJsonObject {
                put("prompt", json.parseToJsonElement(workflow))
            }

            val request = Request.Builder()
                .url("$baseUrl/prompt")
                .addHeader("Content-Type", "application/json")
                .post(promptPayload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val responseBody = withContext(Dispatchers.IO) {
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
                    response.body?.string() ?: throw Exception("Empty response")
                }
            }

            val promptResponse = json.parseToJsonElement(responseBody).jsonObject
            val promptId = promptResponse["prompt_id"]?.jsonPrimitive?.contentOrNull
                ?: throw Exception("No prompt_id returned")

            // Poll for completion
            var completed = false
            while (!completed) {
                delay(1000)
                val historyReq = Request.Builder()
                    .url("$baseUrl/history/$promptId")
                    .get()
                    .build()

                val historyBody = withContext(Dispatchers.IO) {
                    httpClient.newCall(historyReq).execute().use { response ->
                        response.body?.string()
                    }
                } ?: continue

                val historyRoot = json.parseToJsonElement(historyBody).jsonObject
                val promptHistory = historyRoot[promptId]?.jsonObject
                if (promptHistory != null) {
                    val outputs = promptHistory["outputs"]?.jsonObject
                    // Find the SaveImage node output (node "9" in our workflow)
                    val saveNode = outputs?.get("9")?.jsonObject
                    val images = saveNode?.get("images")?.jsonArray
                    val firstImage = images?.firstOrNull()?.jsonObject

                    if (firstImage != null) {
                        val filename = firstImage["filename"]?.jsonPrimitive?.contentOrNull
                        val subfolder = firstImage["subfolder"]?.jsonPrimitive?.contentOrNull ?: ""
                        val imageType = firstImage["type"]?.jsonPrimitive?.contentOrNull ?: "output"

                        if (filename != null) {
                            // Fetch the image
                            val imgUrl = "$baseUrl/view?filename=$filename&subfolder=$subfolder&type=$imageType"
                            val imgReq = Request.Builder().url(imgUrl).get().build()
                            val imageBytes = withContext(Dispatchers.IO) {
                                httpClient.newCall(imgReq).execute().use { resp ->
                                    resp.body?.bytes() ?: throw Exception("Failed to download image")
                                }
                            }
                            val base64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
                            emit(GenerationState.Complete(imageBase64 = base64))
                            completed = true
                        }
                    }
                }
            }
        } catch (e: Exception) {
            emit(GenerationState.Error(e.message ?: "Generation failed"))
        }
    }

    override suspend fun interrupt(): Result<Unit> {
        return try {
            val baseUrl = getBaseUrl()
            val request = Request.Builder()
                .url("$baseUrl/interrupt")
                .post("".toRequestBody("application/json".toMediaType()))
                .build()
            withContext(Dispatchers.IO) {
                httpClient.newCall(request).execute().close()
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(Exception("Failed to interrupt: ${e.message}", e))
        }
    }

    private fun buildDefaultWorkflow(
        prompt: String,
        negativePrompt: String,
        width: Int,
        height: Int,
        steps: Int,
        cfgScale: Float,
        sampler: String,
        seed: Long
    ): String {
        // Minimal ComfyUI workflow: CheckpointLoader → KSampler → VAEDecode → SaveImage
        val escapedPrompt = prompt.replace("\\", "\\\\").replace("\"", "\\\"")
        val escapedNeg = negativePrompt.replace("\\", "\\\\").replace("\"", "\\\"")
        return """
        {
            "3": {
                "class_type": "KSampler",
                "inputs": {
                    "seed": $seed,
                    "steps": $steps,
                    "cfg": $cfgScale,
                    "sampler_name": "$sampler",
                    "scheduler": "normal",
                    "denoise": 1,
                    "model": ["4", 0],
                    "positive": ["6", 0],
                    "negative": ["7", 0],
                    "latent_image": ["5", 0]
                }
            },
            "4": {
                "class_type": "CheckpointLoaderSimple",
                "inputs": {
                    "ckpt_name": "v1-5-pruned-emaonly.safetensors"
                }
            },
            "5": {
                "class_type": "EmptyLatentImage",
                "inputs": {
                    "width": $width,
                    "height": $height,
                    "batch_size": 1
                }
            },
            "6": {
                "class_type": "CLIPTextEncode",
                "inputs": {
                    "text": "$escapedPrompt",
                    "clip": ["4", 1]
                }
            },
            "7": {
                "class_type": "CLIPTextEncode",
                "inputs": {
                    "text": "$escapedNeg",
                    "clip": ["4", 1]
                }
            },
            "8": {
                "class_type": "VAEDecode",
                "inputs": {
                    "samples": ["3", 0],
                    "vae": ["4", 2]
                }
            },
            "9": {
                "class_type": "SaveImage",
                "inputs": {
                    "filename_prefix": "PocketTavern",
                    "images": ["8", 0]
                }
            }
        }
        """.trimIndent()
    }
}
