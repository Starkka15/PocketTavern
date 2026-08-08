package com.pockettavern.app.data.remote.dto.forge

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Txt2ImgRequest(
    val prompt: String,
    // e.g. {"CLIP_stop_at_last_layers": 2}; omitted when null
    @SerialName("override_settings")
    val overrideSettings: Map<String, kotlinx.serialization.json.JsonElement>? = null,
    @SerialName("override_settings_restore_afterwards")
    val overrideSettingsRestore: Boolean = true,
    @SerialName("negative_prompt")
    val negativePrompt: String = "",
    val steps: Int = 20,
    @SerialName("cfg_scale")
    val cfgScale: Float = 7f,
    val width: Int = 512,
    val height: Int = 768,
    @SerialName("sampler_name")
    val samplerName: String = "Euler",
    val seed: Int = -1,
    @SerialName("batch_size")
    val batchSize: Int = 1,
    @SerialName("n_iter")
    val nIter: Int = 1,
    @SerialName("send_images")
    val sendImages: Boolean = true,
    @SerialName("save_images")
    val saveImages: Boolean = false
)

@Serializable
data class Txt2ImgResponse(
    val images: List<String>? = null,  // Base64 encoded
    val info: String? = null,
    val parameters: Txt2ImgRequest? = null
)

@Serializable
data class Img2ImgRequest(
    val prompt: String,
    @SerialName("override_settings")
    val overrideSettings: Map<String, kotlinx.serialization.json.JsonElement>? = null,
    @SerialName("override_settings_restore_afterwards")
    val overrideSettingsRestore: Boolean = true,
    @SerialName("negative_prompt")
    val negativePrompt: String = "",
    @SerialName("init_images")
    val initImages: List<String>,  // Base64 encoded source images
    val steps: Int = 20,
    @SerialName("cfg_scale")
    val cfgScale: Float = 7f,
    val width: Int = 512,
    val height: Int = 768,
    @SerialName("sampler_name")
    val samplerName: String = "Euler",
    @SerialName("denoising_strength")
    val denoisingStrength: Float = 0.5f,
    val seed: Int = -1,
    @SerialName("batch_size")
    val batchSize: Int = 1,
    @SerialName("n_iter")
    val nIter: Int = 1,
    @SerialName("send_images")
    val sendImages: Boolean = true,
    @SerialName("save_images")
    val saveImages: Boolean = false
)

@Serializable
data class SamplerDto(
    val name: String,
    val aliases: List<String>? = null,
    val options: Map<String, String>? = null
)

@Serializable
data class ModelDto(
    val title: String,
    @SerialName("model_name")
    val modelName: String,
    val hash: String? = null,
    @SerialName("sha256")
    val sha256: String? = null,
    val filename: String? = null
)

@Serializable
data class OptionsDto(
    @SerialName("sd_model_checkpoint")
    val sdModelCheckpoint: String? = null,
    @SerialName("sd_vae")
    val sdVae: String? = null,
    @SerialName("CLIP_stop_at_last_layers")
    val clipStopAtLastLayers: Double? = null
)

@Serializable
data class SetOptionsRequest(
    @SerialName("sd_model_checkpoint")
    val sdModelCheckpoint: String? = null
)

@Serializable
data class ProgressResponse(
    val progress: Float = 0f,
    @SerialName("eta_relative")
    val etaRelative: Float = 0f,
    val state: ProgressState? = null,
    @SerialName("current_image")
    val currentImage: String? = null,
    val textinfo: String? = null
)

@Serializable
data class ProgressState(
    val skipped: Boolean = false,
    val interrupted: Boolean = false,
    @SerialName("job_count")
    val jobCount: Int = 0,
    @SerialName("job_no")
    val jobNo: Int = 0,
    @SerialName("sampling_step")
    val samplingStep: Int = 0,
    @SerialName("sampling_steps")
    val samplingSteps: Int = 0
)
