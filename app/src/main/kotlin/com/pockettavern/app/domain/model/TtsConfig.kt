package com.pockettavern.app.domain.model

data class TtsConfig(
    val enabled: Boolean = false,
    val provider: String = "system",      // "system" | "openai"
    val autoPlay: Boolean = true,
    val openAiUrl: String = "",
    val openAiKey: String = "",
    val openAiVoice: String = "alloy",
    val openAiModel: String = "tts-1",
    val speed: Float = 1.0f,
    val filterMode: String = "all",       // "all" | "quotes_only" | "no_asterisks"
    val systemEngine: String = "",          // empty = use system default
    val systemVoice: String = ""             // empty = use engine default
)
