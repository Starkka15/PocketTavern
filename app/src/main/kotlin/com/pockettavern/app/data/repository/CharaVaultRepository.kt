package com.pockettavern.app.data.repository

import android.util.Log
import com.pockettavern.app.data.local.CharacterStorage
import com.pockettavern.app.data.local.LoreBookStorage
import com.pockettavern.app.data.remote.api.CharaVaultApi
import com.pockettavern.app.domain.model.CharaVaultCharacter
import com.pockettavern.app.domain.model.CharaVaultNsfwFilter
import com.pockettavern.app.domain.model.CharaVaultSearchResult
import com.pockettavern.app.domain.model.CharaVaultStats
import com.pockettavern.app.domain.model.CharaVaultLorebook
import com.pockettavern.app.domain.model.CharaVaultLorebookSearchResult
import com.pockettavern.app.domain.model.CharaVaultLorebookStats
import com.pockettavern.app.domain.model.LorebookEntryItem
import com.pockettavern.app.data.remote.dto.charavault.CharaVaultLoginResponse
import com.pockettavern.app.data.remote.dto.charavault.CharaVaultUserResponse
import com.pockettavern.app.domain.model.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil

private const val TAG = "CharaVaultRepository"

@Singleton
class CharaVaultRepository @Inject constructor(
    private val charaVaultApiProvider: javax.inject.Provider<CharaVaultApi>,
    private val characterStorage: CharacterStorage,
    private val loreBookStorage: LoreBookStorage
) {

    private val charaVaultApi: CharaVaultApi
        get() = charaVaultApiProvider.get()

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
        nsfwFilter: CharaVaultNsfwFilter = CharaVaultNsfwFilter.ALL,
        tags: List<String>? = null,
        creator: String? = null,
        page: Int = 1,
        limit: Int = 50
    ): Result<CharaVaultSearchResult> = withContext(Dispatchers.IO) {
        try {
            val offset = (page - 1) * limit
            val nsfw: Boolean? = when (nsfwFilter) {
                CharaVaultNsfwFilter.ALL -> null
                CharaVaultNsfwFilter.SFW_ONLY -> false
                CharaVaultNsfwFilter.NSFW_ONLY -> true
            }
            val tagsParam = tags?.takeIf { it.isNotEmpty() }?.joinToString(",")

            Log.d(TAG, "Searching: query=$query, nsfw=$nsfw, tags=$tagsParam, page=$page, limit=$limit")

            val response = charaVaultApi.search(
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
                CharaVaultCharacter(
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
                CharaVaultSearchResult(
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
    suspend fun getCardDetails(folder: String, filename: String): Result<CharaVaultCharacter> =
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Getting details for $folder/$filename")

                val response = charaVaultApi.getCardDetails(folder, filename)

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
                    CharaVaultCharacter(
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
    suspend fun importCard(character: CharaVaultCharacter): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Importing card: ${character.name} (${character.id})")

                // Download the card PNG
                val downloadResponse = charaVaultApi.downloadCard(character.folder, character.file)

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

                // Save directly to local storage
                val safeFilename = character.file.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                characterStorage.saveRawPng(imageBytes, safeFilename)

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
    suspend fun getStats(): Result<CharaVaultStats> = withContext(Dispatchers.IO) {
        try {
            val response = charaVaultApi.getStats()

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
                CharaVaultStats(
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
            val response = charaVaultApi.getTags()

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

            val response = charaVaultApi.uploadCard(filePart, folderBody)

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
    fun buildImageUrl(baseUrl: String, character: CharaVaultCharacter): String {
        val cleanBaseUrl = baseUrl.trimEnd('/')
        val encodedFolder = URLEncoder.encode(character.folder, "UTF-8").replace("+", "%20")
        val encodedFile = URLEncoder.encode(character.file, "UTF-8").replace("+", "%20")
        return "$cleanBaseUrl/cards/$encodedFolder/$encodedFile"
    }

    // ===== CHARAVAULT.NET AUTH METHODS =====

    suspend fun login(email: String, password: String): Result<CharaVaultLoginResponse> =
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Logging in to CharaVault.net as $email")
                val response = charaVaultApi.login(mapOf("email" to email, "password" to password))

                if (!response.isSuccessful) {
                    val errorBody = response.errorBody()?.string()
                    val detail = try {
                        kotlinx.serialization.json.Json.decodeFromString<Map<String, String>>(errorBody ?: "")["detail"]
                    } catch (e: Exception) { null }
                    return@withContext Result.Error(
                        Exception(detail ?: "Login failed: ${response.code()}")
                    )
                }

                val body = response.body() ?: return@withContext Result.Error(
                    Exception("Empty response from server")
                )

                Log.d(TAG, "Login response: success=${body.success}, requires2fa=${body.requires2fa}")
                Result.Success(body)
            } catch (e: Exception) {
                Log.e(TAG, "Login error", e)
                Result.Error(e)
            }
        }

    suspend fun verify2fa(challengeToken: String, code: String): Result<CharaVaultLoginResponse> =
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Verifying 2FA for CharaVault.net")
                val response = charaVaultApi.verify2fa(
                    mapOf("challenge_token" to challengeToken, "code" to code)
                )

                if (!response.isSuccessful) {
                    val errorBody = response.errorBody()?.string()
                    val detail = try {
                        kotlinx.serialization.json.Json.decodeFromString<Map<String, String>>(errorBody ?: "")["detail"]
                    } catch (e: Exception) { null }
                    return@withContext Result.Error(
                        Exception(detail ?: "2FA verification failed: ${response.code()}")
                    )
                }

                val body = response.body() ?: return@withContext Result.Error(
                    Exception("Empty response")
                )
                Result.Success(body)
            } catch (e: Exception) {
                Log.e(TAG, "2FA verify error", e)
                Result.Error(e)
            }
        }

    suspend fun getMe(): Result<CharaVaultUserResponse> = withContext(Dispatchers.IO) {
        try {
            val response = charaVaultApi.getMe()
            if (!response.isSuccessful) {
                return@withContext Result.Error(Exception("Not authenticated: ${response.code()}"))
            }
            val body = response.body() ?: return@withContext Result.Error(Exception("Empty response"))
            Result.Success(body)
        } catch (e: Exception) {
            Log.e(TAG, "getMe error", e)
            Result.Error(e)
        }
    }

    suspend fun verifyAge(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val response = charaVaultApi.verifyAge()
            if (!response.isSuccessful) {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "verifyAge failed: ${response.code()} $errorBody")
                return@withContext Result.Error(Exception("Age verification failed: ${response.code()}"))
            }
            val body = response.body()
            val newToken = body?.get("token") as? String
            Log.d(TAG, "verifyAge success, got new token: ${newToken != null}")
            Result.Success(newToken ?: "")
        } catch (e: Exception) {
            Log.e(TAG, "verifyAge error", e)
            Result.Error(e)
        }
    }

    suspend fun getMyUploads(page: Int = 1, limit: Int = 50): Result<CharaVaultSearchResult> = withContext(Dispatchers.IO) {
        try {
            val offset = (page - 1) * limit
            val response = charaVaultApi.getMyUploads(limit = limit, offset = offset)
            if (!response.isSuccessful) {
                return@withContext Result.Error(Exception("My uploads failed: ${response.code()}"))
            }
            val body = response.body() ?: return@withContext Result.Error(Exception("Empty response"))
            val characters = body.results.map { dto ->
                CharaVaultCharacter(
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
            val totalPages = if (limit > 0) ((body.total + limit - 1) / limit) else 1
            Result.Success(CharaVaultSearchResult(
                characters = characters,
                totalCount = body.total,
                currentPage = page,
                totalPages = totalPages,
                limit = limit
            ))
        } catch (e: Exception) {
            Log.e(TAG, "getMyUploads error", e)
            Result.Error(e)
        }
    }

    // ===== LOREBOOK METHODS =====

    /**
     * Search for lorebooks.
     */
    suspend fun searchLorebooks(
        query: String? = null,
        nsfwFilter: CharaVaultNsfwFilter = CharaVaultNsfwFilter.ALL,
        topics: List<String>? = null,
        creator: String? = null,
        page: Int = 1,
        limit: Int = 50
    ): Result<CharaVaultLorebookSearchResult> = withContext(Dispatchers.IO) {
        try {
            val offset = (page - 1) * limit
            val nsfw: Boolean? = when (nsfwFilter) {
                CharaVaultNsfwFilter.ALL -> null
                CharaVaultNsfwFilter.SFW_ONLY -> false
                CharaVaultNsfwFilter.NSFW_ONLY -> true
            }
            val topicsParam = topics?.takeIf { it.isNotEmpty() }?.joinToString(",")

            Log.d(TAG, "Searching lorebooks: query=$query, nsfw=$nsfw, topics=$topicsParam")

            val response = charaVaultApi.searchLorebooks(
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
                CharaVaultLorebook(
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
                CharaVaultLorebookSearchResult(
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
    suspend fun getLorebookDetails(id: Int): Result<CharaVaultLorebook> =
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Getting lorebook details for id=$id")

                val response = charaVaultApi.getLorebookDetails(id)

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
                    CharaVaultLorebook(
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
    suspend fun importLorebook(lorebook: CharaVaultLorebook): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Importing lorebook: ${lorebook.name}")

                // Download the lorebook JSON
                val downloadResponse = charaVaultApi.downloadLorebook(lorebook.folder, lorebook.file)

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

                // Save directly to local storage
                val safeName = lorebook.name.replace(Regex("[^a-zA-Z0-9._\\- ]"), "_")
                loreBookStorage.saveRawLorebook(safeName, jsonBytes)

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
    suspend fun getLorebookStats(): Result<CharaVaultLorebookStats> = withContext(Dispatchers.IO) {
        try {
            val response = charaVaultApi.getLorebookStats()

            if (!response.isSuccessful) {
                return@withContext Result.Error(
                    Exception("Failed to get lorebook stats: ${response.code()}")
                )
            }

            val body = response.body() ?: return@withContext Result.Error(
                Exception("Empty response")
            )

            Result.Success(
                CharaVaultLorebookStats(
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
