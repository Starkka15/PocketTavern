package com.stark.sillytavern.domain.model

data class ServerSettings(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val proxyUrl: String = "",
    val forgeUrl: String = "",
    val cardVaultUrl: String = "",
    val chubEnabled: Boolean = false
) {
    val hasCredentials: Boolean
        get() = username.isNotBlank() && password.isNotBlank()

    val normalizedServerUrl: String
        get() = serverUrl.trimEnd('/')

    val normalizedProxyUrl: String
        get() = proxyUrl.trimEnd('/')

    val normalizedForgeUrl: String
        get() = forgeUrl.trimEnd('/')

    val normalizedCardVaultUrl: String
        get() = cardVaultUrl.trimEnd('/')

    val isCardVaultEnabled: Boolean
        get() = cardVaultUrl.isNotBlank()

    val isForgeEnabled: Boolean
        get() = forgeUrl.isNotBlank()
}
