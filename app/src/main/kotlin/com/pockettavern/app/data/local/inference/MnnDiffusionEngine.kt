package com.pockettavern.app.data.local.inference

import android.content.Context
import android.util.Base64
import com.pockettavern.app.GenerationService
import com.pockettavern.app.util.DebugLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device SDXL image generation via MNN (Apache-2.0). CPU backend only — GPU (OpenCL) is a
 * confirmed dead end for this project on this hardware, see mnn_sdxl_android_pipeline memory.
 *
 * Owns the native `StableDiffusionXL` handle lifecycle (create → load → generate* → destroy),
 * mirroring [GgufEngine]'s shape: a load-mutex guarding lazy (re)load, a generation-mutex
 * serializing native calls (MNN's diffusion engine, like llama.cpp here, isn't safe to call
 * concurrently from two overlapping generations), and a `callbackFlow` wrapping the blocking
 * native call in a background job so cancellation/cleanup has somewhere to run.
 *
 * Only SDXL is wired up ([MODEL_TYPE_SDXL], `nativeGenerateXL`) -- SD1.5 support would need a
 * second native entry point (`StableDiffusion::run()`'s signature differs from `runXL()`'s), not
 * added yet.
 */
@Singleton
class MnnDiffusionEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val memoryManager: OnDeviceMemoryManager
) {
    sealed class Progress {
        object Started : Progress()
        data class Step(val percent: Int) : Progress()
        data class Done(val imageBase64: String) : Progress()
        data class Error(val message: String) : Progress()
    }

    private val lock = Mutex()      // serializes create/load/destroy of the native handle
    private val genMutex = Mutex()  // serializes generation calls against that handle

    private var handle: Long = 0
    private var loadedModelPath: String? = null

    val isLoaded: Boolean get() = handle != 0L

    init {
        memoryManager.register(OnDeviceMemoryManager.Slot.SDXL, ::unload)
    }

    private suspend fun ensureLoaded(modelPath: String) = lock.withLock {
        if (loadedModelPath == modelPath && handle != 0L) return@withLock

        val modelBytes = File(modelPath).walkTopDown().filter { it.isFile }.sumOf { it.length() }
        memoryManager.prepareLoad(OnDeviceMemoryManager.Slot.SDXL, modelBytes)

        if (handle != 0L) {
            MnnDiffusionBridge.nativeDestroy(handle)
            handle = 0
            loadedModelPath = null
        }

        DebugLogger.log("MnnDiffusionEngine: creating+loading $modelPath")
        val newHandle = MnnDiffusionBridge.nativeCreate(modelPath, MODEL_TYPE_SDXL, MEMORY_MODE_FAST)
        if (newHandle == 0L) {
            throw IllegalStateException(
                "MNN failed to create the diffusion pipeline (bad model path, or malformed/missing model files at $modelPath)"
            )
        }
        if (!MnnDiffusionBridge.nativeLoad(newHandle)) {
            MnnDiffusionBridge.nativeDestroy(newHandle)
            throw IllegalStateException("MNN failed to load the diffusion model set at $modelPath")
        }
        handle = newHandle
        loadedModelPath = modelPath
        DebugLogger.log("MnnDiffusionEngine: loaded")
    }

    /**
     * Generates one SDXL image. Confirmed on real hardware: several minutes for a full-quality
     * 1024x1024 image (~30s/step for the UNet alone).
     *
     * Runs under [GenerationService] (started/stopped around the native call, same pattern
     * `ChatViewModel` already uses for LLM generation) for the whole call -- confirmed this was
     * not just UX polish but a real performance bug: without an active foreground service, this
     * engine's worker threads stayed busy (~123% CPU each, matching MNN's numThread=4) but the
     * CPU governor never boosted clock frequency for them (measured ~55-65% of max sustained,
     * with correspondingly low heat) -- Android's cpuset/scheduling-group policy apparently
     * doesn't classify a background-dispatcher thread as boost-eligible the way it does a
     * foreground service's process, even though the hosting Activity was itself in the
     * foreground the whole time. `GenerationService` also holds a PARTIAL_WAKE_LOCK, which
     * doubles as the fix for screen-off safety during a long generation -- but its wake lock has
     * a hardcoded 10-minute safety-net timeout, well under this engine's real run length, so
     * [heartbeat] re-invokes `start()` (a safe, idempotent `onStartCommand` re-fire on an
     * already-running service) every 5 minutes to keep renewing it.
     *
     * There is no native cancellation (checked MNN's full `Diffusion`/`StableDiffusionXL` public
     * interface -- nothing like stable-diffusion.cpp's `sd_cancel_generation()` exists). Cancelling
     * the returned [Flow] only detaches this side from listening; the blocking native call keeps
     * running to completion regardless, and its result is simply discarded when it finishes.
     */
    fun generate(
        modelPath: String,
        prompt: String,
        negativePrompt: String,
        width: Int,
        height: Int,
        steps: Int,
        seed: Int,
        cfgScale: Float,
    ): Flow<Progress> = callbackFlow {
        GenerationService.start(context, "Generating image on-device…")

        val heartbeat = launch(Dispatchers.Default) {
            while (isActive) {
                delay(5 * 60 * 1000L)
                GenerationService.start(context, "Generating image on-device…")
            }
        }

        val job = launch(Dispatchers.IO) {
            var outputFile: File? = null
            try {
                genMutex.withLock {
                    trySend(Progress.Started)
                    ensureLoaded(modelPath)

                    val file = File(context.cacheDir, "mnn_gen_${System.currentTimeMillis()}.png")
                    outputFile = file
                    val actualSeed = if (seed < 0) (0..Int.MAX_VALUE).random() else seed

                    DebugLogger.log("MnnDiffusionEngine: generating ${width}x$height, $steps steps, cfg=$cfgScale")
                    val ok = MnnDiffusionBridge.nativeGenerateXL(
                        handle, prompt, negativePrompt, file.absolutePath,
                        width, height, steps, actualSeed, cfgScale,
                    ) { percent ->
                        DebugLogger.log("MnnDiffusionEngine: step progress=$percent%")
                        trySend(Progress.Step(percent))
                    }

                    if (!ok || !file.exists() || file.length() == 0L) {
                        trySend(Progress.Error("MNN generation failed (runXL returned false or produced no output file)"))
                    } else {
                        val base64 = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
                        trySend(Progress.Done(base64))
                    }
                }
            } catch (e: Exception) {
                DebugLogger.logError("MnnDiffusionEngine", "generation failed", e)
                trySend(Progress.Error(e.message ?: "On-device SDXL generation failed"))
            } finally {
                outputFile?.delete()
                heartbeat.cancel()
                GenerationService.stop(context)
                close()
            }
        }

        awaitClose {
            job.cancel()
            heartbeat.cancel()
        }
    }

    suspend fun unload() = lock.withLock {
        if (handle != 0L) {
            MnnDiffusionBridge.nativeDestroy(handle)
            handle = 0
            loadedModelPath = null
        }
    }

    companion object {
        // MNN::DIFFUSION::DiffusionModelType::STABLE_DIFFUSION_XL (diffusion.hpp) -- only model
        // type this engine drives, see class doc.
        private const val MODEL_TYPE_SDXL = 4
        // "Memory enough" mode -- matches the config validated end-to-end via diffusion_demo
        // this session, and the phone has plenty of RAM headroom for one model set at a time.
        private const val MEMORY_MODE_FAST = 1
    }
}
