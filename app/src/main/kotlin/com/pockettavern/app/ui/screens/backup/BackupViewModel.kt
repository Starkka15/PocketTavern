package com.pockettavern.app.ui.screens.backup

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pockettavern.app.data.local.ChatStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject

data class BackupUiState(
    val isExporting: Boolean = false,
    val isImporting: Boolean = false,
    val message: String? = null,
    val error: String? = null
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val chatStorage: ChatStorage
) : ViewModel() {

    private companion object {
        // No "/" in the name, so versions without manifest support skip it on restore
        const val MANIFEST_NAME = "backup.json"
    }

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    private val backupDirs = listOf("characters", "chats", "groups", "worlds", "backgrounds", "extensions")

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, error = null) }
            try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        ZipOutputStream(out).use { zip ->
                            // Marks the entry times below as real file mtimes. Older backups
                            // have no manifest and their entry times are just the export time.
                            zip.putNextEntry(ZipEntry(MANIFEST_NAME))
                            zip.write("""{"version":2}""".toByteArray())
                            zip.closeEntry()
                            for (dir in backupDirs) {
                                val folder = File(context.filesDir, dir)
                                if (!folder.exists()) continue
                                folder.walkTopDown()
                                    .filter { it.isFile }
                                    .forEach { file ->
                                        val entryName = "${dir}/${file.relativeTo(folder).path}"
                                        // Chats are ordered by mtime, so it has to survive the round trip
                                        zip.putNextEntry(ZipEntry(entryName).apply { time = file.lastModified() })
                                        FileInputStream(file).use { it.copyTo(zip) }
                                        zip.closeEntry()
                                    }
                            }
                        }
                    }
                }
                _uiState.update { it.copy(isExporting = false, message = "Backup exported successfully") }
            } catch (e: Exception) {
                _uiState.update { it.copy(isExporting = false, error = "Export failed: ${e.message}") }
            }
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, error = null) }
            try {
                withContext(Dispatchers.IO) {
                    val filesDir = context.filesDir.canonicalPath
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        ZipInputStream(input).use { zip ->
                            var hasFileTimes = false
                            var entry = zip.nextEntry
                            while (entry != null) {
                                val name = entry.name
                                if (name == MANIFEST_NAME) hasFileTimes = true
                                if (!entry.isDirectory && name.contains("/")) {
                                    val outFile = File(context.filesDir, name)
                                    if (!outFile.canonicalPath.startsWith(filesDir + File.separator)) {
                                        zip.closeEntry()
                                        entry = zip.nextEntry
                                        continue
                                    }
                                    outFile.parentFile?.mkdirs()
                                    outFile.outputStream().use { zip.copyTo(it) }
                                    // Without this every chat gets the extraction time and the
                                    // "latest" chat for a character becomes arbitrary.
                                    val restoredTime = if (hasFileTimes) {
                                        entry.time.takeIf { it > 0 }
                                    } else if (name.startsWith("chats/") && name.endsWith(".jsonl")) {
                                        chatStorage.lastMessageTime(outFile)
                                    } else null
                                    restoredTime?.let { outFile.setLastModified(it) }
                                }
                                zip.closeEntry()
                                entry = zip.nextEntry
                            }
                        }
                    }
                }
                _uiState.update { it.copy(isImporting = false, message = "Backup restored. Restart the app to see changes.") }
            } catch (e: Exception) {
                _uiState.update { it.copy(isImporting = false, error = "Import failed: ${e.message}") }
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null, error = null) }
    }
}
