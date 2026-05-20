package com.pockettavern.app.ui.screens.backup

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    private val backupDirs = listOf("characters", "chats", "worlds", "backgrounds", "extensions")

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, error = null) }
            try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        ZipOutputStream(out).use { zip ->
                            for (dir in backupDirs) {
                                val folder = File(context.filesDir, dir)
                                if (!folder.exists()) continue
                                folder.walkTopDown()
                                    .filter { it.isFile }
                                    .forEach { file ->
                                        val entryName = "${dir}/${file.relativeTo(folder).path}"
                                        zip.putNextEntry(ZipEntry(entryName))
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
                            var entry = zip.nextEntry
                            while (entry != null) {
                                val name = entry.name
                                if (!entry.isDirectory && name.contains("/")) {
                                    val outFile = File(context.filesDir, name)
                                    if (!outFile.canonicalPath.startsWith(filesDir + File.separator)) {
                                        zip.closeEntry()
                                        entry = zip.nextEntry
                                        continue
                                    }
                                    outFile.parentFile?.mkdirs()
                                    outFile.outputStream().use { zip.copyTo(it) }
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
