package com.stark.sillytavern.data.remote.dto.chub

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ChubSearchResponse(
    val nodes: List<ChubNodeDto>? = null,
    val data: ChubDataWrapper? = null,
    val count: Int? = null,
    val total: Int? = null
)

@Serializable
data class ChubDataWrapper(
    val nodes: List<ChubNodeDto>? = null,
    val count: Int? = null
)

@Serializable
data class ChubNodeDto(
    val name: String? = null,
    @SerialName("fullPath")
    val fullPath: String? = null,
    val tagline: String? = null,
    val description: String? = null,
    @SerialName("avatar_url")
    val avatarUrl: String? = null,
    @SerialName("max_res_url")
    val maxResUrl: String? = null,
    @SerialName("nDownloads")
    val downloadCount: Int? = null,
    @SerialName("nLikes")
    val starCount: Int? = null,
    @SerialName("rating")
    val rating: Float? = null,
    @SerialName("ratingCount")
    val ratingCount: Int? = null,
    val topics: List<String>? = null,
    val definition: String? = null,  // JSON string containing first_mes
    val labels: JsonElement? = null,  // Can be array of strings or objects
    @SerialName("related_lorebooks")
    val relatedLorebooks: JsonElement? = null  // Can vary in format
)

@Serializable
data class ChubCharacterResponse(
    val node: ChubNodeDto? = null,
    val character: ChubNodeDto? = null,
    val data: ChubNodeDto? = null
)

@Serializable
data class ChubCardResponse(
    @SerialName("png_url")
    val pngUrl: String? = null,
    @SerialName("card_data")
    val cardData: String? = null,  // Base64 or JSON
    val success: Boolean? = null,
    val message: String? = null
)

@Serializable
data class ChubImportRequest(
    @SerialName("stUrl")
    val stUrl: String,
    @SerialName("cardV2")
    val cardV2: JsonElement,
    val username: String? = null,
    val password: String? = null,
    val csrfToken: String? = null
)

@Serializable
data class ChubDefinition(
    @SerialName("first_mes")
    val firstMes: String? = null,
    val description: String? = null,
    val personality: String? = null,
    val scenario: String? = null
)

@Serializable
data class ChubSearchRequest(
    val search: String = "",
    val first: Int = 24,
    val page: Int = 1,
    val sort: String = "download_count",
    val asc: Boolean = false,
    val nsfw: Boolean = false,
    val nsfl: Boolean = false,
    @SerialName("include_forks")
    val includeForks: Boolean = false,
    val tags: String? = null,
    @SerialName("min_tokens")
    val minTokens: Int? = null,
    val venus: Boolean = false
)
