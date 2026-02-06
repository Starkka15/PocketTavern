package com.stark.sillytavern.data.repository

import com.stark.sillytavern.data.remote.api.ForgeApi
import com.stark.sillytavern.data.remote.dto.forge.Txt2ImgRequest
import com.stark.sillytavern.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ForgeRepository @Inject constructor(
    private val apiProvider: () -> ForgeApi
) {
    private val api: ForgeApi
        get() = apiProvider()

    suspend fun getSamplers(): Result<List<String>> {
        return try {
            val samplers = api.getSamplers()
            Result.Success(samplers.map { it.name })
        } catch (e: Exception) {
            Result.Error(Exception("Failed to get samplers: ${e.message}", e))
        }
    }

    suspend fun getModels(): Result<List<String>> {
        return try {
            val models = api.getModels()
            Result.Success(models.map { it.title })
        } catch (e: Exception) {
            Result.Error(Exception("Failed to get models: ${e.message}", e))
        }
    }

    fun generateImageWithProgress(params: ForgeGenerationParams): Flow<GenerationState> = flow {
        emit(GenerationState.Starting)

        try {
            val request = Txt2ImgRequest(
                prompt = params.prompt,
                negativePrompt = params.negativePrompt,
                steps = params.steps,
                cfgScale = params.cfgScale,
                width = params.width,
                height = params.height,
                samplerName = params.sampler,
                seed = params.seed
            )

            // Start generation - this is a long-running request
            // We'll poll for progress while it runs
            var isGenerating = true
            var result: String? = null
            var error: Exception? = null

            // Simple approach: just call the API directly
            // The progress polling would require a more complex setup
            val response = api.generateImage(request)
            result = response.images?.firstOrNull()

            if (error != null) {
                emit(GenerationState.Error(error!!.message ?: "Generation failed"))
            } else if (result != null) {
                emit(GenerationState.Complete(imageBase64 = result))
            } else {
                emit(GenerationState.Error("No image generated"))
            }
        } catch (e: Exception) {
            emit(GenerationState.Error(e.message ?: "Generation failed"))
        }
    }

    suspend fun generateImage(params: ForgeGenerationParams): Result<String> {
        return try {
            val request = Txt2ImgRequest(
                prompt = params.prompt,
                negativePrompt = params.negativePrompt,
                steps = params.steps,
                cfgScale = params.cfgScale,
                width = params.width,
                height = params.height,
                samplerName = params.sampler,
                seed = params.seed
            )

            val response = api.generateImage(request)
            val image = response.images?.firstOrNull()
                ?: throw Exception("No image generated")

            Result.Success(image)
        } catch (e: Exception) {
            Result.Error(Exception("Generation failed: ${e.message}", e))
        }
    }

    suspend fun interrupt(): Result<Unit> {
        return try {
            api.interrupt()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(Exception("Failed to interrupt: ${e.message}", e))
        }
    }
}
