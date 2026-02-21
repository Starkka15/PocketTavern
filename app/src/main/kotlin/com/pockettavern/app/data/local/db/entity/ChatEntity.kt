package com.pockettavern.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey
    val fileName: String,           // e.g. "seraphina - 2024-01-15 @12h30m00s.jsonl"
    val characterName: String,
    val createDate: Long = 0,       // Epoch millis
    val modifyDate: Long = 0,       // Epoch millis (file last modified)
    val messageCount: Int = 0
)
