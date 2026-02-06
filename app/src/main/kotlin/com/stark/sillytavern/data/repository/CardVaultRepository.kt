package com.stark.sillytavern.data.repository

import android.util.Log
import com.stark.sillytavern.data.remote.api.CardVaultApi
import com.stark.sillytavern.data.remote.api.SillyTavernApi
import com.stark.sillytavern.domain.model.CardVaultCharacter
import com.stark.sillytavern.domain.model.CardVaultNsfwFilter
import com.stark.sillytavern.domain.model.CardVaultSearchResult
import com.stark.sillytavern.domain.model.CardVaultStats
import com.stark.sillytavern.domain.model.CardVaultLorebook
import com.stark.sillytavern.domain.model.CardVaultLorebookSearchResult
import com.stark.sillytavern.domain.model.CardVaultLorebookStats
import com.stark.sillytavern.domain.model.LorebookEntryItem
import com.stark.sillytavern.domain.model.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil

private const val TAG = "CardVaultRepository"

@Singleton
class CardVaultRepository @Inject constructor(
    private val cardVaultApiProvider: javax.inject.Provider<CardVaultApi>,
    private val sillyTavernApiProvider: javax.inject.Provider<SillyTavernApi>
) {

    private val cardVaultApi: CardVaultApi
        get() = cardVaultApiProvider.get()

    private val sillyTavernApi: SillyTavernApi
        get() = sillyTavernApiProvider.get()

    /**
     * Search for character cards.
     *
     * @param query Search query
     * @param nsfwFilter NSFW filter option
     * @param tags Tags to filter by
     * @param creator Creator to filter by
     * @param page Page number (1-indexed)
     * @param limit Results per page
     */
    suspend fun search(
        query: String? = null,
        nsfwFilter: CardVaultNsfwFilter = CardVaultNsfwFilter.ALL,
        tags: List<String>? = null,
        creator: String? = null,
        page: Int = 1,
        limit: Int = 50
    ): Result<CardVaultSearchResult> = withContext(Dispatchers.IO) {
        try {
            val offset = (page - 1) * limit
            val nsfw: Boolean? = when (nsfwFilter) {
                CardVaultNsfwFilter.ALL -> null
                CardVaultNsfwFilter.SFW_ONLY -> false
                CardVaultNsfwFilter.NSFW_ONLY -> true
            }
            val tagsParam = tags?.takeIf { it.isNotEmpty() }?.joinToString(",")

            Log.d(TAG, "Searching: query=$query, nsfw=$nsfw, tags=$tagsParam, page=$page, limit=$limit")

            val response = cardVaultApi.search(
                query = query?.takeIf { it.isNotBlank() },
                tags = tagsParam,
                nsfw = nsfw,
                creator = creator?.takeIf { it.isNotBlank() },
                limit = limit,
                offset = offset
            )

            if (!response.isSuccessful) {
                return@withContext Result.Error(
                    Exception("Search failed: ${response.code()} ${response.message()}")
                )
            }

            val body = response.body() ?: return@withContext Result.Error(
                Exception("Empty response from server")
            )

            val characters = body.results.map { dto ->
                CardVaultCharacter(
                    file = dto.file,
                    folder = dto.folder,
                    name = dto.name,
                    creator = dto.creator,
                    tags = dto.tags,
                    nsfw = dto.nsfw,
                    descriptionPreview = dto.descriptionPreview,
                    firstMesPreview = dto.firstMesPreview
                )
            }

            val totalPages = ceil(body.total.toDouble() / limit).toInt().coerceAtLeast(1)

            Log.d(TAG, "Search returned ${characters.size} results, total: ${body.total}")

            Result.Success(
                CardVaultSearchResult(
                    characters = characters,
                    totalCount = body.total,
                    currentPage = page,
                    totalPages = totalPages,
                    limit = limit
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Search error", e)
            Result.Error(e)
        }
    }

    /**
     * Get full details for a character card.
     */
    suspend fun getCardDetails(folder: String, filename: String): Result<CardVaultCharacter> =
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Getting details for $folder/$filename")

                val response = cardVaultApi.getCardDetails(folder, filename)

                if (!response.isSuccessful) {
                    return@withContext Result.Error(
                        Exception("Failed to get details: ${response.code()} ${response.message()}")
                    )
                }

                val body = response.body() ?: return@withContext Result.Error(
                    Exception("Empty response from server")
                )

                val dto = body.entry
                val fullData = body.fullMetadata?.data

                Result.Success(
                    CardVaultCharacter(
                        file = dto.file,
                        folder = dto.folder,
                        name = dto.name,
                        creator = dto.creator,
                        tags = dto.tags,
                        nsfw = dto.nsfw,
                        descriptionPreview = dto.descriptionPreview,
                        firstMesPreview = dto.firstMesPreview,
                        fullDescription = fullData?.description,
                        fullFirstMes = fullData?.firstMes,
                        personality = fullData?.personality,
                        scenario = fullData?.scenario
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Get details error", e)
                Result.Error(e)
            }
        }

    /**
     * Import a character card to SillyTavern.
     */
    suspend fun importCard(character: CardVaultCharacter): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Importing card: ${character.name} (${character.id})")

                // Download the card PNG
                val downloadResponse = cardVaultApi.downloadCard(character.folder, character.file)

                if (!downloadResponse.isSuccessful) {
                    return@withContext Result.Error(
                        Exception("Failed to download card: ${downloadResponse.code()}")
                    )
                }

                val imageBytes = downloadResponse.body()?.bytes()
                if (imageBytes == null || imageBytes.isEmpty()) {
                    return@withContext Result.Error(Exception("Downloaded file is empty"))
                }

                Log.d(TAG, "Downloaded ${imageBytes.size} bytes")

                // Create multipart request for SillyTavern
                val safeFilename = character.file.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                val requestBody = imageBytes.toRequestBody("image/png".toMediaType())
                val filePart = MultipartBody.Part.createFormData(
                    "avatar",
                    safeFilename,
                    requestBody
                )
                val fileTypeBody = "png".toRequestBody("text/plain".toMediaType())

                // Import to SillyTavern
                val importResponse = sillyTavernApi.importCharacter(filePart, fileTypeBody)

                if (!importResponse.isSuccessful) {
                    return@withContext Result.Error(
                        Exception("Import failed: ${importResponse.code()} ${importResponse.message()}")
                    )
                }

                Log.d(TAG, "Successfully imported ${character.name}")
                Result.Success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Import error", e)
                Result.Error(e)
            }
        }

    /**
     * Get index statistics.
     */
    suspend fun getStats(): Result<CardVaultStats> = withContext(Dispatchers.IO) {
        try {
            val response = cardVaultApi.getStats()

            if (!response.isSuccessful) {
                return@withContext Result.Error(
                    Exception("Failed to get stats: ${response.code()}")
                )
            }

            val body = response.body() ?: return@withContext Result.Error(
                Exception("Empty response")
            )

            // Parse top_tags from [[String, Int], ...] format
            val topTags = body.topTags.mapNotNull { pair ->
                if (pair.size >= 2) {
                    val tag = pair[0].toString()
                    val count = (pair[1] as? Number)?.toInt() ?: pair[1].toString().toIntOrNull() ?: 0
                    tag to count
                } else null
            }

            val topCreators = body.topCreators.mapNotNull { pair ->
                if (pair.size >= 2) {
                    val creator = pair[0].toString()
                    val count = (pair[1] as? Number)?.toInt() ?: pair[1].toString().toIntOrNull() ?: 0
                    creator to count
                } else null
            }

            Result.Success(
                CardVaultStats(
                    totalCards = body.totalCards,
                    nsfwCount = body.nsfwCount,
                    sfwCount = body.sfwCount,
                    uniqueCreators = body.uniqueCreators,
                    uniqueTags = body.uniqueTags,
                    topTags = topTags,
                    topCreators = topCreators
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Get stats error", e)
            Result.Error(e)
        }
    }

    /**
     * Get all available tags with counts.
     */
    suspend fun getTags(): Result<List<Pair<String, Int>>> = withContext(Dispatchers.IO) {
        try {
            val response = cardVaultApi.getTags()

            if (!response.isSuccessful) {
                return@withContext Result.Error(
                    Exception("Failed to get tags: ${response.code()}")
                )
            }

            val body = response.body() ?: return@withContext Result.Error(
                Exception("Empty response")
            )

            // Parse tags from [[String, Int], ...] format
            val tags = body.tags.mapNotNull { pair ->
                if (pair.size >= 2) {
                    val tag = pair[0].toString()
                    val count = (pair[1] as? Number)?.toInt() ?: pair[1].toString().toIntOrNull() ?: 0
                    tag to count
                } else null
            }

            Result.Success(tags)
        } catch (e: Exception) {
            Log.e(TAG, "Get tags error", e)
            Result.Error(e)
        }
    }

    /**
     * Upload a character card to the server.
     *
     * @param imageBytes The PNG file bytes
     * @param filename The filename for the card
     * @param folder Subfolder to save to (default: "Uploads")
     */
    suspend fun uploadCard(
        imageBytes: ByteArray,
        filename: String,
        folder: String = "Uploads"
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Uploading card: $filename to folder: $folder")

            val requestBody = imageBytes.toRequestBody("image/png".toMediaType())
            val filePart = MultipartBody.Part.createFormData(
                "file",
                filename,
                requestBody
            )
            val folderBody = folder.toRequestBody("text/plain".toMediaType())

            val response = cardVaultApi.uploadCard(filePart, folderBody)

            if (!response.isSuccessful) {
                return@withContext Result.Error(
                    Exception("Upload failed: ${response.code()} ${response.message()}")
                )
            }

            val body = response.body() ?: return@withContext Result.Error(
                Exception("Empty response from server")
            )

            if (body.success) {
                Log.d(TAG, "Successfully uploaded: ${body.name} to ${body.path}")
                Result.Success(body.name)
            } else {
                Result.Error(Exception(body.detail ?: "Upload rejected"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Upload error", e)
            Result.Error(e)
        }
    }

    /**
     * Build the full URL for a card image.
     * URL-encodes folder and filename to handle spaces and special characters.
     */
    fun buildImageUrl(baseUrl: String, character: CardVaultCharacter): String {
        val cleanBaseUrl = baseUrl.trimEnd('/')
        val encodedFolder = URLEncoder.encode(character.folder, "UTF-8").replace("+", "%20")
        val encodedFile = URLEncoder.encode(character.file, "UTF-8").replace("+", "%20")
        return "$cleanBaseUrl/cards/$encodedFolder/$encodedFile"
    }

    // ===== LOREBOOK METHODS =====

    /**
     * Search for lorebooks.
     */
    suspend fun searchLorebooks(
        query: String? = null,
        nsfwFilter: CardVaultNsfwFilter = CardVaultNsfwFilter.ALL,
        topics: List<String>? = null,
        creator: String? = null,
        page: Int = 1,
        limit: Int = 50
    ): Result<CardVaultLorebookSearchResult> = withContext(Dispatchers.IO) {
        try {
            val offset = (page - 1) * limit
            val nsfw: Boolean? = when (nsfwFilter) {
                CardVaultNsfwFilter.ALL -> null
                CardVaultNsfwFilter.SFW_ONLY -> false
                CardVaultNsfwFilter.NSFW_ONLY -> true
            }
            val topicsParam = topics?.takeIf { it.isNotEmpty() }?.joinToString(",")

            Log.d(TAG, "Searching lorebooks: query=$query, nsfw=$nsfw, topics=$topicsParam")

            val response = cardVaultApi.searchLorebooks(
                query = query?.takeIf { it.isNotBlank() },
                topics = topicsParam,
                creator = creator?.takeIf { it.isNotBlank() },
                nsfw = nsfw,
                limit = limit,
                offset = offset
            )

            if (!response.isSuccessful) {
                return@withContext Result.Error(
                    Exception("Lorebook search failed: ${response.code()} ${response.message()}")
                )
            }

            val body = response.body() ?: return@withContext Result.Error(
                Exception("Empty response from server")
            )

            val lorebooks = body.lorebooks.map { dto ->
                CardVaultLorebook(
                    id = dto.id,
                    file = dto.file,
                    folder = dto.folder,
                    name = dto.name,
                    creator = dto.creator,
                    description = dto.description,
                    topics = dto.topics,
                    entryCount = dto.entryCount,
                    tokenCount = dto.tokenCount,
                    keywords = dto.keywords,
                    starCount = dto.starCount,
                    nsfw = dto.nsfw
                )
            }

            val totalPages = ceil(body.count.toDouble() / limit).toInt().coerceAtLeast(1)

            Log.d(TAG, "Lorebook search returned ${lorebooks.size} results")

            Result.Success(
                CardVaultLorebookSearchResult(
                    lorebooks = lorebooks,
                    totalCount = body.count,
                    currentPage = page,
                    totalPages = totalPages,
                    limit = limit
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Lorebook search error", e)
            Result.Error(e)
        }
    }

    /**
     * Get full details for a lorebook including entries.
     */
    suspend fun getLorebookDetails(id: Int): Result<CardVaultLorebook> =
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Getting lorebook details for id=$id")

                val response = cardVaultApi.getLorebookDetails(id)

                if (!response.isSuccessful) {
                    return@withContext Result.Error(
                        Exception("Failed to get lorebook: ${response.code()} ${response.message()}")
                    )
                }

                val dto = response.body() ?: return@withContext Result.Error(
                    Exception("Empty response from server")
                )

                val entries = dto.content?.entries?.values?.map { entry ->
                    LorebookEntryItem(
                        id = entry.id,
                        name = entry.name,
                        content = entry.content,
                        keys = entry.keys,
                        enabled = entry.enabled,
                        priority = entry.priority
                    )
                }

                Result.Success(
                    CardVaultLorebook(
                        id = dto.id,
                        file = dto.file,
                        folder = dto.folder,
                        name = dto.name,
                        creator = dto.creator,
                        description = dto.description,
                        topics = dto.topics,
                        entryCount = dto.entryCount,
                        tokenCount = dto.tokenCount,
                        keywords = dto.keywords,
                        starCount = dto.starCount,
                        nsfw = dto.nsfw,
                        entries = entries
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Get lorebook details error", e)
                Result.Error(e)
            }
        }

    /**
     * Import a lorebook to SillyTavern.
     */
    suspend fun importLorebook(lorebook: CardVaultLorebook): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Importing lorebook: ${lorebook.name}")

                // Download the lorebook JSON
                val downloadResponse = cardVaultApi.downloadLorebook(lorebook.folder, lorebook.file)

                if (!downloadResponse.isSuccessful) {
                    return@withContext Result.Error(
                        Exception("Failed to download lorebook: ${downloadResponse.code()}")
                    )
                }

                val jsonBytes = downloadResponse.body()?.bytes()
                if (jsonBytes == null || jsonBytes.isEmpty()) {
                    return@withContext Result.Error(Exception("Downloaded file is empty"))
                }

                Log.d(TAG, "Downloaded ${jsonBytes.size} bytes")

                // Create multipart request for SillyTavern
                // Use the lorebook name with .json extension for the filename
                val safeFilename = lorebook.name.replace(Regex("[^a-zA-Z0-9._\\- ]"), "_") + ".json"
                val requestBody = jsonBytes.toRequestBody("application/json".toMediaType())
                val filePart = MultipartBody.Part.createFormData(
                    "avatar",  // SillyTavern uses global multer with 'avatar' field name
                    safeFilename,
                    requestBody
                )

                // Import to SillyTavern
                val importResponse = sillyTavernApi.importLorebook(filePart)

                if (!importResponse.isSuccessful) {
                    return@withContext Result.Error(
                        Exception("Import failed: ${importResponse.code()} ${importResponse.message()}")
                    )
                }

                Log.d(TAG, "Successfully imported lorebook ${lorebook.name}")
                Result.Success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Import lorebook error", e)
                Result.Error(e)
            }
        }

    /**
     * Get lorebook statistics.
     */
    suspend fun getLorebookStats(): Result<CardVaultLorebookStats> = withContext(Dispatchers.IO) {
        try {
            val response = cardVaultApi.getLorebookStats()

            if (!response.isSuccessful) {
                return@withContext Result.Error(
                    Exception("Failed to get lorebook stats: ${response.code()}")
                )
            }

            val body = response.body() ?: return@withContext Result.Error(
                Exception("Empty response")
            )

            Result.Success(
                CardVaultLorebookStats(
                    totalLorebooks = body.totalLorebooks,
                    nsfwCount = body.nsfwCount,
                    sfwCount = body.sfwCount,
                    creatorCount = body.creatorCount,
                    totalEntries = body.totalEntries,
                    configuredDirs = body.configuredDirs
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Get lorebook stats error", e)
            Result.Error(e)
        }
    }
}
