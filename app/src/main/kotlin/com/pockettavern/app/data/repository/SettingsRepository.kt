package com.pockettavern.app.data.repository

import com.pockettavern.app.data.local.SettingsDataStore
import com.pockettavern.app.domain.model.ApiConfiguration
import com.pockettavern.app.domain.model.ServerSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: SettingsDataStore
) {
    val settingsFlow: Flow<ServerSettings> = combine(
        dataStore.forgeUrlFlow,
        dataStore.charaVaultUrlFlow,
        dataStore.charavaultModeFlow
    ) { forgeUrl, charaVaultUrl, charavaultMode ->
        ServerSettings(
            forgeUrl = forgeUrl,
            charaVaultUrl = charaVaultUrl,
            charavaultMode = charavaultMode
        )
    }

    suspend fun getSettings(): ServerSettings = settingsFlow.first()

    suspend fun saveSettings(settings: ServerSettings) {
        dataStore.saveForgeUrl(settings.forgeUrl)
        dataStore.saveCharaVaultUrl(settings.charaVaultUrl)
        dataStore.saveCharaVaultMode(settings.charavaultMode)
    }

    suspend fun getForgeUrl(): String = dataStore.getForgeUrl()

    suspend fun getCharaVaultUrl(): String = dataStore.getCharaVaultUrl()

    // LLM config delegation
    val llmConfigFlow: Flow<ApiConfiguration> = dataStore.llmConfigFlow

    suspend fun getLlmConfig(): ApiConfiguration = dataStore.getLlmConfig()

    suspend fun saveLlmConfig(config: ApiConfiguration) = dataStore.saveLlmConfig(config)

    suspend fun getLlmApiKey(): String = dataStore.getLlmApiKey()

    suspend fun saveLlmApiKey(key: String) = dataStore.saveLlmApiKey(key)

    suspend fun clearSettings() {
        dataStore.clearAll()
    }
}
