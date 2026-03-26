package com.pockettavern.app.data.repository

import android.graphics.Bitmap
import android.util.Log
import com.pockettavern.app.data.local.CharacterStorage
import com.pockettavern.app.domain.model.CardSource
import com.pockettavern.app.domain.model.CharaVaultCharacter
import com.pockettavern.app.domain.model.CharaVaultNsfwFilter
import com.pockettavern.app.domain.model.CharaVaultSearchResult
import com.pockettavern.app.domain.model.Result
import com.pockettavern.app.util.CharacterCardData
import com.pockettavern.app.util.CharacterCardV2
import com.pockettavern.app.util.PngCharacterCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import kotlin.math.ceil

private const val TAG = "CardSearchRepository"

// ── Harpy / Supabase ──────────────────────────────────────────────────────────
private const val HARPY_SUPABASE_URL = "https://ehgqxxoeyqsdgquzzond.supabase.co"
private const val HARPY_ANON_KEY =
    "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9." +
    "eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImVoZ3F4eG9leXFzZGdxdXp6b25kIiwicm9sZSI6ImFub24iLCJpYXQiOjE2OTI5NTM0ODUsImV4cCI6MjAwODUyOTQ4NX0." +
    "Cn-jDJqZFnwnhV9H6sBdRj8a3RA_XNWsBrApg4spOis"
private const val HARPY_STORAGE_BASE = "$HARPY_SUPABASE_URL/storage/v1/object/public/astrsk-assets"

// ── JannyAI / MeiliSearch ────────────────────────────────────────────────────
private const val JANNY_SEARCH_URL = "https://search.jannyai.com/multi-search"
private const val JANNY_SEARCH_TOKEN = "88a6463b66e04fb07ba87ee3db06af337f492ce511d93df6e2d2968cb2ff2b30"
private const val JANNY_IMAGE_BASE = "https://image.jannyai.com/bot-avatars"

// ── SpicyChat / Typesense ────────────────────────────────────────────────────
private const val SPICY_TYPESENSE_HOST = "https://etmzpxgvnid370fyp.a1.typesense.net"
private const val SPICY_TYPESENSE_KEY = "STHKtT6jrC5z1IozTJHIeSN4qN9oL1s3"
private const val SPICY_CDN_BASE = "https://cdn.nd-api.com/avatars"

// ── RisuRealm ────────────────────────────────────────────────────────────────
private const val RISU_IMAGE_BASE = "https://sv.risuai.xyz/resource"

