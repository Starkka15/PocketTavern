package com.pockettavern.app.ui.screens.ptexport

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pockettavern.app.data.local.CharacterStorage
import com.pockettavern.app.util.DebugLogger
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
import javax.inject.Inject

data class ExportProgress(
    val current: Int = 0,
    val total: Int = 0,
    val currentItem: String = ""
)

data class PtExportUiState(
    val isExporting: Boolean = false,
    val progress: ExportProgress = ExportProgress(),
    val log: List<String> = emptyList(),
    val isComplete: Boolean = false,
    val charactersExported: Int = 0,
    val lorebooksExported: Int = 0,
    val chatsExported: Int = 0,
    val errors: Int = 0
)

@HiltViewModel
class PtExportViewModel @Inject constructor(
    private val characterStorage: CharacterStorage,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(PtExportUiState())
    val uiState: StateFlow<PtExportUiState> = _uiState.asStateFlow()

    fun resetState() {
        _uiState.update {
            PtExportUiState()
        }
    }

    fun exportToFolder(treeUri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, isComplete = false, log = emptyList()) }

            withContext(Dispatchers.IO) {
                val root = DocumentFile.fromTreeUri(context, treeUri)
                if (root == null) {
                    addLog("ERROR: Could not open folder")
                    finish(errors = 1)
                    return@withContext
                }

                var chars = 0; var lorebooks = 0; var chats = 0; var errors = 0

                // ── Characters ──────────────────────────────────────────
                val charsSourceDir = File(context.filesDir, "characters")
                val pngFiles = charsSourceDir.listFiles { f -> f.extension == "png" } ?: emptyArray()
                addLog("Found ${pngFiles.size} character(s) to export")

                if (pngFiles.isNotEmpty()) {
                    val charsDestDir = root.findFile("characters") ?: root.createDirectory("characters")
                    if (charsDestDir == null) {
                        addLog("ERROR: Could not create characters/ directory")
                        errors++
                    } else {
                        pngFiles.forEachIndexed { i, file ->
                            updateProgress(i + 1, pngFiles.size, file.name)
                            try {
                                val bytes = file.readBytes()
                                val destFile = charsDestDir.createFile("image/png", file.nameWithoutExtension)
                                if (destFile != null) {
                                    context.contentResolver.openOutputStream(destFile.uri)?.use { os ->
                                        os.write(bytes)
                                    }
                                    chars++
                                    addLog("Exported character: ${file.name}")
                                } else {
                                    errors++
                                    addLog("ERROR: Could not create file for ${file.name}")
                                }
                            } catch (e: Exception) {
                                errors++
                                addLog("ERROR exporting ${file.name}: ${e.message}")
                            }
                        }
                    }
                }

                // ── Chats ───────────────────────────────────────────────
                val chatsSourceDir = File(context.filesDir, "chats")
                val charChatDirs = chatsSourceDir.listFiles { f -> f.isDirectory } ?: emptyArray()
                addLog("Found ${charChatDirs.size} character chat folder(s)")

                if (charChatDirs.isNotEmpty()) {
                    val chatsDestDir = root.findFile("chats") ?: root.createDirectory("chats")
                    if (chatsDestDir == null) {
                        addLog("ERROR: Could not create chats/ directory")
                        errors++
                    } else {
                        for (charDir in charChatDirs) {
                            val jsonlFiles = charDir.listFiles { f -> f.extension == "jsonl" } ?: continue
                            if (jsonlFiles.isEmpty()) continue

                            val charChatDestDir = chatsDestDir.findFile(charDir.name)
                                ?: chatsDestDir.createDirectory(charDir.name)
                            if (charChatDestDir == null) {
                                addLog("ERROR: Could not create chats/${charDir.name}/ directory")
                                errors++
                                continue
                            }

                            for (chatFile in jsonlFiles) {
                                try {
                                    val text = chatFile.readText()
                                    val destFile = charChatDestDir.createFile(
                                        "application/octet-stream",
                                        chatFile.name
                                    )
                                    if (destFile != null) {
                                        context.contentResolver.openOutputStream(destFile.uri)?.use { os ->
                                            os.write(text.toByteArray())
                                        }
                                        chats++
                                        addLog("Exported chat: ${charDir.name}/${chatFile.name}")
                                    } else {
                                        errors++
                                        addLog("ERROR: Could not create ${charDir.name}/${chatFile.name}")
                                    }
                                } catch (e: Exception) {
                                    errors++
                                    addLog("ERROR exporting chat ${chatFile.name}: ${e.message}")
                                }
                            }
                        }
                    }
                }

                // ── Lorebooks ───────────────────────────────────────────
                val worldsSourceDir = File(context.filesDir, "worlds")
                val jsonFiles = worldsSourceDir.listFiles { f -> f.extension == "json" } ?: emptyArray()
                addLog("Found ${jsonFiles.size} lorebook(s) to export")

                if (jsonFiles.isNotEmpty()) {
                    val worldsDestDir = root.findFile("worlds") ?: root.createDirectory("worlds")
                    if (worldsDestDir == null) {
                        addLog("ERROR: Could not create worlds/ directory")
                        errors++
                    } else {
                        for (file in jsonFiles) {
                            try {
                                val text = file.readText()
                                val destFile = worldsDestDir.createFile(
                                    "application/json",
                                    file.nameWithoutExtension
                                )
                                if (destFile != null) {
                                    context.contentResolver.openOutputStream(destFile.uri)?.use { os ->
                                        os.write(text.toByteArray())
                                    }
                                    lorebooks++
                                    addLog("Exported lorebook: ${file.name}")
                                } else {
                                    errors++
                                    addLog("ERROR: Could not create file for ${file.name}")
                                }
                            } catch (e: Exception) {
                                errors++
                                addLog("ERROR exporting lorebook ${file.name}: ${e.message}")
                            }
                        }
                    }
                }

                finish(chars = chars, lorebooks = lorebooks, chats = chats, errors = errors)
            }
        }
    }

    private fun addLog(message: String) {
        _uiState.update { it.copy(log = it.log + message) }
    }

    private fun updateProgress(current: Int, total: Int, item: String) {
        _uiState.update { it.copy(progress = ExportProgress(current, total, item)) }
    }

    private fun finish(chars: Int = 0, lorebooks: Int = 0, chats: Int = 0, errors: Int = 0) {
        _uiState.update {
            it.copy(
                isExporting = false,
                isComplete = true,
                charactersExported = chars,
                lorebooksExported = lorebooks,
                chatsExported = chats,
                errors = errors
            )
        }
        addLog("Done. Characters: $chars, Lorebooks: $lorebooks, Chats: $chats, Errors: $errors")
    }
}
