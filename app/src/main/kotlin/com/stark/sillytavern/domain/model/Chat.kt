package com.stark.sillytavern.domain.model

import java.time.Instant

data class Chat(
    val fileName: String,
    val characterName: String,
    val messages: List<ChatMessage> = emptyList(),
    val createDate: Instant = Instant.now()
)

data class ChatInfo(
    val fileName: String,
    val lastMessage: String? = null,
    val messageCount: Int = 0
)
