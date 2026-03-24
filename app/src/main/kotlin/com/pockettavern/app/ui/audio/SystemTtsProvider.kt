package com.pockettavern.app.ui.audio

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.pockettavern.app.util.DebugLogger
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume

class SystemTtsProvider(context: Context) : TtsProvider {

    private var tts: TextToSpeech? = null
    private var ready = false
    private var speaking = false

    init {
        tts = TextToSpeech(context) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                tts?.language = Locale.getDefault()
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) { speaking = true }
                    override fun onDone(utteranceId: String?) { speaking = false }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) { speaking = false }
                    override fun onError(utteranceId: String?, errorCode: Int) { speaking = false }
                })
                DebugLogger.log("[SystemTTS] Initialized successfully")
            } else {
                DebugLogger.log("[SystemTTS] Initialization failed with status: $status")
            }
        }
    }

    override suspend fun speak(text: String, voiceId: String?, speed: Float) {
        val engine = tts ?: return
        if (!ready) return

        engine.setSpeechRate(speed)

        // Set voice if specified
        if (voiceId != null) {
            val voice = engine.voices?.find { it.name == voiceId }
            if (voice != null) engine.voice = voice
        }

        val utteranceId = UUID.randomUUID().toString()
        suspendCancellableCoroutine { cont ->
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) { speaking = true }
                override fun onDone(id: String?) {
                    speaking = false
                    if (cont.isActive) cont.resume(Unit)
                }
                @Deprecated("Deprecated in Java")
                override fun onError(id: String?) {
                    speaking = false
                    if (cont.isActive) cont.resume(Unit)
                }
                override fun onError(id: String?, errorCode: Int) {
                    speaking = false
                    if (cont.isActive) cont.resume(Unit)
                }
            })
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            cont.invokeOnCancellation { engine.stop(); speaking = false }
        }
    }

    override suspend fun synthesizeToFile(text: String, voiceId: String?, speed: Float, outputFile: File): Boolean {
        val engine = tts ?: return false
        if (!ready) return false

        engine.setSpeechRate(speed)

        if (voiceId != null) {
            val voice = engine.voices?.find { it.name == voiceId }
            if (voice != null) engine.voice = voice
        }

        val utteranceId = UUID.randomUUID().toString()
        return suspendCancellableCoroutine { cont ->
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) {
                    if (cont.isActive) cont.resume(true)
                }
                @Deprecated("Deprecated in Java")
                override fun onError(id: String?) {
                    DebugLogger.log("[SystemTTS] synthesizeToFile error")
                    if (cont.isActive) cont.resume(false)
                }
                override fun onError(id: String?, errorCode: Int) {
                    DebugLogger.log("[SystemTTS] synthesizeToFile error: $errorCode")
                    if (cont.isActive) cont.resume(false)
                }
            })
            outputFile.parentFile?.mkdirs()
            engine.synthesizeToFile(text, Bundle(), outputFile, utteranceId)
            cont.invokeOnCancellation { engine.stop() }
        }
    }

    override fun stop() {
        tts?.stop()
        speaking = false
    }

    override suspend fun getVoices(): List<TtsVoice> {
        val engine = tts ?: return emptyList()
        if (!ready) return emptyList()
        return engine.voices?.map { voice ->
            TtsVoice(
                id = voice.name,
                name = voice.name,
                language = voice.locale.displayName
            )
        }?.sortedBy { it.language } ?: emptyList()
    }

    override fun isReady(): Boolean = ready

    override fun isSpeaking(): Boolean = speaking

    fun shutdown() {
        tts?.shutdown()
        tts = null
        ready = false
        speaking = false
    }
}
