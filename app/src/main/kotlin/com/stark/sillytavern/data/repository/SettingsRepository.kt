package com.stark.sillytavern.data.repository

import com.stark.sillytavern.data.local.SettingsDataStore
import com.stark.sillytavern.domain.model.ServerSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: SettingsDataStore
) {
    val settingsFlow: Flow<ServerSettings> = dataStore.settingsFlow

    suspend fun getSettings(): ServerSettings = dataStore.settingsFlow.first()

    suspend fun saveSettings(settings: ServerSettings) {
        dataStore.saveSettings(settings)
    }

    suspend fun getServerUrl(): String = getSettings().normalizedServerUrl

    suspend fun getProxyUrl(): String = getSettings().normalizedProxyUrl

    suspend fun getForgeUrl(): String = getSettings().normalizedForgeUrl

    suspend fun clearSettings() {
        dataStore.clearSettings()
    }
}
