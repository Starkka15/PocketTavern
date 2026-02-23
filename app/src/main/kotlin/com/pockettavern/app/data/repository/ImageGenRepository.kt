package com.pockettavern.app.data.repository

import com.pockettavern.app.data.local.SettingsDataStore
import com.pockettavern.app.data.remote.imagegen.ImageGenBackend
import com.pockettavern.app.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImageGenRepository @Inject constructor(
    private val backends: Map<ImageGenBackendType, @JvmSuppressWildcards ImageGenBackend>,
    private val settingsDataStore: SettingsDataStore
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
        val backend = getActiveBackend()
        if (backend == null) {
            emit(GenerationState.Error("No image generation backend configured"))
            return@flow
        }
        backend.generate(params).collect { emit(it) }
    }

    suspend fun interrupt(): Result<Unit> {
        val backend = getActiveBackend()
            ?: return Result.Error(Exception("No backend configured"))
        return backend.interrupt()
    }
}
