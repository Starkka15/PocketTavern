package com.pockettavern.app.data.repository

import com.pockettavern.app.data.local.SettingsDataStore
import com.pockettavern.app.data.local.inference.GgufEngine
import com.pockettavern.app.data.local.inference.OnDeviceEngine
import com.pockettavern.app.data.local.inference.OnDeviceModelManager
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
    @Named("LLM") private val okHttpClient: OkHttpClient,
    private val onDeviceEngine: OnDeviceEngine,
    private val ggufEngine: GgufEngine,
    private val onDeviceModels: OnDeviceModelManager
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
        oaiPreset: OaiPreset? = null,
        showThoughts: Boolean = false
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
                    apiKey = apiKey,
                    showThoughts = showThoughts
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
                config.isOnDevice -> onDeviceModels.listModels(OnDeviceModelManager.LITERTLM_EXTS)
                config.isOnDeviceGguf -> onDeviceModels.listModels(OnDeviceModelManager.GGUF_EXTS)
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
                config.isOnDevice -> onDeviceModels.pathFor(config.currentModel, OnDeviceModelManager.LITERTLM_EXTS) != null
                config.isOnDeviceGguf -> onDeviceModels.pathFor(config.currentModel, OnDeviceModelManager.GGUF_EXTS) != null
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

    /**
     * On-device generation via LiteRT-LM. System messages become the systemInstruction, prior
     * turns become conversation history, and the final message is the turn to respond to.
     * Sampler params come from the Chat Completion Preset (the only knobs LiteRT-LM exposes).
     */
    private fun streamOnDevice(
        messages: List<PromptMessage>?,
        prompt: String,
        config: ApiConfiguration,
        oaiPreset: OaiPreset?,
        stopSequences: List<String>
    ): Flow<StreamEvent> = flow {
        val modelPath = onDeviceModels.pathFor(config.currentModel, OnDeviceModelManager.LITERTLM_EXTS)
        if (modelPath == null) {
            emit(StreamEvent.Error("On-device model \"${config.currentModel}\" is not downloaded."))
            return@flow
        }

        val msgs = messages ?: listOf(PromptMessage("user", prompt))
        val system = msgs.filter { it.role.equals("system", true) }
            .joinToString("\n\n") { it.content }.ifBlank { null }
        val convo = msgs.filterNot { it.role.equals("system", true) }
        val userMessage = convo.lastOrNull()?.content ?: prompt
        val history = convo.dropLast(1).map {
            OnDeviceEngine.Turn(isUser = !it.role.equals("assistant", true), content = it.content)
        }

        val maxTokens = if (oaiPreset != null && oaiPreset.maxTokensEnabled) oaiPreset.maxTokens else 512
        val params = OnDeviceEngine.Params(
            maxTokens = maxTokens,
            topK = if (oaiPreset != null && oaiPreset.topKEnabled) oaiPreset.topK else 40,
            topP = if (oaiPreset != null && oaiPreset.topPEnabled) oaiPreset.topP else 0.95f,
            temperature = if (oaiPreset != null && oaiPreset.temperatureEnabled) oaiPreset.temperature else 0.8f,
        )

        emitOnDeviceStream(
            onDeviceEngine.generate(system, history, userMessage, modelPath, useGpu = true, params),
            showThoughts = config.showThoughts,
            primeThink = isReasoningModel(config.currentModel),
            maxOutputTokens = maxTokens,
            stopSequences = stopSequences,
        ) { emit(it) }
    }

    /**
     * On-device GGUF generation via llama.cpp (Llamatik). Chat-completion path: messages are
     * fed through the GGUF's embedded chat template. Samplers from the Chat Completion Preset.
     */
    private fun streamOnDeviceGguf(
        messages: List<PromptMessage>?,
        prompt: String,
        config: ApiConfiguration,
        oaiPreset: OaiPreset?,
        stopSequences: List<String>
    ): Flow<StreamEvent> = flow {
        val modelPath = onDeviceModels.pathFor(config.currentModel, OnDeviceModelManager.GGUF_EXTS)
        if (modelPath == null) {
            emit(StreamEvent.Error("GGUF model \"${config.currentModel}\" is not downloaded."))
            return@flow
        }
        val msgs = messages ?: listOf(PromptMessage("user", prompt))
        val pairs = msgs.map { it.role to it.content }
        val maxTokens = if (oaiPreset != null && oaiPreset.maxTokensEnabled) oaiPreset.maxTokens else 512
        val params = GgufEngine.Params(
            maxTokens = maxTokens,
            topK = if (oaiPreset != null && oaiPreset.topKEnabled) oaiPreset.topK else 40,
            topP = if (oaiPreset != null && oaiPreset.topPEnabled) oaiPreset.topP else 0.95f,
            temperature = if (oaiPreset != null && oaiPreset.temperatureEnabled) oaiPreset.temperature else 0.8f,
            repeatPenalty = if (oaiPreset != null && oaiPreset.repetitionPenaltyEnabled) oaiPreset.repetitionPenalty else 1.1f,
        )
        emitOnDeviceStream(
            ggufEngine.generate(pairs, modelPath, params),
            showThoughts = config.showThoughts,
            primeThink = isReasoningModel(config.currentModel),
            maxOutputTokens = maxTokens,
            stopSequences = stopSequences,
        ) { emit(it) }
    }

    /** Reasoning models embed <think>…</think> in their output (often only the closing tag). */
    private fun isReasoningModel(modelId: String): Boolean {
        val m = modelId.lowercase()
        return listOf("r1", "qwq", "distill", "deepseek-r", "reasoning", "-think", "thinking")
            .any { m.contains(it) }
    }

    private fun streamChatCompletions(
        prompt: String,
        messages: List<PromptMessage>?,
        config: ApiConfiguration,
        preset: TextGenPreset?,
        oaiPreset: OaiPreset?,
        stopSequences: List<String>,
        apiKey: String,
        showThoughts: Boolean = false
    ): Flow<StreamEvent> = flow {
        // On-device: run locally instead of an HTTP chat-completion call.
        if (config.isOnDevice) {
            streamOnDevice(messages, prompt, config, oaiPreset, stopSequences).collect { emit(it) }
            return@flow
        }
        if (config.isOnDeviceGguf) {
            streamOnDeviceGguf(messages, prompt, config, oaiPreset, stopSequences).collect { emit(it) }
            return@flow
        }

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
        val accumulatedThinking = StringBuilder()
        var inThinkBlock = false

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
                        val delta = chunk.choices.firstOrNull()?.delta ?: continue

                        // reasoning_content (OpenRouter/official) or reasoning (nano-gpt, some proxies)
                        val reasoningToken = delta.reasoningContent ?: delta.reasoning
                        if (reasoningToken != null && reasoningToken.isNotEmpty()) {
                            if (showThoughts) {
                                accumulatedThinking.append(reasoningToken)
                                emit(StreamEvent.ThinkingToken(reasoningToken, accumulatedThinking.toString()))
                            }
                            // NB: do NOT `continue` — the same delta may also carry the first
                            // content token(s) (transition chunk). Skipping it dropped the opening
                            // words of the response on reasoning models (R1). Fall through.
                        }

                        val rawToken = delta.content ?: continue
                        if (rawToken.isEmpty()) continue

                        // <think>...</think> tag handling (local endpoints that embed thinking in content)
                        emitTokenWithThinkHandling(
                            rawToken, showThoughts,
                            accumulated, accumulatedThinking,
                            inThinkBlockRef = { inThinkBlock },
                            setInThinkBlock = { inThinkBlock = it }
                        ) { event -> emit(event) }

                    } catch (e: Exception) { /* skip malformed */ }
                }
            }
        }
        val result = accumulated.toString()
        DebugLogger.logSection("Chat Completion Response")
        result.lines().take(20).forEach { DebugLogger.log("  $it") }
        if (result.lines().size > 20) DebugLogger.log("  ... (${result.lines().size - 20} more lines)")
        emit(StreamEvent.Complete(result, accumulatedThinking.toString()))
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
 * Splits a raw content token around <think>...</think> tags, emitting ThinkingToken or Token
 * events accordingly. Handles tags that span multiple tokens via the inThinkBlock flag.
 */
