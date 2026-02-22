package com.pockettavern.app.ui.audio

import android.media.MediaPlayer
import com.pockettavern.app.util.DebugLogger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThemeAudioManager @Inject constructor() {

    private var mediaPlayer: MediaPlayer? = null
    private var currentPath: String? = null
    private var wasPlaying = false

    fun play(audioPath: String, loop: Boolean) {
        if (audioPath == currentPath && mediaPlayer != null) {
            // Same track — just resume if paused
            try { mediaPlayer?.start() } catch (_: Exception) {}
            return
        }
        stop()
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(audioPath)
                isLooping = loop
                setOnPreparedListener { it.start() }
                setOnErrorListener { _, what, extra ->
                    DebugLogger.log("[ThemeAudio] MediaPlayer error: $what/$extra")
                    true
                }
                prepareAsync()
            }
            currentPath = audioPath
            DebugLogger.log("[ThemeAudio] Playing: $audioPath (loop=$loop)")
        } catch (e: Exception) {
            DebugLogger.log("[ThemeAudio] Play failed: ${e.message}")
        }
    }

    fun pause() {
        try {
            wasPlaying = mediaPlayer?.isPlaying == true
            if (wasPlaying) mediaPlayer?.pause()
        } catch (_: Exception) {}
    }

    fun resume() {
        try {
            if (wasPlaying) mediaPlayer?.start()
        } catch (_: Exception) {}
    }

    fun stop() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
        } catch (_: Exception) {}
        mediaPlayer = null
        currentPath = null
        wasPlaying = false
    }

    fun setVolume(volume: Float) {
        try {
            val v = volume.coerceIn(0f, 1f)
            mediaPlayer?.setVolume(v, v)
        } catch (_: Exception) {}
    }
}
