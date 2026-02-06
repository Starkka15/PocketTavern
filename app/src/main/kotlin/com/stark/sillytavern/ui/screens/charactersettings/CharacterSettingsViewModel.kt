package com.stark.sillytavern.ui.screens.charactersettings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stark.sillytavern.data.repository.SillyTavernRepository
import com.stark.sillytavern.domain.model.Character
import com.stark.sillytavern.domain.model.Result
import com.stark.sillytavern.domain.model.WorldInfoListItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CharacterSettingsUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val character: Character? = null,
    val avatarUrl: String = "",
    // Editable fields
    val systemPrompt: String = "",
    val postHistoryInstructions: String = "",
    val attachedWorldInfo: String? = null,
    val depthPrompt: String = "",
    val depthPromptDepth: Int = 4,
    val depthPromptRole: String = "system",
    val talkativeness: Float = 0.5f,
    val isFavorite: Boolean = false,
    // Available world info files
    val availableWorldInfo: List<WorldInfoListItem> = emptyList(),
    // Messages
    val error: String? = null,
    val saveSuccess: Boolean = false
)

@HiltViewModel
class CharacterSettingsViewModel @Inject constructor(
    private val repository: SillyTavernRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(CharacterSettingsUiState())
    val uiState: StateFlow<CharacterSettingsUiState> = _uiState.asStateFlow()

    private val avatarUrl: String = savedStateHandle.get<String>("avatarUrl") ?: ""

    init {
        loadCharacter()
        loadAvailableWorldInfo()
    }

    private fun loadCharacter() {
        if (avatarUrl.isBlank()) {
            _uiState.update { it.copy(isLoading = false, error = "No character specified") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, avatarUrl = avatarUrl) }

            when (val result = repository.getCharacter(avatarUrl)) {
                is Result.Success -> {
                    val character = result.data
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            character = character,
                            systemPrompt = character.systemPrompt,
                            postHistoryInstructions = character.postHistoryInstructions,
                            attachedWorldInfo = character.attachedWorldInfo,
                            depthPrompt = character.depthPrompt,
                            depthPromptDepth = character.depthPromptDepth,
                            depthPromptRole = character.depthPromptRole,
                            talkativeness = character.talkativeness,
                            isFavorite = character.isFavorite
                        )
                    }
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(isLoading = false, error = result.exception.message)
                    }
                }
            }
        }
    }

    private fun loadAvailableWorldInfo() {
        viewModelScope.launch {
            when (val result = repository.getWorldInfoList()) {
                is Result.Success -> {
                    _uiState.update { it.copy(availableWorldInfo = result.data) }
                }
                is Result.Error -> {
                    // Not critical, just log
                }
            }
        }
    }

    fun updateSystemPrompt(value: String) {
        _uiState.update { it.copy(systemPrompt = value) }
    }

    fun updatePostHistoryInstructions(value: String) {
        _uiState.update { it.copy(postHistoryInstructions = value) }
    }

    fun updateAttachedWorldInfo(value: String?) {
        _uiState.update { it.copy(attachedWorldInfo = value) }
    }

    fun updateDepthPrompt(value: String) {
        _uiState.update { it.copy(depthPrompt = value) }
    }

    fun updateDepthPromptDepth(value: Int) {
        _uiState.update { it.copy(depthPromptDepth = value.coerceIn(0, 999)) }
    }

    fun updateDepthPromptRole(value: String) {
        _uiState.update { it.copy(depthPromptRole = value) }
    }

    fun updateTalkativeness(value: Float) {
        _uiState.update { it.copy(talkativeness = value.coerceIn(0f, 1f)) }
    }

    fun updateIsFavorite(value: Boolean) {
        _uiState.update { it.copy(isFavorite = value) }
    }

    fun saveSettings() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }

            val state = _uiState.value
            when (val result = repository.updateCharacterSettings(
                avatarUrl = avatarUrl,
                systemPrompt = state.systemPrompt,
                postHistoryInstructions = state.postHistoryInstructions,
                attachedWorldInfo = state.attachedWorldInfo,
                depthPrompt = state.depthPrompt,
                depthPromptDepth = state.depthPromptDepth,
                depthPromptRole = state.depthPromptRole,
                talkativeness = state.talkativeness,
                isFavorite = state.isFavorite
            )) {
                is Result.Success -> {
                    _uiState.update { it.copy(isSaving = false, saveSuccess = true) }
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(isSaving = false, error = result.exception.message)
                    }
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearSaveSuccess() {
        _uiState.update { it.copy(saveSuccess = false) }
    }
}
