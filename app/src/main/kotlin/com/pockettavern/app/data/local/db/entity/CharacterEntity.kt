package com.pockettavern.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "characters")
data class CharacterEntity(
    @PrimaryKey
    val fileName: String,
    val name: String,
    val avatar: String = "",
    val description: String = "",
    val personality: String = "",
    val scenario: String = "",
    val firstMessage: String = "",
    val messageExample: String = "",
    val creatorNotes: String = "",
    val systemPrompt: String = "",
    val postHistoryInstructions: String = "",
    val alternateGreetings: String = "[]",  // JSON array
    val tags: String = "",                  // comma-separated
    val hasCharacterBook: Boolean = false,
    val characterBookEntryCount: Int = 0,
    val isFavorite: Boolean = false,
    val lastChatDate: Long = 0,
    val useAvatarForImageGen: Boolean = true,
    val notes: String = "",
    val loreHints: String = ""
)
