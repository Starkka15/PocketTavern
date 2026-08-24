package com.pockettavern.app.data.local.inference

/**
 * Raw JNI surface for MNN's diffusion engine (`app/src/main/cpp/jni_diffusion.cpp`) — no business
 * logic here, matches the thin style of the prebuilt `LlamaBridge`/`LiteRT` wrappers this app
 * already uses. See [MnnDiffusionEngine] for the actual handle lifecycle / generation flow.
 *
 * modelType matches MNN::DIFFUSION::DiffusionModelType (only STABLE_DIFFUSION_XL = 4 is wired
 * up on the native side right now — see [MnnDiffusionEngine.MODEL_TYPE_SDXL] — the JNI layer
 * only exposes nativeGenerateXL, which calls StableDiffusionXL::runXL() specifically).
 */
object MnnDiffusionBridge {
    init {
        System.loadLibrary("pockettavern_diffusion")
    }

    /** Returns an opaque native handle (0 on failure), or 0 if createDiffusion() returned null. */
    external fun nativeCreate(modelPath: String, modelType: Int, memoryMode: Int): Long

    external fun nativeLoad(handle: Long): Boolean

    /**
     * Blocking — runs the full text-encode/UNet/VAE-decode pipeline on the calling thread.
     * Confirmed on real hardware: several minutes for a full-quality SDXL image. Must be called
     * from a background dispatcher (see [MnnDiffusionEngine.generate]), never the main thread.
     * [progressCallback] is invoked synchronously, on that same calling thread, once per
     * denoising step.
     */
    external fun nativeGenerateXL(
        handle: Long,
        prompt: String,
        negativePrompt: String,
        outputPath: String,
        width: Int,
        height: Int,
        steps: Int,
        seed: Int,
        cfgScale: Float,
        progressCallback: (Int) -> Unit,
    ): Boolean

    /** Frees the native StableDiffusionXL instance. handle is invalid after this call. */
    external fun nativeDestroy(handle: Long)
}
