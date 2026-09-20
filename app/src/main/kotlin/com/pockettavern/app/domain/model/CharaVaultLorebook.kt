package com.pockettavern.app.domain.model

/**
 * Domain model for a lorebook from CharaVault.
 */
data class CharaVaultLorebook(
    val id: Int,
    val file: String,
    val folder: String,
    val name: String,
    val creator: String,
    val description: String = "",
    val topics: List<String> = emptyList(),
    val entryCount: Int = 0,
    val tokenCount: Int = 0,
    val keywords: String = "",
    val starCount: Int = 0,
    val nsfw: Boolean = false,
    // Full content (loaded on demand)
    val entries: List<LorebookEntryItem>? = null
) {
    /**
     * Unique identifier for the lorebook.
     */
    val uniqueId: String
        get() = "$folder/$file"

    /**
     * URL path to fetch the lorebook JSON.
     */
    val downloadPath: String
        get() = "lorebooks/$folder/$file"

    /**
     * Keywords as a list.
     */
    val keywordList: List<String>
        get() = keywords.split(",").map { it.trim() }.filter { it.isNotEmpty() }
}

/**
 * Individual lorebook entry.
 */
data class LorebookEntryItem(
    val id: Int,
    val name: String,
    val content: String,
    val keys: List<String>,
    val enabled: Boolean = true,
    val priority: Int = 10
)

/**
 * Search results from CharaVault lorebooks.
 */
data class CharaVaultLorebookSearchResult(
    val lorebooks: List<CharaVaultLorebook>,
    val totalCount: Int,
    val currentPage: Int,
    val totalPages: Int,
    val limit: Int
)

/**
 * Statistics for lorebooks from CharaVault.
 */
data class CharaVaultLorebookStats(
    val totalLorebooks: Int,
    val nsfwCount: Int,
    val sfwCount: Int,
    val creatorCount: Int,
    val totalEntries: Int,
    val configuredDirs: List<String>
)
