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
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Manages locally-downloaded MNN SDXL model sets. Unlike [OnDeviceModelManager]'s single-file
 * `.litertlm`/`.gguf` downloads, each SDXL "model" is a directory of ~14 files (~3.6-3.7GB,
 * see [REQUIRED_FILES] for the exact manifest MnnDiffusionEngine/stable_diffusion_xl.cpp expect)
 * -- downloads are a single zip archive, extracted into place.
 */
@Singleton
class SdxlModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
    @Named("LLM") private val okHttpClient: OkHttpClient,
) {
    private val modelsDir: File by lazy {
        File(context.filesDir, "sd-models").apply { mkdirs() }
    }

    private fun modelDir(modelId: String): File = File(modelsDir, modelId)

    private fun isComplete(dir: File): Boolean =
        REQUIRED_FILES.all { File(dir, it).let { f -> f.exists() && f.length() > 0 } }

    fun isDownloaded(modelId: String): Boolean = isComplete(modelDir(modelId))

    /** Absolute path to a downloaded model set's directory, or null if incomplete/missing. */
    fun pathFor(modelId: String): String? = modelDir(modelId).takeIf { isComplete(it) }?.absolutePath

    fun listModels(): List<AvailableModel> =
        (modelsDir.listFiles { f -> f.isDirectory } ?: emptyArray())
            .filter { isComplete(it) }
            .sortedBy { it.name }
            .map { AvailableModel(id = it.name, name = it.name) }

    fun delete(modelId: String): Boolean = modelDir(modelId).deleteRecursively()

    fun freeSpaceBytes(): Long = modelsDir.usableSpace

    private fun modelIdFromUrl(url: String): String =
        url.substringAfterLast('/').substringBefore('?').removeSuffix(".zip").ifBlank { "sdxl-model" }

    sealed class Progress {
        data class Downloading(val bytesDownloaded: Long, val totalBytes: Long) : Progress()
        data class Extracting(val filesExtracted: Int, val totalFiles: Int) : Progress()
        data class Done(val path: String) : Progress()
        data class Error(val message: String) : Progress()
    }

    /**
     * Downloads a zip of one SDXL model set from [url] and extracts it to
     * `sd-models/<modelId>/`, [modelId] taken from the zip's filename. Downloads to a `.part`
     * file and extracts to a `.part` directory, only renaming into place once every file in
     * [REQUIRED_FILES] is confirmed present -- a killed/failed run never leaves a directory that
     * looks downloaded but isn't (matching OnDeviceModelManager's atomicity for single files).
     */
    fun download(url: String, authToken: String? = null): Flow<Progress> = flow {
        val modelId = modelIdFromUrl(url)
        val tmpZip = File(modelsDir, "$modelId.zip.part")
        val tmpDir = File(modelsDir, "$modelId.part")
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
                    tmpZip.outputStream().use { output ->
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

            tmpDir.deleteRecursively()
            tmpDir.mkdirs()
            extractZip(tmpZip, tmpDir) { done, total -> emit(Progress.Extracting(done, total)) }

            if (!isComplete(tmpDir)) {
                val missing = REQUIRED_FILES.filterNot { File(tmpDir, it).let { f -> f.exists() && f.length() > 0 } }
                emit(Progress.Error("Extracted archive is missing expected files: ${missing.joinToString()}"))
                return@flow
            }

            val dest = modelDir(modelId)
            dest.deleteRecursively()
            if (!tmpDir.renameTo(dest)) {
                emit(Progress.Error("Failed to move extracted model into place"))
                return@flow
            }
            emit(Progress.Done(dest.absolutePath))
        } catch (e: Exception) {
            DebugLogger.logError("SdxlModelManager", "download failed", e)
            emit(Progress.Error(e.message ?: "Download failed"))
        } finally {
            tmpZip.delete()
            tmpDir.deleteRecursively()
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Extracts [zipFile] into [destDir]. Strips a single common leading path segment if every
     * entry shares one (handles both `zip -r archive.zip model-name/` and a flat zip of the
     * model directory's own contents -- not dictating which one the user's desktop-side zip
     * step produces). Rejects any entry that would resolve outside [destDir] (zip-slip).
     */
    private suspend fun extractZip(
        zipFile: File,
        destDir: File,
        onProgress: suspend (Int, Int) -> Unit,
    ) {
        ZipFile(zipFile).use { zip ->
            val entries = zip.entries().toList().filterNot { it.isDirectory }
            val prefix = commonLeadingSegment(entries.map { it.name })
            entries.forEachIndexed { index, entry ->
                val relativeName = if (prefix != null) entry.name.removePrefix(prefix) else entry.name
                val outFile = File(destDir, relativeName).canonicalFile
                if (!outFile.path.startsWith(destDir.canonicalFile.path + File.separator)) {
                    throw SecurityException("Zip entry escapes destination: ${entry.name}")
                }
                outFile.parentFile?.mkdirs()
                zip.getInputStream(entry).use { input ->
                    outFile.outputStream().use { output -> input.copyTo(output) }
                }
                onProgress(index + 1, entries.size)
            }
        }
    }

    private fun commonLeadingSegment(names: List<String>): String? {
        if (names.isEmpty()) return null
        val firstSegments = names.first().split('/')
        if (firstSegments.size < 2) return null
        val candidate = firstSegments[0] + "/"
        return candidate.takeIf { p -> names.all { it.startsWith(p) } }
    }

    companion object {
        val REQUIRED_FILES = listOf(
            "tokenizer/tokenizer_config.json",
            "tokenizer/tokenizer.json",
            "tokenizer/tokenizer.mtok",
            "tokenizer_2/tokenizer_config.json",
            "tokenizer_2/tokenizer.json",
            "tokenizer_2/tokenizer.mtok",
            "text_encoder.mnn",
            "text_encoder.mnn.weight",
            "text_encoder_2.mnn",
            "text_encoder_2.mnn.weight",
            "unet.mnn",
            "unet.mnn.weight",
            "vae_decoder.mnn",
            "vae_decoder.mnn.weight",
        )
    }
}
