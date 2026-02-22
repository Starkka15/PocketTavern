package com.pockettavern.app.ui.screens.theme

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pockettavern.app.ui.theme.ThemeEntry
import com.pockettavern.app.ui.theme.ThemeManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ThemeUiState(
    val themes: List<ThemeEntry> = emptyList(),
    val activeId: String = ThemeManager.THEME_DEFAULT,
    val importError: String? = null
)

@HiltViewModel
class ThemeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    val themeManager: ThemeManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ThemeUiState())
    val uiState: StateFlow<ThemeUiState> = _uiState.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            themeManager.activeId.collect { id ->
                _uiState.update { it.copy(activeId = id) }
            }
        }
    }

    fun refresh() {
        _uiState.update {
            it.copy(
                themes   = themeManager.listThemes(),
                activeId = themeManager.activeId.value
            )
        }
    }

    fun applyTheme(id: String) {
        themeManager.applyTheme(id)
        refresh()
    }

    fun deleteTheme(id: String) {
        themeManager.deleteTheme(id)
        refresh()
    }

    fun importFromUri(uri: Uri) {
        viewModelScope.launch {
            try {
                val id = withContext(Dispatchers.IO) {
                    val mimeType = context.contentResolver.getType(uri)
                    val isZip = mimeType == "application/zip" ||
                        mimeType == "application/x-zip-compressed" ||
                        uri.toString().endsWith(".zip", ignoreCase = true)

                    if (isZip) {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            themeManager.importThemeZip(stream)
                        } ?: throw Exception("Could not read file")
                    } else {
                        val json = context.contentResolver.openInputStream(uri)
                            ?.bufferedReader()?.readText()
                            ?: throw Exception("Could not read file")
                        themeManager.importTheme(json)
                    }
                } ?: throw Exception("Invalid theme file")
                themeManager.applyTheme(id)
                refresh()
                _uiState.update { it.copy(importError = null) }
            } catch (e: Exception) {
                _uiState.update { it.copy(importError = e.message ?: "Import failed") }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(importError = null) }
}
