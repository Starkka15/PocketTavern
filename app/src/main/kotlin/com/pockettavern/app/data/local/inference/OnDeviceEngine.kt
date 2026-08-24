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
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
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
    @ApplicationContext private val context: Context,
    private val memoryManager: OnDeviceMemoryManager
) {
    data class Params(
        val maxTokens: Int = 1024,
        val topK: Int = 40,
        val topP: Float = 0.95f,
        val temperature: Float = 0.8f,
    )

    /** A prior conversation turn (system messages go to systemInstruction instead). */
    data class Turn(val isUser: Boolean, val content: String)

    private val loadMutex = Mutex()  // serializes model load
    private val genMutex = Mutex()   // serializes generation (shared Engine, one at a time)
    private var engine: Engine? = null
    private var loadedModelPath: String? = null

    /** Backend the loaded model is actually running on ("NPU"/"GPU"/"CPU"), for reporting. */
    @Volatile var activeBackend: String? = null
        private set

    val isLoaded: Boolean get() = engine != null

    init {
        memoryManager.register(OnDeviceMemoryManager.Slot.LITERTLM, ::unload)
    }

    @OptIn(ExperimentalApi::class)
    private suspend fun ensureLoaded(modelPath: String, useGpu: Boolean) = loadMutex.withLock {
        if (engine != null && loadedModelPath == modelPath) return@withLock
        memoryManager.prepareLoad(OnDeviceMemoryManager.Slot.LITERTLM, File(modelPath).length())
        closeInternal()
        // Attempt best→safest backend with fallback. NPU only on capable SoCs (needs a vendor
        // delegate that may be absent → falls back). GPU/OpenCL works on most Adreno (and some
        // Mali); generic .litertlm models that lack a working GPU delegate fall back to CPU.
        val attempts = buildList<Pair<String, () -> Backend>> {
            if (useGpu && DeviceCapabilities.isNpuCapableSoc()) {
                add("NPU" to { Backend.NPU(nativeLibraryDir = context.applicationInfo.nativeLibraryDir) })
            }
            if (useGpu) add("GPU" to { Backend.GPU() })
            add("CPU" to { Backend.CPU() })
        }
        var lastError: Exception? = null
        for ((label, backendFactory) in attempts) {
            try {
                DebugLogger.log("OnDeviceEngine: loading $modelPath on $label (soc=${DeviceCapabilities.soc()})")
                val config = EngineConfig(
                    modelPath = modelPath,
                    backend = backendFactory(),
                    cacheDir = context.getExternalFilesDir(null)?.absolutePath,
                )
                val e = Engine(config)
                e.initialize()
                engine = e
                loadedModelPath = modelPath
                activeBackend = label
                DebugLogger.log("OnDeviceEngine: model loaded on $label")
                return@withLock
            } catch (ex: Exception) {
                lastError = ex
                DebugLogger.logError("OnDeviceEngine", "load failed on $label", ex)
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
        // Serialize generations: the LiteRT [Engine] is shared, so overlapping generations (e.g.
        // cancel-then-regenerate) on it can crash. genMutex is held for the whole async generation
        // (released when it completes/errs/cancels via [finished]).
        var conversation: Conversation? = null
        val finished = kotlinx.coroutines.CompletableDeferred<Unit>()
        val job = launch {
            try {
                genMutex.withLock {
                    ensureLoaded(modelPath, useGpu)
                    val activeEngine = engine ?: throw IllegalStateException("On-device engine failed to load")

                    val initialMessages = history.map { turn ->
                        if (turn.isUser) Message.user(turn.content) else Message.model(turn.content)
                    }
                    val conv = activeEngine.createConversation(
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
                    conversation = conv

                    val t0 = System.currentTimeMillis()
                    DebugLogger.log("OnDeviceEngine: prefill start (system ${system?.length ?: 0} + user ${userMessage.length} chars, ${initialMessages.size} history msgs)")
                    var firstToken = true
                    conv.sendMessageAsync(
                        Contents.of(listOf(Content.Text(userMessage))),
                        object : MessageCallback {
                            override fun onMessage(message: Message) {
                                val delta = message.toString()
                                if (delta.startsWith("<ctrl")) return  // skip control tokens
                                if (firstToken) {
                                    firstToken = false
                                    DebugLogger.log("OnDeviceEngine: first token after ${System.currentTimeMillis() - t0}ms")
                                }
                                val thought = message.channels["thought"]
                                if (!thought.isNullOrEmpty()) trySend(Chunk.Thinking(thought))
                                if (delta.isNotEmpty()) trySend(Chunk.Token(delta))
                            }

                            override fun onDone() {
                                DebugLogger.log("OnDeviceEngine: done in ${System.currentTimeMillis() - t0}ms")
                                trySend(Chunk.Done)
                                finished.complete(Unit)
                            }

                            override fun onError(throwable: Throwable) {
                                if (throwable is CancellationException) {
                                    trySend(Chunk.Done)
                                } else {
                                    DebugLogger.logError("OnDeviceEngine", "inference error", throwable)
                                    trySend(Chunk.Error(throwable.message ?: "On-device inference failed"))
                                }
                                finished.complete(Unit)
                            }
                        }
                    )
                    finished.await()  // hold genMutex until this generation actually finishes
                }
            } catch (e: Exception) {
                trySend(Chunk.Error(e.message ?: "On-device inference failed"))
            } finally {
                try { conversation?.close() } catch (_: Exception) {}
                close()
            }
        }

        awaitClose {
            try { conversation?.cancelProcess() } catch (_: Exception) {}
            finished.complete(Unit)  // release the await → genMutex frees for the next gen
            job.cancel()
        }
    }

    suspend fun unload() = loadMutex.withLock { closeInternal() }

    private fun closeInternal() {
        try { engine?.close() } catch (e: Exception) {
            DebugLogger.logError("OnDeviceEngine", "engine close failed", e)
        }
        engine = null
        loadedModelPath = null
        activeBackend = null
    }

    sealed class Chunk {
        data class Token(val text: String) : Chunk()
        data class Thinking(val text: String) : Chunk()
        data object Done : Chunk()
        data class Error(val message: String) : Chunk()
    }
}
