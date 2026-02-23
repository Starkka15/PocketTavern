package com.pockettavern.app.ui.audio

import android.content.Context
import android.media.MediaPlayer
import com.pockettavern.app.util.DebugLogger
import kotlinx.serialization.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import kotlin.coroutines.resume

class OpenAiTtsProvider(
    private val context: Context,
    private val okHttpClient: OkHttpClient
) : TtsProvider {

    var apiUrl: String = ""
    var apiKey: String = ""
    var model: String = "tts-1"

    private var mediaPlayer: MediaPlayer? = null
    private var speaking = false

    override suspend fun speak(text: String, voiceId: String?, speed: Float) {
        if (apiUrl.isBlank()) return
        stop()

        val voice = voiceId ?: "alloy"
        val url = "${apiUrl.trimEnd('/')}/v1/audio/speech"
        val jsonBody = """{"model":"$model","input":${escapeJson(text)},"voice":"$voice","speed":$speed}"""

        try {
            val audioBytes = withContext(Dispatchers.IO) {
                val request = Request.Builder()
                    .url(url)
                    .post(jsonBody.toRequestBody("application/json".toMediaType()))
                    .apply {
                        if (apiKey.isNotBlank()) addHeader("Authorization", "Bearer $apiKey")
                    }
                    .build()

                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    DebugLogger.log("[OpenAiTTS] API error: ${response.code} ${response.message}")
                    return@withContext null
                }
                response.body?.bytes()
            } ?: return

            // Write to temp file and play with MediaPlayer
            val tempFile = File(context.cacheDir, "tts_audio_${System.currentTimeMillis()}.mp3")
            withContext(Dispatchers.IO) { tempFile.writeBytes(audioBytes) }

            suspendCancellableCoroutine { cont ->
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(tempFile.absolutePath)
                    setOnPreparedListener {
                        speaking = true
                        start()
                    }
                    setOnCompletionListener {
                        speaking = false
                        release()
                        mediaPlayer = null
                        tempFile.delete()
                        if (cont.isActive) cont.resume(Unit)
                    }
                    setOnErrorListener { _, what, extra ->
                        DebugLogger.log("[OpenAiTTS] MediaPlayer error: $what/$extra")
                        speaking = false
                        release()
                        mediaPlayer = null
                        tempFile.delete()
                        if (cont.isActive) cont.resume(Unit)
                        true
                    }
                    prepareAsync()
                }
                cont.invokeOnCancellation { stop(); tempFile.delete() }
            }
        } catch (e: Exception) {
            DebugLogger.log("[OpenAiTTS] Speak failed: ${e.message}")
            speaking = false
        }
    }

    override fun stop() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
        } catch (_: Exception) {}
        mediaPlayer = null
        speaking = false
    }

    override suspend fun getVoices(): List<TtsVoice> {
        // Try fetching from server (Kokoro, AllTalk, etc. expose /v1/audio/voices)
        if (apiUrl.isNotBlank()) {
            try {
                val serverVoices = withContext(Dispatchers.IO) { fetchVoicesFromServer() }
                if (serverVoices.isNotEmpty()) return serverVoices
            } catch (e: Exception) {
                DebugLogger.log("[OpenAiTTS] Failed to fetch voices from server: ${e.message}")
            }
        }
        // Fallback: standard OpenAI voices
        return OPENAI_DEFAULT_VOICES
    }

    private fun fetchVoicesFromServer(): List<TtsVoice> {
        val baseUrl = apiUrl.trimEnd('/')
        // Try standard OpenAI-compatible path first, then fallback
        val paths = listOf("/v1/audio/voices", "/v1/voices")
        var body: String? = null
        for (path in paths) {
            val request = Request.Builder().url("$baseUrl$path")
                .apply { if (apiKey.isNotBlank()) addHeader("Authorization", "Bearer $apiKey") }
                .build()
            try {
                val response = okHttpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    body = response.body?.string()
                    if (!body.isNullOrBlank()) break
                }
            } catch (e: Exception) {
                DebugLogger.log("[OpenAiTTS] Failed to fetch from $path: ${e.message}")
            }
        }
        if (body.isNullOrBlank()) return emptyList()

        // Parse response — handles {"voices": ["name1", "name2"]} format (Kokoro)
        // and {"voices": [{"id": "...", "name": "..."}]} format
        return try {
            val jsonElement = jsonParser.parseToJsonElement(body)
            val voicesArray = jsonElement.jsonObject["voices"]?.jsonArray ?: return emptyList()
            voicesArray.mapNotNull { elem ->
                when (elem) {
                    is kotlinx.serialization.json.JsonPrimitive -> {
                        val name = elem.content
                        TtsVoice(id = name, name = name)
                    }
                    is kotlinx.serialization.json.JsonObject -> {
                        val id = elem["id"]?.jsonPrimitive?.contentOrNull
                            ?: elem["voice_id"]?.jsonPrimitive?.contentOrNull
                            ?: return@mapNotNull null
                        val displayName = elem["name"]?.jsonPrimitive?.contentOrNull ?: id
                        TtsVoice(id = id, name = displayName)
                    }
                    else -> null
                }
            }
        } catch (e: Exception) {
            DebugLogger.log("[OpenAiTTS] Voice parse error: ${e.message}")
            emptyList()
        }
    }

    companion object {
        private val jsonParser = Json { ignoreUnknownKeys = true; isLenient = true }
        val OPENAI_DEFAULT_VOICES = listOf(
            TtsVoice("alloy", "Alloy"),
            TtsVoice("echo", "Echo"),
            TtsVoice("fable", "Fable"),
            TtsVoice("onyx", "Onyx"),
            TtsVoice("nova", "Nova"),
            TtsVoice("shimmer", "Shimmer")
        )
    }

    override fun isReady(): Boolean = apiUrl.isNotBlank()

    override fun isSpeaking(): Boolean = speaking

    private fun escapeJson(text: String): String {
        val sb = StringBuilder("\"")
        for (c in text) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> sb.append(c)
            }
        }
        sb.append("\"")
        return sb.toString()
    }
}
