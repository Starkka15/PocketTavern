package com.pockettavern.app.data.remote.imagegen

import com.pockettavern.app.data.remote.api.ForgeApi
import com.pockettavern.app.data.remote.dto.forge.Img2ImgRequest
import com.pockettavern.app.data.remote.dto.forge.Txt2ImgRequest
import com.pockettavern.app.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class SdWebuiBackend(
    private val apiProvider: () -> ForgeApi
) : ImageGenBackend {

    private val api: ForgeApi get() = apiProvider()

    override val type = ImageGenBackendType.SD_WEBUI

    override val capabilities = ImageGenCapabilities(
        supportsSamplers = true,
        supportsSchedulers = false,
        supportsModels = true,
        supportsSteps = true,
        supportsCfgScale = true,
        supportsSeed = true,
        supportsNegativePrompt = true,
        supportsImg2Img = true,
        supportsClipSkip = true,
        supportsVae = true,
        supportsResolutionPresets = true,
        supportsProgress = true,
        requiresUrl = true
    )

    override suspend fun testConnection(): Result<Boolean> {
        return try {
            api.getOptions()
            Result.Success(true)
        } catch (e: Exception) {
            Result.Error(Exception("Connection failed: ${e.message}", e))
        }
    }

    override suspend fun getSamplers(): Result<List<String>> {
        return try {
            val samplers = api.getSamplers()
            Result.Success(samplers.map { it.name })
        } catch (e: Exception) {
            Result.Error(Exception("Failed to get samplers: ${e.message}", e))
        }
    }

    override suspend fun getSchedulers(): Result<List<String>> {
        return Result.Success(emptyList())
    }

    override suspend fun getModels(): Result<List<String>> {
        return try {
            val models = api.getModels()
            Result.Success(models.map { it.title })
        } catch (e: Exception) {
            Result.Error(Exception("Failed to get models: ${e.message}", e))
        }
    }

    override fun generate(params: ForgeGenerationParams): Flow<GenerationState> = flow {
        emit(GenerationState.Starting)

        try {
            val response = if (params.sourceImageBase64 != null) {
                api.img2img(
                    Img2ImgRequest(
                        prompt = params.prompt,
                        negativePrompt = params.negativePrompt,
                        initImages = listOf(params.sourceImageBase64),
                        steps = params.steps,
                        cfgScale = params.cfgScale,
                        width = params.width,
                        height = params.height,
                        samplerName = params.sampler,
                        denoisingStrength = params.denoisingStrength,
                        seed = params.seed
                    )
                )
            } else {
                api.generateImage(
                    Txt2ImgRequest(
                        prompt = params.prompt,
                        negativePrompt = params.negativePrompt,
                        steps = params.steps,
                        cfgScale = params.cfgScale,
                        width = params.width,
                        height = params.height,
                        samplerName = params.sampler,
                        seed = params.seed
                    )
                )
            }

            val image = response.images?.firstOrNull()
            if (image != null) {
                emit(GenerationState.Complete(imageBase64 = image))
            } else {
                emit(GenerationState.Error("No image generated"))
            }
        } catch (e: Exception) {
            emit(GenerationState.Error(e.message ?: "Generation failed"))
        }
    }

    override suspend fun interrupt(): Result<Unit> {
        return try {
            api.interrupt()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(Exception("Failed to interrupt: ${e.message}", e))
        }
    }
}
