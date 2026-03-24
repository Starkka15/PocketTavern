package com.pockettavern.app.ui.screens.context

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pockettavern.app.data.repository.LocalRepository
import com.pockettavern.app.domain.model.AuthorsNote
import com.pockettavern.app.domain.model.Result
import com.pockettavern.app.domain.model.UserPersona
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

    // Author's Note (stored per-chat in chat metadata, not globally)
    val authorsNoteContent: String = "",
    val authorsNoteInterval: Int = 1,
    val authorsNoteDepth: Int = 4,
    val authorsNotePosition: Int = 0,
    val authorsNoteRole: Int = 0,

    // Auto-Continue
    val autoContinueEnabled: Boolean = false,
    val autoContinueMinLength: Int = 200,

    // UI state
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ContextSettingsViewModel @Inject constructor(
    private val localRepository: LocalRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ContextSettingsUiState())
    val uiState: StateFlow<ContextSettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
        viewModelScope.launch {
            localRepository.autoContinueFlow.collect { (enabled, minLength) ->
                _uiState.update { it.copy(autoContinueEnabled = enabled, autoContinueMinLength = minLength) }
            }
        }
    }

    fun loadSettings() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            when (val result = localRepository.getUserPersona()) {
                is Result.Success -> {
                    val persona = result.data
                    val note = localRepository.getGlobalAuthorsNote()
                    _uiState.update {
                        it.copy(
                            personaName = persona.name,
                            personaDescription = persona.description,
                            personaPosition = persona.position,
                            personaDepth = persona.depth,
                            authorsNoteContent = note.content,
                            authorsNoteInterval = note.interval,
                            authorsNoteDepth = note.depth,
                            authorsNotePosition = note.position,
                            authorsNoteRole = note.role,
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

    // Auto-Continue updates
    fun updateAutoContinueEnabled(enabled: Boolean) {
        _uiState.update { it.copy(autoContinueEnabled = enabled) }
    }

    fun updateAutoContinueMinLength(length: Int) {
        _uiState.update { it.copy(autoContinueMinLength = length) }
    }

    fun saveSettings() {
        val state = _uiState.value

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }

            try {
                val persona = UserPersona(
                    name = state.personaName,
                    description = state.personaDescription,
                    position = state.personaPosition,
                    depth = state.personaDepth
                )
                localRepository.saveUserPersona(persona)
                localRepository.saveAutoContinueConfig(state.autoContinueEnabled, state.autoContinueMinLength)
                localRepository.saveGlobalAuthorsNote(AuthorsNote(
                    content = state.authorsNoteContent,
                    interval = state.authorsNoteInterval,
                    depth = state.authorsNoteDepth,
                    position = state.authorsNotePosition,
                    role = state.authorsNoteRole
                ))
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        saveSuccess = true
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        error = e.message
                    )
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
