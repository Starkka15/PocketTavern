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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class ComfyUiBackend private constructor(
    private val httpClient: OkHttpClient,
    private val configProvider: suspend () -> ImageGenConfig
) : ImageGenBackend {

    constructor(httpClient: OkHttpClient, settingsDataStore: SettingsDataStore) :
        this(httpClient, { settingsDataStore.getImageGenConfig() })

    internal constructor(httpClient: OkHttpClient, config: ImageGenConfig) :
        this(httpClient, { config })

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
        supportsCustomWorkflow = true,
        requiresUrl = true
    )

    private suspend fun getBaseUrl(): String {
        val config = configProvider()
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

            val config = configProvider()
            val seed = if (params.seed == -1) (0..Int.MAX_VALUE).random().toLong() else params.seed.toLong()

            val customWorkflow = CustomImageTemplate.checkedSelected(config, params, type)

            val workflow = if (customWorkflow != null) {
                applyPocketTavernInputsToCustomWorkflow(
                    workflowJson = customWorkflow,
                    prompt = params.prompt,
                    negativePrompt = params.negativePrompt,
                    width = params.width,
                    height = params.height,
                    steps = params.steps,
                    cfgScale = params.cfgScale,
                    sampler = config.sampler,
                    seed = seed,
                    // Own character graphs may intentionally use distinct checkpoints.
                    modelName = if (params.preserveCustomWorkflowModel) "" else config.sdModel
                )
            } else {
                buildDefaultWorkflow(
                    prompt = params.prompt,
                    negativePrompt = params.negativePrompt,
                    width = params.width,
                    height = params.height,
                    steps = params.steps,
                    cfgScale = params.cfgScale,
                    sampler = normalizeSamplerName(config.sampler),
                    seed = seed
                )
            }

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
                        if (!response.isSuccessful) throw Exception("ComfyUI history HTTP ${response.code}")
                        response.body?.string()
                    }
                } ?: continue

                val historyRoot = json.parseToJsonElement(historyBody).jsonObject
                val promptHistory = historyRoot[promptId]?.jsonObject
                if (promptHistory != null) {
                    val outputs = promptHistory["outputs"]?.jsonObject
                    val firstImage = outputs
                        ?.values
                        ?.asSequence()
                        ?.mapNotNull { it.jsonObject["images"]?.jsonArray?.firstOrNull()?.jsonObject }
                        ?.firstOrNull()

                    if (firstImage != null) {
                        val filename = firstImage["filename"]?.jsonPrimitive?.contentOrNull
                        val subfolder = firstImage["subfolder"]?.jsonPrimitive?.contentOrNull ?: ""
                        val imageType = firstImage["type"]?.jsonPrimitive?.contentOrNull ?: "output"

                        if (filename != null) {
                            // Fetch the image
                            val imgUrl = "$baseUrl/view".toHttpUrl().newBuilder()
                                .addQueryParameter("filename", filename)
                                .addQueryParameter("subfolder", subfolder)
                                .addQueryParameter("type", imageType)
                                .build()
                            val imgReq = Request.Builder().url(imgUrl).get().build()
                            val imageBytes = withContext(Dispatchers.IO) {
                                httpClient.newCall(imgReq).execute().use { resp ->
                                    if (!resp.isSuccessful) throw Exception("ComfyUI image HTTP ${resp.code}")
                                    val body = resp.body ?: throw Exception("Failed to download image")
                                    if (body.contentType()?.subtype?.contains("json", ignoreCase = true) == true) {
                                        throw Exception("ComfyUI returned JSON instead of an image")
                                    }
                                    body.bytes().takeIf { it.isNotEmpty() } ?: throw Exception("Empty image response")
                                }
                            }
                            val base64 = Base64.getEncoder().encodeToString(imageBytes)
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

    private fun normalizeSamplerName(name: String): String {
        return when (name.trim().lowercase()) {
            "euler" -> "euler"
            "euler a", "euler_a" -> "euler_ancestral"
            "dpm++ 2m karras", "dpmpp_2m_karras" -> "dpmpp_2m"
            "dpm++ sde karras", "dpmpp_sde_karras" -> "dpmpp_sde"
            "ddim" -> "ddim"
            "unipc", "uni_pc" -> "uni_pc"
            else -> name.trim().lowercase().replace(" ", "_")
        }
    }

    private fun applyPocketTavernInputsToCustomWorkflow(
        workflowJson: String,
        prompt: String,
        negativePrompt: String,
        width: Int,
        height: Int,
        steps: Int,
        cfgScale: Float,
        sampler: String,
        seed: Long,
        modelName: String
    ): String {
        val root = json.parseToJsonElement(workflowJson).jsonObject
        val nodes = root.toMutableMap()

        fun nodeObject(id: String?): JsonObject? =
            id?.let { nodes[it]?.jsonObject }

        fun replaceNodeInputs(id: String, newInputs: Map<String, JsonElement>) {
            val oldNode = nodes[id]?.jsonObject ?: return
            val updated = buildJsonObject {
                oldNode.forEach { (k, v) ->
                    if (k == "inputs") put(k, JsonObject(newInputs))
                    else put(k, v)
                }
            }
            nodes[id] = updated
        }

        val samplerEntry = nodes.entries.firstOrNull { (_, value) ->
            value.jsonObject["class_type"]?.jsonPrimitive?.contentOrNull == "KSampler"
        } ?: throw IllegalArgumentException(
            "Custom ComfyUI workflow contains no KSampler node. Export an API workflow with a KSampler."
        )

        val samplerId = samplerEntry.key
        val samplerNode = samplerEntry.value.jsonObject
        val inputs = samplerNode["inputs"]?.jsonObject?.toMutableMap()
            ?: throw IllegalArgumentException("KSampler has no inputs")

        val positiveId = inputs["positive"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.contentOrNull
        val negativeId = inputs["negative"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.contentOrNull
        val latentId = inputs["latent_image"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.contentOrNull
        val modelId = inputs["model"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.contentOrNull

        inputs["seed"] = JsonPrimitive(seed)
        inputs["steps"] = JsonPrimitive(steps)
        inputs["cfg"] = JsonPrimitive(cfgScale)
        inputs["sampler_name"] = JsonPrimitive(normalizeSamplerName(sampler))
        replaceNodeInputs(samplerId, inputs)

        if (positiveId != null) {
            val pInputs = nodeObject(positiveId)?.get("inputs")?.jsonObject?.toMutableMap()
            if (pInputs != null && pInputs.containsKey("text")) {
                pInputs["text"] = JsonPrimitive(prompt)
                replaceNodeInputs(positiveId, pInputs)
            }
        }

        if (negativeId != null) {
            val nInputs = nodeObject(negativeId)?.get("inputs")?.jsonObject?.toMutableMap()
            if (nInputs != null && nInputs.containsKey("text")) {
                nInputs["text"] = JsonPrimitive(negativePrompt)
                replaceNodeInputs(negativeId, nInputs)
            }
        }

        if (latentId != null) {
            val lInputs = nodeObject(latentId)?.get("inputs")?.jsonObject?.toMutableMap()
            if (lInputs != null) {
                if (lInputs.containsKey("width")) lInputs["width"] = JsonPrimitive(width)
                if (lInputs.containsKey("height")) lInputs["height"] = JsonPrimitive(height)
                replaceNodeInputs(latentId, lInputs)
            }
        }

        if (modelName.isNotBlank() && modelId != null) {
            val mInputs = nodeObject(modelId)?.get("inputs")?.jsonObject?.toMutableMap()
            if (mInputs != null && mInputs.containsKey("ckpt_name")) {
                mInputs["ckpt_name"] = JsonPrimitive(modelName)
                replaceNodeInputs(modelId, mInputs)
            }
        }

        return JsonObject(nodes).toString()
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
