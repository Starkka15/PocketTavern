package com.pockettavern.app.domain.model

import kotlinx.serialization.Serializable

enum class ImageGenBackendType {
    SD_WEBUI,
    COMFYUI,
    DALLE,
    STABILITY,
    POLLINATIONS,
    HUGGINGFACE,
    NANO_GPT,
    LOCAL_SD_MNN;

    val displayName: String
        get() = when (this) {
            SD_WEBUI -> "SD WebUI / Forge"
            COMFYUI -> "ComfyUI"
            DALLE -> "DALL-E (OpenAI)"
            STABILITY -> "Stability AI"
            POLLINATIONS -> "Pollinations"
            HUGGINGFACE -> "HuggingFace"
            NANO_GPT -> "nano-gpt"
            LOCAL_SD_MNN -> "On-Device (SDXL)"
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
    val requiresUrl: Boolean = false,
    // Whether interrupt() actually stops in-flight generation, vs. only detaching the listener
    // while the backend keeps generating regardless (true by default: most backends' interrupt()
    // cancels a real in-flight HTTP call). False for MnnDiffusionBackend specifically -- MNN's
    // diffusion engine has no cancellation primitive, so a started generation always runs to
    // completion natively no matter what the UI does.
    val supportsCancel: Boolean = true
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
    // Directory containing the MNN-converted SDXL model set (text_encoder.mnn,
    // text_encoder_2.mnn, unet.mnn, vae_decoder.mnn + weights, tokenizer/, tokenizer_2/) --
    // see mnn_sdxl_android_pipeline memory for how this gets produced. Populated via
    // SdxlModelManager's download-by-URL flow (ImageGenSettingsScreen's SdxlModelSection).
    val localSdxlModelPath: String = "",
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

    /**
     * Whether the currently active backend has its required connection info filled in --
     * general replacement for the old `forgeUrl.isNotBlank()` check that gated AI avatar
     * generation regardless of which backend was actually selected (a pre-existing bug: any
     * non-SD_WEBUI backend, not just the on-device one, was affected).
     */
    val isActiveBackendConfigured: Boolean
        get() = when (activeBackendType) {
            ImageGenBackendType.SD_WEBUI -> sdWebuiUrl.isNotBlank()
            ImageGenBackendType.COMFYUI -> comfyuiUrl.isNotBlank()
            ImageGenBackendType.DALLE -> dalleApiKey.isNotBlank()
            ImageGenBackendType.STABILITY -> stabilityApiKey.isNotBlank()
            ImageGenBackendType.POLLINATIONS -> true // free tier, no required field
            ImageGenBackendType.HUGGINGFACE -> huggingfaceApiKey.isNotBlank()
            ImageGenBackendType.NANO_GPT -> nanoGptApiKey.isNotBlank()
            ImageGenBackendType.LOCAL_SD_MNN -> localSdxlModelPath.isNotBlank()
        }
}
