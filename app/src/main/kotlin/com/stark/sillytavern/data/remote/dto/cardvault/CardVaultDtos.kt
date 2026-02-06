package com.stark.sillytavern.data.remote.dto.cardvault

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Response from /api/cards search endpoint.
 */
@Serializable
data class CardVaultSearchResponse(
    val total: Int,
    val limit: Int,
    val offset: Int,
    val results: List<CardVaultCardDto>
)

/**
 * Individual card entry from search results.
 */
@Serializable
data class CardVaultCardDto(
    val file: String = "",
    val path: String = "",
    val folder: String = "",
    val name: String = "",
    val creator: String = "",
    val tags: List<String> = emptyList(),
    val nsfw: Boolean = false,
    @SerialName("description_preview")
    val descriptionPreview: String = "",
    @SerialName("first_mes_preview")
    val firstMesPreview: String = "",
    @SerialName("indexed_at")
    val indexedAt: String = "",
    @SerialName("content_hash")
    val contentHash: String = ""
)

/**
 * Response from /api/cards/{folder}/{filename} detail endpoint.
 */
@Serializable
data class CardVaultDetailResponse(
    val entry: CardVaultCardDto,
    @SerialName("full_metadata")
    val fullMetadata: CardVaultFullMetadata? = null
)

/**
 * Full character card metadata (V2 spec).
 */
@Serializable
data class CardVaultFullMetadata(
    val spec: String? = null,
    @SerialName("spec_version")
    val specVersion: String? = null,
    val data: CardVaultCharacterData? = null
)

/**
 * Character data from the full metadata.
 */
@Serializable
data class CardVaultCharacterData(
    val name: String = "",
    val description: String = "",
    val personality: String = "",
    @SerialName("first_mes")
    val firstMes: String = "",
    @SerialName("mes_example")
    val mesExample: String = "",
    val scenario: String = "",
    @SerialName("creator_notes")
    val creatorNotes: String = "",
    @SerialName("system_prompt")
    val systemPrompt: String = "",
    @SerialName("post_history_instructions")
    val postHistoryInstructions: String = "",
    val tags: List<String> = emptyList(),
    val creator: String = "",
    @SerialName("character_version")
    val characterVersion: String = "",
    @SerialName("alternate_greetings")
    val alternateGreetings: List<String> = emptyList()
)

/**
 * Response from /api/stats endpoint.
 */
@Serializable
data class CardVaultStatsResponse(
    @SerialName("total_cards")
    val totalCards: Int,
    @SerialName("nsfw_count")
    val nsfwCount: Int,
    @SerialName("sfw_count")
    val sfwCount: Int,
    @SerialName("unique_creators")
    val uniqueCreators: Int,
    @SerialName("unique_tags")
    val uniqueTags: Int,
    @SerialName("top_tags")
    val topTags: List<List<@Serializable(with = TagCountSerializer::class) Any>> = emptyList(),
    @SerialName("top_creators")
    val topCreators: List<List<@Serializable(with = TagCountSerializer::class) Any>> = emptyList(),
    val folders: Map<String, Int> = emptyMap()
)

/**
 * Response from /api/tags endpoint.
 */
@Serializable
data class CardVaultTagsResponse(
    val tags: List<List<@Serializable(with = TagCountSerializer::class) Any>> = emptyList()
)

/**
 * Response from /api/cards/upload endpoint.
 */
@Serializable
data class CardVaultUploadResponse(
    val success: Boolean = false,
    val path: String = "",
    val folder: String = "",
    val file: String = "",
    val name: String = "",
    val indexed: Boolean = false,
    val detail: String? = null
)

/**
 * Custom serializer for boolean values that may come as 0/1 integers.
 */
object IntBooleanSerializer : kotlinx.serialization.KSerializer<Boolean> {
    override val descriptor = kotlinx.serialization.descriptors.PrimitiveSerialDescriptor(
        "IntBoolean",
        kotlinx.serialization.descriptors.PrimitiveKind.BOOLEAN
    )

    override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: Boolean) {
        encoder.encodeBoolean(value)
    }

    override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): Boolean {
        val jsonDecoder = decoder as? kotlinx.serialization.json.JsonDecoder
            ?: return decoder.decodeBoolean()

        return when (val element = jsonDecoder.decodeJsonElement()) {
            is kotlinx.serialization.json.JsonPrimitive -> {
                when {
                    element.isString -> element.content.equals("true", ignoreCase = true) || element.content == "1"
                    else -> element.intOrNull?.let { it != 0 } ?: element.booleanOrNull ?: false
                }
            }
            else -> false
        }
    }
}

