package com.stark.sillytavern.data.remote.api

import com.stark.sillytavern.data.remote.dto.chub.*
import okhttp3.ResponseBody
import retrofit2.http.*

interface ChubApi {

    @GET("search")
    suspend fun search(
        @Query("search") query: String = "",
        @Query("first") first: Int = 24,
        @Query("page") page: Int = 1,
        @Query("sort") sort: String = "download_count",
        @Query("asc") asc: Boolean = false,
        @Query("nsfw") nsfw: Boolean = false,
        @Query("nsfl") nsfl: Boolean = false,
        @Query("include_forks") includeForks: Boolean = false,
        @Query("tags") tags: String? = null,
        @Query("exclude_tags") excludeTags: String? = null,
        @Query("min_tokens") minTokens: Int = 50
    ): ChubSearchResponse

    @GET("api/characters/{fullPath}")
    suspend fun getCharacter(
        @Path("fullPath", encoded = true) fullPath: String
    ): ChubCharacterResponse
}

// Separate API for downloading character PNGs from avatars.charhub.io
interface ChubAvatarApi {
    @GET("avatars/{fullPath}/chara")
    @Streaming
    suspend fun downloadCharacterCard(
        @Path("fullPath", encoded = true) fullPath: String
    ): ResponseBody

    @GET("avatars/{fullPath}/avatar.webp")
    @Streaming
    suspend fun downloadAvatar(
        @Path("fullPath", encoded = true) fullPath: String
    ): ResponseBody
}
