package com.pockettavern.app.domain.model

import java.util.UUID

data class QuickReplyButton(
    val id: String = UUID.randomUUID().toString(),
    /** Text shown on the button. Supports {{user}}, {{char}} macros. */
    val label: String,
    /** Message sent when button is tapped. Supports {{user}}, {{char}} macros. */
    val message: String,
    /** ExtensionEvent names that auto-fire this button (e.g. "CHAT_CHANGED"). */
    val autoTriggers: Set<String> = emptySet()
)

data class QuickReplyPreset(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val enabled: Boolean = true,
    val buttons: List<QuickReplyButton> = emptyList()
)
