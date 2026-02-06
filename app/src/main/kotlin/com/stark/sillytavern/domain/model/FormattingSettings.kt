package com.stark.sillytavern.domain.model

data class FormattingSettings(
    val instructPresets: List<String> = emptyList(),
    val selectedInstructPreset: String = "",
    val contextPresets: List<String> = emptyList(),
    val selectedContextPreset: String = "",
    val systemPromptPresets: List<String> = emptyList(),
    val selectedSystemPromptPreset: String = "",
    val customSystemPrompt: String = ""
)

data class InstructTemplate(
    val name: String,
    val systemPrompt: String = "",
    val inputSequence: String = "",       // User message prefix (e.g., "<|im_start|>user\n")
    val inputSuffix: String = "",         // User message suffix (after content)
    val outputSequence: String = "",      // Assistant message prefix (e.g., "<|im_start|>assistant\n")
    val outputSuffix: String = "",        // Assistant message suffix (after content)
    val firstOutputSequence: String = "", // First assistant message (optional)
    val lastOutputSequence: String = "",  // Last assistant message suffix (optional)
    val systemSequence: String = "",      // System message prefix (e.g., "<|im_start|>system\n")
    val systemSuffix: String = "",        // System message suffix (after content)
    val stopSequence: String = "",        // End of message marker (e.g., "<|im_end|>")
    val separatorSequence: String = "",   // Between messages
    val wrap: Boolean = false
)

data class ContextTemplate(
    val name: String,
    val storyString: String = "",
    val chatStart: String = "",
    val exampleSeparator: String = ""
)

data class SystemPromptPreset(
    val name: String,
    val content: String = ""
)
