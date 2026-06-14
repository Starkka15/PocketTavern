package com.pockettavern.app.data.local.inference

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
import com.pockettavern.app.util.DebugLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device LLM inference via LiteRT-LM (Google AI Edge, Apache-2.0).
 *
 * LiteRT-LM's Conversation applies each model's own chat template internally, so on-device is
 * driven through PocketTavern's chat-completion path (role-tagged messages), NOT a pre-built
 * text-gen prompt. Each generation is stateless: system text → [systemInstruction], prior turns
 * → [initialMessages], the final user turn → sendMessageAsync. The (expensive) [Engine] is kept
 * loaded across generations and only reloaded when the model file or backend changes.
 */
@Singleton
class OnDeviceEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    data class Params(
        val maxTokens: Int = 1024,
        val topK: Int = 40,
        val topP: Float = 0.95f,
        val temperature: Float = 0.8f,
    )

    /** A prior conversation turn (system messages go to systemInstruction instead). */
    data class Turn(val isUser: Boolean, val content: String)

    private val loadMutex = Mutex()
    private var engine: Engine? = null
    private var loadedModelPath: String? = null
    private var loadedUseGpu: Boolean = true

    val isLoaded: Boolean get() = engine != null

    @OptIn(ExperimentalApi::class)
    private suspend fun ensureLoaded(modelPath: String, useGpu: Boolean) = loadMutex.withLock {
        if (engine != null && loadedModelPath == modelPath) return@withLock
        closeInternal()
        // Try the preferred backend first; many generic .litertlm models don't have a working
        // GPU/OpenCL delegate on a given device, so fall back to CPU rather than failing.
        val order = if (useGpu) listOf(true, false) else listOf(false)
        var lastError: Exception? = null
        for (gpu in order) {
            try {
                DebugLogger.log("OnDeviceEngine: loading $modelPath on ${if (gpu) "GPU" else "CPU"}")
                val config = EngineConfig(
                    modelPath = modelPath,
                    backend = if (gpu) Backend.GPU() else Backend.CPU(),
                    cacheDir = context.getExternalFilesDir(null)?.absolutePath,
                )
                val e = Engine(config)
                e.initialize()
                engine = e
                loadedModelPath = modelPath
                loadedUseGpu = gpu
                DebugLogger.log("OnDeviceEngine: model loaded on ${if (gpu) "GPU" else "CPU"}")
                return@withLock
            } catch (ex: Exception) {
                lastError = ex
                DebugLogger.logError("OnDeviceEngine", "load failed on ${if (gpu) "GPU" else "CPU"}", ex)
                closeInternal()
            }
        }
        throw lastError ?: IllegalStateException("Failed to load on-device model")
    }

    /**
     * Stream a generation. [system] is the system prompt (may be null/blank), [history] the prior
     * turns in order, [userMessage] the final turn to respond to. Emits engine-level [Chunk]s; the
     * caller maps them to domain StreamEvents.
     */
    @OptIn(ExperimentalApi::class)
    fun generate(
        system: String?,
        history: List<Turn>,
        userMessage: String,
        modelPath: String,
        useGpu: Boolean,
        params: Params,
    ): Flow<Chunk> = callbackFlow {
        ensureLoaded(modelPath, useGpu)
        val activeEngine = engine ?: run {
            trySend(Chunk.Error("On-device engine failed to load"))
            close(); return@callbackFlow
        }

        val initialMessages = history.map { turn ->
            if (turn.isUser) Message.user(turn.content) else Message.model(turn.content)
        }

        val conversation: Conversation = activeEngine.createConversation(
            ConversationConfig(
                systemInstruction = if (system.isNullOrBlank()) null else Contents.of(system),
                initialMessages = initialMessages,
                samplerConfig = SamplerConfig(
                    topK = params.topK,
                    topP = params.topP.toDouble(),
                    temperature = params.temperature.toDouble(),
                ),
            )
        )

        conversation.sendMessageAsync(
            Contents.of(listOf(Content.Text(userMessage))),
            object : MessageCallback {
                override fun onMessage(message: Message) {
                    val delta = message.toString()
                    if (delta.startsWith("<ctrl")) return  // skip control tokens
                    val thought = message.channels["thought"]
                    if (!thought.isNullOrEmpty()) trySend(Chunk.Thinking(thought))
                    if (delta.isNotEmpty()) trySend(Chunk.Token(delta))
                }

                override fun onDone() {
                    trySend(Chunk.Done); close()
                }

                override fun onError(throwable: Throwable) {
                    if (throwable is CancellationException) {
                        trySend(Chunk.Done)
                    } else {
                        DebugLogger.logError("OnDeviceEngine", "inference error", throwable)
                        trySend(Chunk.Error(throwable.message ?: "On-device inference failed"))
                    }
                    close()
                }
            }
        )

        awaitClose {
            try { conversation.cancelProcess() } catch (_: Exception) {}
            try { conversation.close() } catch (_: Exception) {}
        }
    }

    suspend fun unload() = loadMutex.withLock { closeInternal() }

    private fun closeInternal() {
        try { engine?.close() } catch (e: Exception) {
            DebugLogger.logError("OnDeviceEngine", "engine close failed", e)
        }
        engine = null
        loadedModelPath = null
    }

    sealed class Chunk {
        data class Token(val text: String) : Chunk()
        data class Thinking(val text: String) : Chunk()
        data object Done : Chunk()
        data class Error(val message: String) : Chunk()
    }
}
