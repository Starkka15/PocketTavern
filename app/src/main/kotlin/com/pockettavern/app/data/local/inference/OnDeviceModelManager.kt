package com.pockettavern.app.data.local.inference

import android.content.Context
import com.pockettavern.app.domain.model.AvailableModel
import com.pockettavern.app.util.DebugLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Manages locally-downloaded LiteRT-LM model files (`.litertlm`). PocketTavern never bundles
 * models — the user downloads them through the app, so each model's license is accepted at
 * download time (gated repos like Gemma still go through the host's own gate / token).
 */
@Singleton
class OnDeviceModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
    @Named("LLM") private val okHttpClient: OkHttpClient,
) {
    private val modelsDir: File by lazy {
        File(context.filesDir, "litertlm-models").apply { mkdirs() }
    }

    /** Model files for a backend keep their real extension. modelId = name w/o extension. */
    private fun modelFiles(exts: Set<String>): List<File> =
        modelsDir.listFiles { f -> f.isFile && exts.any { f.name.endsWith(it) } }?.toList() ?: emptyList()

    /** The actual file backing a modelId (matched by base name), or null. */
    private fun fileFor(modelId: String, exts: Set<String>): File? =
        modelFiles(exts).firstOrNull { it.nameWithoutExtension == modelId }

    private fun fileNameFromUrl(url: String): String =
        url.substringAfterLast('/').substringBefore('?').ifBlank { "model.litertlm" }

    /** Absolute path to a downloaded model, or null if it isn't present. */
    fun pathFor(modelId: String, exts: Set<String> = ALL_EXTS): String? =
        fileFor(modelId, exts)?.takeIf { it.exists() && it.length() > 0 }?.absolutePath

    fun isDownloaded(modelId: String, exts: Set<String> = ALL_EXTS): Boolean = pathFor(modelId, exts) != null

    /** Downloaded models for a backend, as AvailableModel for the config UI. */
    fun listModels(exts: Set<String> = ALL_EXTS): List<AvailableModel> =
        modelFiles(exts).sortedBy { it.name }
            .map { AvailableModel(id = it.nameWithoutExtension, name = it.nameWithoutExtension) }

    fun delete(modelId: String, exts: Set<String> = ALL_EXTS): Boolean = fileFor(modelId, exts)?.delete() ?: false

    companion object {
        val LITERTLM_EXTS = setOf(".litertlm", ".task")
        val GGUF_EXTS = setOf(".gguf")
        val ALL_EXTS = LITERTLM_EXTS + GGUF_EXTS
    }

    /** Bytes free on the models volume — used to warn before a large download. */
    fun freeSpaceBytes(): Long = modelsDir.usableSpace

    /** Short device summary for the on-device UI (so users can gauge expected speed). */
    val deviceSummary: String
        get() = "%.0f GB RAM · %d cores".format(
            DeviceCapabilities.totalRamGb(context), DeviceCapabilities.cores()
        )

    sealed class Progress {
        data class Downloading(val bytesDownloaded: Long, val totalBytes: Long) : Progress()
        data class Done(val path: String) : Progress()
        data class Error(val message: String) : Progress()
    }

    /**
     * Download a `.litertlm` model from [url] to local storage, streaming progress. [authToken]
     * is sent as a Bearer header for gated hosts (e.g. HuggingFace gated repos). Downloads to a
     * `.part` file and renames on success so partial files are never treated as valid models.
     */
    fun download(url: String, authToken: String? = null): Flow<Progress> = flow {
        val dest = File(modelsDir, fileNameFromUrl(url))
        val tmp = File(dest.parentFile, "${dest.name}.part")
        try {
            val reqBuilder = Request.Builder().url(url)
            if (!authToken.isNullOrBlank()) reqBuilder.addHeader("Authorization", "Bearer $authToken")
            okHttpClient.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) {
                    emit(Progress.Error("Download failed: HTTP ${resp.code}"))
                    return@flow
                }
                val body = resp.body ?: run { emit(Progress.Error("Empty response")); return@flow }
                val total = body.contentLength()
                var downloaded = 0L
                body.byteStream().use { input ->
                    tmp.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buf)
                            if (read < 0) break
                            output.write(buf, 0, read)
                            downloaded += read
                            emit(Progress.Downloading(downloaded, total))
                        }
                    }
                }
            }
            if (tmp.exists() && tmp.length() > 0) {
                if (dest.exists()) dest.delete()
                tmp.renameTo(dest)
                emit(Progress.Done(dest.absolutePath))
            } else {
                emit(Progress.Error("Downloaded file was empty"))
            }
        } catch (e: Exception) {
            DebugLogger.logError("OnDeviceModelManager", "download failed", e)
            tmp.delete()
            emit(Progress.Error(e.message ?: "Download failed"))
        }
    }.flowOn(Dispatchers.IO)
}
