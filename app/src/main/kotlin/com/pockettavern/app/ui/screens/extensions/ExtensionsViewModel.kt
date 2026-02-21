package com.pockettavern.app.ui.screens.extensions

import androidx.lifecycle.ViewModel
import com.pockettavern.app.extensions.ExtensionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class ExtensionsUiState(
    val quickReplyEnabled: Boolean = true,
    val regexEnabled: Boolean = true,
    val tokenCounterEnabled: Boolean = false
)

@HiltViewModel
class ExtensionsViewModel @Inject constructor(
    private val extensionManager: ExtensionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExtensionsUiState())
    val uiState: StateFlow<ExtensionsUiState> = _uiState.asStateFlow()

    init {
        extensionManager.load()
        _uiState.update {
            it.copy(
                quickReplyEnabled = extensionManager.quickReply.enabled,
                regexEnabled = extensionManager.regex.enabled,
                tokenCounterEnabled = extensionManager.tokenCounter.enabled
            )
        }
    }

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
}
