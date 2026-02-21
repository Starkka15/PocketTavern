package com.pockettavern.app.ui.screens.extensions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pockettavern.app.data.local.JsExtensionStorage
import com.pockettavern.app.extensions.ExtensionManager
import com.pockettavern.app.extensions.JsExtension
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExtensionsUiState(
    val quickReplyEnabled: Boolean = true,
    val regexEnabled: Boolean = true,
    val tokenCounterEnabled: Boolean = false,
    val jsExtensions: List<JsExtension> = emptyList(),
    val isInstalling: Boolean = false,
    val installError: String? = null
)

@HiltViewModel
class ExtensionsViewModel @Inject constructor(
    private val extensionManager: ExtensionManager,
    private val jsExtensionStorage: JsExtensionStorage
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExtensionsUiState())
    val uiState: StateFlow<ExtensionsUiState> = _uiState.asStateFlow()

    init {
        extensionManager.load()
        _uiState.update {
            it.copy(
                quickReplyEnabled   = extensionManager.quickReply.enabled,
                regexEnabled        = extensionManager.regex.enabled,
                tokenCounterEnabled = extensionManager.tokenCounter.enabled,
                jsExtensions        = jsExtensionStorage.listExtensions()
            )
        }
    }

    // ── Native extensions ─────────────────────────────────────────────────────

    fun setQuickReplyEnabled(value: Boolean) {
        extensionManager.quickReply.setEnabled(value)
        _uiState.update { it.copy(quickReplyEnabled = value) }
    }

    fun setRegexEnabled(value: Boolean) {
        extensionManager.regex.setEnabled(value)
        _uiState.update { it.copy(regexEnabled = value) }
    }

    fun setTokenCounterEnabled(value: Boolean) {
        extensionManager.tokenCounter.setEnabled(value)
        _uiState.update { it.copy(tokenCounterEnabled = value) }
    }

    // ── JS extensions ─────────────────────────────────────────────────────────

    fun installFromUrl(url: String) {
        if (url.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isInstalling = true, installError = null) }
            try {
                jsExtensionStorage.installFromUrl(url)
                extensionManager.jsHost.reload()
                _uiState.update {
                    it.copy(isInstalling = false, jsExtensions = jsExtensionStorage.listExtensions())
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isInstalling = false, installError = e.message ?: "Install failed")
                }
            }
        }
    }

    fun uninstall(id: String) {
        jsExtensionStorage.uninstall(id)
        extensionManager.jsHost.reload()
        _uiState.update { it.copy(jsExtensions = jsExtensionStorage.listExtensions()) }
    }

    fun setJsExtensionEnabled(id: String, enabled: Boolean) {
        jsExtensionStorage.setEnabled(id, enabled)
        extensionManager.jsHost.reload()
        _uiState.update { it.copy(jsExtensions = jsExtensionStorage.listExtensions()) }
    }

    fun clearInstallError() {
        _uiState.update { it.copy(installError = null) }
    }
}