private suspend fun emitTokenWithThinkHandling(
    rawToken: String,
    showThoughts: Boolean,
    accumulated: StringBuilder,
    accumulatedThinking: StringBuilder,
    inThinkBlockRef: () -> Boolean,
    setInThinkBlock: (Boolean) -> Unit,
    emit: suspend (StreamEvent) -> Unit
) {
    var remaining = rawToken
    while (remaining.isNotEmpty()) {
        if (!inThinkBlockRef()) {
            val startIdx = remaining.indexOf("<think>")
            if (startIdx < 0) {
                accumulated.append(remaining)
                emit(StreamEvent.Token(remaining, accumulated.toString()))
                break
            }
            val before = remaining.substring(0, startIdx)
            if (before.isNotEmpty()) {
                accumulated.append(before)
                emit(StreamEvent.Token(before, accumulated.toString()))
            }
            remaining = remaining.substring(startIdx + "<think>".length)
            setInThinkBlock(true)
        } else {
            val endIdx = remaining.indexOf("</think>")
            if (endIdx < 0) {
                if (showThoughts) {
                    accumulatedThinking.append(remaining)
                    emit(StreamEvent.ThinkingToken(remaining, accumulatedThinking.toString()))
                }
                break
            }
            val thinkChunk = remaining.substring(0, endIdx)
            if (showThoughts && thinkChunk.isNotEmpty()) {
                accumulatedThinking.append(thinkChunk)
                emit(StreamEvent.ThinkingToken(thinkChunk, accumulatedThinking.toString()))
            }
            remaining = remaining.substring(endIdx + "</think>".length)
            setInThinkBlock(false)
        }
    }
}

