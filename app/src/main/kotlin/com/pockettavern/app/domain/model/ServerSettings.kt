package com.pockettavern.app.domain.model

/**
 * User-configurable settings for external services (Forge, CharaVault).
 * LLM backend configuration is stored separately in ApiConfiguration (via SettingsDataStore).
 */
data class ServerSettings(
    val forgeUrl: String = "",
    val proxyUrl: String = "",
    val charaVaultUrl: String = "",
    val charavaultMode: String = "local", // "local" or "charavault"
    // Whether the actually-active ImageGenBackendType (which may not be SD_WEBUI/Forge at all)
    // has its required config filled in -- see ImageGenConfig.isActiveBackendConfigured.
    val imageGenBackendConfigured: Boolean = false
) {
    val normalizedForgeUrl: String
        get() = forgeUrl.trimEnd('/')

    val normalizedProxyUrl: String
        get() = proxyUrl.trimEnd('/')

    val normalizedCharaVaultUrl: String
        get() = charaVaultUrl.trimEnd('/')

    val isCharaVaultEnabled: Boolean
        get() = charaVaultUrl.isNotBlank()

    val isForgeEnabled: Boolean
        get() = forgeUrl.isNotBlank()
}
