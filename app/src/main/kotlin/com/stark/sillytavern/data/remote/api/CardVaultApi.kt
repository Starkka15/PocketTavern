package com.stark.sillytavern.data.remote.api

import com.stark.sillytavern.data.remote.dto.cardvault.CardVaultSearchResponse
import com.stark.sillytavern.data.remote.dto.cardvault.CardVaultDetailResponse
import com.stark.sillytavern.data.remote.dto.cardvault.CardVaultStatsResponse
import com.stark.sillytavern.data.remote.dto.cardvault.CardVaultTagsResponse
import com.stark.sillytavern.data.remote.dto.cardvault.CardVaultUploadResponse
import com.stark.sillytavern.data.remote.dto.cardvault.CardVaultLorebookSearchResponse
import com.stark.sillytavern.data.remote.dto.cardvault.CardVaultLorebookDetailResponse
import com.stark.sillytavern.data.remote.dto.cardvault.CardVaultLorebookStatsResponse
import com.stark.sillytavern.data.remote.dto.cardvault.CardVaultLorebookTopicsResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * API interface for CardVault - local character card index server.
 * Connects to the card-index-server running on user's network.
 */
interface CardVaultApi {

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
    ): Response<CardVaultSearchResponse>

    /**
     * Get full metadata for a specific card.
     */
    @GET("api/cards/{folder}/{filename}")
    suspend fun getCardDetails(
        @Path("folder") folder: String,
        @Path("filename") filename: String
    ): Response<CardVaultDetailResponse>

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
    suspend fun getStats(): Response<CardVaultStatsResponse>

    /**
     * Get all available tags with counts.
     */
    @GET("api/tags")
    suspend fun getTags(): Response<CardVaultTagsResponse>

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
    ): Response<CardVaultUploadResponse>

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
    ): Response<CardVaultLorebookSearchResponse>

    /**
     * Get full details for a specific lorebook.
     */
    @GET("api/lorebooks/{id}")
    suspend fun getLorebookDetails(
        @Path("id") id: Int
    ): Response<CardVaultLorebookDetailResponse>

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
    suspend fun getLorebookStats(): Response<CardVaultLorebookStatsResponse>

    /**
     * Get all available lorebook topics with counts.
     */
    @GET("api/lorebooks/topics")
    suspend fun getLorebookTopics(): Response<CardVaultLorebookTopicsResponse>
}
