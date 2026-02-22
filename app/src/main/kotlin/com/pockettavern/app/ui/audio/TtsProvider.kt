package com.pockettavern.app.ui.audio

interface TtsProvider {
    suspend fun speak(text: String, voiceId: String?, speed: Float = 1.0f)
    fun stop()
    suspend fun getVoices(): List<TtsVoice>
    fun isReady(): Boolean
    fun isSpeaking(): Boolean
}

data class TtsVoice(
    val id: String,
    val name: String,
    val language: String = ""
)
