package com.pockettavern.app.data.remote.api

import com.pockettavern.app.data.remote.dto.charavault.CharaVaultSearchResponse
import com.pockettavern.app.data.remote.dto.charavault.CharaVaultDetailResponse
import com.pockettavern.app.data.remote.dto.charavault.CharaVaultStatsResponse
import com.pockettavern.app.data.remote.dto.charavault.CharaVaultTagsResponse
import com.pockettavern.app.data.remote.dto.charavault.CharaVaultUploadResponse
import com.pockettavern.app.data.remote.dto.charavault.CharaVaultLorebookSearchResponse
import com.pockettavern.app.data.remote.dto.charavault.CharaVaultLorebookDetailResponse
import com.pockettavern.app.data.remote.dto.charavault.CharaVaultLorebookStatsResponse
import com.pockettavern.app.data.remote.dto.charavault.CharaVaultLorebookTopicsResponse
import com.pockettavern.app.data.remote.dto.charavault.CharaVaultLoginResponse
import com.pockettavern.app.data.remote.dto.charavault.CharaVaultUserResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * API interface for CharaVault - local character card index server.
 * Connects to the card-index-server running on user's network.
 */
interface CharaVaultApi {

    /**
     * Search character cards with filters.
     *
     * @param query Search query (matches name, description, creator)
     * @param tags Comma-separated tags to filter by
     * @param nsfw Filter by NSFW status (null = all, true = NSFW only, false = SFW only)
     * @param creator Filter by creator name
     * @param folder Filter by source folder (e.g., "chub", "booru")
     * @param limit Results per page (max 200)
     * @param offset Pagination offset
     */
    @GET("api/cards")
    suspend fun search(
        @Query("q") query: String? = null,
        @Query("tags") tags: String? = null,
        @Query("nsfw") nsfw: Boolean? = null,
        @Query("creator") creator: String? = null,
        @Query("folder") folder: String? = null,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): Response<CharaVaultSearchResponse>

    /**
     * Get full metadata for a specific card.
     */
    @GET("api/cards/{folder}/{filename}")
    suspend fun getCardDetails(
        @Path("folder") folder: String,
        @Path("filename") filename: String
    ): Response<CharaVaultDetailResponse>

    /**
     * Download the card PNG image.
     */
    @GET("cards/{folder}/{filename}")
    suspend fun downloadCard(
        @Path("folder") folder: String,
        @Path("filename") filename: String
    ): Response<ResponseBody>

    /**
     * Get index statistics.
     */
    @GET("api/stats")
    suspend fun getStats(): Response<CharaVaultStatsResponse>

    /**
     * Get all available tags with counts.
     */
    @GET("api/tags")
    suspend fun getTags(): Response<CharaVaultTagsResponse>

    /**
     * Upload a character card to the server.
     *
     * @param file The PNG character card file
     * @param folder Subfolder to save to (default: "Uploads")
     */
    @Multipart
    @POST("api/cards/upload")
    suspend fun uploadCard(
        @Part file: MultipartBody.Part,
        @Part("folder") folder: RequestBody
    ): Response<CharaVaultUploadResponse>

    // ===== CHARAVAULT.NET AUTH ENDPOINTS =====

    /**
     * Login to CharaVault.net with email and password.
     * Returns token on success, or requires_2fa if 2FA is enabled.
     */
    @POST("api/auth/login")
    suspend fun login(@Body body: Map<String, String>): Response<CharaVaultLoginResponse>

    /**
     * Verify 2FA code to complete login.
     */
    @POST("api/auth/verify-2fa")
    suspend fun verify2fa(@Body body: Map<String, String>): Response<CharaVaultLoginResponse>

    /**
     * Get current authenticated user info.
     */
    @GET("api/auth/me")
    suspend fun getMe(): Response<CharaVaultUserResponse>

    /**
     * Verify age (18+) to unlock NSFW content.
     */
    @POST("api/auth/verify-age")
    suspend fun verifyAge(): Response<Map<String, @JvmSuppressWildcards Any>>

    // ===== LOREBOOK ENDPOINTS =====

    /**
     * Search lorebooks with filters.
     *
     * @param query Search query (matches name, description, keywords)
     * @param topics Comma-separated topics to filter by
     * @param creator Filter by creator name
     * @param nsfw Filter by NSFW status
     * @param limit Results per page (max 200)
     * @param offset Pagination offset
     */
    @GET("api/lorebooks")
    suspend fun searchLorebooks(
        @Query("q") query: String? = null,
        @Query("topics") topics: String? = null,
        @Query("creator") creator: String? = null,
        @Query("nsfw") nsfw: Boolean? = null,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): Response<CharaVaultLorebookSearchResponse>

    /**
     * Get full details for a specific lorebook.
     */
    @GET("api/lorebooks/{id}")
    suspend fun getLorebookDetails(
        @Path("id") id: Int
    ): Response<CharaVaultLorebookDetailResponse>

    /**
     * Download the lorebook JSON file.
     */
    @GET("lorebooks/{folder}/{filename}")
    suspend fun downloadLorebook(
        @Path("folder") folder: String,
        @Path("filename") filename: String
    ): Response<ResponseBody>

    /**
     * Get lorebook statistics.
     */
    @GET("api/lorebooks/stats")
    suspend fun getLorebookStats(): Response<CharaVaultLorebookStatsResponse>

    /**
     * Get all available lorebook topics with counts.
     */
    @GET("api/lorebooks/topics")
    suspend fun getLorebookTopics(): Response<CharaVaultLorebookTopicsResponse>
}
