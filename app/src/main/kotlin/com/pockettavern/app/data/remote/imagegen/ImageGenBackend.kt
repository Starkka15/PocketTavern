package com.pockettavern.app.data.remote.imagegen

import com.pockettavern.app.domain.model.ForgeGenerationParams
import com.pockettavern.app.domain.model.GenerationState
import com.pockettavern.app.domain.model.ImageGenBackendType
import com.pockettavern.app.domain.model.ImageGenCapabilities
import com.pockettavern.app.domain.model.Result
import kotlinx.coroutines.flow.Flow

interface ImageGenBackend {
    val type: ImageGenBackendType
    val capabilities: ImageGenCapabilities

    suspend fun testConnection(): Result<Boolean>
    suspend fun getSamplers(): Result<List<String>>
    suspend fun getSchedulers(): Result<List<String>>
    suspend fun getModels(): Result<List<String>>
    suspend fun getStyles(): Result<List<String>> = Result.Success(emptyList())
    fun generate(params: ForgeGenerationParams): Flow<GenerationState>
    suspend fun interrupt(): Result<Unit>
}
