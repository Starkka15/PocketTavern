package com.pockettavern.app.data.repository

import com.pockettavern.app.data.local.SettingsDataStore
import com.pockettavern.app.data.remote.api.*
import com.pockettavern.app.domain.model.*
import com.pockettavern.app.domain.model.OaiPreset
import com.pockettavern.app.domain.model.PromptMessage
import com.pockettavern.app.domain.prompt.PromptBuilder
import com.pockettavern.app.util.DebugLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Dispatches LLM generation to the appropriate backend.
 * Handles streaming, model listing, and connection testing
 * without any SillyTavern server dependency.
 */
@Singleton
class LlmRepository @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    @Named("LLM") private val okHttpClient: OkHttpClient
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
    }

    /**
     * Stream text generation for a chat.
     * Dispatches to the correct backend based on ApiConfiguration.
     * Pass [messages] for chat completion APIs; text completion backends use [prompt].
     */
    fun generate(
        prompt: String,
        config: ApiConfiguration,
        preset: TextGenPreset?,
        stopSequences: List<String> = emptyList(),
        messages: List<PromptMessage>? = null,
        oaiPreset: OaiPreset? = null
    ): Flow<StreamEvent> = flow {
        val endpoint = config.effectiveBaseUrl
        DebugLogger.log("LlmRepository: generating with ${config.displayName} ($endpoint)")
        DebugLogger.logApiRequest(endpoint, prompt.take(200))

        val apiKey = config.apiKey

        try {
            when {
                config.usesChatCompletions -> streamChatCompletions(
                    prompt = prompt,
                    messages = messages,
                    config = config,
                    preset = preset,
                    oaiPreset = oaiPreset,
                    stopSequences = stopSequences,
                    apiKey = apiKey
                ).collect { emit(it) }

                config.textGenType == "koboldcpp" || config.mainApi == "kobold" ->
                    streamKobold(prompt, config, preset, stopSequences).collect { emit(it) }

                config.textGenType == "ollama" ->
                    streamOllama(prompt, config, preset, stopSequences).collect { emit(it) }

                config.textGenType == "llamacpp" ->
                    streamLlamaCpp(prompt, config, preset, stopSequences).collect { emit(it) }

                else ->
                    // Fallback: try OpenAI-compatible text completion
                    streamOaiText(prompt, config, preset, stopSequences, apiKey).collect { emit(it) }
            }
        } catch (e: Exception) {
            DebugLogger.logError("LlmRepository", "Generation failed", e)
            emit(StreamEvent.Error("Generation failed: ${e.message}"))
        }
    }.flowOn(Dispatchers.IO)

    /** Get available models from the configured backend. */
    suspend fun getAvailableModels(config: ApiConfiguration): List<AvailableModel> {
        return try {
            when {
                config.usesChatCompletions || config.textGenType in setOf("ooba", "vllm", "aphrodite", "tabby") ->
                    fetchOaiModels(config)
                config.textGenType == "ollama" -> fetchOllamaModels(config)
                config.textGenType == "koboldcpp" -> fetchKoboldModel(config)
                else -> emptyList()
            }
        } catch (e: Exception) {
            DebugLogger.logError("LlmRepository", "getAvailableModels failed", e)
            emptyList()
        }
    }

    /** Test connection to the configured backend. */
    suspend fun testConnection(config: ApiConfiguration): Boolean {
        return try {
            when {
                config.textGenType == "koboldcpp" -> {
                    val url = "${config.apiServer.trimEnd('/')}/api/v1/model"
                    val resp = okHttpClient.newCall(Request.Builder().url(url).build()).execute()
                    resp.isSuccessful
                }
                config.textGenType == "ollama" -> {
                    val url = "${config.apiServer.trimEnd('/')}/api/tags"
                    val resp = okHttpClient.newCall(Request.Builder().url(url).build()).execute()
                    resp.isSuccessful
                }
                else -> {
                    val baseUrl = config.effectiveBaseUrl
                    val url = "$baseUrl/v1/models"
                    val req = Request.Builder().url(url)
                        .also { b -> if (config.apiKey.isNotBlank()) b.addHeader("Authorization", "Bearer ${config.apiKey}") }
                        .build()
                    val resp = okHttpClient.newCall(req).execute()
                    resp.isSuccessful || resp.code == 404 // 404 means server is alive, just no /models
                }
            }
        } catch (e: Exception) {
            false
        }
    }

    /** Abort any in-flight generation (KoboldCpp only supports this natively). */
    suspend fun abortGeneration(config: ApiConfiguration) {
        if (config.textGenType == "koboldcpp") {
            try {
                val url = "${config.apiServer.trimEnd('/')}/api/extra/abort"
                okHttpClient.newCall(
                    Request.Builder().url(url).post("{}".toRequestBody("application/json".toMediaType())).build()
                ).execute()
            } catch (e: Exception) {
                DebugLogger.logError("LlmRepository", "abort failed", e)
            }
        }
        // For other backends, cancellation is handled via coroutine cancellation
    }

    /**
     * Unload the model from VRAM (KoboldCpp only).
     * Frees GPU memory so other apps (e.g. Forge) can use it.
     * The model auto-reloads with the same settings on the next generation request.
     */
    suspend fun unloadModel(config: ApiConfiguration) {
        if (config.textGenType != "koboldcpp") return
        try {
            val url = "${config.apiServer.trimEnd('/')}/api/extra/unload"
            val response = okHttpClient.newCall(
                Request.Builder().url(url).post("{}".toRequestBody("application/json".toMediaType())).build()
            ).execute()
            if (response.isSuccessful) {
                DebugLogger.log("LlmRepository: KoboldCpp model unloaded from VRAM")
            } else {
                DebugLogger.log("LlmRepository: KoboldCpp unload returned ${response.code}")
            }
        } catch (e: Exception) {
            DebugLogger.logError("LlmRepository", "unload failed", e)
        }
    }

    // ── KoboldCpp ─────────────────────────────────────────────────────────────

    private fun streamKobold(
        prompt: String,
        config: ApiConfiguration,
        preset: TextGenPreset?,
        stopSequences: List<String>
    ): Flow<StreamEvent> = flow {
        val baseUrl = config.apiServer.trimEnd('/')
        val request = KoboldGenerateRequest(
            prompt = prompt,
            maxLength = preset?.maxNewTokens ?: 200,
            maxContextLength = preset?.truncationLength ?: 4096,
            minLength = preset?.minTokens ?: 0,
            temperature = preset?.temperature ?: 0.7f,
            topP = preset?.topP ?: 0.92f,
            topK = preset?.topK ?: 0,
            topA = preset?.topA ?: 0f,
            minP = preset?.minP ?: 0f,
            typical = preset?.typicalP ?: 1.0f,
            tfs = preset?.tfs ?: 1.0f,
            repPen = preset?.repPen ?: 1.0f,
            repPenRange = preset?.repPenRange ?: 0,
            repPenSlope = preset?.repPenSlope ?: 1.0f,
            dryMultiplier = preset?.dryMultiplier ?: 0f,
            dryBase = preset?.dryBase ?: 1.75f,
            dryAllowedLength = preset?.dryAllowedLength ?: 2,
            dryPenaltyLastN = preset?.dryPenaltyLastN ?: 0,
            xtcThreshold = preset?.xtcThreshold ?: 0.1f,
            xtcProbability = preset?.xtcProbability ?: 0f,
            smoothingFactor = preset?.smoothingFactor ?: 0f,
            mirostat = preset?.mirostatMode ?: 0,
            mirostatTau = preset?.mirostatTau ?: 5f,
            mirostatEta = preset?.mirostatEta ?: 0.1f,
            banEosToken = preset?.banEosToken ?: false,
            addBosToken = preset?.addBosToken ?: true,
            stopSequence = stopSequences,
            stream = true
        )

        val body = json.encodeToString(KoboldGenerateRequest.serializer(), request)
        val httpReq = Request.Builder()
            .url("$baseUrl/api/extra/generate/stream")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        val accumulated = StringBuilder()
        okHttpClient.newCall(httpReq).execute().use { response ->
            if (!response.isSuccessful) {
                emit(StreamEvent.Error("KoboldCpp error: HTTP ${response.code}"))
                return@use
            }
            response.body?.source()?.let { source ->
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (line.startsWith("data: ")) {
                        val data = line.removePrefix("data: ").trim()
                        if (data == "[DONE]") break
                        try {
                            val obj = json.decodeFromString<kotlinx.serialization.json.JsonObject>(data)
                            val token = obj["token"]?.jsonPrimitive?.contentOrNull ?: continue
                            accumulated.append(token)
                            emit(StreamEvent.Token(token, accumulated.toString()))
                        } catch (e: Exception) { /* skip malformed */ }
                    }
                }
            }
        }
        emit(StreamEvent.Complete(accumulated.toString()))
    }

    // ── Ollama ────────────────────────────────────────────────────────────────

    private fun streamOllama(
        prompt: String,
        config: ApiConfiguration,
        preset: TextGenPreset?,
        stopSequences: List<String>
    ): Flow<StreamEvent> = flow {
        val baseUrl = config.apiServer.trimEnd('/')
        val model = config.currentModel.ifBlank { "llama3" }
        val options = mapOf(
            "temperature" to (preset?.temperature ?: 0.7f),
            "top_p" to (preset?.topP ?: 0.9f),
            "top_k" to (preset?.topK ?: 40),
            "repeat_penalty" to (preset?.repPen ?: 1.1f),
            "num_predict" to (preset?.maxNewTokens ?: 200)
        ).let {
            buildString {
                append("{")
                it.entries.forEachIndexed { idx, (k, v) ->
                    if (idx > 0) append(",")
                    append("\"$k\":$v")
                }
                if (stopSequences.isNotEmpty()) {
                    append(",\"stop\":[")
                    append(stopSequences.joinToString(",") { "\"${it.replace("\"", "\\\"")}\"" })
                    append("]")
                }
                append("}")
            }
        }

        val promptJson = json.encodeToString(kotlinx.serialization.json.JsonPrimitive.serializer(), kotlinx.serialization.json.JsonPrimitive(prompt))
        val body = """{"model":"$model","prompt":$promptJson,"stream":true,"options":$options}"""
        val httpReq = Request.Builder()
            .url("$baseUrl/api/generate")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        val accumulated = StringBuilder()
        okHttpClient.newCall(httpReq).execute().use { response ->
            if (!response.isSuccessful) {
                emit(StreamEvent.Error("Ollama error: HTTP ${response.code}"))
                return@use
            }
            response.body?.source()?.let { source ->
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (line.isBlank()) continue
                    try {
                        val chunk = json.decodeFromString<OllamaStreamChunk>(line)
                        if (chunk.error != null) {
                            emit(StreamEvent.Error("Ollama: ${chunk.error}"))
                            return@use
                        }
                        val token = chunk.response
                        if (token.isNotEmpty()) {
                            accumulated.append(token)
                            emit(StreamEvent.Token(token, accumulated.toString()))
                        }
                        if (chunk.done) break
                    } catch (e: Exception) { /* skip */ }
                }
            }
        }
        emit(StreamEvent.Complete(accumulated.toString()))
    }

    // ── llama.cpp server ─────────────────────────────────────────────────────

    private fun streamLlamaCpp(
        prompt: String,
        config: ApiConfiguration,
        preset: TextGenPreset?,
        stopSequences: List<String>
    ): Flow<StreamEvent> = flow {
        val baseUrl = config.apiServer.trimEnd('/')
        val request = LlamaCppRequest(
            prompt = prompt,
            nPredict = preset?.maxNewTokens ?: 200,
            temperature = preset?.temperature ?: 0.8f,
            topP = preset?.topP ?: 0.95f,
            topK = preset?.topK ?: 40,
            minP = preset?.minP ?: 0.05f,
            repeatPenalty = preset?.repPen ?: 1.1f,
            mirostat = preset?.mirostatMode ?: 0,
            mirostatTau = preset?.mirostatTau ?: 5f,
            mirostatEta = preset?.mirostatEta ?: 0.1f,
            stop = stopSequences,
            stream = true
        )

        val body = json.encodeToString(LlamaCppRequest.serializer(), request)
        val httpReq = Request.Builder()
            .url("$baseUrl/completion")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        val accumulated = StringBuilder()
        okHttpClient.newCall(httpReq).execute().use { response ->
            if (!response.isSuccessful) {
                emit(StreamEvent.Error("llama.cpp error: HTTP ${response.code}"))
                return@use
            }
            response.body?.source()?.let { source ->
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (line.startsWith("data: ")) {
                        val data = line.removePrefix("data: ").trim()
                        if (data == "[DONE]") break
                        try {
                            val chunk = json.decodeFromString<LlamaCppStreamChunk>(data)
                            val token = chunk.content
                            if (token.isNotEmpty()) {
                                accumulated.append(token)
                                emit(StreamEvent.Token(token, accumulated.toString()))
                            }
                            if (chunk.stop) break
                        } catch (e: Exception) { /* skip */ }
                    }
                }
            }
        }
        emit(StreamEvent.Complete(accumulated.toString()))
    }

    // ── OpenAI Chat Completions (shared by many backends) ────────────────────

    private fun streamChatCompletions(
        prompt: String,
        messages: List<PromptMessage>?,
        config: ApiConfiguration,
        preset: TextGenPreset?,
        oaiPreset: OaiPreset?,
        stopSequences: List<String>,
        apiKey: String
    ): Flow<StreamEvent> = flow {
        val baseUrl = config.effectiveBaseUrl.trimEnd('/').removeSuffix("/v1")

        // Use structured messages from PromptBuilder when available,
        // otherwise fall back to wrapping the flat prompt as a single user message.
        val oaiMessages = messages?.map { OaiMessage(it.role, it.content) }
            ?: listOf(OaiMessage("user", prompt))

        // Parameters come exclusively from OaiPreset. TextGen preset values are never used
        // for chat completion — they're local sampler concepts that don't map to cloud APIs.
        // If no OAI preset is selected, all params are null (model uses its own defaults),
        // except max_tokens which gets a safe fallback to prevent runaway generation.
        val request = OaiChatRequest(
            model = config.currentModel.ifBlank { "gpt-4o-mini" },
            messages = oaiMessages,
            stream = true,
            temperature = if (oaiPreset != null && oaiPreset.temperatureEnabled) oaiPreset.temperature else null,
            maxTokens = if (oaiPreset != null && oaiPreset.maxTokensEnabled) oaiPreset.maxTokens else 1024,
            topP = if (oaiPreset != null && oaiPreset.topPEnabled) oaiPreset.topP else null,
            topK = if (oaiPreset != null && oaiPreset.topKEnabled) oaiPreset.topK else null,
            frequencyPenalty = if (oaiPreset != null && oaiPreset.frequencyPenaltyEnabled) oaiPreset.frequencyPenalty else null,
            presencePenalty = if (oaiPreset != null && oaiPreset.presencePenaltyEnabled) oaiPreset.presencePenalty else null,
            repetitionPenalty = if (oaiPreset != null && oaiPreset.repetitionPenaltyEnabled) oaiPreset.repetitionPenalty else null,
            minP = if (oaiPreset != null && oaiPreset.minPEnabled) oaiPreset.minP else null,
            topA = if (oaiPreset != null && oaiPreset.topAEnabled) oaiPreset.topA else null,
            seed = if (oaiPreset != null && oaiPreset.seedEnabled && oaiPreset.seed >= 0) oaiPreset.seed else null,
            stop = stopSequences.ifEmpty { null }
        )

        val body = json.encodeToString(OaiChatRequest.serializer(), request)

        // Log the full request so the in-app Debug Log shows exactly what was sent
        DebugLogger.logSection("Chat Completion Request → $baseUrl")
        DebugLogger.logKeyValue("model", request.model)
        DebugLogger.logKeyValue("max_tokens", request.maxTokens)
        DebugLogger.logKeyValue("temperature", request.temperature)
        DebugLogger.logKeyValue("messages count", oaiMessages.size)
        oaiMessages.forEachIndexed { i, m ->
            DebugLogger.log("  [msg $i] role=${m.role}")
            m.content.lines().take(8).forEach { DebugLogger.log("    $it") }
            if (m.content.lines().size > 8) DebugLogger.log("    ... (${m.content.lines().size - 8} more lines)")
        }

        val httpReq = Request.Builder()
            .url("$baseUrl/v1/chat/completions")
            .post(body.toRequestBody("application/json".toMediaType()))
            .addHeader("Content-Type", "application/json")
            .also { if (apiKey.isNotBlank()) it.addHeader("Authorization", "Bearer $apiKey") }
            .also { if (config.chatCompletionSource == "claude") it.addHeader("x-api-key", apiKey)
                        .addHeader("anthropic-version", "2023-06-01") }
            .build()

        val accumulated = StringBuilder()
        okHttpClient.newCall(httpReq).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                val message = try {
                    val obj = json.parseToJsonElement(errorBody).jsonObject
                    val msg = (obj["error"]?.jsonPrimitive?.contentOrNull
                        ?: obj["message"]?.jsonPrimitive?.contentOrNull
                        ?: "").ifBlank { null }
                    if (msg != null) "${config.displayName} error HTTP ${response.code}: $msg"
                    else "${config.displayName} error HTTP ${response.code}"
                } catch (_: Exception) {
                    "${config.displayName} error HTTP ${response.code}: $errorBody"
                }
                emit(StreamEvent.Error(message))
                return@use
            }
            response.body?.source()?.let { source ->
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data: ")) continue
                    val data = line.removePrefix("data: ").trim()
                    if (data == "[DONE]") break
                    try {
                        val chunk = json.decodeFromString<OaiStreamChunk>(data)
                        val token = chunk.choices.firstOrNull()?.delta?.content ?: continue
                        if (token.isNotEmpty()) {
                            accumulated.append(token)
                            emit(StreamEvent.Token(token, accumulated.toString()))
                        }
                    } catch (e: Exception) { /* skip */ }
                }
            }
        }
        val result = accumulated.toString()
        DebugLogger.logSection("Chat Completion Response")
        result.lines().take(20).forEach { DebugLogger.log("  $it") }
        if (result.lines().size > 20) DebugLogger.log("  ... (${result.lines().size - 20} more lines)")
        emit(StreamEvent.Complete(result))
    }

    // OpenAI text completions fallback (TextGenWebUI, Ooba, etc.)
    private fun streamOaiText(
        prompt: String,
        config: ApiConfiguration,
        preset: TextGenPreset?,
        stopSequences: List<String>,
        apiKey: String
    ): Flow<StreamEvent> = flow {
        val baseUrl = config.apiServer.trimEnd('/').removeSuffix("/v1")
        val request = OaiTextRequest(
            model = config.currentModel.ifBlank { "default" },
            prompt = prompt,
            stream = true,
            temperature = preset?.temperature,
            maxTokens = preset?.maxNewTokens,
            topP = preset?.topP,
            stop = stopSequences.ifEmpty { null }
        )
        val body = json.encodeToString(OaiTextRequest.serializer(), request)
        val httpReq = Request.Builder()
            .url("$baseUrl/v1/completions")
            .post(body.toRequestBody("application/json".toMediaType()))
            .addHeader("Content-Type", "application/json")
            .also { if (apiKey.isNotBlank()) it.addHeader("Authorization", "Bearer $apiKey") }
            .build()

        val accumulated = StringBuilder()
        okHttpClient.newCall(httpReq).execute().use { response ->
            if (!response.isSuccessful) {
                emit(StreamEvent.Error("API error: HTTP ${response.code}"))
                return@use
            }
            response.body?.source()?.let { source ->
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data: ")) continue
                    val data = line.removePrefix("data: ").trim()
                    if (data == "[DONE]") break
                    try {
                        val obj = json.decodeFromString<kotlinx.serialization.json.JsonObject>(data)
                        val token = obj["choices"]?.let { choices ->
                            val arr = choices.let { kotlinx.serialization.json.Json.decodeFromJsonElement(kotlinx.serialization.json.JsonArray.serializer(), it) }
                            arr.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
                        } ?: continue
                        if (token.isNotEmpty()) {
                            accumulated.append(token)
                            emit(StreamEvent.Token(token, accumulated.toString()))
                        }
                    } catch (e: Exception) { /* skip */ }
                }
            }
        }
        emit(StreamEvent.Complete(accumulated.toString()))
    }

    // ── Model Listing Helpers ─────────────────────────────────────────────────

    private suspend fun fetchOaiModels(config: ApiConfiguration): List<AvailableModel> {
        // Strip trailing /v1 — we always append it ourselves, so users entering
        // "http://ip:4141/v1" as customUrl don't get "…/v1/v1/models".
        val baseUrl = config.effectiveBaseUrl.trimEnd('/').removeSuffix("/v1")
        val url = "$baseUrl/v1/models"
        DebugLogger.log("fetchOaiModels: GET $url")
        val req = Request.Builder()
            .url(url)
            .also { if (config.apiKey.isNotBlank()) it.addHeader("Authorization", "Bearer ${config.apiKey}") }
            .build()
        return try {
            okHttpClient.newCall(req).execute().use { response ->
                val body = response.body?.string() ?: ""
                DebugLogger.log("fetchOaiModels: HTTP ${response.code}, body=${body.take(300)}")
                if (!response.isSuccessful) return emptyList()
                val parsed = json.decodeFromString<OaiModelsResponse>(body)
                parsed.data.map { AvailableModel(id = it.id, contextLength = it.contextLength) }
            }
        } catch (e: Exception) {
            DebugLogger.logError("LlmRepository", "fetchOaiModels failed: $url", e)
            emptyList()
        }
    }

    private suspend fun fetchOllamaModels(config: ApiConfiguration): List<AvailableModel> {
        val req = Request.Builder().url("${config.apiServer.trimEnd('/')}/api/tags").build()
        return try {
            okHttpClient.newCall(req).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: return emptyList()
                val parsed = json.decodeFromString<OllamaTagsResponse>(body)
                parsed.models.map { AvailableModel(id = it.name) }
            }
        } catch (e: Exception) { emptyList() }
    }

    private suspend fun fetchKoboldModel(config: ApiConfiguration): List<AvailableModel> {
        val req = Request.Builder().url("${config.apiServer.trimEnd('/')}/api/v1/model").build()
        return try {
            okHttpClient.newCall(req).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: return emptyList()
                val parsed = json.decodeFromString<KoboldModelResponse>(body)
                if (parsed.result.isNotBlank()) listOf(AvailableModel(id = parsed.result))
                else emptyList()
            }
        } catch (e: Exception) { emptyList() }
    }
}

/**
 * Effective base URL for generation requests.
 * Chat completion sources use their hardcoded cloud URL (or customUrl override).
 * Text completion sources always use apiServer (local/self-hosted).
 */
private val ApiConfiguration.effectiveBaseUrl: String
    get() = if (usesChatCompletions) chatCompletionBaseUrl else apiServer.trimEnd('/')
