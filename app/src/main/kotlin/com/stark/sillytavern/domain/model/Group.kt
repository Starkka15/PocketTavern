package com.stark.sillytavern.domain.model

/**
 * A group chat with multiple characters
 */
data class Group(
    val id: String,
    val name: String,
    val members: List<String> = emptyList(),  // Character avatar URLs
    val chatId: String? = null,
    val avatar: String? = null,
    val description: String = "",
    val favorite: Boolean = false
)

/**
 * A simple world info list item for displaying in UI
 */
data class WorldInfoListItem(
    val fileId: String,
    val name: String
)
