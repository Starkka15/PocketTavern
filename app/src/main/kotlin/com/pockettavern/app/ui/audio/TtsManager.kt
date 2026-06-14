package com.pockettavern.app.ui.audio

import android.content.Context
import android.content.Intent
import com.pockettavern.app.data.local.SettingsDataStore
import com.pockettavern.app.data.local.TtsVoiceStorage
import com.pockettavern.app.util.DebugLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

data class TtsEngineInfo(
    val packageName: String,
    val label: String
)

@Singleton
class TtsManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsDataStore: SettingsDataStore,
    private val voiceStorage: TtsVoiceStorage,
    @Named("LLM") private val okHttpClient: OkHttpClient
) {
    private var systemProvider: SystemTtsProvider? = null
    private var systemEngine: String? = null
    private var openAiProvider: OpenAiTtsProvider? = null

    private val _speakingState = MutableStateFlow(false)
    val speakingState: StateFlow<Boolean> = _speakingState.asStateFlow()

    private fun getSystemProvider(engine: String?): SystemTtsProvider {
        if (systemProvider == null || systemEngine != engine) {
            systemProvider?.shutdown()
            systemEngine = engine
            systemProvider = SystemTtsProvider(context, engine)
        }
        return systemProvider!!
    }

    fun getSystemEngines(): List<TtsEngineInfo> {
        try {
            val intent = Intent("android.intent.action.TTS_SERVICE")
            return context.packageManager.queryIntentServices(intent, 0).map { info ->
                TtsEngineInfo(
                    packageName = info.serviceInfo.packageName,
                    label = info.loadLabel(context.packageManager).toString()
                )
            }
        } catch (e: Exception) {
            DebugLogger.log("[TtsManager] Failed to query TTS engines: ${e.message}")
            return emptyList()
        }
    }

    private fun getOpenAiProvider(): OpenAiTtsProvider {
        if (openAiProvider == null) {
            openAiProvider = OpenAiTtsProvider(context, okHttpClient)
        }
        return openAiProvider!!
    }

    suspend fun speak(text: String, characterFile: String?) {
        val config = settingsDataStore.getTtsConfig()
        if (!config.enabled) return

        val filteredText = TtsTextFilter.filter(text, config.filterMode)
        if (filteredText.isBlank()) return

        // Determine provider: per-character override or global
        val providerName = if (characterFile != null) {
            voiceStorage.getProviderOverride(characterFile) ?: config.provider
        } else {
            config.provider
        }

        // Determine voice: per-character or global default
        val voiceId = if (characterFile != null) {
            voiceStorage.getVoiceId(characterFile) ?: 
                if (config.systemVoice.isNotEmpty()) config.systemVoice else null
        } else if (config.systemVoice.isNotEmpty()) {
            config.systemVoice
        } else null

        _speakingState.value = true
        try {
            when (providerName) {
                "openai" -> {
                    val provider = getOpenAiProvider()
                    provider.apiUrl = config.openAiUrl
                    provider.apiKey = config.openAiKey
                    provider.model = config.openAiModel
                    val voice = voiceId ?: config.openAiVoice
                    provider.speak(filteredText, voice, config.speed)
                }
                else -> {
                    val engine = if (config.systemEngine.isNotEmpty()) config.systemEngine else null
                    val provider = getSystemProvider(engine)
                    provider.speak(filteredText, voiceId, config.speed)
                }
            }
        } catch (e: Exception) {
            DebugLogger.log("[TtsManager] Speak failed: ${e.message}")
        } finally {
            _speakingState.value = false
        }
    }

    fun stop() {
        systemProvider?.stop()
        openAiProvider?.stop()
        _speakingState.value = false
    }

    suspend fun getVoices(): List<TtsVoice> {
        val config = settingsDataStore.getTtsConfig()
        return getVoicesForProvider(config.provider)
    }

    suspend fun getVoicesForProvider(provider: String): List<TtsVoice> {
        return when (provider) {
            "openai" -> {
                val config = settingsDataStore.getTtsConfig()
                val p = getOpenAiProvider()
                p.apiUrl = config.openAiUrl
                p.apiKey = config.openAiKey
                p.model = config.openAiModel
                p.getVoices()
            }
            else -> {
                val config = settingsDataStore.getTtsConfig()
                val engine = if (config.systemEngine.isNotEmpty()) config.systemEngine else null
                getSystemProvider(engine).getVoices()
            }
        }
    }

    suspend fun getVoicesForSystemEngine(engine: String?): List<TtsVoice> {
        val temp = SystemTtsProvider(context, engine)
        if (temp.waitForReady()) {
            val voices = temp.getVoices()
            temp.shutdown()
            return voices
        }
        temp.shutdown()
        return emptyList()
    }

    fun isSpeaking(): Boolean =
        systemProvider?.isSpeaking() == true || openAiProvider?.isSpeaking() == true

    fun shutdown() {
        systemProvider?.shutdown()
        openAiProvider?.stop()
        systemProvider = null
        openAiProvider = null
    }
}
