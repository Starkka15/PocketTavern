package com.pockettavern.app.domain.model

data class ForgeGenerationParams(
    val prompt: String,
    val negativePrompt: String = "blurry, low quality, distorted, deformed, bad anatomy",
    val width: Int = 512,
    val height: Int = 768,
    val steps: Int = 20,
    val cfgScale: Float = 7f,
    val sampler: String = "Euler",
    val seed: Int = -1,
    /** Base64-encoded source image for img2img. Null = txt2img. */
    val sourceImageBase64: String? = null,
    /** How much to change the source image (0.0 = identical, 1.0 = ignore source). */
    val denoisingStrength: Float = 0.5f,
    /** CLIP skip (SD WebUI: CLIP_stop_at_last_layers). 0/1 = server default. */
    val clipSkip: Int = 0
)

data class ForgeProgress(
    val progress: Float,
    val etaSeconds: Float,
    val jobCount: Int,
    val currentImage: String? = null
)

sealed class GenerationState {
    data object Idle : GenerationState()
    data object Starting : GenerationState()
    data class InProgress(
        val progress: Float,
        val eta: Float,
        val previewImage: String? = null
    ) : GenerationState()
    data class Complete(
        val imageBase64: String
    ) : GenerationState()
    data class Error(val message: String) : GenerationState()
}
