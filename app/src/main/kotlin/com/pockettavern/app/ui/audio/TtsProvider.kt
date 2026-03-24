package com.pockettavern.app.ui.audio

import java.io.File

interface TtsProvider {
    suspend fun speak(text: String, voiceId: String?, speed: Float = 1.0f)
    suspend fun synthesizeToFile(text: String, voiceId: String?, speed: Float = 1.0f, outputFile: File): Boolean
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