class CardSearchRepository(
    private val okHttpClient: OkHttpClient,
    private val characterStorage: CharacterStorage
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val jsonStrict = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
    }

    // ── Dispatch ──────────────────────────────────────────────────────────────

    suspend fun search(
        source: CardSource,
        query: String? = null,
        nsfwFilter: CharaVaultNsfwFilter = CharaVaultNsfwFilter.ALL,
        tags: List<String>? = null,
        page: Int = 1,
        limit: Int = 48
    ): Result<CharaVaultSearchResult> = when (source) {
        CardSource.CHARAVAULT -> Result.Error(Exception("Use CharaVaultRepository for CharaVault"))
        CardSource.CHUB -> searchChub(query, nsfwFilter, tags, page, limit)
        CardSource.CHARACTER_TAVERN -> searchCharacterTavern(query, nsfwFilter, tags, page, limit)
        CardSource.WYVERN -> searchWyvern(query, nsfwFilter, page, limit)
        CardSource.PYGMALION -> searchPygmalion(query, nsfwFilter, tags, page, limit)
        CardSource.SPICYCHAT -> searchSpicyChat(query, nsfwFilter, page, limit)
        CardSource.HARPY -> searchHarpy(query, nsfwFilter, tags, page, limit)
        CardSource.JANNYAI -> searchJannyAI(query, nsfwFilter, page, limit)
        CardSource.SAKURA -> searchSakura(query, nsfwFilter, page, limit)
        CardSource.RISUREALM -> searchRisuRealm(query, nsfwFilter, page, limit)
        CardSource.SAUCEPAN -> searchSaucepan(query, nsfwFilter, page, limit)
    }

    // ── Chub.ai ───────────────────────────────────────────────────────────────

    private suspend fun searchChub(
        query: String?,
        nsfwFilter: CharaVaultNsfwFilter,
        tags: List<String>?,
        page: Int,
        limit: Int
    ): Result<CharaVaultSearchResult> = withContext(Dispatchers.IO) {
        try {
            val includeNsfw = nsfwFilter != CharaVaultNsfwFilter.SFW_ONLY
            val nsfwOnly = nsfwFilter == CharaVaultNsfwFilter.NSFW_ONLY

            val urlBuilder = "https://api.chub.ai/search".toHttpUrl().newBuilder()
                .addQueryParameter("search", query ?: "")
                .addQueryParameter("first", limit.toString())
                .addQueryParameter("page", page.toString())
                .addQueryParameter("sort", "last_activity_at")
                .addQueryParameter("asc", "false")
                .addQueryParameter("include_forks", "false")
                .addQueryParameter("nsfw", includeNsfw.toString())
                .addQueryParameter("nsfl", includeNsfw.toString())
                .addQueryParameter("nsfw_only", nsfwOnly.toString())
                .addQueryParameter("min_tokens", "50")

            if (!tags.isNullOrEmpty()) {
                urlBuilder.addQueryParameter("tags", tags.joinToString(","))
            }

            val request = Request.Builder()
                .url(urlBuilder.build())
                .get()
                .header("Accept", "application/json")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.Error(Exception("Chub search failed: ${response.code}"))
            }

            val bodyStr = response.body?.string()
                ?: return@withContext Result.Error(Exception("Empty Chub response"))

            val root = json.parseToJsonElement(bodyStr).jsonObject
            val data = root["data"]?.jsonObject
                ?: return@withContext Result.Error(Exception("No data field in Chub response"))
            val nodes = data["nodes"]?.jsonArray
                ?: return@withContext Result.Error(Exception("No nodes in Chub response"))

            val characters = nodes.mapNotNull { nodeEl ->
                try {
                    val node = nodeEl.jsonObject
                    val fullPath = node["fullPath"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val name = node["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val tagline = node["tagline"]?.jsonPrimitive?.content ?: ""
                    val topics = node["topics"]?.jsonArray
                        ?.map { it.jsonPrimitive.content } ?: emptyList()
                    val nodeNsfw = topics.any { it.equals("nsfw", ignoreCase = true) }
                        || node["nsfw"]?.jsonPrimitive?.content == "true"
                    val creator = fullPath.substringBefore("/")
                    val avatarUrl = node["avatar_url"]?.jsonPrimitive?.content
                        ?: "https://avatars.charhub.io/avatars/$fullPath/chara_card_v2.png"
                    val cardUrl = "https://avatars.charhub.io/avatars/$fullPath/chara_card_v2.png"
                    val safeName = name.replace(Regex("[^a-zA-Z0-9._-]"), "_")

                    CharaVaultCharacter(
                        file = "$safeName.png",
                        folder = "chub.ai",
                        name = name,
                        creator = creator,
                        tags = topics,
                        nsfw = nodeNsfw,
                        descriptionPreview = tagline,
                        firstMesPreview = "",
                        externalImageUrl = avatarUrl,
                        externalDownloadUrl = cardUrl
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse Chub node", e)
                    null
                }
            }

            val explicitCount = data["count"]?.jsonPrimitive?.content?.toIntOrNull()
            val inferredTotal = if (characters.size < limit) {
                (page - 1) * limit + characters.size
            } else {
                page * limit + 1
            }
            val totalCount = explicitCount ?: inferredTotal
            val totalPages = ceil(totalCount.toDouble() / limit).toInt().coerceAtLeast(1)

            Result.Success(
                CharaVaultSearchResult(
                    characters = characters,
                    totalCount = totalCount,
                    currentPage = page,
                    totalPages = totalPages,
                    limit = limit
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Chub search error", e)
            Result.Error(e)
        }
    }

    // ── Character Tavern ──────────────────────────────────────────────────────

    private suspend fun searchCharacterTavern(
        query: String?,
        nsfwFilter: CharaVaultNsfwFilter,
        tags: List<String>?,
        page: Int,
        limit: Int
    ): Result<CharaVaultSearchResult> = withContext(Dispatchers.IO) {
        try {
            val urlBuilder = "https://character-tavern.com/api/search/cards".toHttpUrl().newBuilder()
                .addQueryParameter("query", query ?: "")
                .addQueryParameter("page", page.toString())
                .addQueryParameter("limit", limit.toString())

            if (!tags.isNullOrEmpty()) {
                urlBuilder.addQueryParameter("tags", tags.joinToString(","))
            }
            if (nsfwFilter == CharaVaultNsfwFilter.SFW_ONLY) {
                urlBuilder.addQueryParameter("nsfw", "false")
            } else if (nsfwFilter == CharaVaultNsfwFilter.NSFW_ONLY) {
                urlBuilder.addQueryParameter("nsfw", "true")
                urlBuilder.addQueryParameter("nsfw_only", "true")
            }

            val request = Request.Builder()
                .url(urlBuilder.build())
                .get()
                .header("Accept", "application/json")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.Error(Exception("Character Tavern search failed: ${response.code}"))
            }

            val bodyStr = response.body?.string()
                ?: return@withContext Result.Error(Exception("Empty Character Tavern response"))

            val root = json.parseToJsonElement(bodyStr).jsonObject
            val data = root["data"]?.jsonObject ?: root
            val hits = data["hits"]?.jsonArray ?: data["results"]?.jsonArray
                ?: return@withContext Result.Error(Exception("No hits in Character Tavern response"))

            val totalCount = data["totalHits"]?.jsonPrimitive?.intOrNull
                ?: data["total"]?.jsonPrimitive?.intOrNull
                ?: hits.size
            val totalPages = data["totalPages"]?.jsonPrimitive?.intOrNull
                ?: ceil(totalCount.toDouble() / limit).toInt().coerceAtLeast(1)

            val characters = hits.mapNotNull { el ->
                try {
                    val node = el.jsonObject
                    val id = node["originalCardsId"]?.jsonPrimitive?.content
                        ?: node["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val name = node["name"]?.jsonPrimitive?.content
                        ?: node["inChatName"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val path = node["path"]?.jsonPrimitive?.content ?: id
                    val tagsArr = node["tags"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
                    val isNsfw = node["isNSFW"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
                    val descPreview = node["definition_personality"]?.jsonPrimitive?.content
                        ?: node["characterPersonality"]?.jsonPrimitive?.content ?: ""
                    val firstMes = node["definition_first_message"]?.jsonPrimitive?.content
                        ?: node["characterFirstMessage"]?.jsonPrimitive?.content ?: ""
                    // Use the card PNG CDN URL directly for display (same file, different param triggers download)
                    val avatarUrl = "https://cards.character-tavern.com/$path.png"
                    val cardUrl = "https://cards.character-tavern.com/$path.png?action=download"
                    val safeName = name.replace(Regex("[^a-zA-Z0-9._-]"), "_")

                    CharaVaultCharacter(
                        file = "$safeName.png",
                        folder = "character-tavern",
                        name = name,
                        creator = node["ownerCTId"]?.jsonPrimitive?.content ?: "",
                        tags = tagsArr,
                        nsfw = isNsfw,
                        descriptionPreview = descPreview.take(200),
                        firstMesPreview = firstMes.take(200),
                        externalImageUrl = avatarUrl,
                        externalDownloadUrl = cardUrl
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse Character Tavern card", e)
                    null
                }
            }

            Result.Success(CharaVaultSearchResult(characters, totalCount, page, totalPages, limit))
        } catch (e: Exception) {
            Log.e(TAG, "Character Tavern search error", e)
            Result.Error(e)
        }
    }

    // ── Wyvern Chat ───────────────────────────────────────────────────────────

    private suspend fun searchWyvern(
        query: String?,
        nsfwFilter: CharaVaultNsfwFilter,
        page: Int,
        limit: Int
    ): Result<CharaVaultSearchResult> = withContext(Dispatchers.IO) {
        try {
            val urlBuilder = "https://app.wyvern.chat/api/characters/public".toHttpUrl().newBuilder()
                .addQueryParameter("page", page.toString())
                .addQueryParameter("limit", limit.toString())
                .addQueryParameter("sort", "dateCreated")

            if (!query.isNullOrBlank()) {
                urlBuilder.addQueryParameter("search", query)
            }
            if (nsfwFilter == CharaVaultNsfwFilter.SFW_ONLY) {
                urlBuilder.addQueryParameter("nsfw", "false")
            } else if (nsfwFilter == CharaVaultNsfwFilter.NSFW_ONLY) {
                urlBuilder.addQueryParameter("nsfw", "true")
                urlBuilder.addQueryParameter("nsfw_only", "true")
            }

            val request = Request.Builder()
                .url(urlBuilder.build())
                .get()
                .header("Accept", "application/json")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.Error(Exception("Wyvern search failed: ${response.code}"))
            }

            val bodyStr = response.body?.string()
                ?: return@withContext Result.Error(Exception("Empty Wyvern response"))

            val root = json.parseToJsonElement(bodyStr)
            val (items, totalCount) = when (root) {
                is JsonArray -> Pair(root, root.size)
                is JsonObject -> {
                    val arr = root["data"]?.jsonArray ?: root["characters"]?.jsonArray ?: JsonArray(emptyList())
                    val total = root["total"]?.jsonPrimitive?.intOrNull
                        ?: root["maxCount"]?.jsonPrimitive?.intOrNull ?: arr.size
                    Pair(arr, total)
                }
                else -> Pair(JsonArray(emptyList()), 0)
            }

            val totalPages = ceil(totalCount.toDouble() / limit).toInt().coerceAtLeast(1)

            val characters = items.mapNotNull { el ->
                try {
                    val node = el.jsonObject
                    val id = node["id"]?.jsonPrimitive?.content
                        ?: node["_id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val name = node["name"]?.jsonPrimitive?.content
                        ?: node["chat_name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val avatarUrl = node["avatar"]?.jsonPrimitive?.content ?: ""
                    val tagsArr = node["tags"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
                    val desc = node["description"]?.jsonPrimitive?.content
                        ?: node["personality"]?.jsonPrimitive?.content ?: ""
                    val firstMes = node["greeting"]?.jsonPrimitive?.content
                        ?: node["first_message"]?.jsonPrimitive?.content ?: ""
                    val safeName = name.replace(Regex("[^a-zA-Z0-9._-]"), "_")

                    CharaVaultCharacter(
                        file = "$safeName.png",
                        folder = "wyvern",
                        name = name,
                        creator = node["creator"]?.jsonObject?.get("username")?.jsonPrimitive?.content ?: "",
                        tags = tagsArr,
                        nsfw = false,
                        descriptionPreview = desc.take(200),
                        firstMesPreview = firstMes.take(200),
                        fullDescription = desc.takeIf { it.isNotBlank() },
                        fullFirstMes = firstMes.takeIf { it.isNotBlank() },
                        externalImageUrl = avatarUrl.takeIf { it.isNotBlank() },
                        externalDownloadUrl = null // metadata-only import
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse Wyvern card", e)
                    null
                }
            }

            Result.Success(CharaVaultSearchResult(characters, totalCount, page, totalPages, limit))
        } catch (e: Exception) {
            Log.e(TAG, "Wyvern search error", e)
            Result.Error(e)
        }
    }

    // ── Pygmalion ─────────────────────────────────────────────────────────────

    private suspend fun searchPygmalion(
        query: String?,
        nsfwFilter: CharaVaultNsfwFilter,
        tags: List<String>?,
        page: Int,
        limit: Int
    ): Result<CharaVaultSearchResult> = withContext(Dispatchers.IO) {
        try {
            val includeSensitive = nsfwFilter != CharaVaultNsfwFilter.SFW_ONLY
            val body = buildJsonObject {
                put("orderBy", "approved_at")
                put("orderDescending", true)
                put("includeSensitive", includeSensitive)
                put("pageSize", limit)
                put("pageNumber", page - 1)
                put("query", query ?: "")
                putJsonArray("tagsNamesInclude") {
                    tags?.forEach { add(it) }
                }
                putJsonArray("tagsNamesExclude") {}
            }.toString()

            val request = Request.Builder()
                .url("https://server.pygmalion.chat/galatea.v1.PublicCharacterService/CharacterSearch")
                .post(body.toRequestBody("application/json".toMediaType()))
                .header("Accept", "application/json")
                .header("Connect-Protocol-Version", "1")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.Error(Exception("Pygmalion search failed: ${response.code}"))
            }

            val bodyStr = response.body?.string()
                ?: return@withContext Result.Error(Exception("Empty Pygmalion response"))

            val root = json.parseToJsonElement(bodyStr).jsonObject
            val result = root["result"]?.jsonObject ?: root
            val charArray = result["characters"]?.jsonArray ?: JsonArray(emptyList())
            val totalCount = result["totalItems"]?.jsonPrimitive?.content?.toIntOrNull() ?: charArray.size
            val totalPages = ceil(totalCount.toDouble() / limit).toInt().coerceAtLeast(1)

            val characters = charArray.mapNotNull { el ->
                try {
                    val char = el.jsonObject
                    val id = char["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val name = char["displayName"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val avatarUrl = char["avatarUrl"]?.jsonPrimitive?.content ?: ""
                    val tagsArr = char["tags"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
                    val personality = char["personality"]?.jsonObject
                    val desc = personality?.get("persona")?.jsonPrimitive?.content ?: ""
                    val firstMes = personality?.get("greeting")?.jsonPrimitive?.content ?: ""
                    val creator = personality?.get("creator")?.jsonPrimitive?.content
                        ?: char["owner"]?.jsonObject?.get("displayName")?.jsonPrimitive?.content ?: ""
                    val safeName = name.replace(Regex("[^a-zA-Z0-9._-]"), "_")

                    CharaVaultCharacter(
                        file = "$safeName.png",
                        folder = "pygmalion",
                        name = name,
                        creator = creator,
                        tags = tagsArr,
                        nsfw = false,
                        descriptionPreview = desc.take(200),
                        firstMesPreview = firstMes.take(200),
                        fullDescription = desc.takeIf { it.isNotBlank() },
                        fullFirstMes = firstMes.takeIf { it.isNotBlank() },
                        externalImageUrl = avatarUrl.takeIf { it.isNotBlank() },
                        // JSON V2 export URL; importCard() will parse it and embed in PNG
                        externalDownloadUrl = "https://server.pygmalion.chat/api/export/character/$id/v2"
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse Pygmalion card", e)
                    null
                }
            }

            // If NSFW_ONLY requested, filter client-side (Pygmalion has no NSFW-only filter)
            val filtered = if (nsfwFilter == CharaVaultNsfwFilter.NSFW_ONLY) {
                characters.filter { it.nsfw }
            } else characters

            Result.Success(CharaVaultSearchResult(filtered, totalCount, page, totalPages, limit))
        } catch (e: Exception) {
            Log.e(TAG, "Pygmalion search error", e)
            Result.Error(e)
        }
    }

    // ── SpicyChat (Typesense) ─────────────────────────────────────────────────

    private suspend fun searchSpicyChat(
        query: String?,
        nsfwFilter: CharaVaultNsfwFilter,
        page: Int,
        limit: Int
    ): Result<CharaVaultSearchResult> = withContext(Dispatchers.IO) {
        try {
            val filterParts = mutableListOf("type:!=META", "visibility:=public")
            when (nsfwFilter) {
                CharaVaultNsfwFilter.SFW_ONLY -> filterParts.add("is_nsfw:=false")
                CharaVaultNsfwFilter.NSFW_ONLY -> filterParts.add("is_nsfw:=true")
                CharaVaultNsfwFilter.ALL -> { }
            }

            val urlBuilder = ("$SPICY_TYPESENSE_HOST/collections/public_characters_alias/documents/search")
                .toHttpUrl().newBuilder()
                .addQueryParameter("q", if (query.isNullOrBlank()) "*" else query)
                .addQueryParameter("query_by", "name,title,creator_username")
                .addQueryParameter("sort_by", "num_messages:desc")
                .addQueryParameter("per_page", limit.toString())
                .addQueryParameter("page", page.toString())
                .addQueryParameter("filter_by", filterParts.joinToString("&&"))
                .addQueryParameter("use_cache", "true")

            val request = Request.Builder()
                .url(urlBuilder.build())
                .get()
                .header("X-TYPESENSE-API-KEY", SPICY_TYPESENSE_KEY)
                .header("Accept", "application/json")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.Error(Exception("SpicyChat search failed: ${response.code}"))
            }

            val bodyStr = response.body?.string()
                ?: return@withContext Result.Error(Exception("Empty SpicyChat response"))

            val root = json.parseToJsonElement(bodyStr).jsonObject
            val hits = root["hits"]?.jsonArray ?: JsonArray(emptyList())
            val totalCount = root["found"]?.jsonPrimitive?.intOrNull ?: hits.size
            val totalPages = ceil(totalCount.toDouble() / limit).toInt().coerceAtLeast(1)

            val characters = hits.mapNotNull { hitEl ->
                try {
                    val doc = hitEl.jsonObject["document"]?.jsonObject ?: hitEl.jsonObject
                    val id = doc["character_id"]?.jsonPrimitive?.content
                        ?: doc["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val name = doc["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val avatarPath = doc["avatar_url"]?.jsonPrimitive?.content ?: ""
                    val avatarUrl = when {
                        avatarPath.startsWith("http") -> avatarPath
                        avatarPath.isNotBlank() -> "$SPICY_CDN_BASE/$avatarPath?class=avatar256x256"
                        else -> ""
                    }
                    val title = doc["title"]?.jsonPrimitive?.content ?: ""
                    val tagsArr = doc["tags"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
                    val isNsfw = doc["is_nsfw"]?.jsonPrimitive?.content?.toBooleanStrictOrNull()
                        ?: doc["avatar_is_nsfw"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
                    val creator = doc["creator_username"]?.jsonPrimitive?.content ?: ""
                    val firstMes = doc["greeting"]?.jsonPrimitive?.content ?: ""
                    val safeName = name.replace(Regex("[^a-zA-Z0-9._-]"), "_")

                    CharaVaultCharacter(
                        file = "$safeName.png",
                        folder = "spicychat",
                        name = name,
                        creator = creator,
                        tags = tagsArr,
                        nsfw = isNsfw,
                        descriptionPreview = title.take(200),
                        firstMesPreview = firstMes.take(200),
                        fullFirstMes = firstMes.takeIf { it.isNotBlank() },
                        externalImageUrl = avatarUrl.takeIf { it.isNotBlank() },
                        externalDownloadUrl = null
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse SpicyChat card", e)
                    null
                }
            }

            Result.Success(CharaVaultSearchResult(characters, totalCount, page, totalPages, limit))
        } catch (e: Exception) {
            Log.e(TAG, "SpicyChat search error", e)
            Result.Error(e)
        }
    }

    // ── Harpy (Supabase PostgREST) ────────────────────────────────────────────

    private suspend fun searchHarpy(
        query: String?,
        nsfwFilter: CharaVaultNsfwFilter,
        tags: List<String>?,
        page: Int,
        limit: Int
    ): Result<CharaVaultSearchResult> = withContext(Dispatchers.IO) {
        try {
            val offset = (page - 1) * limit

            val urlBuilder = ("$HARPY_SUPABASE_URL/rest/v1/hub_characters_with_likes").toHttpUrl().newBuilder()
                .addQueryParameter("select",
                    "id,name,title,tags,token_count,creator,created_at,is_public,is_nsfw,is_nsfw_image," +
                    "is_draft,owner_id,summary,like_count,chat_count,total_interactions," +
                    "icon_asset:hub_assets!icon_asset_id(file_path)," +
                    "owner_profile:astrsk_users!owner_id(name)")
                .addQueryParameter("is_public", "eq.true")
                .addQueryParameter("is_draft", "eq.false")
                .addQueryParameter("owner_id", "not.is.null")
                .addQueryParameter("order", "total_interactions.desc.nullslast")
                .addQueryParameter("offset", offset.toString())
                .addQueryParameter("limit", limit.toString())

            when (nsfwFilter) {
                CharaVaultNsfwFilter.SFW_ONLY -> {
                    urlBuilder.addQueryParameter("is_nsfw_image", "eq.false")
                    urlBuilder.addQueryParameter("is_nsfw", "eq.false")
                }
                CharaVaultNsfwFilter.NSFW_ONLY -> {
                    urlBuilder.addQueryParameter("is_nsfw", "eq.true")
                }
                CharaVaultNsfwFilter.ALL -> { }
            }

            if (!query.isNullOrBlank()) {
                val q = query.trim()
                urlBuilder.addQueryParameter("or", "(search_text.ilike.%${q}%,creator.ilike.%${q}%,tags_search.ilike.%${q}%)")
            }

            if (!tags.isNullOrEmpty()) {
                urlBuilder.addQueryParameter("tags", "cs.{${tags.joinToString(",")}}")
            }

            val request = Request.Builder()
                .url(urlBuilder.build())
                .get()
                .header("apikey", HARPY_ANON_KEY)
                .header("Authorization", "Bearer $HARPY_ANON_KEY")
                .header("Accept", "application/json")
                .header("Prefer", "count=exact")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.Error(Exception("Harpy search failed: ${response.code}"))
            }

            // Total count is in Content-Range header: "0-47/1234"
            val contentRange = response.header("Content-Range") ?: ""
            val totalCount = contentRange.substringAfter("/").toIntOrNull() ?: limit
            val totalPages = ceil(totalCount.toDouble() / limit).toInt().coerceAtLeast(1)

            val bodyStr = response.body?.string()
                ?: return@withContext Result.Error(Exception("Empty Harpy response"))

            val arr = json.parseToJsonElement(bodyStr).jsonArray

            val characters = arr.mapNotNull { el ->
                try {
                    val card = el.jsonObject
                    val id = card["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val name = card["title"]?.jsonPrimitive?.content
                        ?: card["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val iconAsset = card["icon_asset"]?.jsonObject
                    val filePath = iconAsset?.get("file_path")?.jsonPrimitive?.content ?: ""
                    val avatarUrl = if (filePath.isNotBlank()) "$HARPY_STORAGE_BASE/$filePath" else ""
                    val tagsArr = card["tags"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
                    val isNsfw = card["is_nsfw"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
                    val ownerProfile = card["owner_profile"]?.jsonObject
                    val creatorName = ownerProfile?.get("name")?.jsonPrimitive?.content
                        ?: card["creator"]?.jsonPrimitive?.content ?: ""
                    val summary = proseMirrorToText(card["summary"])
                    val safeName = name.replace(Regex("[^a-zA-Z0-9._-]"), "_")

                    CharaVaultCharacter(
                        file = "$safeName.png",
                        folder = "harpy",
                        name = name,
                        creator = creatorName,
                        tags = tagsArr,
                        nsfw = isNsfw,
                        descriptionPreview = summary.take(200),
                        firstMesPreview = "",
                        externalImageUrl = avatarUrl.takeIf { it.isNotBlank() },
                        externalDownloadUrl = null
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse Harpy card", e)
                    null
                }
            }

            Result.Success(CharaVaultSearchResult(characters, totalCount, page, totalPages, limit))
        } catch (e: Exception) {
            Log.e(TAG, "Harpy search error", e)
            Result.Error(e)
        }
    }

    /** Extract plain text from a ProseMirror JSON document (used by Harpy). */
    private fun proseMirrorToText(element: JsonElement?): String {
        if (element == null || element is JsonNull) return ""
        return try {
            val obj = element.jsonObject
            val sb = StringBuilder()
            fun extract(node: JsonObject) {
                val text = node["text"]?.jsonPrimitive?.content
                if (text != null) sb.append(text)
                node["content"]?.jsonArray?.forEach { child ->
                    if (child is JsonObject) extract(child)
                }
            }
            extract(obj)
            sb.toString().trim()
        } catch (e: Exception) {
            ""
        }
    }

    // ── JannyAI (MeiliSearch) ─────────────────────────────────────────────────

    private suspend fun searchJannyAI(
        query: String?,
        nsfwFilter: CharaVaultNsfwFilter,
        page: Int,
        limit: Int
    ): Result<CharaVaultSearchResult> = withContext(Dispatchers.IO) {
        try {
            val filters = mutableListOf<String>()
            filters.add("totalToken <= 65536 AND totalToken >= 10")
            if (nsfwFilter == CharaVaultNsfwFilter.SFW_ONLY) filters.add("isNsfw = false")
            if (nsfwFilter == CharaVaultNsfwFilter.NSFW_ONLY) filters.add("isNsfw = true")

            val queryObj = buildJsonObject {
                put("indexUid", "janny-characters")
                put("q", query ?: "")
                put("hitsPerPage", limit)
                put("page", page)
                put("sort", buildJsonArray { add("createdAtStamp:desc") })
                putJsonArray("filter") { filters.forEach { add(it) } }
            }

            val body = buildJsonObject {
                putJsonArray("queries") { add(queryObj) }
            }.toString()

            val request = Request.Builder()
                .url(JANNY_SEARCH_URL)
                .post(body.toRequestBody("application/json".toMediaType()))
                .header("Authorization", "Bearer $JANNY_SEARCH_TOKEN")
                .header("Accept", "application/json")
                .header("Origin", "https://jannyai.com")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.Error(Exception("JannyAI search failed: ${response.code}"))
            }

            val bodyStr = response.body?.string()
                ?: return@withContext Result.Error(Exception("Empty JannyAI response"))

            val root = json.parseToJsonElement(bodyStr).jsonObject
            val results = root["results"]?.jsonArray ?: JsonArray(emptyList())
            val firstResult = results.firstOrNull()?.jsonObject ?: JsonObject(emptyMap())
            val hits = firstResult["hits"]?.jsonArray ?: JsonArray(emptyList())
            val totalCount = firstResult["estimatedTotalHits"]?.jsonPrimitive?.intOrNull
                ?: firstResult["totalHits"]?.jsonPrimitive?.intOrNull ?: hits.size
            val totalPages = firstResult["totalPages"]?.jsonPrimitive?.intOrNull
                ?: ceil(totalCount.toDouble() / limit).toInt().coerceAtLeast(1)

            val characters = hits.mapNotNull { hitEl ->
                try {
                    val hit = hitEl.jsonObject
                    val id = hit["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val name = hit["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val avatarFile = hit["avatar"]?.jsonPrimitive?.content ?: ""
                    val avatarUrl = if (avatarFile.isNotBlank()) "$JANNY_IMAGE_BASE/$avatarFile" else ""
                    val descHtml = hit["description"]?.jsonPrimitive?.content ?: ""
                    val desc = descHtml.replace(Regex("<[^>]+>"), "").trim()
                    val tagsArr = hit["tags"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
                    val isNsfw = hit["isNsfw"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
                    val creator = hit["creatorUsername"]?.jsonPrimitive?.content
                        ?: hit["creatorName"]?.jsonPrimitive?.content ?: ""
                    val safeName = name.replace(Regex("[^a-zA-Z0-9._-]"), "_")

                    CharaVaultCharacter(
                        file = "$safeName.png",
                        folder = "jannyai",
                        name = name,
                        creator = creator,
                        tags = tagsArr,
                        nsfw = isNsfw,
                        descriptionPreview = desc.take(200),
                        firstMesPreview = "",
                        fullDescription = desc.takeIf { it.isNotBlank() },
                        externalImageUrl = avatarUrl.takeIf { it.isNotBlank() },
                        externalDownloadUrl = "https://jannyai.com/api/characters/$id"
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse JannyAI card", e)
                    null
                }
            }

            Result.Success(CharaVaultSearchResult(characters, totalCount, page, totalPages, limit))
        } catch (e: Exception) {
            Log.e(TAG, "JannyAI search error", e)
            Result.Error(e)
        }
    }

    // ── Sakura.fm ─────────────────────────────────────────────────────────────

    private suspend fun searchSakura(
        query: String?,
        nsfwFilter: CharaVaultNsfwFilter,
        page: Int,
        limit: Int
    ): Result<CharaVaultSearchResult> = withContext(Dispatchers.IO) {
        try {
            val offset = (page - 1) * limit
            val allowNsfw = nsfwFilter != CharaVaultNsfwFilter.SFW_ONLY

            val body = buildJsonObject {
                put("offset", offset)
                put("search", query ?: "")
                put("allowNsfw", allowNsfw)
                put("sortType", "message-count")
                put("limit", limit)
                put("creatorId", "")
                put("matchType", "any")
                put("favoritesOnly", false)
                put("followingOnly", false)
                put("blockedOnly", false)
                put("eraseNsfw", nsfwFilter == CharaVaultNsfwFilter.SFW_ONLY)
                putJsonArray("tags") {}
                put("hideExplicit", nsfwFilter == CharaVaultNsfwFilter.SFW_ONLY)
            }.toString()

            val request = Request.Builder()
                .url("https://api.sakura.fm/api/get-characters")
                .post(body.toRequestBody("application/json".toMediaType()))
                .header("Accept", "application/json")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.Error(Exception("Sakura search failed: ${response.code}"))
            }

            val bodyStr = response.body?.string()
                ?: return@withContext Result.Error(Exception("Empty Sakura response"))

            val root = json.parseToJsonElement(bodyStr).jsonObject
            val data = root["data"]?.jsonObject ?: root
            val charArray = data["characters"]?.jsonArray ?: JsonArray(emptyList())
            val hasMore = data["hasMore"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
            val inferredTotal = if (hasMore) offset + charArray.size + 1
                                else offset + charArray.size
            val totalPages = if (hasMore) page + 1 else page.coerceAtLeast(1)

            val characters = charArray.mapNotNull { el ->
                try {
                    val card = el.jsonObject
                    val id = card["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val name = card["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val avatarUrl = card["imageUri"]?.jsonPrimitive?.content
                        ?: card["image"]?.jsonPrimitive?.content ?: ""
                    val desc = card["description"]?.jsonPrimitive?.content ?: ""
                    val firstMes = card["firstMessage"]?.jsonPrimitive?.content
                        ?: card["greeting"]?.jsonPrimitive?.content ?: ""
                    val tagsArr = card["tags"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
                    val isNsfw = card["nsfw"]?.jsonPrimitive?.content?.toBooleanStrictOrNull()
                        ?: card["explicitText"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
                    val creator = card["creatorUsername"]?.jsonPrimitive?.content
                        ?: card["creatorId"]?.jsonPrimitive?.content ?: ""
                    val safeName = name.replace(Regex("[^a-zA-Z0-9._-]"), "_")

                    CharaVaultCharacter(
                        file = "$safeName.png",
                        folder = "sakura",
                        name = name,
                        creator = creator,
                        tags = tagsArr,
                        nsfw = isNsfw,
                        descriptionPreview = desc.take(200),
                        firstMesPreview = firstMes.take(200),
                        fullDescription = desc.takeIf { it.isNotBlank() },
                        fullFirstMes = firstMes.takeIf { it.isNotBlank() },
                        externalImageUrl = avatarUrl.takeIf { it.isNotBlank() },
                        externalDownloadUrl = null
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse Sakura card", e)
                    null
                }
            }

            // Filter NSFW-only client-side if needed
            val filtered = if (nsfwFilter == CharaVaultNsfwFilter.NSFW_ONLY) characters.filter { it.nsfw }
                           else characters

            Result.Success(CharaVaultSearchResult(filtered, inferredTotal, page, totalPages, limit))
        } catch (e: Exception) {
            Log.e(TAG, "Sakura search error", e)
            Result.Error(e)
        }
    }

    // ── RisuRealm ─────────────────────────────────────────────────────────────

    private suspend fun searchRisuRealm(
        query: String?,
        nsfwFilter: CharaVaultNsfwFilter,
        page: Int,
        limit: Int
    ): Result<CharaVaultSearchResult> = withContext(Dispatchers.IO) {
        try {
            val urlBuilder = "https://realm.risuai.net/__data.json".toHttpUrl().newBuilder()
                .addQueryParameter("sort", "download")
                .addQueryParameter("mode", "character")
                .addQueryParameter("page", (page - 1).toString())
                .addQueryParameter("x-sveltekit-trailing-slash", "1")
                .addQueryParameter("x-sveltekit-invalidated", "01")

            if (!query.isNullOrBlank()) {
                urlBuilder.addQueryParameter("q", query)
            }
            if (nsfwFilter == CharaVaultNsfwFilter.SFW_ONLY) {
                urlBuilder.addQueryParameter("nsfw", "false")
            }

            val request = Request.Builder()
                .url(urlBuilder.build())
                .get()
                .header("Accept", "application/json")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.Error(Exception("RisuRealm search failed: ${response.code}"))
            }

            val bodyStr = response.body?.string()
                ?: return@withContext Result.Error(Exception("Empty RisuRealm response"))

            val root = json.parseToJsonElement(bodyStr).jsonObject
            val nodes = root["nodes"]?.jsonArray ?: JsonArray(emptyList())

            // Scan each node's devalue flat array for card-like objects
            var risuCards = emptyList<JsonObject>()
            for (nodeEl in nodes) {
                val nodeData = nodeEl.jsonObject["data"]?.jsonArray ?: continue
                val found = scanDevalueForRisuCards(nodeData)
                if (found.isNotEmpty()) {
                    risuCards = found
                    break
                }
            }

            if (risuCards.isEmpty()) {
                return@withContext Result.Error(Exception("Could not parse RisuRealm response"))
            }

            val hasMore = risuCards.size >= 30
            val inferredTotal = if (hasMore) (page - 1) * 30 + risuCards.size + 1
                                else (page - 1) * 30 + risuCards.size
            val totalPages = if (hasMore) page + 1 else page.coerceAtLeast(1)

            val characters = risuCards.mapNotNull { card ->
                try {
                    val id = card["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val name = card["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val imgFile = card["img"]?.jsonPrimitive?.content ?: ""
                    val avatarUrl = if (imgFile.isNotBlank()) "$RISU_IMAGE_BASE/$imgFile" else ""
                    val desc = card["desc"]?.jsonPrimitive?.content ?: ""
                    val tagsArr = card["tags"]?.jsonArray?.mapNotNull { it.jsonPrimitive.content } ?: emptyList()
                    val isNsfw = card["hidden"]?.jsonPrimitive?.intOrNull == 1
                    val creator = card["authorname"]?.jsonPrimitive?.content ?: ""
                    val safeName = name.replace(Regex("[^a-zA-Z0-9._-]"), "_")

                    if (nsfwFilter == CharaVaultNsfwFilter.NSFW_ONLY && !isNsfw) return@mapNotNull null

                    CharaVaultCharacter(
                        file = "$safeName.png",
                        folder = "risurealm",
                        name = name,
                        creator = creator,
                        tags = tagsArr,
                        nsfw = isNsfw,
                        descriptionPreview = desc.take(200),
                        firstMesPreview = "",
                        externalImageUrl = avatarUrl.takeIf { it.isNotBlank() },
                        // JSON V3 export URL (V2-compatible format)
                        externalDownloadUrl = "https://realm.risuai.net/api/v1/download/json-v3/$id?non_commercial=true&cors=true&access_token=guest"
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse RisuRealm card", e)
                    null
                }
            }

            Result.Success(CharaVaultSearchResult(characters, inferredTotal, page, totalPages, limit))
        } catch (e: Exception) {
            Log.e(TAG, "RisuRealm search error", e)
            Result.Error(e)
        }
    }

    /**
     * Scan a SvelteKit devalue flat array for RisuRealm card objects.
     * Devalue format: [root_index, value_at_0, value_at_1, ..., value_at_N]
     * Objects in the array use integer field values as index references into the array.
     * We look for objects that have both "name", "img", and "id" keys — these are card entries.
     */
    private fun scanDevalueForRisuCards(outer: JsonArray): List<JsonObject> {
        val results = mutableListOf<JsonObject>()
        // outer[j] is the value stored at devalue index (j-1); skip outer[0] (root index)
        for (j in 1 until outer.size) {
            val el = outer[j]
            if (el !is JsonObject) continue
            // Require the keys we actually use when parsing cards
            if (!el.containsKey("name") || !el.containsKey("img") || !el.containsKey("id")) continue
            // Resolve integer field values as index references: index i → outer[i + 1]
            val resolved = buildJsonObject {
                el.entries.forEach { (k, v) ->
                    val i = (v as? JsonPrimitive)?.intOrNull
                    if (i != null && i + 1 < outer.size) {
                        put(k, outer[i + 1])
                    } else {
                        put(k, v)
                    }
                }
            }
            results.add(resolved)
        }
        return results
    }

    /**
     * Recursively scan a JSON element for the first array whose elements look like
     * companion/character objects (have id or companion_id, plus a name field).
     */
    private fun findFirstCompanionsArray(element: JsonElement): JsonArray? {
        when (element) {
            is JsonArray -> {
                if (element.size > 0) {
                    val first = element[0]
                    if (first is JsonObject && (first.containsKey("id") || first.containsKey("companion_id") ||
                        first.containsKey("display_name") || first.containsKey("name"))) {
                        return element
                    }
                }
                for (el in element) {
                    val found = findFirstCompanionsArray(el)
                    if (found != null) return found
                }
            }
            is JsonObject -> {
                for ((_, v) in element.entries) {
                    val found = findFirstCompanionsArray(v)
                    if (found != null) return found
                }
            }
            else -> {}
        }
        return null
    }

    // ── Saucepan ──────────────────────────────────────────────────────────────

    private suspend fun searchSaucepan(
        query: String?,
        nsfwFilter: CharaVaultNsfwFilter,
        page: Int,
        limit: Int
    ): Result<CharaVaultSearchResult> = withContext(Dispatchers.IO) {
        try {
            val offset = (page - 1) * limit
            val includeSus = nsfwFilter != CharaVaultNsfwFilter.SFW_ONLY

            val body = buildJsonObject {
                put("text_search", query ?: "")
                putJsonArray("tags") {}
                putJsonArray("excluded_tags") {}
                put("limit", limit)
                put("offset", offset)
                put("sus", includeSus)
                put("order_by", "popularity")
                put("asc", false)
                put("match_all_tags", true)
                put("hide_hidden_content", false)
            }.toString()

            val request = Request.Builder()
                .url("https://api.saucepan.ai/api/v1/search")
                .post(body.toRequestBody("application/json".toMediaType()))
                .header("Accept", "application/json")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.Error(Exception("Saucepan search failed: ${response.code}"))
            }

            val bodyStr = response.body?.string()
                ?: return@withContext Result.Error(Exception("Empty Saucepan response"))

            val root = json.parseToJsonElement(bodyStr).jsonObject
            Log.d(TAG, "Saucepan response keys: ${root.keys}, body[0..300]: ${bodyStr.take(300)}")
            // API returns either {data: {companions: [...], total_count: N}} or {data: [...], total_count: N}
            val dataObj = root["data"]?.jsonObject
            val companions = dataObj?.get("companions")?.jsonArray
                ?: root["data"]?.jsonArray        // data IS the companions array
                ?: root["companions"]?.jsonArray
                ?: root["results"]?.jsonArray
                ?: findFirstCompanionsArray(root) // recursive fallback
                ?: JsonArray(emptyList())
            val totalCount = dataObj?.get("total_count")?.jsonPrimitive?.intOrNull
                ?: root["total_count"]?.jsonPrimitive?.intOrNull
                ?: root["totalCount"]?.jsonPrimitive?.intOrNull
                ?: root["count"]?.jsonPrimitive?.intOrNull
                ?: companions.size
            val totalPages = ceil(totalCount.toDouble() / limit).toInt().coerceAtLeast(1)

            val characters = companions.mapNotNull { el ->
                try {
                    val card = el.jsonObject
                    val id = card["id"]?.jsonPrimitive?.content
                        ?: card["companion_id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val name = card["display_name"]?.jsonPrimitive?.content
                        ?: card["name"]?.jsonPrimitive?.content
                        ?: card["title"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val imageId = card["image"]?.jsonPrimitive?.content
                        ?: card["image_id"]?.jsonPrimitive?.content ?: ""
                    val avatarUrl = if (imageId.isNotBlank()) "https://cdn.saucepan.ai/images/$imageId/card" else ""
                    val desc = card["short_description"]?.jsonPrimitive?.content ?: ""
                    val tagsArr = card["tags"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
                    val isNsfw = card["sus"]?.jsonPrimitive?.content?.toBooleanStrictOrNull()
                        ?: card["very_sus"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
                    val creator = card["author_handle"]?.jsonPrimitive?.content ?: ""
                    val safeName = name.replace(Regex("[^a-zA-Z0-9._-]"), "_")

                    if (nsfwFilter == CharaVaultNsfwFilter.NSFW_ONLY && !isNsfw) return@mapNotNull null

                    CharaVaultCharacter(
                        file = "$safeName.png",
                        folder = "saucepan",
                        name = name,
                        creator = creator,
                        tags = tagsArr,
                        nsfw = isNsfw,
                        descriptionPreview = desc.take(200),
                        firstMesPreview = "",
                        externalImageUrl = avatarUrl.takeIf { it.isNotBlank() },
                        externalDownloadUrl = null
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse Saucepan card", e)
                    null
                }
            }

            Result.Success(CharaVaultSearchResult(characters, totalCount, page, totalPages, limit))
        } catch (e: Exception) {
            Log.e(TAG, "Saucepan search error", e)
            Result.Error(e)
        }
    }

    // ── External card import ──────────────────────────────────────────────────

    suspend fun importCard(character: CharaVaultCharacter): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val safeFilename = character.file.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val downloadUrl = character.externalDownloadUrl

            if (downloadUrl != null) {
                Log.d(TAG, "Downloading card for ${character.name} from $downloadUrl")
                val request = Request.Builder()
                    .url(downloadUrl)
                    .get()
                    .header("Accept", "*/*")
                    .build()

                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    Log.w(TAG, "Download failed (${response.code}) for ${character.name}, falling back to metadata")
                    return@withContext importFromMetadata(character, safeFilename)
                }

                val bytes = response.body?.bytes()
                if (bytes == null || bytes.isEmpty()) {
                    Log.w(TAG, "Empty download for ${character.name}, falling back to metadata")
                    return@withContext importFromMetadata(character, safeFilename)
                }

                // Check if JSON (first byte is '{') or PNG
                if (bytes[0] == '{'.code.toByte() || bytes[0] == '['.code.toByte()) {
                    // JSON export format — parse as V2 and reconstruct PNG
                    importFromJsonBytes(bytes, character, safeFilename)
                } else {
                    // PNG — check for embedded character data; embed from metadata if absent
                    val pngBytes = ensurePng(bytes)
                    val hasEmbedded = PngCharacterCard.extractCharacterData(pngBytes) != null
                    if (hasEmbedded) {
                        Log.d(TAG, "Saving PNG card with embedded data (${pngBytes.size} bytes) for ${character.name}")
                        characterStorage.saveRawPng(pngBytes, safeFilename)
                    } else {
                        Log.d(TAG, "PNG has no embedded data, embedding from metadata for ${character.name}")
                        val cardData = buildCardV2FromCharacter(character)
                        val pngWithCard = PngCharacterCard.embedCharacterData(pngBytes, cardData)
                        characterStorage.saveRawPng(pngWithCard, safeFilename)
                    }
                    Result.Success(Unit)
                }
            } else {
                // No dedicated download URL — reconstruct PNG from metadata + avatar
                importFromMetadata(character, safeFilename)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Import error for ${character.name}", e)
            Result.Error(e)
        }
    }

    /** Import from a V2 JSON export file: parse card data, download avatar, embed, save. */
    private suspend fun importFromJsonBytes(
        jsonBytes: ByteArray,
        character: CharaVaultCharacter,
        safeFilename: String
    ): Result<Unit> {
        return try {
            val jsonStr = String(jsonBytes, Charsets.UTF_8).trimStart('\uFEFF') // strip BOM if present
            val cardData = parseJsonToCardV2(jsonStr) ?: buildCardV2FromCharacter(character)

            val avatarBytes = downloadAvatarOrPlaceholder(character.externalImageUrl)
            val pngWithCard = PngCharacterCard.embedCharacterData(avatarBytes, cardData)
            characterStorage.saveRawPng(pngWithCard, safeFilename)
            Log.d(TAG, "Imported JSON card for ${character.name}")
            Result.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "JSON import failed for ${character.name}", e)
            Result.Error(e)
        }
    }

    /**
     * Try several JSON formats: SillyTavern V2, V1 (flat CharacterCardData),
     * and Pygmalion-native format.
     */
    private fun parseJsonToCardV2(jsonStr: String): CharacterCardV2? {
        // 1. Standard SillyTavern V2: {"spec":"chara_card_v2","data":{...}}
        try {
            return jsonStrict.decodeFromString<CharacterCardV2>(jsonStr)
        } catch (_: Exception) {}

        // 2. SillyTavern V1 / flat format: {"name":"...","description":"...",...}
        try {
            val v1 = jsonStrict.decodeFromString<CharacterCardData>(jsonStr)
            return CharacterCardV2(data = v1)
        } catch (_: Exception) {}

        // 3. JannyAI character API format: {"id":"...","name":"...","description":"...","greeting":"...",...}
        try {
            val root = json.parseToJsonElement(jsonStr).jsonObject
            if (root.containsKey("greeting") || root.containsKey("firstMes")) {
                val char = root["character"]?.jsonObject ?: root
                val name = char["name"]?.jsonPrimitive?.content ?: ""
                if (name.isNotBlank()) {
                    val desc = char["description"]?.jsonPrimitive?.content
                        ?: char["persona"]?.jsonPrimitive?.content ?: ""
                    val firstMes = char["greeting"]?.jsonPrimitive?.content
                        ?: char["firstMes"]?.jsonPrimitive?.content
                        ?: char["first_mes"]?.jsonPrimitive?.content ?: ""
                    val creator = char["creatorUsername"]?.jsonPrimitive?.content
                        ?: char["creatorName"]?.jsonPrimitive?.content ?: ""
                    val tags = char["tags"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
                    return CharacterCardV2(data = CharacterCardData(
                        name = name,
                        description = desc,
                        firstMes = firstMes,
                        creator = creator,
                        tags = tags
                    ))
                }
            }
        } catch (_: Exception) {}

        // 4. Pygmalion-native export format
        try {
            val root = json.parseToJsonElement(jsonStr).jsonObject
            // Pygmalion wraps character data under various keys
            val char = root["character"]?.jsonObject
                ?: root["data"]?.jsonObject
                ?: root
            val personality = char["personality"]?.jsonObject
            val name = char["displayName"]?.jsonPrimitive?.content
                ?: char["name"]?.jsonPrimitive?.content ?: return null
            val desc = personality?.get("persona")?.jsonPrimitive?.content
                ?: char["description"]?.jsonPrimitive?.content ?: ""
            val firstMes = personality?.get("greeting")?.jsonPrimitive?.content
                ?: char["first_mes"]?.jsonPrimitive?.content
                ?: char["greeting"]?.jsonPrimitive?.content ?: ""
            val creatorNotes = personality?.get("characterNotes")?.jsonPrimitive?.content ?: ""
            val creator = personality?.get("creator")?.jsonPrimitive?.content
                ?: char["owner"]?.jsonObject?.get("displayName")?.jsonPrimitive?.content ?: ""
            val tags = char["tags"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
            return CharacterCardV2(data = CharacterCardData(
                name = name,
                description = desc,
                firstMes = firstMes,
                creatorNotes = creatorNotes,
                creator = creator,
                tags = tags
            ))
        } catch (_: Exception) {}

        return null
    }

    /** Import from character metadata only: build V2 card, embed in avatar PNG, save. */
    private suspend fun importFromMetadata(
        character: CharaVaultCharacter,
        safeFilename: String
    ): Result<Unit> {
        return try {
            val cardData = buildCardV2FromCharacter(character)
            val avatarBytes = downloadAvatarOrPlaceholder(character.externalImageUrl)
            val pngWithCard = PngCharacterCard.embedCharacterData(avatarBytes, cardData)
            characterStorage.saveRawPng(pngWithCard, safeFilename)
            Log.d(TAG, "Imported metadata card for ${character.name}")
            Result.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Metadata import failed for ${character.name}", e)
            Result.Error(e)
        }
    }

    private fun buildCardV2FromCharacter(character: CharaVaultCharacter) = CharacterCardV2(
        data = CharacterCardData(
            name = character.name,
            description = character.fullDescription ?: character.descriptionPreview,
            personality = character.personality ?: "",
            scenario = character.scenario ?: "",
            firstMes = character.fullFirstMes ?: character.firstMesPreview,
            tags = character.tags,
            creator = character.creator
        )
    )

    /** Download avatar bytes and ensure they are valid PNG, or return a placeholder. */
    private fun downloadAvatarOrPlaceholder(avatarUrl: String?): ByteArray {
        if (!avatarUrl.isNullOrBlank()) {
            try {
                val request = Request.Builder()
                    .url(avatarUrl)
                    .get()
                    .header("Accept", "image/*")
                    .build()
                val response = okHttpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val bytes = response.body?.bytes()
                    if (bytes != null && bytes.size > 8) return ensurePng(bytes)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Avatar download failed for $avatarUrl", e)
            }
        }
        return createPlaceholderPng()
    }

    /**
     * If bytes are already a valid PNG, return as-is.
     * Otherwise decode via BitmapFactory (handles JPEG, WebP, etc.) and re-encode as PNG.
     * embedCharacterData() requires valid PNG input; non-PNG formats cause out-of-bounds reads.
     */
    private fun ensurePng(bytes: ByteArray): ByteArray {
        if (bytes.size >= 4 &&
            bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()
        ) {
            return bytes // already PNG
        }
        return try {
            val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: return createPlaceholderPng()
            ByteArrayOutputStream().also { bos ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, bos)
                bitmap.recycle()
            }.toByteArray()
        } catch (e: Exception) {
            Log.w(TAG, "Image re-encode to PNG failed", e)
            createPlaceholderPng()
        }
    }

    private fun createPlaceholderPng(): ByteArray {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        return ByteArrayOutputStream().also { bos ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, bos)
            bitmap.recycle()
        }.toByteArray()
    }
}
