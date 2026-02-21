package com.pockettavern.app.domain.model

import java.util.UUID

/**
 * A saved snapshot of API + preset selections that can be activated in one tap.
 * Activating a profile overwrites the current settings in DataStore.
 */
data class ConnectionProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    // API configuration
    val mainApi: String = "textgenerationwebui",
    val textGenType: String = "koboldcpp",
    val apiServer: String = "http://127.0.0.1:5001",
    val chatCompletionSource: String = "openai",
    val customUrl: String? = null,
    val apiKey: String = "",
    val model: String = "",
    // Preset selections
    val textGenPreset: String = "",
    val instructPreset: String = "",
    val contextPreset: String = "",
    val syspromptPreset: String = ""
) {
    /** Short human-readable description of the API type. */
    val apiLabel: String get() = if (mainApi.lowercase() == "openai") {
        ApiConfiguration.chatCompletionSourceDisplayName(chatCompletionSource)
    } else {
        ApiConfiguration.textGenTypeDisplayName(textGenType)
    }
}
