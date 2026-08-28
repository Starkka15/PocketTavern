package com.pockettavern.app.data.local.inference

import android.content.Context
import android.os.Environment
import com.pockettavern.app.util.DebugLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class OnDevicePocketTtsModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
    @Named("LLM") private val okHttpClient: OkHttpClient,
) {
    val modelsDir: File by lazy {
        File(context.filesDir, "tts-models/pocket-tts").apply { mkdirs() }
    }

    val voicesDir: File by lazy {
        File(context.filesDir, "tts-voices").apply { mkdirs() }
    }

    val requiredFiles = listOf(
        "encoder.onnx",
        "decoder.int8.onnx",
        "lm_main.int8.onnx",
        "lm_flow.int8.onnx",
        "text_conditioner.onnx",
        "vocab.json",
        "token_scores.json",
    )

    private val HF_BASE_URL = "https://huggingface.co/csukuangfj2/sherpa-onnx-pocket-tts-int8-2026-01-26/resolve/main"

    init {
        extractVoicesFromAssets()
    }

    fun isModelDownloaded(): Boolean {
        ensureStagedFromDownloads()
        return requiredFiles.all { fileName ->
            val f = File(modelsDir, fileName)
            f.exists() && f.length() > 0
        }
    }

    private fun extractVoicesFromAssets() {
        try {
            val assetList = context.assets.list("tts-voices") ?: return
            for (assetName in assetList) {
                val destFile = File(voicesDir, assetName)
                if (!destFile.exists() || destFile.length() == 0L) {
                    context.assets.open("tts-voices/$assetName").use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            DebugLogger.log("OnDevicePocketTtsModelManager: Error extracting asset voices: ${e.message}")
        }
    }

    private fun ensureStagedFromDownloads() {
        try {
            val downloadFolder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "pocket-tts")
            if (downloadFolder.exists() && downloadFolder.isDirectory) {
                for (name in requiredFiles) {
                    val dest = File(modelsDir, name)
                    val src = File(downloadFolder, name)
                    if (!dest.exists() && src.exists() && src.length() > 0) {
                        src.copyTo(dest, overwrite = true)
                    }
                }
            }
        } catch (e: Exception) {
            DebugLogger.log("OnDevicePocketTtsModelManager: Staging check error: ${e.message}")
        }
    }

    fun getModelFile(fileName: String): File = File(modelsDir, fileName)

    fun listVoices(): List<String> {
        extractVoicesFromAssets()
        val voiceFiles = voicesDir.listFiles { f -> f.isFile && (f.name.endsWith(".wav") || f.name.endsWith(".safetensors")) }
            ?.map { it.nameWithoutExtension }
            ?: emptyList()

        val defaultList = listOf("cosette_calm", "cosette_happy", "cosette_narration", "milo_calm")
        return (voiceFiles + defaultList).distinct()
    }

    fun getVoiceFile(voiceName: String): File? {
        extractVoicesFromAssets()
        val wav = File(voicesDir, "$voiceName.wav")
        if (wav.exists() && wav.length() > 0) return wav
        val st = File(voicesDir, "$voiceName.safetensors")
        if (st.exists() && st.length() > 0) return st
        return null
    }

    fun deleteModel(): Boolean {
        return try {
            modelsDir.deleteRecursively()
            modelsDir.mkdirs()
            true
        } catch (e: Exception) {
            DebugLogger.logError("OnDevicePocketTtsModelManager", "Error deleting model", e)
            false
        }
    }

    sealed class DownloadProgress {
        data class Progress(val currentFile: String, val fileIndex: Int, val totalFiles: Int, val bytesDownloaded: Long, val totalBytes: Long) : DownloadProgress()
        data class Done(val modelsPath: String) : DownloadProgress()
        data class Error(val message: String) : DownloadProgress()
    }

    fun downloadModel(): Flow<DownloadProgress> = flow {
        ensureStagedFromDownloads()
        if (isModelDownloaded()) {
            emit(DownloadProgress.Done(modelsDir.absolutePath))
            return@flow
        }

        var totalBytesAll = 190L * 1024L * 1024L
        var cumulativeBytesDownloaded = 0L

        for ((index, fileName) in requiredFiles.withIndex()) {
            val destFile = File(modelsDir, fileName)
            if (destFile.exists() && destFile.length() > 0) {
                cumulativeBytesDownloaded += destFile.length()
                continue
            }

            val tmpFile = File(modelsDir, "$fileName.part")
            val url = "$HF_BASE_URL/$fileName?download=true"
            emit(DownloadProgress.Progress(fileName, index + 1, requiredFiles.size, cumulativeBytesDownloaded, totalBytesAll))

            try {
                val req = Request.Builder().url(url).build()
                okHttpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        emit(DownloadProgress.Error("Download failed for $fileName: HTTP ${resp.code}"))
                        return@flow
                    }
                    val body = resp.body ?: run {
                        emit(DownloadProgress.Error("Empty response for $fileName"))
                        return@flow
                    }

                    var fileDownloaded = 0L

                    body.byteStream().use { input ->
                        FileOutputStream(tmpFile).use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var read: Int
                            while (input.read(buffer).also { read = it } != -1) {
                                output.write(buffer, 0, read)
                                fileDownloaded += read
                                emit(DownloadProgress.Progress(
                                    fileName,
                                    index + 1,
                                    requiredFiles.size,
                                    cumulativeBytesDownloaded + fileDownloaded,
                                    totalBytesAll
                                ))
                            }
                        }
                    }

                    if (tmpFile.renameTo(destFile)) {
                        cumulativeBytesDownloaded += destFile.length()
                    } else {
                        emit(DownloadProgress.Error("Failed to rename temporary file for $fileName"))
                        return@flow
                    }
                }
            } catch (e: Exception) {
                DebugLogger.logError("OnDevicePocketTtsModelManager", "Error downloading $fileName", e)
                emit(DownloadProgress.Error("Download error: ${e.message}"))
                return@flow
            }
        }

        emit(DownloadProgress.Done(modelsDir.absolutePath))
    }.flowOn(Dispatchers.IO)
}
