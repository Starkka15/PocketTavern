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
import kotlinx.serialization.json.JsonPrimitive
import javax.inject.Inject

data class ExtensionsUiState(
    val quickReplyEnabled: Boolean = true,
    val regexEnabled: Boolean = true,
    val tokenCounterEnabled: Boolean = false,
    val jsExtensions: List<JsExtension> = emptyList(),
    val isInstalling: Boolean = false,
    val installError: String? = null,
    /** Per-extension settings: extensionId → (key → JsonPrimitive) */
    val jsExtensionSettings: Map<String, Map<String, JsonPrimitive>> = emptyMap()
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
        val extensions = jsExtensionStorage.listExtensions()
        val settings = extensions.associate { it.id to jsExtensionStorage.getExtensionSettings(it.id) }
        _uiState.update {
            it.copy(
                quickReplyEnabled   = extensionManager.quickReply.enabled,
                regexEnabled        = extensionManager.regex.enabled,
                tokenCounterEnabled = extensionManager.tokenCounter.enabled,
                jsExtensions        = extensions,
                jsExtensionSettings = settings
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
                refreshJsExtensions()
                _uiState.update { it.copy(isInstalling = false) }
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
        refreshJsExtensions()
    }

    fun setJsExtensionEnabled(id: String, enabled: Boolean) {
        jsExtensionStorage.setEnabled(id, enabled)
        extensionManager.jsHost.reload()
        refreshJsExtensions()
    }

    fun clearInstallError() {
        _uiState.update { it.copy(installError = null) }
    }

    // ── JS extension settings ─────────────────────────────────────────────────

    fun updateJsSetting(extensionId: String, key: String, value: JsonPrimitive) {
        // Persist to disk
        jsExtensionStorage.updateExtensionSetting(extensionId, key, value)
        // Push to running JS sandbox
        extensionManager.jsHost.updateSettingInSandbox(extensionId, key, value.toString())
        // Update UI state
        val currentSettings = _uiState.value.jsExtensionSettings.toMutableMap()
        val extSettings = (currentSettings[extensionId] ?: emptyMap()).toMutableMap()
        extSettings[key] = value
        currentSettings[extensionId] = extSettings
        _uiState.update { it.copy(jsExtensionSettings = currentSettings) }
    }

    private fun refreshJsExtensions() {
        val extensions = jsExtensionStorage.listExtensions()
        val settings = extensions.associate { it.id to jsExtensionStorage.getExtensionSettings(it.id) }
        _uiState.update { it.copy(jsExtensions = extensions, jsExtensionSettings = settings) }
    }
}
