package com.pockettavern.app.domain.model

import java.time.Instant

data class Chat(
    val fileName: String,
    val characterName: String,
    val messages: List<ChatMessage> = emptyList(),
    val createDate: Instant = Instant.now(),
    val memoryBlock: String = "",
    val summarizedTurnCount: Int = 0
)

data class ChatInfo(
    val fileName: String,
    val lastMessage: String? = null,
    val messageCount: Int = 0,
    val lastModified: Long = 0L
)
