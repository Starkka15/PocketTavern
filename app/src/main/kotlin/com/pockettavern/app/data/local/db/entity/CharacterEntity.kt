package com.pockettavern.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "characters")
data class CharacterEntity(
    @PrimaryKey
    val fileName: String,       // e.g. "seraphina.png"
    val name: String,
    val tags: String = "",      // Comma-separated
    val isFavorite: Boolean = false,
    val lastChatDate: Long = 0, // Epoch millis of most recent chat
    val hasCharacterBook: Boolean = false
)
