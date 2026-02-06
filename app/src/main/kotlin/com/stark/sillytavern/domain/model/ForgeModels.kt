package com.stark.sillytavern.domain.model

data class ForgeGenerationParams(
    val prompt: String,
    val negativePrompt: String = "blurry, low quality, distorted, deformed, bad anatomy",
    val width: Int = 512,
    val height: Int = 768,
    val steps: Int = 20,
    val cfgScale: Float = 7f,
    val sampler: String = "Euler",
    val seed: Int = -1
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
