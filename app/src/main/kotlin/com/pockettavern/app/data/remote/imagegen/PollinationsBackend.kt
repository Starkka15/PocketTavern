package com.pockettavern.app.data.remote.imagegen

import android.util.Base64
import com.pockettavern.app.domain.model.ForgeGenerationParams
import com.pockettavern.app.domain.model.GenerationState
import com.pockettavern.app.domain.model.ImageGenBackendType
import com.pockettavern.app.domain.model.ImageGenCapabilities
import com.pockettavern.app.domain.model.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder

class PollinationsBackend(
    private val httpClient: OkHttpClient
) : ImageGenBackend {

    override val type = ImageGenBackendType.POLLINATIONS

    override val capabilities = ImageGenCapabilities(
        supportsResolutionPresets = true,
        supportsNegativePrompt = true,
        supportsSeed = true
    )

    override suspend fun testConnection(): Result<Boolean> {
        return try {
            val url = "https://image.pollinations.ai/prompt/test?width=64&height=64&nologo=true"
            val request = Request.Builder().url(url).head().build()
            withContext(Dispatchers.IO) {
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) Result.Success(true)
                    else Result.Error(Exception("HTTP ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.Error(Exception("Connection failed: ${e.message}", e))
        }
    }

    override suspend fun getSamplers(): Result<List<String>> = Result.Success(emptyList())
    override suspend fun getSchedulers(): Result<List<String>> = Result.Success(emptyList())
    override suspend fun getModels(): Result<List<String>> {
        return Result.Success(listOf("flux", "flux-realism", "flux-anime", "flux-3d", "turbo"))
    }

    override fun generate(params: ForgeGenerationParams): Flow<GenerationState> = flow {
        emit(GenerationState.Starting)
        try {
            val encodedPrompt = withContext(Dispatchers.IO) {
                URLEncoder.encode(params.prompt, "UTF-8")
            }
            val urlBuilder = StringBuilder("https://image.pollinations.ai/prompt/$encodedPrompt")
            urlBuilder.append("?width=${params.width}")
            urlBuilder.append("&height=${params.height}")
            urlBuilder.append("&nologo=true")
            if (params.seed != -1) {
                urlBuilder.append("&seed=${params.seed}")
            }
            if (params.negativePrompt.isNotBlank()) {
                val encodedNeg = withContext(Dispatchers.IO) {
                    URLEncoder.encode(params.negativePrompt, "UTF-8")
                }
                urlBuilder.append("&negative=$encodedNeg")
            }

            val request = Request.Builder().url(urlBuilder.toString()).get().build()
            val imageBytes = withContext(Dispatchers.IO) {
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
                    response.body?.bytes() ?: throw Exception("Empty response")
                }
            }
            val base64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
            emit(GenerationState.Complete(imageBase64 = base64))
        } catch (e: Exception) {
            emit(GenerationState.Error(e.message ?: "Generation failed"))
        }
    }

    override suspend fun interrupt(): Result<Unit> = Result.Success(Unit)
}