/** Thrown to stop an on-device generation early (stop sequence / max tokens / loop). */
private class OnDeviceStop : RuntimeException()

/**
 * Degeneration guard for engines without a repetition penalty (LiteRT-LM): if the trailing
 * window has already appeared 3+ times, the model is looping — bail. Conservative (48-char
 * exact repeats are vanishingly rare in good prose).
 */
private fun isDegenerateLoop(s: CharSequence): Boolean {
    if (s.length < 300) return false
    val w = s.substring(s.length - 48)
    var idx = s.indexOf(w); var count = 0
    while (idx >= 0) { count++; if (count >= 3) return true; idx = s.indexOf(w, idx + 1) }
    return false
}

/** Trim a looping tail back to the second occurrence of the repeating window. */
private fun trimLoop(s: CharSequence): String {
    val str = s.toString()
    val w = str.substring(str.length - 48)
    val first = str.indexOf(w)
    val second = if (first >= 0) str.indexOf(w, first + w.length) else -1
    return if (second > 0) str.substring(0, second).trimEnd() else str
}

/**
 * Drives an on-device engine [chunks] flow into [StreamEvent]s, applying the Chat-Completion
 * settings the engines don't enforce natively: <think> splitting, stop sequences, a max-token
 * (approx via chars) cap, and a repetition/loop guard. Stops the engine early when any fires.
 */
private suspend fun emitOnDeviceStream(
    chunks: Flow<OnDeviceEngine.Chunk>,
    showThoughts: Boolean,
    primeThink: Boolean,
    maxOutputTokens: Int,
    stopSequences: List<String>,
    emit: suspend (StreamEvent) -> Unit
) {
    val acc = StringBuilder()
    val think = StringBuilder()
    var inThink = primeThink
    val maxChars = maxOutputTokens.coerceAtLeast(16) * 4   // rough tokens→chars
    val stops = stopSequences.filter { it.isNotBlank() }
    try {
        chunks.collect { chunk ->
            when (chunk) {
                is OnDeviceEngine.Chunk.Token -> {
                    val before = acc.length
                    emitTokenWithThinkHandling(chunk.text, showThoughts, acc, think, { inThink }, { inThink = it }) { emit(it) }
                    if (!inThink && acc.length > before) {
                        val cut = stops.mapNotNull { s -> acc.indexOf(s).takeIf { it >= 0 } }.minOrNull()
                        when {
                            cut != null -> { emit(StreamEvent.Complete(acc.substring(0, cut).trimEnd(), think.toString())); throw OnDeviceStop() }
                            acc.length >= maxChars -> { emit(StreamEvent.Complete(acc.toString().trimEnd(), think.toString())); throw OnDeviceStop() }
                            isDegenerateLoop(acc) -> { emit(StreamEvent.Complete(trimLoop(acc), think.toString())); throw OnDeviceStop() }
                        }
                    }
                }
                is OnDeviceEngine.Chunk.Thinking ->
                    if (showThoughts) { think.append(chunk.text); emit(StreamEvent.ThinkingToken(chunk.text, think.toString())) }
                is OnDeviceEngine.Chunk.Done -> emit(StreamEvent.Complete(acc.toString().trimEnd(), think.toString()))
                is OnDeviceEngine.Chunk.Error -> emit(StreamEvent.Error(chunk.message))
            }
        }
    } catch (e: OnDeviceStop) { /* early stop — engine cancelled via the flow's awaitClose */ }
}

/**
 * Effective base URL for generation requests.
 * Chat completion sources use their hardcoded cloud URL (or customUrl override).
 * Text completion sources always use apiServer (local/self-hosted).
 */
private val ApiConfiguration.effectiveBaseUrl: String
    get() = if (usesChatCompletions) chatCompletionBaseUrl else apiServer.trimEnd('/')
