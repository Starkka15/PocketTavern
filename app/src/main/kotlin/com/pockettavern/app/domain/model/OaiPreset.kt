package com.pockettavern.app.domain.model

/**
 * Sampling preset for OpenAI-compatible chat completion APIs.
 * Each parameter has an independent enabled toggle; disabled params are omitted
 * from the request so the backend uses its own defaults.
 *
 * Prompt block content (Main Prompt, Auxiliary Prompt, Post-History Instructions,
 * custom blocks) is stored inside each OaiPromptOrderItem.content.
 */
data class OaiPreset(
    val name: String,

    // Temperature
    val temperature: Float = 1.0f,
    val temperatureEnabled: Boolean = true,

    // Top P
    val topP: Float = 1.0f,
    val topPEnabled: Boolean = false,

    // Top K (supported by OpenRouter, local models, etc.)
    val topK: Int = 0,
    val topKEnabled: Boolean = false,

    // Max tokens (response length)
    val maxTokens: Int = 500,
    val maxTokensEnabled: Boolean = true,

    // Frequency penalty
    val frequencyPenalty: Float = 0f,
    val frequencyPenaltyEnabled: Boolean = false,

    // Presence penalty
    val presencePenalty: Float = 0f,
    val presencePenaltyEnabled: Boolean = false,

    // Repetition penalty (local/compatible backends)
    val repetitionPenalty: Float = 1.0f,
    val repetitionPenaltyEnabled: Boolean = false,

    // Min P (local/compatible backends)
    val minP: Float = 0f,
    val minPEnabled: Boolean = false,

    // Top A (local/compatible backends)
    val topA: Float = 0f,
    val topAEnabled: Boolean = false,

    // Context size (max tokens in context window for prompt building)
    val contextSize: Int = 4096,
    val contextSizeEnabled: Boolean = false,

    // Seed (-1 = random)
    val seed: Int = -1,
    val seedEnabled: Boolean = false,

    // Prompt block ordering, enable/disable, and content
    val promptOrder: List<OaiPromptOrderItem> = OaiPromptOrderItem.defaultOrder()
) {
    /** Convenience: content of the main_prompt block when enabled. */
    val mainPromptContent: String
        get() = promptOrder.find { it.id == "main_prompt" }
            ?.takeIf { it.enabled }?.content ?: ""
}
