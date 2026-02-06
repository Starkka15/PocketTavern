package com.stark.sillytavern.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stark.sillytavern.data.repository.SettingsRepository
import com.stark.sillytavern.data.repository.SillyTavernRepository
import com.stark.sillytavern.domain.model.Result
import com.stark.sillytavern.domain.model.ServerSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val proxyUrl: String = "",
    val forgeUrl: String = "",
    val cardVaultUrl: String = "",
    val chubEnabled: Boolean = false,
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
    private val stRepository: SillyTavernRepository
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
                    serverUrl = settings.serverUrl,
                    username = settings.username,
                    password = settings.password,
                    proxyUrl = settings.proxyUrl,
                    forgeUrl = settings.forgeUrl,
                    cardVaultUrl = settings.cardVaultUrl,
                    chubEnabled = settings.chubEnabled,
                    isLoading = false
                )
            }
        }
    }

    fun updateServerUrl(value: String) {
        _uiState.update { it.copy(serverUrl = value, testResult = null) }
    }

    fun updateUsername(value: String) {
        _uiState.update { it.copy(username = value, testResult = null) }
    }

    fun updatePassword(value: String) {
        _uiState.update { it.copy(password = value, testResult = null) }
    }

    fun updateProxyUrl(value: String) {
        _uiState.update { it.copy(proxyUrl = value) }
    }

    fun updateForgeUrl(value: String) {
        _uiState.update { it.copy(forgeUrl = value) }
    }

    fun updateCardVaultUrl(value: String) {
        _uiState.update { it.copy(cardVaultUrl = value) }
    }

    fun updateChubEnabled(value: Boolean) {
        _uiState.update { it.copy(chubEnabled = value) }
    }

    fun testConnection() {
        viewModelScope.launch {
            _uiState.update { it.copy(isTesting = true, testResult = null) }

            // First save the settings so they're available for the connection
            saveSettingsInternal()

            when (val result = stRepository.connect()) {
                is Result.Success -> {
                    // Sync our locally saved presets to the server
                    stRepository.syncLocalSettingsToServer()

                    _uiState.update {
                        it.copy(
                            isTesting = false,
                            testResult = "Connection successful!"
                        )
                    }
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            isTesting = false,
                            testResult = "Connection failed: ${result.exception.message}"
                        )
                    }
                }
            }
        }
    }

    fun saveSettings() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }

            saveSettingsInternal()

            _uiState.update {
                it.copy(
                    isSaving = false,
                    saveSuccess = true
                )
            }
        }
    }

    private suspend fun saveSettingsInternal() {
        val settings = ServerSettings(
            serverUrl = _uiState.value.serverUrl.trim(),
            username = _uiState.value.username.trim(),
            password = _uiState.value.password,
            proxyUrl = _uiState.value.proxyUrl.trim(),
            forgeUrl = _uiState.value.forgeUrl.trim(),
            cardVaultUrl = _uiState.value.cardVaultUrl.trim(),
            chubEnabled = _uiState.value.chubEnabled
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
