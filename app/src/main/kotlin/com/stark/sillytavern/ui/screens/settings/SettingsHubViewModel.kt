package com.stark.sillytavern.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stark.sillytavern.data.repository.SillyTavernRepository
import com.stark.sillytavern.domain.model.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsHubUiState(
    val isConnected: Boolean = false,
    val currentPersonaName: String? = null
)

@HiltViewModel
class SettingsHubViewModel @Inject constructor(
    private val repository: SillyTavernRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsHubUiState())
    val uiState: StateFlow<SettingsHubUiState> = _uiState.asStateFlow()

    init {
        checkConnection()
        loadCurrentPersona()
    }

    private fun checkConnection() {
        viewModelScope.launch {
            // Try to get API configuration - if successful, we're connected
            val isConnected = when (repository.getApiConfiguration()) {
                is Result.Success -> true
                is Result.Error -> false
            }
            _uiState.value = _uiState.value.copy(isConnected = isConnected)
        }
    }

    private fun loadCurrentPersona() {
        viewModelScope.launch {
            when (val result = repository.getPersonas()) {
                is Result.Success -> {
                    val selected = result.data.find { it.isSelected }
                    _uiState.value = _uiState.value.copy(currentPersonaName = selected?.name)
                }
                is Result.Error -> {
                    // Ignore errors, just don't show persona
                }
            }
        }
    }

    fun refresh() {
        checkConnection()
        loadCurrentPersona()
    }
}
