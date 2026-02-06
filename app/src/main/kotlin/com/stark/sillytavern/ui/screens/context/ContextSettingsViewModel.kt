package com.stark.sillytavern.ui.screens.context

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stark.sillytavern.data.repository.SillyTavernRepository
import com.stark.sillytavern.domain.model.AuthorsNote
import com.stark.sillytavern.domain.model.Result
import com.stark.sillytavern.domain.model.UserPersona
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ContextSettingsUiState(
    // User Persona
    val personaName: String = "User",
    val personaDescription: String = "",
    val personaPosition: Int = 0,
    val personaDepth: Int = 2,

    // Author's Note
    val authorsNoteContent: String = "",
    val authorsNoteInterval: Int = 1,
    val authorsNoteDepth: Int = 4,
    val authorsNotePosition: Int = 0,
    val authorsNoteRole: Int = 0,

    // UI state
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ContextSettingsViewModel @Inject constructor(
    private val repository: SillyTavernRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ContextSettingsUiState())
    val uiState: StateFlow<ContextSettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    fun loadSettings() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            // Load user persona from settings
            when (val result = repository.getUserPersona()) {
                is Result.Success -> {
                    val persona = result.data
                    _uiState.update {
                        it.copy(
                            personaName = persona.name,
                            personaDescription = persona.description,
                            personaPosition = persona.position,
                            personaDepth = persona.depth,
                            isLoading = false
                        )
                    }
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = result.exception.message
                        )
                    }
                }
            }
        }
    }

    // User Persona updates
    fun updatePersonaName(name: String) {
        _uiState.update { it.copy(personaName = name) }
    }

    fun updatePersonaDescription(description: String) {
        _uiState.update { it.copy(personaDescription = description) }
    }

    fun updatePersonaPosition(position: Int) {
        _uiState.update { it.copy(personaPosition = position) }
    }

    fun updatePersonaDepth(depth: Int) {
        _uiState.update { it.copy(personaDepth = depth) }
    }

    // Author's Note updates
    fun updateAuthorsNoteContent(content: String) {
        _uiState.update { it.copy(authorsNoteContent = content) }
    }

    fun updateAuthorsNoteInterval(interval: Int) {
        _uiState.update { it.copy(authorsNoteInterval = interval) }
    }

    fun updateAuthorsNoteDepth(depth: Int) {
        _uiState.update { it.copy(authorsNoteDepth = depth) }
    }

    fun updateAuthorsNotePosition(position: Int) {
        _uiState.update { it.copy(authorsNotePosition = position) }
    }

    fun updateAuthorsNoteRole(role: Int) {
        _uiState.update { it.copy(authorsNoteRole = role) }
    }

    fun saveSettings() {
        val state = _uiState.value

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }

            val persona = UserPersona(
                name = state.personaName,
                description = state.personaDescription,
                position = state.personaPosition,
                depth = state.personaDepth
            )

            when (val result = repository.saveUserPersona(persona)) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            saveSuccess = true
                        )
                    }
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            error = result.exception.message
                        )
                    }
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun resetSaveSuccess() {
        _uiState.update { it.copy(saveSuccess = false) }
    }
}
