package com.stark.sillytavern.domain.model

data class ChubCharacter(
    val name: String,
    val fullPath: String,
    val tagline: String = "",
    val description: String = "",
    val avatarUrl: String? = null,
    val downloadCount: Int = 0,
    val starCount: Int = 0,
    val ratingCount: Int = 0,
    val topics: List<String> = emptyList(),
    val firstMessage: String? = null,
    val creator: String = ""
) {
    val creatorName: String
        get() = fullPath.split("/").firstOrNull() ?: creator
}

data class ChubSearchResult(
    val characters: List<ChubCharacter>,
    val totalCount: Int,
    val currentPage: Int,
    val totalPages: Int
)

enum class ChubSortOption(val value: String, val displayName: String) {
    DOWNLOADS("download_count", "Most Downloads"),
    STARS("star_count", "Most Stars"),
    RATING("rating", "Highest Rated"),
    NEWEST("id", "Newest")
}