/**
 * Custom serializer for tag/count pairs that come as [String, Int] arrays.
 */
object TagCountSerializer : kotlinx.serialization.KSerializer<Any> {
    override val descriptor = kotlinx.serialization.descriptors.buildClassSerialDescriptor("TagCount")

    override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: Any) {
        when (value) {
            is String -> encoder.encodeString(value)
            is Number -> encoder.encodeInt(value.toInt())
            else -> encoder.encodeString(value.toString())
        }
    }

    override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): Any {
        return try {
            decoder.decodeInt()
        } catch (e: Exception) {
            decoder.decodeString()
        }
    }
}

// ===== LOREBOOK DTOs =====

/**
 * Response from /api/lorebooks search endpoint.
 */
@Serializable
data class CardVaultLorebookSearchResponse(
    val count: Int,
    val lorebooks: List<CardVaultLorebookDto>
)

/**
 * Individual lorebook entry from search results.
 */
@Serializable
data class CardVaultLorebookDto(
    val id: Int = 0,
    @SerialName("file_path")
    val filePath: String = "",
    val file: String = "",
    val folder: String = "",
    val name: String = "",
    val creator: String = "",
    val description: String = "",
    val topics: List<String> = emptyList(),
    @SerialName("entry_count")
    val entryCount: Int = 0,
    @SerialName("token_count")
    val tokenCount: Int = 0,
    val keywords: String = "",
    @SerialName("star_count")
    val starCount: Int = 0,
    @SerialName("chub_id")
    val chubId: Int = 0,
    @Serializable(with = IntBooleanSerializer::class)
    val nsfw: Boolean = false,
    @SerialName("indexed_at")
    val indexedAt: String = "",
    @SerialName("content_hash")
    val contentHash: String = ""
)

/**
 * Full lorebook detail response with content.
 */
@Serializable
data class CardVaultLorebookDetailResponse(
    val id: Int = 0,
    @SerialName("file_path")
    val filePath: String = "",
    val file: String = "",
    val folder: String = "",
    val name: String = "",
    val creator: String = "",
    val description: String = "",
    val topics: List<String> = emptyList(),
    @SerialName("entry_count")
    val entryCount: Int = 0,
    @SerialName("token_count")
    val tokenCount: Int = 0,
    val keywords: String = "",
    @SerialName("star_count")
    val starCount: Int = 0,
    @Serializable(with = IntBooleanSerializer::class)
    val nsfw: Boolean = false,
    val content: LorebookContent? = null
)

/**
 * Lorebook content with entries.
 */
@Serializable
data class LorebookContent(
    val name: String = "",
    val description: String = "",
    val entries: Map<String, LorebookEntry> = emptyMap(),
    @SerialName("scan_depth")
    val scanDepth: Int = 2,
    @SerialName("token_budget")
    val tokenBudget: Int = 512,
    @SerialName("recursive_scanning")
    val recursiveScanning: Boolean = false
)

/**
 * Individual lorebook entry.
 */
@Serializable
data class LorebookEntry(
    val id: Int = 0,
    val name: String = "",
    val content: String = "",
    val keys: List<String> = emptyList(),
    val enabled: Boolean = true,
    @SerialName("case_sensitive")
    val caseSensitive: Boolean = false,
    val priority: Int = 10,
    val position: Int = 0,
    val depth: Int = 4,
    val probability: Int = 100
)

/**
 * Response from /api/lorebooks/stats endpoint.
 */
@Serializable
data class CardVaultLorebookStatsResponse(
    @SerialName("total_lorebooks")
    val totalLorebooks: Int = 0,
    @SerialName("nsfw_count")
    val nsfwCount: Int = 0,
    @SerialName("sfw_count")
    val sfwCount: Int = 0,
    @SerialName("creator_count")
    val creatorCount: Int = 0,
    @SerialName("total_entries")
    val totalEntries: Int = 0,
    @SerialName("configured_dirs")
    val configuredDirs: List<String> = emptyList()
)

/**
 * Response from /api/lorebooks/topics endpoint.
 */
@Serializable
data class CardVaultLorebookTopicsResponse(
    val topics: List<TopicCount> = emptyList()
)

@Serializable
data class TopicCount(
    val name: String,
    val count: Int
)
