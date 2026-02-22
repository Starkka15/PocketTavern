package com.pockettavern.app.domain.model

import java.time.Instant
import java.util.UUID

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val isUser: Boolean,
    /** True for narrator/system messages inserted via /sys — rendered differently, not sent to AI. */
    val isNarrator: Boolean = false,
    val timestamp: Instant = Instant.now(),
    // Chat integrity slug - only present on first message, used for server-side validation
    val integritySlug: String? = null,
    // Full chat metadata (only on first message) - includes author's note per-chat
    val chatMetadata: ChatMessageMetadata? = null,
    // Sender name for group chats - null for 1-on-1 (no behavior change)
    val senderName: String? = null,
    // Raw content before output filters stripped extension tags (null if no filtering happened)
    val rawContent: String? = null,
    // Header text set by extensions (persisted so it survives chat reload)
    val extensionHeader: String? = null
)

// Per-chat metadata stored in the first message
data class ChatMessageMetadata(
    val notePrompt: String? = null,
    val noteInterval: Int? = null,
    val noteDepth: Int? = null,
    val notePosition: Int? = null,
    val noteRole: Int? = null
)

// Group chat message - includes sender information
data class GroupChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val isUser: Boolean,
    val isSystem: Boolean = false,
    val senderName: String? = null,  // Character name for AI messages
    val senderAvatar: String? = null,  // Character avatar URL
    val timestamp: Instant = Instant.now()
)
