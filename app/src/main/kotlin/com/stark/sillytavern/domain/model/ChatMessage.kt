package com.stark.sillytavern.domain.model

import java.time.Instant
import java.util.UUID

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val isUser: Boolean,
    val timestamp: Instant = Instant.now(),
    // Chat integrity slug - only present on first message, used for server-side validation
    val integritySlug: String? = null,
    // Full chat metadata (only on first message) - includes author's note per-chat
    val chatMetadata: ChatMessageMetadata? = null
)

// Per-chat metadata stored in the first message
data class ChatMessageMetadata(
    val notePrompt: String? = null,
    val noteInterval: Int? = null,
    val noteDepth: Int? = null,
    val notePosition: Int? = null,
    val noteRole: Int? = null
)
