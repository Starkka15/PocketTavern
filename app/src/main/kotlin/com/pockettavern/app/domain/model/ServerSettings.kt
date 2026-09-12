package com.pockettavern.app.domain.model

/**
 * User-configurable settings for external services (Forge).
 * LLM backend configuration is stored separately in ApiConfiguration (via SettingsDataStore).
 */
data class ServerSettings(
    val forgeUrl: String = "",
    val proxyUrl: String = "",
    // Whether the actually-active ImageGenBackendType (which may not be SD_WEBUI/Forge at all)
    // has its required config filled in -- see ImageGenConfig.isActiveBackendConfigured.
    val imageGenBackendConfigured: Boolean = false
) {
    val normalizedForgeUrl: String
        get() = forgeUrl.trimEnd('/')

    val normalizedProxyUrl: String
        get() = proxyUrl.trimEnd('/')

    val isForgeEnabled: Boolean
        get() = forgeUrl.isNotBlank()
}
