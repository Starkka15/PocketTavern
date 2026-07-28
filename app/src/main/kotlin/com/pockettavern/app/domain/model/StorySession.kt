package com.pockettavern.app.domain.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * T4 — runtime session types for Stories. A Story session = an imported [Story] (cast + world) +
 * a speaker-tagged log. One assistant turn holds the WHOLE multi-speaker scene (speakers tagged
 * inline in [content]); the player's turns are their own beats. Persisted by StoryStorage.
 */
@Serializable
data class StoryMessage(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val isUser: Boolean,
    /** DeepSeek R1 `<think>` for this scene — the hidden director reasoning (V6), shown collapsed. */
    val reasoning: String? = null,
    val timestamp: Long = 0L
) {
    /** Map to the prompt-layer [ChatMessage] that StoryPromptBuilder consumes. */
    fun toChatMessage() = ChatMessage(
        id = id,
        content = content,
        isUser = isUser,
        reasoning = reasoning
    )
}

/** Lightweight catalog entry for an imported Story card (for the Stories tab list). */
@Serializable
data class StoryCardRef(
    val id: String,
    val name: String,
    val creator: String = "",
    val tags: List<String> = emptyList(),
    /** Cover art filename within the story's dir (the embedding PNG), or null. */
    val cover: String? = null,
    val castCount: Int = 0,
    val importedAt: Long = 0L
)
