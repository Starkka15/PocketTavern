package com.stark.sillytavern.domain.model

/**
 * Domain model for a character from CardVault.
 */
data class CardVaultCharacter(
    val file: String,
    val folder: String,
    val name: String,
    val creator: String,
    val tags: List<String> = emptyList(),
    val nsfw: Boolean = false,
    val descriptionPreview: String = "",
    val firstMesPreview: String = "",
    // Full details (loaded on demand)
    val fullDescription: String? = null,
    val fullFirstMes: String? = null,
    val personality: String? = null,
    val scenario: String? = null
) {
    /**
     * Unique identifier for the card (folder/filename).
     */
    val id: String
        get() = "$folder/$file"

    /**
     * URL path to fetch the card image.
     */
    val imagePath: String
        get() = "cards/$folder/$file"
}

/**
 * Search results from CardVault.
 */
data class CardVaultSearchResult(
    val characters: List<CardVaultCharacter>,
    val totalCount: Int,
    val currentPage: Int,
    val totalPages: Int,
    val limit: Int
)

/**
 * Statistics from CardVault.
 */
data class CardVaultStats(
    val totalCards: Int,
    val nsfwCount: Int,
    val sfwCount: Int,
    val uniqueCreators: Int,
    val uniqueTags: Int,
    val topTags: List<Pair<String, Int>>,
    val topCreators: List<Pair<String, Int>>
)

/**
 * Sort options for CardVault search.
 * Note: Current server doesn't support sorting, but we can add it later.
 */
enum class CardVaultSortOption(val value: String, val displayName: String) {
    NAME("name", "Name"),
    CREATOR("creator", "Creator"),
    NEWEST("newest", "Newest")
}

/**
 * Filter options for NSFW content.
 */
enum class CardVaultNsfwFilter(val displayName: String) {
    ALL("All"),
    SFW_ONLY("SFW Only"),
    NSFW_ONLY("NSFW Only")
}
