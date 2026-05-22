package com.pockettavern.app.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.pockettavern.app.domain.model.ApiConfiguration
import com.pockettavern.app.domain.model.ImageGenConfig
import com.pockettavern.app.domain.model.TtsConfig
import kotlinx.serialization.json.Json
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
    // Encrypted storage for sensitive credentials (API keys, auth tokens)
    private val encryptedPrefs: SharedPreferences by lazy {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            "secure_prefs",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private companion object {
        const val SECURE_LLM_API_KEY = "llm_api_key"
        const val SECURE_CHARAVAULT_TOKEN = "charavault_token"
        const val SECURE_TTS_OPENAI_KEY = "tts_openai_key"
    }

    private object Keys {
        // LLM backend configuration
        val LLM_MAIN_API = stringPreferencesKey("llm_main_api")
        val CUSTOM_SYSTEM_PROMPT = stringPreferencesKey("custom_system_prompt")
        val LLM_TEXT_GEN_TYPE = stringPreferencesKey("llm_text_gen_type")
        val LLM_API_SERVER = stringPreferencesKey("llm_api_server")
        val LLM_CHAT_COMPLETION_SOURCE = stringPreferencesKey("llm_chat_completion_source")
        val LLM_CUSTOM_URL = stringPreferencesKey("llm_custom_url")
        val LLM_API_KEY = stringPreferencesKey("llm_api_key") // kept for migration reads only
        val LLM_CURRENT_MODEL = stringPreferencesKey("llm_current_model")

        // External tool URLs
        val FORGE_URL = stringPreferencesKey("sillytavern_forge")
        val PROXY_URL = stringPreferencesKey("sillytavern_proxy")

        // Preset selections (persist between sessions)
        val SELECTED_TEXTGEN_PRESET = stringPreferencesKey("selected_textgen_preset")
        val SELECTED_OAI_PRESET = stringPreferencesKey("selected_oai_preset")
        val SELECTED_INSTRUCT_PRESET = stringPreferencesKey("selected_instruct_preset")
        val SELECTED_SYSPROMPT_PRESET = stringPreferencesKey("selected_sysprompt_preset")
        val SELECTED_CONTEXT_PRESET = stringPreferencesKey("selected_context_preset")

        // CharaVault server
        val CHARAVAULT_URL = stringPreferencesKey("cardvault_url")

        // CharaVault.net session
        val CHARAVAULT_TOKEN = stringPreferencesKey("charavault_token") // kept for migration reads only
        val CHARAVAULT_EMAIL = stringPreferencesKey("charavault_email")
        val CHARAVAULT_MODE = stringPreferencesKey("charavault_mode")

        // Auto-continue
        val AUTO_CONTINUE_ENABLED = booleanPreferencesKey("auto_continue_enabled")
        val AUTO_CONTINUE_MIN_LENGTH = intPreferencesKey("auto_continue_min_length")

        // Last activated connection profile
        val LAST_ACTIVATED_PROFILE_ID = stringPreferencesKey("last_activated_profile_id")

        // User persona
        val USER_PERSONA_NAME = stringPreferencesKey("user_persona_name")
        val USER_PERSONA_DESC = stringPreferencesKey("user_persona_desc")
        val USER_PERSONA_POSITION = intPreferencesKey("user_persona_position")
        val USER_PERSONA_DEPTH = intPreferencesKey("user_persona_depth")

        // TTS
        val TTS_ENABLED = booleanPreferencesKey("tts_enabled")
        val TTS_PROVIDER = stringPreferencesKey("tts_provider")
        val TTS_AUTO_PLAY = booleanPreferencesKey("tts_auto_play")
        val TTS_OPENAI_URL = stringPreferencesKey("tts_openai_url")
        val TTS_OPENAI_KEY = stringPreferencesKey("tts_openai_key") // kept for migration reads only
        val TTS_OPENAI_VOICE = stringPreferencesKey("tts_openai_voice")
        val TTS_OPENAI_MODEL = stringPreferencesKey("tts_openai_model")
        val TTS_SPEED = floatPreferencesKey("tts_speed")
        val TTS_FILTER_MODE = stringPreferencesKey("tts_filter_mode")

        // Global Author's Note (applies to all chats unless overridden per-chat)
        val GLOBAL_AUTHORS_NOTE_CONTENT = stringPreferencesKey("global_authors_note_content")
        val GLOBAL_AUTHORS_NOTE_DEPTH = intPreferencesKey("global_authors_note_depth")
        val GLOBAL_AUTHORS_NOTE_INTERVAL = intPreferencesKey("global_authors_note_interval")
        val GLOBAL_AUTHORS_NOTE_POSITION = intPreferencesKey("global_authors_note_position")
        val GLOBAL_AUTHORS_NOTE_ROLE = intPreferencesKey("global_authors_note_role")

        // Image Generation
        val IMAGE_GEN_CONFIG = stringPreferencesKey("image_gen_config")

        // Long-Term Memory
        val MEMORY_ENABLED = booleanPreferencesKey("memory_enabled")

        // Sentinel incremented when an encrypted-prefs value changes, to trigger flow re-emit
        val SECURE_REFRESH = intPreferencesKey("secure_refresh")
    }

    // ── LLM Configuration ────────────────────────────────────────────────────

    val llmConfigFlow: Flow<ApiConfiguration> = context.dataStore.data.map { prefs ->
        ApiConfiguration(
            mainApi = prefs[Keys.LLM_MAIN_API] ?: "textgenerationwebui",
            textGenType = prefs[Keys.LLM_TEXT_GEN_TYPE] ?: "koboldcpp",
            apiServer = prefs[Keys.LLM_API_SERVER] ?: "http://127.0.0.1:5001",
            chatCompletionSource = prefs[Keys.LLM_CHAT_COMPLETION_SOURCE] ?: "openai",
            customUrl = prefs[Keys.LLM_CUSTOM_URL],
            apiKey = encryptedPrefs.getString(SECURE_LLM_API_KEY, null)
                ?: prefs[Keys.LLM_API_KEY] ?: "",
            currentModel = prefs[Keys.LLM_CURRENT_MODEL] ?: ""
        )
    }

    suspend fun getLlmConfig(): ApiConfiguration = llmConfigFlow.first()

    suspend fun saveLlmConfig(config: ApiConfiguration) {
        encryptedPrefs.edit().putString(SECURE_LLM_API_KEY, config.apiKey).apply()
        context.dataStore.edit { prefs ->
            prefs[Keys.LLM_MAIN_API] = config.mainApi
            prefs[Keys.LLM_TEXT_GEN_TYPE] = config.textGenType
            prefs[Keys.LLM_API_SERVER] = config.apiServer.trimEnd('/')
            prefs[Keys.LLM_CHAT_COMPLETION_SOURCE] = config.chatCompletionSource
            if (config.customUrl != null) prefs[Keys.LLM_CUSTOM_URL] = config.customUrl
            else prefs.remove(Keys.LLM_CUSTOM_URL)
            prefs.remove(Keys.LLM_API_KEY)
            prefs[Keys.LLM_CURRENT_MODEL] = config.currentModel
            prefs[Keys.SECURE_REFRESH] = (prefs[Keys.SECURE_REFRESH] ?: 0) + 1
        }
    }

    suspend fun getLlmApiKey(): String =
        encryptedPrefs.getString(SECURE_LLM_API_KEY, null)
            ?: context.dataStore.data.map { it[Keys.LLM_API_KEY] ?: "" }.first()

    suspend fun saveLlmApiKey(key: String) {
        encryptedPrefs.edit().putString(SECURE_LLM_API_KEY, key).apply()
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.LLM_API_KEY)
            prefs[Keys.SECURE_REFRESH] = (prefs[Keys.SECURE_REFRESH] ?: 0) + 1
        }
    }

    // ── Preset Selections ────────────────────────────────────────────────────

    suspend fun getSelectedTextGenPreset(): String? =
        context.dataStore.data.map { it[Keys.SELECTED_TEXTGEN_PRESET] }.first()

    suspend fun setSelectedTextGenPreset(presetName: String?) {
        context.dataStore.edit { prefs ->
            if (presetName != null) prefs[Keys.SELECTED_TEXTGEN_PRESET] = presetName
            else prefs.remove(Keys.SELECTED_TEXTGEN_PRESET)
        }
    }

    suspend fun getSelectedOaiPreset(): String? =
        context.dataStore.data.map { it[Keys.SELECTED_OAI_PRESET] }.first()

    suspend fun setSelectedOaiPreset(presetName: String?) {
        context.dataStore.edit { prefs ->
            if (presetName != null) prefs[Keys.SELECTED_OAI_PRESET] = presetName
            else prefs.remove(Keys.SELECTED_OAI_PRESET)
        }
    }

    suspend fun getSelectedInstructPreset(): String? =
        context.dataStore.data.map { it[Keys.SELECTED_INSTRUCT_PRESET] }.first()

    suspend fun setSelectedInstructPreset(presetName: String?) {
        context.dataStore.edit { prefs ->
            if (presetName != null) prefs[Keys.SELECTED_INSTRUCT_PRESET] = presetName
            else prefs.remove(Keys.SELECTED_INSTRUCT_PRESET)
        }
    }

    suspend fun getSelectedSyspromptPreset(): String? =
        context.dataStore.data.map { it[Keys.SELECTED_SYSPROMPT_PRESET] }.first()

    suspend fun setSelectedSyspromptPreset(presetName: String?) {
        context.dataStore.edit { prefs ->
            if (presetName != null) prefs[Keys.SELECTED_SYSPROMPT_PRESET] = presetName
            else prefs.remove(Keys.SELECTED_SYSPROMPT_PRESET)
        }
    }

    suspend fun getSelectedContextPreset(): String? =
        context.dataStore.data.map { it[Keys.SELECTED_CONTEXT_PRESET] }.first()

    suspend fun setSelectedContextPreset(presetName: String?) {
        context.dataStore.edit { prefs ->
            if (presetName != null) prefs[Keys.SELECTED_CONTEXT_PRESET] = presetName
            else prefs.remove(Keys.SELECTED_CONTEXT_PRESET)
        }
    }

    // ── External Tool URLs ───────────────────────────────────────────────────

    val forgeUrlFlow: Flow<String> = context.dataStore.data.map { it[Keys.FORGE_URL] ?: "" }

    suspend fun getForgeUrl(): String = forgeUrlFlow.first()

    suspend fun saveForgeUrl(url: String) {
        context.dataStore.edit { prefs -> prefs[Keys.FORGE_URL] = url.trimEnd('/') }
    }

    // ── CharaVault / CharaVault.net ───────────────────────────────────────────

    val charaVaultUrlFlow: Flow<String> = context.dataStore.data.map { it[Keys.CHARAVAULT_URL] ?: "" }

    suspend fun getCharaVaultUrl(): String = charaVaultUrlFlow.first()

    suspend fun saveCharaVaultUrl(url: String) {
        context.dataStore.edit { prefs -> prefs[Keys.CHARAVAULT_URL] = url.trimEnd('/') }
    }

    val charavaultSessionFlow: Flow<CharaVaultSession?> = context.dataStore.data.map { prefs ->
        val token = encryptedPrefs.getString(SECURE_CHARAVAULT_TOKEN, null)
            ?: prefs[Keys.CHARAVAULT_TOKEN]
        val email = prefs[Keys.CHARAVAULT_EMAIL]
        if (token != null && email != null) CharaVaultSession(token = token, email = email) else null
    }

    val charavaultModeFlow: Flow<String> = context.dataStore.data.map { it[Keys.CHARAVAULT_MODE] ?: "local" }

    suspend fun saveCharaVaultSession(token: String, email: String) {
        encryptedPrefs.edit().putString(SECURE_CHARAVAULT_TOKEN, token).apply()
        context.dataStore.edit { prefs ->
            prefs[Keys.CHARAVAULT_EMAIL] = email
            prefs.remove(Keys.CHARAVAULT_TOKEN)
            prefs[Keys.SECURE_REFRESH] = (prefs[Keys.SECURE_REFRESH] ?: 0) + 1
        }
    }

    suspend fun clearCharaVaultSession() {
        encryptedPrefs.edit().remove(SECURE_CHARAVAULT_TOKEN).apply()
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.CHARAVAULT_TOKEN)
            prefs.remove(Keys.CHARAVAULT_EMAIL)
            prefs[Keys.SECURE_REFRESH] = (prefs[Keys.SECURE_REFRESH] ?: 0) + 1
        }
    }

    suspend fun getCharaVaultSession(): CharaVaultSession? = charavaultSessionFlow.first()

    suspend fun saveCharaVaultMode(mode: String) {
        context.dataStore.edit { prefs -> prefs[Keys.CHARAVAULT_MODE] = mode }
    }

    suspend fun getCharaVaultMode(): String = charavaultModeFlow.first()

    // ── User Persona ─────────────────────────────────────────────────────────

    suspend fun getUserPersonaName(): String =
        context.dataStore.data.map { it[Keys.USER_PERSONA_NAME] ?: "User" }.first()

    suspend fun saveUserPersonaName(name: String) {
        context.dataStore.edit { prefs -> prefs[Keys.USER_PERSONA_NAME] = name }
    }

    suspend fun getUserPersonaDesc(): String =
        context.dataStore.data.map { it[Keys.USER_PERSONA_DESC] ?: "" }.first()

    suspend fun saveUserPersonaDesc(desc: String) {
        context.dataStore.edit { prefs -> prefs[Keys.USER_PERSONA_DESC] = desc }
    }

    suspend fun getUserPersonaPosition(): Int =
        context.dataStore.data.map { it[Keys.USER_PERSONA_POSITION] ?: 0 }.first()

    suspend fun saveUserPersonaPosition(position: Int) {
        context.dataStore.edit { prefs -> prefs[Keys.USER_PERSONA_POSITION] = position }
    }

    suspend fun getUserPersonaDepth(): Int =
        context.dataStore.data.map { it[Keys.USER_PERSONA_DEPTH] ?: 2 }.first()

    suspend fun saveUserPersonaDepth(depth: Int) {
        context.dataStore.edit { prefs -> prefs[Keys.USER_PERSONA_DEPTH] = depth }
    }

    suspend fun getCustomSystemPrompt(): String =
        context.dataStore.data.map { it[Keys.CUSTOM_SYSTEM_PROMPT] ?: "" }.first()

    suspend fun saveCustomSystemPrompt(prompt: String) {
        context.dataStore.edit { prefs -> prefs[Keys.CUSTOM_SYSTEM_PROMPT] = prompt }
    }

    // ── Auto-Continue ─────────────────────────────────────────────────────────

    val autoContinueFlow: kotlinx.coroutines.flow.Flow<Pair<Boolean, Int>> =
        context.dataStore.data.map { prefs ->
            (prefs[Keys.AUTO_CONTINUE_ENABLED] ?: false) to
                (prefs[Keys.AUTO_CONTINUE_MIN_LENGTH] ?: 200)
        }

    suspend fun getAutoContinueConfig(): Pair<Boolean, Int> = autoContinueFlow.first()

    suspend fun saveAutoContinueConfig(enabled: Boolean, minLength: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.AUTO_CONTINUE_ENABLED] = enabled
            prefs[Keys.AUTO_CONTINUE_MIN_LENGTH] = minLength
        }
    }

    val lastActivatedProfileIdFlow: kotlinx.coroutines.flow.Flow<String?> =
        context.dataStore.data.map { it[Keys.LAST_ACTIVATED_PROFILE_ID] }

    suspend fun setLastActivatedProfileId(id: String?) {
        context.dataStore.edit { prefs ->
            if (id != null) prefs[Keys.LAST_ACTIVATED_PROFILE_ID] = id
            else prefs.remove(Keys.LAST_ACTIVATED_PROFILE_ID)
        }
    }

    // ── Global Author's Note ──────────────────────────────────────────────────

    suspend fun getGlobalAuthorsNoteContent(): String =
        context.dataStore.data.map { it[Keys.GLOBAL_AUTHORS_NOTE_CONTENT] ?: "" }.first()

    suspend fun getGlobalAuthorsNoteDepth(): Int =
        context.dataStore.data.map { it[Keys.GLOBAL_AUTHORS_NOTE_DEPTH] ?: 4 }.first()

    suspend fun getGlobalAuthorsNoteInterval(): Int =
        context.dataStore.data.map { it[Keys.GLOBAL_AUTHORS_NOTE_INTERVAL] ?: 1 }.first()

    suspend fun getGlobalAuthorsNotePosition(): Int =
        context.dataStore.data.map { it[Keys.GLOBAL_AUTHORS_NOTE_POSITION] ?: 0 }.first()

    suspend fun getGlobalAuthorsNoteRole(): Int =
        context.dataStore.data.map { it[Keys.GLOBAL_AUTHORS_NOTE_ROLE] ?: 0 }.first()

    suspend fun saveGlobalAuthorsNote(
        content: String,
        depth: Int,
        interval: Int,
        position: Int,
        role: Int
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.GLOBAL_AUTHORS_NOTE_CONTENT] = content
            prefs[Keys.GLOBAL_AUTHORS_NOTE_DEPTH] = depth
            prefs[Keys.GLOBAL_AUTHORS_NOTE_INTERVAL] = interval
            prefs[Keys.GLOBAL_AUTHORS_NOTE_POSITION] = position
            prefs[Keys.GLOBAL_AUTHORS_NOTE_ROLE] = role
        }
    }

    // ── TTS ─────────────────────────────────────────────────────────────────

    val ttsConfigFlow: Flow<TtsConfig> = context.dataStore.data.map { prefs ->
        TtsConfig(
            enabled = prefs[Keys.TTS_ENABLED] ?: false,
            provider = prefs[Keys.TTS_PROVIDER] ?: "system",
            autoPlay = prefs[Keys.TTS_AUTO_PLAY] ?: true,
            openAiUrl = prefs[Keys.TTS_OPENAI_URL] ?: "",
            openAiKey = encryptedPrefs.getString(SECURE_TTS_OPENAI_KEY, null)
                ?: prefs[Keys.TTS_OPENAI_KEY] ?: "",
            openAiVoice = prefs[Keys.TTS_OPENAI_VOICE] ?: "alloy",
            openAiModel = prefs[Keys.TTS_OPENAI_MODEL] ?: "tts-1",
            speed = prefs[Keys.TTS_SPEED] ?: 1.0f,
            filterMode = prefs[Keys.TTS_FILTER_MODE] ?: "all"
        )
    }

    suspend fun getTtsConfig(): TtsConfig = ttsConfigFlow.first()

    suspend fun saveTtsConfig(config: TtsConfig) {
        encryptedPrefs.edit().putString(SECURE_TTS_OPENAI_KEY, config.openAiKey).apply()
        context.dataStore.edit { prefs ->
            prefs[Keys.TTS_ENABLED] = config.enabled
            prefs[Keys.TTS_PROVIDER] = config.provider
            prefs[Keys.TTS_AUTO_PLAY] = config.autoPlay
            prefs[Keys.TTS_OPENAI_URL] = config.openAiUrl
            prefs.remove(Keys.TTS_OPENAI_KEY)
            prefs[Keys.TTS_OPENAI_VOICE] = config.openAiVoice
            prefs[Keys.TTS_OPENAI_MODEL] = config.openAiModel
            prefs[Keys.TTS_SPEED] = config.speed
            prefs[Keys.TTS_FILTER_MODE] = config.filterMode
            prefs[Keys.SECURE_REFRESH] = (prefs[Keys.SECURE_REFRESH] ?: 0) + 1
        }
    }

    // ── Image Generation ────────────────────────────────────────────────────

    private val imageGenJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val imageGenConfigFlow: Flow<ImageGenConfig> = context.dataStore.data.map { prefs ->
        val raw = prefs[Keys.IMAGE_GEN_CONFIG]
        if (raw != null) {
            try {
                imageGenJson.decodeFromString<ImageGenConfig>(raw)
            } catch (_: Exception) {
                val oldUrl = prefs[Keys.FORGE_URL] ?: ""
                ImageGenConfig(sdWebuiUrl = oldUrl)
            }
        } else {
            val oldUrl = prefs[Keys.FORGE_URL] ?: ""
            ImageGenConfig(sdWebuiUrl = oldUrl)
        }
    }

    suspend fun getImageGenConfig(): ImageGenConfig = imageGenConfigFlow.first()

    suspend fun saveImageGenConfig(config: ImageGenConfig) {
        context.dataStore.edit { prefs ->
            prefs[Keys.IMAGE_GEN_CONFIG] = imageGenJson.encodeToString(ImageGenConfig.serializer(), config)
            // Keep forgeUrl in sync for backward compat
            prefs[Keys.FORGE_URL] = config.sdWebuiUrl.trimEnd('/')
        }
    }

    suspend fun clearAll() {
        encryptedPrefs.edit().clear().apply()
        context.dataStore.edit { prefs -> prefs.clear() }
    }

    // ── Long-Term Memory ─────────────────────────────────────────────────────

    val memoryEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.MEMORY_ENABLED] ?: true
    }

    suspend fun getMemoryEnabled(): Boolean = memoryEnabledFlow.first()

    suspend fun saveMemoryEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.MEMORY_ENABLED] = enabled }
    }
}

data class CharaVaultSession(
    val token: String,
    val email: String
)
