package com.stark.sillytavern.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.stark.sillytavern.domain.model.ServerSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val SERVER_URL = stringPreferencesKey("sillytavern_server")
        val USERNAME = stringPreferencesKey("sillytavern_username")
        val PASSWORD = stringPreferencesKey("sillytavern_password")
        val PROXY_URL = stringPreferencesKey("sillytavern_proxy")
        val FORGE_URL = stringPreferencesKey("sillytavern_forge")
        // Preset selections (persist between sessions)
        val SELECTED_TEXTGEN_PRESET = stringPreferencesKey("selected_textgen_preset")
        val SELECTED_INSTRUCT_PRESET = stringPreferencesKey("selected_instruct_preset")
        val SELECTED_SYSPROMPT_PRESET = stringPreferencesKey("selected_sysprompt_preset")
        // Chub.ai session
        val CHUB_SESSION_COOKIE = stringPreferencesKey("chub_session_cookie")
        val CHUB_USERNAME = stringPreferencesKey("chub_username")
        val CHUB_ENABLED = booleanPreferencesKey("chub_enabled")
        // CardVault server
        val CARDVAULT_URL = stringPreferencesKey("cardvault_url")
    }

    val settingsFlow: Flow<ServerSettings> = context.dataStore.data.map { prefs ->
        ServerSettings(
            serverUrl = prefs[Keys.SERVER_URL] ?: "",
            username = prefs[Keys.USERNAME] ?: "",
            password = prefs[Keys.PASSWORD] ?: "",
            proxyUrl = prefs[Keys.PROXY_URL] ?: "",
            forgeUrl = prefs[Keys.FORGE_URL] ?: "",
            cardVaultUrl = prefs[Keys.CARDVAULT_URL] ?: "",
            chubEnabled = prefs[Keys.CHUB_ENABLED] ?: false
        )
    }

    suspend fun saveSettings(settings: ServerSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SERVER_URL] = settings.serverUrl.trimEnd('/')
            prefs[Keys.USERNAME] = settings.username
            prefs[Keys.PASSWORD] = settings.password
            prefs[Keys.PROXY_URL] = settings.proxyUrl.trimEnd('/')
            prefs[Keys.FORGE_URL] = settings.forgeUrl.trimEnd('/')
            prefs[Keys.CARDVAULT_URL] = settings.cardVaultUrl.trimEnd('/')
            prefs[Keys.CHUB_ENABLED] = settings.chubEnabled
        }
    }

    suspend fun clearSettings() {
        context.dataStore.edit { prefs ->
            prefs.clear()
        }
    }

    // Preset selection persistence
    suspend fun getSelectedTextGenPreset(): String? {
        return context.dataStore.data.map { it[Keys.SELECTED_TEXTGEN_PRESET] }.first()
    }

    suspend fun setSelectedTextGenPreset(presetName: String?) {
        context.dataStore.edit { prefs ->
            if (presetName != null) {
                prefs[Keys.SELECTED_TEXTGEN_PRESET] = presetName
            } else {
                prefs.remove(Keys.SELECTED_TEXTGEN_PRESET)
            }
        }
    }

    suspend fun getSelectedInstructPreset(): String? {
        return context.dataStore.data.map { it[Keys.SELECTED_INSTRUCT_PRESET] }.first()
    }

    suspend fun setSelectedInstructPreset(presetName: String?) {
        context.dataStore.edit { prefs ->
            if (presetName != null) {
                prefs[Keys.SELECTED_INSTRUCT_PRESET] = presetName
            } else {
                prefs.remove(Keys.SELECTED_INSTRUCT_PRESET)
            }
        }
    }

    suspend fun getSelectedSyspromptPreset(): String? {
        return context.dataStore.data.map { it[Keys.SELECTED_SYSPROMPT_PRESET] }.first()
    }

    suspend fun setSelectedSyspromptPreset(presetName: String?) {
        context.dataStore.edit { prefs ->
            if (presetName != null) {
                prefs[Keys.SELECTED_SYSPROMPT_PRESET] = presetName
            } else {
                prefs.remove(Keys.SELECTED_SYSPROMPT_PRESET)
            }
        }
    }

    suspend fun clearPresetSelections() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.SELECTED_TEXTGEN_PRESET)
            prefs.remove(Keys.SELECTED_INSTRUCT_PRESET)
            prefs.remove(Keys.SELECTED_SYSPROMPT_PRESET)
        }
    }

    // Chub.ai session management
    val chubSessionFlow: Flow<ChubSession?> = context.dataStore.data.map { prefs ->
        val cookie = prefs[Keys.CHUB_SESSION_COOKIE]
        val username = prefs[Keys.CHUB_USERNAME]
        if (cookie != null) {
            ChubSession(cookie = cookie, username = username)
        } else {
            null
        }
    }

    suspend fun saveChubSession(cookie: String, username: String?) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CHUB_SESSION_COOKIE] = cookie
            if (username != null) {
                prefs[Keys.CHUB_USERNAME] = username
            }
        }
    }

    suspend fun clearChubSession() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.CHUB_SESSION_COOKIE)
            prefs.remove(Keys.CHUB_USERNAME)
        }
    }

    suspend fun getChubSession(): ChubSession? {
        return chubSessionFlow.first()
    }

    // CardVault server URL
    val cardVaultUrlFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.CARDVAULT_URL] ?: ""
    }

    suspend fun saveCardVaultUrl(url: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CARDVAULT_URL] = url.trimEnd('/')
        }
    }

    suspend fun getCardVaultUrl(): String {
        return cardVaultUrlFlow.first()
    }
}

data class ChubSession(
    val cookie: String,
    val username: String? = null
)
