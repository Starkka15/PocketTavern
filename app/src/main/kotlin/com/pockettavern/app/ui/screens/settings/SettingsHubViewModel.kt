package com.pockettavern.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pockettavern.app.data.repository.LocalRepository
import com.pockettavern.app.domain.model.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsHubUiState(
    val isConnected: Boolean = true,  // Always "connected" in standalone mode
    val currentPersonaName: String? = null,
    val usesChatCompletions: Boolean = false
)

@HiltViewModel
class SettingsHubViewModel @Inject constructor(
    private val localRepository: LocalRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsHubUiState())
    val uiState: StateFlow<SettingsHubUiState> = _uiState.asStateFlow()

    init {
        loadPersonaName()
        viewModelScope.launch {
            localRepository.apiConfigFlow.collect { config ->
                _uiState.value = _uiState.value.copy(usesChatCompletions = config.usesChatCompletions)
            }
        }
    }

    private fun loadPersonaName() {
        viewModelScope.launch {
            when (val result = localRepository.getUserPersona()) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(currentPersonaName = result.data.name)
                }
                is Result.Error -> { /* persona name is optional display */ }
            }
        }
    }

    fun refresh() {
        loadPersonaName()
    }
}
