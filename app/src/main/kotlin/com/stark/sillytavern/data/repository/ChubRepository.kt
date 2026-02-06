package com.stark.sillytavern.data.repository

import com.stark.sillytavern.data.remote.api.ChubApi
import com.stark.sillytavern.data.remote.api.ChubAvatarApi
import com.stark.sillytavern.data.remote.api.SillyTavernApi
import com.stark.sillytavern.data.remote.dto.chub.ChubDefinition
import com.stark.sillytavern.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlin.math.ceil

@Singleton
class ChubRepository @Inject constructor(
    private val chubApi: ChubApi,
    private val chubAvatarApi: ChubAvatarApi,
    private val sillyTavernApiProvider: () -> SillyTavernApi,
    private val settingsRepository: SettingsRepository,
    @Named("Chub") private val okHttpClient: OkHttpClient
) {
    private val sillyTavernApi: SillyTavernApi
        get() = sillyTavernApiProvider()

    private val json = Json { ignoreUnknownKeys = true }

    // Helper to extract string labels from flexible JSON format
    private fun extractLabels(element: JsonElement?): List<String> {
        if (element == null) return emptyList()
        return when (element) {
            is JsonArray -> element.mapNotNull { item ->
                when (item) {
                    is JsonPrimitive -> item.content
                    is JsonObject -> item["title"]?.jsonPrimitive?.content
                        ?: item["name"]?.jsonPrimitive?.content
                    else -> null
                }
            }
            else -> emptyList()
        }
    }

    suspend fun search(
        query: String = "",
        page: Int = 1,
        perPage: Int = 48,
        sort: ChubSortOption = ChubSortOption.DOWNLOADS,
        nsfw: Boolean = false,
        minStars: Int = 0
    ): Result<ChubSearchResult> {
        return try {
            val response = chubApi.search(
                query = query,
                first = perPage,
                page = page,
                sort = sort.value,
                nsfw = nsfw,
                nsfl = false
            )

            val nodes = response.nodes ?: response.data?.nodes ?: emptyList()
            val total = response.total ?: response.data?.count ?: response.count ?: nodes.size

            val characters = nodes
                .filter { node ->
                    minStars == 0 || (node.starCount ?: 0) >= minStars
                }
                .mapNotNull { node ->
                    val fullPath = node.fullPath ?: return@mapNotNull null
                    val name = node.name ?: fullPath.split("/").lastOrNull() ?: return@mapNotNull null

                    // Parse firstMessage from definition JSON if available
                    val firstMessage = node.definition?.let { defJson ->
                        try {
                            json.decodeFromString<ChubDefinition>(defJson).firstMes
                        } catch (e: Exception) {
                            null
                        }
                    }

                    ChubCharacter(
                        name = name,
                        fullPath = fullPath,
                        tagline = node.tagline ?: "",
                        description = node.description ?: "",
                        avatarUrl = node.avatarUrl ?: node.maxResUrl,
                        downloadCount = node.downloadCount ?: 0,
                        starCount = node.starCount ?: 0,
                        ratingCount = node.ratingCount ?: 0,
                        topics = node.topics ?: extractLabels(node.labels),
                        firstMessage = firstMessage
                    )
                }

            val totalPages = ceil(total.toDouble() / perPage).toInt().coerceAtLeast(1)

            Result.Success(
                ChubSearchResult(
                    characters = characters,
                    totalCount = total,
                    currentPage = page,
                    totalPages = totalPages
                )
            )
        } catch (e: Exception) {
            Result.Error(Exception("Search failed: ${e.message}", e))
        }
    }

    suspend fun getCharacterDetails(fullPath: String): Result<ChubCharacter> {
        return try {
            val response = chubApi.getCharacter(fullPath)
            val node = response.node ?: response.character ?: response.data
                ?: throw Exception("Character not found")

            val name = node.name ?: fullPath.split("/").lastOrNull()
                ?: throw Exception("Character has no name")

            val firstMessage = node.definition?.let { defJson ->
                try {
                    json.decodeFromString<ChubDefinition>(defJson).firstMes
                } catch (e: Exception) {
                    null
                }
            }

            Result.Success(
                ChubCharacter(
                    name = name,
                    fullPath = node.fullPath ?: fullPath,
                    tagline = node.tagline ?: "",
                    description = node.description ?: "",
                    avatarUrl = node.avatarUrl ?: node.maxResUrl,
                    downloadCount = node.downloadCount ?: 0,
                    starCount = node.starCount ?: 0,
                    ratingCount = node.ratingCount ?: 0,
                    topics = node.topics ?: extractLabels(node.labels),
                    firstMessage = firstMessage
                )
            )
        } catch (e: Exception) {
            Result.Error(Exception("Failed to get character details: ${e.message}", e))
        }
    }

    suspend fun importCharacter(fullPath: String): Result<Unit> {
        return try {
            val settings = settingsRepository.getSettings()

            if (settings.normalizedServerUrl.isBlank()) {
                throw Exception("SillyTavern server not configured")
            }

            // Step 1: Get character details to obtain the download URL
            val charResponse = chubApi.getCharacter(fullPath)
            val node = charResponse.node ?: charResponse.character ?: charResponse.data
                ?: throw Exception("Character not found")

            val downloadUrl = node.maxResUrl
                ?: throw Exception("No download URL available for this character")

            // Step 2: Download the character card PNG from the maxResUrl
            val pngBytes = withContext(Dispatchers.IO) {
                val request = Request.Builder()
                    .url(downloadUrl)
                    .build()
                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    throw Exception("Failed to download character: HTTP ${response.code}")
                }
                response.body?.bytes() ?: throw Exception("Empty response body")
            }

            if (pngBytes.isEmpty()) {
                throw Exception("Downloaded character card is empty")
            }

            // Step 3: Create multipart request for SillyTavern import
            val fileName = fullPath.replace("/", "_") + ".png"
            val requestBody = pngBytes.toRequestBody("image/png".toMediaType())
            // SillyTavern expects the file field to be named "avatar"
            val filePart = MultipartBody.Part.createFormData("avatar", fileName, requestBody)
            val fileTypePart = "png".toRequestBody("text/plain".toMediaType())

            // Step 4: Import directly to SillyTavern
            val response = sillyTavernApi.importCharacter(filePart, fileTypePart)

            if (!response.isSuccessful) {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                throw Exception("Import failed: ${response.code()} - $errorBody")
            }

            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(Exception("Import failed: ${e.message}", e))
        }
    }

    /**
     * Build direct avatar URL for Chub characters.
     * Since Android can access Chub directly, no proxy is needed.
     */
    fun buildAvatarUrl(fullPath: String): String {
        return "https://avatars.charhub.io/avatars/$fullPath/avatar.webp"
    }

    /**
     * For backward compatibility - just returns the original URL
     * since Android can access Chub directly without a proxy.
     */
    fun buildProxiedAvatarUrl(originalUrl: String?): String? {
        return originalUrl
    }
}
