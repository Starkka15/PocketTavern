package com.pockettavern.app.ui.audio

import com.pockettavern.app.data.local.inference.OnDevicePocketTtsModelManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OnDevicePocketTtsProvider @Inject constructor(
    private val engine: OnDevicePocketTtsEngine,
    private val modelManager: OnDevicePocketTtsModelManager,
) : TtsProvider {

    var defaultVoice: String = "cosette_calm"

    override suspend fun speak(text: String, voiceId: String?, speed: Float) {
        val voice = voiceId?.takeIf { it.isNotBlank() } ?: defaultVoice
        engine.speak(text, voice, speed)
    }

    override fun stop() {
        engine.stop()
    }

    override suspend fun getVoices(): List<TtsVoice> {
        val voiceNames = modelManager.listVoices()
        return voiceNames.map { name ->
            val label = when (name) {
                "cosette_calm" -> "Cosette (Calm)"
                "cosette_happy" -> "Cosette (Happy)"
                "cosette_narration" -> "Cosette (Narration)"
                "milo_calm" -> "Milo (Calm)"
                else -> name.replace('_', ' ').replaceFirstChar { it.uppercase() }
            }
            TtsVoice(id = name, name = label, language = "en")
        }
    }

    override fun isReady(): Boolean = modelManager.isModelDownloaded()

    override fun isSpeaking(): Boolean = engine.isSpeaking()
}
