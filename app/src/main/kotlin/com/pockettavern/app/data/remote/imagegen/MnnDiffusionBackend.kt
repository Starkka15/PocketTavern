package com.pockettavern.app.data.remote.imagegen

import com.pockettavern.app.data.local.SettingsDataStore
import com.pockettavern.app.data.local.inference.MnnDiffusionEngine
import com.pockettavern.app.domain.model.ForgeGenerationParams
import com.pockettavern.app.domain.model.GenerationState
import com.pockettavern.app.domain.model.ImageGenBackendType
import com.pockettavern.app.domain.model.ImageGenCapabilities
import com.pockettavern.app.domain.model.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File

/**
 * On-device SDXL generation via MNN (Apache-2.0), wired through [MnnDiffusionEngine]. First
 * backend to actually populate GenerationState.InProgress -- every remote backend leaves it
 * unused. No DI annotations, plain class constructed in NetworkModule -- matches every other
 * ImageGenBackend in this codebase.
 */
class MnnDiffusionBackend(
    private val engine: MnnDiffusionEngine,
    private val settingsDataStore: SettingsDataStore,
) : ImageGenBackend {

    override val type = ImageGenBackendType.LOCAL_SD_MNN

    override val capabilities = ImageGenCapabilities(
        supportsSteps = true,
        supportsCfgScale = true,
        supportsSeed = true,
        supportsNegativePrompt = true,
        // Not a real choice: the exported UNet ONNX graph is a *fixed* {2,4,128,128} shape
        // (dynamic_axes=None at export time, see onnx_export_xl.py), and
        // StableDiffusionXL::load() sets shapeMutable=false, so MNN can't adapt to any other
        // input shape. generate() below hardcodes 1024x1024 regardless of what's requested --
        // confirmed on real hardware that anything else (the default 512x768 from
        // ForgeGenerationParams) SIGSEGVs deep in StableDiffusionXL::unet() (a null VARP from
        // onForward() on a shape mismatch, not caught/handled on the C++ side).
        supportsResolutionPresets = false,
        supportsProgress = true,
        // Reuses the existing "Connection" settings-screen section (a single text field) for the
        // local model directory path -- not literally a URL, but the same one-field-of-config
        // shape, and avoids adding a whole new capability flag + UI gate for Phase 3. The field
        // label in ImageGenSettingsScreen.kt says "Model Path", not "URL".
        requiresUrl = true,
        requiresApiKey = false,
    )

    private suspend fun modelPath(): String = settingsDataStore.getImageGenConfig().localSdxlModelPath

    private val requiredFiles = listOf("text_encoder.mnn", "text_encoder_2.mnn", "unet.mnn", "vae_decoder.mnn")

    override suspend fun testConnection(): Result<Boolean> {
        val path = modelPath()
        if (path.isBlank()) return Result.Error(Exception("No local SDXL model path configured"))
        val dir = File(path)
        if (!dir.isDirectory) return Result.Error(Exception("Model directory doesn't exist: $path"))
        val missing = requiredFiles.filterNot { File(dir, it).exists() }
        return if (missing.isEmpty()) {
            Result.Success(true)
        } else {
            Result.Error(Exception("Missing model files in $path: $missing"))
        }
    }

    // MNN's StableDiffusionXL hardcodes EulerDiscreteScheduler (see stable_diffusion_xl.cpp) --
    // nothing to query natively, this is just what's actually used.
    override suspend fun getSamplers(): Result<List<String>> = Result.Success(listOf("Euler"))

    override suspend fun getSchedulers(): Result<List<String>> = Result.Success(emptyList())

    override suspend fun getModels(): Result<List<String>> {
        val path = modelPath()
        return Result.Success(if (path.isNotBlank()) listOf(File(path).name) else emptyList())
    }

    override fun generate(params: ForgeGenerationParams): Flow<GenerationState> = flow {
        emit(GenerationState.Starting)
        val path = modelPath()
        if (path.isBlank()) {
            emit(GenerationState.Error("No local SDXL model configured -- set a model path in Settings"))
            return@flow
        }

        engine.generate(
            modelPath = path,
            prompt = params.prompt,
            negativePrompt = params.negativePrompt,
            // Fixed, not params.width/height -- see capabilities' comment on why this backend
            // can't honor an arbitrary resolution.
            width = FIXED_RESOLUTION,
            height = FIXED_RESOLUTION,
            steps = params.steps,
            seed = params.seed,
            cfgScale = params.cfgScale,
        ).collect { progress ->
            when (progress) {
                is MnnDiffusionEngine.Progress.Started -> emit(GenerationState.Starting)
                is MnnDiffusionEngine.Progress.Step ->
                    emit(GenerationState.InProgress(progress = progress.percent / 100f, eta = 0f, previewImage = null))
                is MnnDiffusionEngine.Progress.Done ->
                    emit(GenerationState.Complete(imageBase64 = progress.imageBase64))
                is MnnDiffusionEngine.Progress.Error -> emit(GenerationState.Error(progress.message))
            }
        }
    }

    override suspend fun interrupt(): Result<Unit> {
        // No native cancellation exists in MNN's diffusion engine (checked its full public
        // interface -- nothing like stable-diffusion.cpp's sd_cancel_generation()). A started
        // generation always runs to completion natively; this can't stop it, only detach the
        // Flow's listener (see MnnDiffusionEngine.generate()'s doc). Success here reflects "the
        // app stopped listening," not "the compute stopped" -- deliberate, not a placeholder.
        return Result.Success(Unit)
    }

    private companion object {
        // Matches onnx_export_xl.py's export_unet: sample_size=128 (1024px / 8), the only shape
        // the exported UNet graph actually accepts.
        const val FIXED_RESOLUTION = 1024
    }
}
