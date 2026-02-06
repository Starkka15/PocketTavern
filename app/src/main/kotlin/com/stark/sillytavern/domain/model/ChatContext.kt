package com.stark.sillytavern.domain.model

/**
 * Complete context data pulled from SillyTavern for a chat session.
 * This is the source of truth - we pull from ST and save changes back.
 */
data class ChatContext(
    // Character info (from /api/characters/get)
    val characterName: String = "",
    val characterDescription: String = "",
    val characterPersonality: String = "",
    val characterScenario: String = "",
    val characterFirstMessage: String = "",
    val characterMessageExamples: String = "",
    val characterSystemPrompt: String = "",
    val characterPostHistoryInstructions: String = "",

    // User persona (from settings)
    val userPersona: UserPersona = UserPersona(),

    // Author's Note (from chat_metadata in chat file header)
    val authorsNote: AuthorsNote = AuthorsNote(),

    // World Info entries (from /api/worldinfo/get + character attached)
    val worldInfoEntries: List<WorldInfoEntry> = emptyList(),
    val worldInfoSettings: WorldInfoSettings = WorldInfoSettings(),

    // Prompt templates (from settings)
    val instructTemplate: InstructTemplate? = null,
    val contextTemplate: ContextTemplate? = null,
    val systemPromptPreset: String = "",

    // OAI prompts (for chat completion APIs)
    val oaiPrompts: Map<String, String> = emptyMap(),
    val oaiPromptOrder: List<PromptOrderEntry> = emptyList(),

    // Tracking
    val isLoaded: Boolean = false,
    val lastModified: Long = 0
)

data class UserPersona(
    val name: String = "User",
    val description: String = "",
    val position: Int = 0,  // 0 = in prompt, 1 = in chat @ depth, 2 = top of AN, 3 = bottom of AN
    val depth: Int = 2,
    val role: Int = 0       // 0 = system, 1 = user, 2 = assistant
)

data class AuthorsNote(
    val content: String = "",
    val interval: Int = 1,     // Every N messages (0 = every message)
    val depth: Int = 4,        // How many messages from bottom
    val position: Int = 0,     // 0 = after scenario, 1 = in-chat, 2 = before scenario
    val role: Int = 0          // 0 = system, 1 = user, 2 = assistant
)

data class WorldInfoEntry(
    val uid: String,
    val key: List<String>,           // Trigger keywords
    val keysecondary: List<String>,  // Secondary keys (AND logic)
    val content: String,             // The actual lore text
    val comment: String = "",        // Display name/comment
    val constant: Boolean = false,   // Always include
    val selective: Boolean = false,  // Use secondary keys
    val order: Int = 100,            // Sort order
    val position: Int = 0,           // 0 = before char, 1 = after char
    val depth: Int = 4,              // Scan depth
    val probability: Int = 100,      // Activation probability
    val enabled: Boolean = true,
    val group: String = "",          // Group name
    val scanDepth: Int? = null,      // Custom scan depth
    val caseSensitive: Boolean = false,
    val matchWholeWords: Boolean = false
)

data class WorldInfoSettings(
    val depth: Int = 2,
    val budget: Int = 25,            // % of context
    val budgetCap: Int = 0,          // Max tokens (0 = no cap)
    val minActivations: Int = 0,
    val recursive: Boolean = false,
    val caseSensitive: Boolean = false,
    val matchWholeWords: Boolean = false
)

// InstructTemplate and ContextTemplate are defined in FormattingSettings.kt

data class PromptOrderEntry(
    val identifier: String,
    val enabled: Boolean
)
