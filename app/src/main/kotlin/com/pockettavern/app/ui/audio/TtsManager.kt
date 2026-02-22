package com.pockettavern.app.ui.audio

import android.content.Context
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

@Singleton
class TtsManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsDataStore: SettingsDataStore,
    private val voiceStorage: TtsVoiceStorage,
    @Named("LLM") private val okHttpClient: OkHttpClient
) {
    private var systemProvider: SystemTtsProvider? = null
    private var openAiProvider: OpenAiTtsProvider? = null

    private val _speakingState = MutableStateFlow(false)
    val speakingState: StateFlow<Boolean> = _speakingState.asStateFlow()

    private fun getSystemProvider(): SystemTtsProvider {
        if (systemProvider == null) {
            systemProvider = SystemTtsProvider(context)
        }
        return systemProvider!!
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
            voiceStorage.getVoiceId(characterFile)
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
                    val provider = getSystemProvider()
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
            else -> getSystemProvider().getVoices()
        }
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
