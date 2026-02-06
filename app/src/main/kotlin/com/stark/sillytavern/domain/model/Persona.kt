package com.stark.sillytavern.domain.model

/**
 * Represents a user persona in SillyTavern.
 * Personas are identified by their avatar filename.
 */
data class Persona(
    val avatarId: String,           // Avatar filename (e.g., "user123.png")
    val name: String = "",          // Display name (derived from filename or set)
    val description: String = "",   // Persona description to include in prompts
    val position: PersonaPosition = PersonaPosition.IN_PROMPT,
    val role: PersonaRole = PersonaRole.SYSTEM,
    val depth: Int = 2,             // Depth for in-chat injection
    val lorebook: String = "",      // Attached lorebook name
    val isSelected: Boolean = false // Whether this is the active persona
)

enum class PersonaPosition(val value: Int) {
    IN_PROMPT(0),      // Include in system prompt
    IN_CHAT(1),        // Include at depth in chat
    TOP_OF_AN(2),      // Top of Author's Note
    BOTTOM_OF_AN(3);   // Bottom of Author's Note

    companion object {
        fun fromInt(value: Int) = entries.find { it.value == value } ?: IN_PROMPT
    }
}

enum class PersonaRole(val value: Int) {
    SYSTEM(0),
    USER(1),
    ASSISTANT(2);

    companion object {
        fun fromInt(value: Int) = entries.find { it.value == value } ?: SYSTEM
    }
}
