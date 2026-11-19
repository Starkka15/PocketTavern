package com.pockettavern.app.data.repository

import com.pockettavern.app.data.local.SettingsDataStore
import com.pockettavern.app.data.local.CharacterImageWorkflowStorage
import com.pockettavern.app.data.remote.imagegen.ImageGenBackend
import com.pockettavern.app.data.remote.imagegen.CustomImageTemplate
import com.pockettavern.app.domain.model.*
import com.pockettavern.app.util.DebugLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImageGenRepository @Inject constructor(
    private val backends: Map<ImageGenBackendType, @JvmSuppressWildcards ImageGenBackend>,
    private val settingsDataStore: SettingsDataStore,
    private val characterImageWorkflowStorage: CharacterImageWorkflowStorage
) {
    suspend fun getActiveBackend(): ImageGenBackend? {
        val config = settingsDataStore.getImageGenConfig()
        return backends[config.activeBackendType]
    }

    fun getBackend(type: ImageGenBackendType): ImageGenBackend? = backends[type]

    fun getAvailableBackends(): List<ImageGenBackendType> = backends.keys.toList()

    suspend fun getCapabilities(): ImageGenCapabilities {
        return getActiveBackend()?.capabilities ?: ImageGenCapabilities()
    }

    suspend fun testConnection(): Result<Boolean> {
        val backend = getActiveBackend()
            ?: return Result.Error(Exception("No backend configured"))
        return backend.testConnection()
    }

    suspend fun getSamplers(): Result<List<String>> {
        val backend = getActiveBackend()
            ?: return Result.Error(Exception("No backend configured"))
        return backend.getSamplers()
    }

    suspend fun getSchedulers(): Result<List<String>> {
        val backend = getActiveBackend()
            ?: return Result.Error(Exception("No backend configured"))
        return backend.getSchedulers()
    }

    suspend fun getModels(): Result<List<String>> {
        val backend = getActiveBackend()
            ?: return Result.Error(Exception("No backend configured"))
        return backend.getModels()
    }

    fun generateImageWithProgress(params: ForgeGenerationParams): Flow<GenerationState> = flow {
        val config = settingsDataStore.getImageGenConfig()
        val backend = backends[config.activeBackendType]
        if (backend == null) {
            DebugLogger.log("ImageGen: no backend found for ${config.activeBackend}")
            emit(GenerationState.Error("No image generation backend configured"))
            return@flow
        }

        val characterSettings = try {
            params.characterFileName?.takeIf { it.isNotBlank() }?.let { fileName ->
                withContext(Dispatchers.IO) { characterImageWorkflowStorage.get(fileName) }
            }
        } catch (e: Exception) {
            emit(GenerationState.Error("Could not load character image workflow settings: ${e.message}"))
            return@flow
        }
        val characterMode = characterSettings?.modeFor(backend.type) ?: "GLOBAL"
        val effectiveParams = try {
            CharacterImageWorkflowResolver.resolve(config, params, characterSettings)
        } catch (e: IllegalArgumentException) {
            emit(GenerationState.Error(e.message ?: "Invalid character image workflow mode"))
            return@flow
        }

        val mode = effectiveParams.workflowModeOverride ?: config.workflowModeFor(backend.type)
        val source = when {
            characterMode != "GLOBAL" -> "CHARACTER"
            effectiveParams.workflowModeOverride != null ||
                !effectiveParams.customWorkflowOverride.isNullOrBlank() -> "REQUEST_OVERRIDE"
            else -> "GLOBAL"
        }
        val templateName = (if (mode != "CUSTOM") "built-in"
            else if (characterMode == "CUSTOM" &&
                !characterSettings?.templateFor(backend.type)?.template.isNullOrBlank())
                characterSettings?.templateFor(backend.type)?.name.orEmpty()
            else if (!effectiveParams.customWorkflowOverride.isNullOrBlank()) "request override"
            else config.customWorkflowFor(backend.type)?.name.orEmpty().ifBlank { "unnamed" })
            .replace(Regex("[\\r\\n\\t]"), "_").take(80)

        DebugLogger.log("ImageGen: backend=${backend.type} mode=$mode source=$source template=$templateName")

        // Validate before contacting the provider; backend checks also protect direct calls.
        try {
            if (backend.capabilities.supportsCustomWorkflow) {
                CustomImageTemplate.checkedSelected(config, effectiveParams, backend.type)
            } else if (mode == "CUSTOM") {
                throw IllegalArgumentException("${backend.type.displayName} does not support custom templates.")
            } else if (mode != "STANDARD") {
                throw IllegalArgumentException("Unknown image workflow mode: $mode")
            }
        } catch (e: IllegalArgumentException) {
            DebugLogger.log("ImageGen: configuration error for ${backend.type}: ${e.message}")
            emit(GenerationState.Error(e.message ?: "Invalid image workflow configuration"))
            return@flow
        }

        DebugLogger.log("ImageGen: dispatching to ${backend.type}")
        backend.generate(effectiveParams).collect { state ->
            when (state) {
                is GenerationState.Complete -> DebugLogger.log("ImageGen: SUCCESS backend=${backend.type} mode=$mode")
                is GenerationState.Error -> DebugLogger.log("ImageGen: FAILED backend=${backend.type} mode=$mode (details shown in UI)")
                else -> Unit
            }
            emit(state)
        }
    }

    suspend fun interrupt(): Result<Unit> {
        val backend = getActiveBackend()
            ?: return Result.Error(Exception("No backend configured"))
        return backend.interrupt()
    }

    suspend fun setModel(model: String): Result<Unit> {
        val backend = getActiveBackend()
            ?: return Result.Error(Exception("No backend configured"))
        return backend.setModel(model)
    }
}
