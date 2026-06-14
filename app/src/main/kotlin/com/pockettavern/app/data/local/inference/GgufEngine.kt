package com.pockettavern.app.data.local.inference

import com.llamatik.library.platform.GenStream
import com.llamatik.library.platform.LlamaBridge
import com.pockettavern.app.util.DebugLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device GGUF inference via llama.cpp (Llamatik, MIT). Runs the huge GGUF ecosystem —
 * including uncensored/abliterated RP fine-tunes that LiteRT-LM's `.litertlm` catalog lacks.
 *
 * Chat-completion path: messages → the GGUF's embedded chat template (applyChatTemplate) →
 * streamed generation. [LlamaBridge] is a global singleton (one model at a time), so we
 * serialize load + generation and keep the model loaded until the file changes.
 */
@Singleton
class GgufEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    data class Params(
        val maxTokens: Int = 512,
        val topK: Int = 40,
        val topP: Float = 0.95f,
        val temperature: Float = 0.8f,
        val repeatPenalty: Float = 1.1f,
        val contextLength: Int = 0,  // 0 = auto (scaled to device RAM)
        val gpuLayers: Int = 0,  // 0 = CPU (reliable on mobile); -1 = offload all to GPU/Vulkan
    )

    private val lock = Mutex()
    private var loadedModelPath: String? = null

    val isLoaded: Boolean get() = loadedModelPath != null

    private suspend fun ensureLoaded(modelPath: String) = lock.withLock {
        if (loadedModelPath == modelPath) return@withLock
        DebugLogger.log("GgufEngine: loading $modelPath")
        if (!LlamaBridge.initGenerateModel(modelPath)) {
            loadedModelPath = null
            throw IllegalStateException("llama.cpp failed to load model")
        }
        loadedModelPath = modelPath
        DebugLogger.log("GgufEngine: model loaded")
    }

    /**
     * Stream a generation. [messages] are (role, content) pairs (system/user/assistant) fed
     * through the model's own chat template. Emits [OnDeviceEngine.Chunk]s.
     */
    fun generate(
        messages: List<Pair<String, String>>,
        modelPath: String,
        params: Params,
    ): Flow<OnDeviceEngine.Chunk> = callbackFlow {
        ensureLoaded(modelPath)

        // Scale context + threads to the device unless the caller overrides contextLength.
        val ctx = if (params.contextLength > 0) params.contextLength
                  else DeviceCapabilities.recommendedContextLength(context)
        val threads = DeviceCapabilities.recommendedThreads()
        DebugLogger.log("GgufEngine: ${"%.1f".format(DeviceCapabilities.totalRamGb(context))}GB RAM → ctx=$ctx threads=$threads")

        LlamaBridge.updateGenerateParams(
            temperature = params.temperature,
            maxTokens = params.maxTokens,
            topP = params.topP,
            topK = params.topK,
            repeatPenalty = params.repeatPenalty,
            contextLength = ctx,
            numThreads = threads,
            useMmap = true,
            flashAttention = false,
            batchSize = 256,
            gpuLayers = params.gpuLayers,
        )

        // Apply the GGUF's embedded chat template; fall back to a simple join if absent.
        val prompt = LlamaBridge.applyChatTemplate(messages, addAssistantPrefix = true)
            ?: messages.joinToString("\n") { (role, content) -> "$role: $content" } + "\nassistant:"

        val t0 = System.currentTimeMillis()
        DebugLogger.log("GgufEngine: prefill start (prompt ${prompt.length} chars)")
        var firstToken = true
        LlamaBridge.generateStream(prompt, object : GenStream {
            override fun onDelta(text: String) {
                if (firstToken) {
                    firstToken = false
                    DebugLogger.log("GgufEngine: first token after ${System.currentTimeMillis() - t0}ms")
                }
                if (text.isNotEmpty()) trySend(OnDeviceEngine.Chunk.Token(text))
            }
            override fun onComplete() {
                DebugLogger.log("GgufEngine: done in ${System.currentTimeMillis() - t0}ms")
                trySend(OnDeviceEngine.Chunk.Done); close()
            }
            override fun onError(message: String) {
                DebugLogger.log("GgufEngine inference error: $message")
                trySend(OnDeviceEngine.Chunk.Error(message)); close()
            }
        })

        awaitClose {
            try { LlamaBridge.nativeCancelGenerate() } catch (_: Exception) {}
        }
    }

    suspend fun unload() = lock.withLock {
        try { LlamaBridge.shutdown() } catch (e: Exception) {
            DebugLogger.logError("GgufEngine", "shutdown failed", e)
        }
        loadedModelPath = null
    }
}
