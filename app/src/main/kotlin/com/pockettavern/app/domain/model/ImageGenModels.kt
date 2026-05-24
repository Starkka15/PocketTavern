package com.pockettavern.app.domain.model

import kotlinx.serialization.Serializable

enum class ImageGenBackendType {
    SD_WEBUI,
    COMFYUI,
    DALLE,
    STABILITY,
    POLLINATIONS,
    HUGGINGFACE,
    NANO_GPT;

    val displayName: String
        get() = when (this) {
            SD_WEBUI -> "SD WebUI / Forge"
            COMFYUI -> "ComfyUI"
            DALLE -> "DALL-E (OpenAI)"
            STABILITY -> "Stability AI"
            POLLINATIONS -> "Pollinations"
            HUGGINGFACE -> "HuggingFace"
            NANO_GPT -> "nano-gpt"
        }
}

data class ImageGenCapabilities(
    val supportsSamplers: Boolean = false,
    val supportsSchedulers: Boolean = false,
    val supportsModels: Boolean = false,
    val supportsSteps: Boolean = false,
    val supportsCfgScale: Boolean = false,
    val supportsSeed: Boolean = false,
    val supportsNegativePrompt: Boolean = false,
    val supportsImg2Img: Boolean = false,
    val supportsClipSkip: Boolean = false,
    val supportsVae: Boolean = false,
    val supportsResolutionPresets: Boolean = true,
    val supportsProgress: Boolean = false,
    val requiresApiKey: Boolean = false,
    val requiresUrl: Boolean = false
)

@Serializable
data class ImageGenConfig(
    val activeBackend: String = "SD_WEBUI",
    val sdWebuiUrl: String = "",
    val comfyuiUrl: String = "",
    val dalleApiKey: String = "",
    val dalleModel: String = "dall-e-3",
    val stabilityApiKey: String = "",
    val pollinationsApiKey: String = "",
    val pollinationsModel: String = "flux",
    val huggingfaceApiKey: String = "",
    val huggingfaceModel: String = "stabilityai/stable-diffusion-xl-base-1.0",
    val nanoGptApiKey: String = "",
    val nanoGptModel: String = "chroma",
    val sdModel: String = "",
    val sampler: String = "Euler",
    val scheduler: String = "",
    val steps: Int = 20,
    val cfgScale: Float = 7f,
    val seed: Int = -1,
    val negativePrompt: String = "blurry, low quality, distorted, deformed, bad anatomy",
    val clipSkip: Int = 1,
    val width: Int = 512,
    val height: Int = 768
) {
    val activeBackendType: ImageGenBackendType
        get() = try {
            ImageGenBackendType.valueOf(activeBackend)
        } catch (_: Exception) {
            ImageGenBackendType.SD_WEBUI
        }
}
