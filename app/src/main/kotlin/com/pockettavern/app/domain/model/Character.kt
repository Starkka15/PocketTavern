package com.pockettavern.app.domain.model

data class Character(
    val name: String,
    val avatar: String?,
    val description: String = "",
    val personality: String = "",
    val scenario: String = "",
    val firstMessage: String = "",
    val messageExample: String = "",
    val creatorNotes: String = "",
    val systemPrompt: String = "",
    val tags: List<String> = emptyList(),
    // Alternate greetings (V2 spec)
    val alternateGreetings: List<String> = emptyList(),
    // World Info / Lorebook attachment
    val attachedWorldInfo: String? = null,  // Name of attached World Info file
    val hasCharacterBook: Boolean = false,   // Has embedded character book
    val characterBookEntryCount: Int = 0,    // Number of entries in embedded book
    // Author's Note / Post-history instructions
    val postHistoryInstructions: String = "",
    // Depth prompt settings (advanced Author's Note)
    val depthPrompt: String = "",
    val depthPromptDepth: Int = 4,
    val depthPromptRole: String = "system",
    // Talkativeness (for group chats)
    val talkativeness: Float = 0.5f,
    // Favorite flag
    val isFavorite: Boolean = false,
    // Image generation: use avatar as img2img source
    val useAvatarForImageGen: Boolean = true,
    // Personal notes about this character (stored in Room, not embedded in PNG)
    val notes: String = "",
    // PocketTavern-only: hints for /scanlore extraction (stored in card extensions)
    val loreHints: String = ""
)
