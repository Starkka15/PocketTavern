package com.pockettavern.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pockettavern.app.data.repository.LocalRepository
import com.pockettavern.app.data.repository.LlmRepository
import com.pockettavern.app.data.repository.SettingsRepository
import com.pockettavern.app.domain.model.ServerSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val forgeUrl: String = "",
    val charaVaultUrl: String = "",
    val charavaultMode: String = "local",
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isTesting: Boolean = false,
    val testResult: String? = null,
    val error: String? = null,
    val saveSuccess: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val localRepository: LocalRepository,
    private val llmRepository: LlmRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val settings = settingsRepository.getSettings()
            _uiState.update {
                it.copy(
                    charaVaultUrl = settings.charaVaultUrl,
                    charavaultMode = settings.charavaultMode,
                    forgeUrl = settings.forgeUrl,
                    isLoading = false
                )
            }
        }
    }

    fun updateForgeUrl(value: String) {
        _uiState.update { it.copy(forgeUrl = value) }
    }

    fun updateCharaVaultUrl(value: String) {
        _uiState.update { it.copy(charaVaultUrl = value) }
    }

    fun testConnection() {
        viewModelScope.launch {
            _uiState.update { it.copy(isTesting = true, testResult = null) }

            saveSettingsInternal()

            try {
                val config = localRepository.getApiConfiguration().let {
                    when (it) {
                        is com.pockettavern.app.domain.model.Result.Success -> it.data
                        is com.pockettavern.app.domain.model.Result.Error -> null
                    }
                }
                if (config == null) {
                    _uiState.update { it.copy(isTesting = false, testResult = "No API configured") }
                    return@launch
                }
                val ok = llmRepository.testConnection(config)
                _uiState.update {
                    it.copy(
                        isTesting = false,
                        testResult = if (ok) "Connection successful!" else "Connection failed: server did not respond"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isTesting = false, testResult = "Connection failed: ${e.message}")
                }
            }
        }
    }

    fun saveSettings() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            saveSettingsInternal()
            _uiState.update { it.copy(isSaving = false, saveSuccess = true) }
        }
    }

    private suspend fun saveSettingsInternal() {
        val settings = ServerSettings(
            forgeUrl = _uiState.value.forgeUrl.trim(),
            charaVaultUrl = _uiState.value.charaVaultUrl.trim(),
            charavaultMode = _uiState.value.charavaultMode
        )
        settingsRepository.saveSettings(settings)
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun resetSaveSuccess() {
        _uiState.update { it.copy(saveSuccess = false) }
    }
}
