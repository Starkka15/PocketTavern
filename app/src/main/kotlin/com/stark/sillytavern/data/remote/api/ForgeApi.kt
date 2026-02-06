package com.stark.sillytavern.data.remote.api

import com.stark.sillytavern.data.remote.dto.forge.*
import retrofit2.Response
import retrofit2.http.*

interface ForgeApi {

    @GET("sdapi/v1/samplers")
    suspend fun getSamplers(): List<SamplerDto>

    @GET("sdapi/v1/sd-models")
    suspend fun getModels(): List<ModelDto>

    @GET("sdapi/v1/options")
    suspend fun getOptions(): OptionsDto

    @POST("sdapi/v1/options")
    suspend fun setOptions(@Body options: SetOptionsRequest): Response<Unit>

    @POST("sdapi/v1/txt2img")
    suspend fun generateImage(@Body request: Txt2ImgRequest): Txt2ImgResponse

    @GET("sdapi/v1/progress")
    suspend fun getProgress(): ProgressResponse

    @POST("sdapi/v1/interrupt")
    suspend fun interrupt(): Response<Unit>
}
