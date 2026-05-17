package com.pockettavern.app.domain.model

import kotlinx.serialization.Serializable

/**
 * A group chat with multiple characters
 */
@Serializable
data class Group(
    val id: String,
    val name: String,
    val members: List<String> = emptyList(),  // Character avatar filenames
    val chatId: String? = null,
    val avatar: String? = null,
    val favorite: Boolean = false,
    val activationStrategy: Int = ActivationStrategy.NATURAL,
    val generationMode: Int = GenerationMode.SWAP,
    val chatStyle: Int = ChatStyle.DIALOGUE,
    val systemPrompt: String = "",
    val disabledMembers: List<String> = emptyList(),
    val allowSelfResponses: Boolean = false,
    val chats: List<String> = emptyList()
) {
    /** Which characters are eligible to respond */
    val enabledMembers: List<String>
        get() = members.filter { it !in disabledMembers }
}

object ActivationStrategy {
    const val NATURAL = 0
    const val LIST = 1
    const val MANUAL = 2
    const val POOLED = 3
}

object GenerationMode {
    const val SWAP = 0
    const val APPEND = 1
}

object ChatStyle {
    const val DIALOGUE = 0  // spoken words only
    const val RP = 1        // dialogue + *actions*
}

/**
 * A simple world info list item for displaying in UI
 */
data class WorldInfoListItem(
    val fileId: String,
    val name: String
)
