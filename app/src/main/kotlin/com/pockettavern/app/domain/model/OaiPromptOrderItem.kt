package com.pockettavern.app.domain.model

import java.util.UUID

data class OaiPromptOrderItem(
    val id: String,
    val enabled: Boolean = true,
    /** Non-null only for user-created custom prompt blocks. */
    val customLabel: String? = null,
    /** Editable prompt text for content blocks (built-in and custom). Null for marker blocks. */
    val content: String? = null,
    /** "system" or "assistant" — controls what role this block is emitted as in chat completions. */
    val role: String = "system",
    /** ST injection_position: 0 = normal prompt order, 1 = depth-injected into chat history. */
    val injectionPosition: Int = 0,
    /** ST injection_depth: how many messages from the bottom to inject at. 0 = after last message. */
    val injectionDepth: Int = 4
) {
    /** Marker blocks have their content injected dynamically (character card, chat history, etc.). */
    val isMarker: Boolean get() = id in MARKER_IDS

    /** Custom blocks are user-created (id starts with "custom_"). */
    val isCustom: Boolean get() = id.startsWith("custom_")

    val label: String get() = customLabel ?: idToLabel(id)

    companion object {
        val MARKER_IDS = setOf(
            "world_info_before",
            "world_info_after",
            "persona_description",
            "char_description",
            "char_personality",
            "scenario",
            "chat_examples",
            "chat_history"
        )

        fun custom(label: String, content: String) = OaiPromptOrderItem(
            id = "custom_${UUID.randomUUID()}",
            enabled = true,
            customLabel = label,
            content = content
        )

        fun defaultOrder(): List<OaiPromptOrderItem> = listOf(
            OaiPromptOrderItem("main_prompt", content = ""),
            OaiPromptOrderItem("world_info_before"),
            OaiPromptOrderItem("persona_description"),
            OaiPromptOrderItem("char_description"),
            OaiPromptOrderItem("char_personality"),
            OaiPromptOrderItem("scenario"),
            OaiPromptOrderItem("auxiliary_prompt", content = ""),
            OaiPromptOrderItem("world_info_after"),
            OaiPromptOrderItem("chat_examples"),
            OaiPromptOrderItem("chat_history"),
            OaiPromptOrderItem("post_history_instructions", content = ""),
        )

        private fun idToLabel(id: String): String = when (id) {
            "main_prompt" -> "Main Prompt"
            "world_info_before" -> "World Info (before)"
            "persona_description" -> "Persona Description"
            "char_description" -> "Char Description"
            "char_personality" -> "Char Personality"
            "scenario" -> "Scenario"
            "auxiliary_prompt" -> "Auxiliary Prompt"
            "world_info_after" -> "World Info (after)"
            "chat_examples" -> "Chat Examples"
            "chat_history" -> "Chat History"
            "post_history_instructions" -> "Post-History Instructions"
            else -> id
        }

        /** Merge a stored order list with the canonical default — adds any missing built-in items. */
        fun mergeWithDefault(stored: List<OaiPromptOrderItem>): List<OaiPromptOrderItem> {
            val defaults = defaultOrder()
            val storedIds = stored.map { it.id }.toSet()
            return stored + defaults.filter { it.id !in storedIds }
        }

        /** Map ST identifier strings to our internal IDs. */
        fun stIdentifierToId(stId: String): String = when (stId) {
            "main" -> "main_prompt"
            "nsfw" -> "auxiliary_prompt"
            "jailbreak" -> "post_history_instructions"
            "dialogueExamples" -> "chat_examples"
            "chatHistory" -> "chat_history"
            "worldInfoBefore" -> "world_info_before"
            "worldInfoAfter" -> "world_info_after"
            "charDescription" -> "char_description"
            "charPersonality" -> "char_personality"
            "scenario" -> "scenario"
            "personaDescription" -> "persona_description"
            "enhanceDefinitions" -> "enhance_definitions"
            else -> stId
        }
    }
}
