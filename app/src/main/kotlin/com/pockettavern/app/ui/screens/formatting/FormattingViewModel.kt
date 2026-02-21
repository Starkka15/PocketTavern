package com.pockettavern.app.ui.screens.formatting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pockettavern.app.data.repository.LocalRepository
import com.pockettavern.app.domain.model.FormattingSettings
import com.pockettavern.app.domain.model.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FormattingUiState(
    val instructPresets: List<String> = emptyList(),
    val selectedInstructIndex: Int = 0,
    val contextPresets: List<String> = emptyList(),
    val selectedContextIndex: Int = 0,
    val systemPromptPresets: List<String> = emptyList(),
    val selectedSyspromptIndex: Int = 0,
    // Editable content for the currently selected system prompt preset
    val syspromptContent: String = "",
    val syspromptIsUserPreset: Boolean = false,
    val syspromptSaving: Boolean = false,
    // New-preset dialog
    val showNewSyspromptDialog: Boolean = false,
    val newSyspromptNameField: String = "",
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val error: String? = null,
    val debugInfo: String = ""
)

@HiltViewModel
class FormattingViewModel @Inject constructor(
    private val localRepository: LocalRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FormattingUiState())
    val uiState: StateFlow<FormattingUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    fun loadSettings() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            when (val result = localRepository.getFormattingSettings()) {
                is Result.Success -> {
                    val settings = result.data

                    fun normalizePresetName(name: String): String =
                        name.trim().removeSuffix(".json").removeSuffix(".JSON").lowercase()

                    fun findPresetIndex(presets: List<String>, selected: String): Int {
                        val normalizedSelected = normalizePresetName(selected)
                        if (normalizedSelected.isBlank()) return 0
                        return presets.indexOfFirst { normalizePresetName(it) == normalizedSelected }
                            .coerceAtLeast(0)
                    }

                    val debug = buildString {
                        append("Instruct: ${settings.instructPresets.size}")
                        append(" (selected: ${settings.selectedInstructPreset})")
                        append(", Context: ${settings.contextPresets.size}")
                        append(" (selected: ${settings.selectedContextPreset})")
                        append(", SysPrompt: ${settings.systemPromptPresets.size}")
                        append(" (selected: ${settings.selectedSystemPromptPreset})")
                    }

                    val syspromptIndex = findPresetIndex(
                        settings.systemPromptPresets, settings.selectedSystemPromptPreset
                    )
                    val syspromptName = settings.systemPromptPresets.getOrNull(syspromptIndex) ?: ""
                    val syspromptContent = if (syspromptName.isNotBlank()) {
                        localRepository.loadSystemPrompt(syspromptName)?.content ?: ""
                    } else ""
                    val syspromptIsUser = syspromptName.isNotBlank() &&
                        localRepository.isUserSystemPrompt(syspromptName)

                    _uiState.update {
                        it.copy(
                            instructPresets = settings.instructPresets,
                            selectedInstructIndex = findPresetIndex(settings.instructPresets, settings.selectedInstructPreset),
                            contextPresets = settings.contextPresets,
                            selectedContextIndex = findPresetIndex(settings.contextPresets, settings.selectedContextPreset),
                            systemPromptPresets = settings.systemPromptPresets,
                            selectedSyspromptIndex = syspromptIndex,
                            syspromptContent = syspromptContent,
                            syspromptIsUserPreset = syspromptIsUser,
                            isLoading = false,
                            debugInfo = debug
                        )
                    }
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = result.exception.message,
                            debugInfo = "Error: ${result.exception.message}"
                        )
                    }
                }
            }
        }
    }

    fun selectInstructPreset(index: Int) {
        _uiState.update { it.copy(selectedInstructIndex = index) }
    }

    fun selectContextPreset(index: Int) {
        _uiState.update { it.copy(selectedContextIndex = index) }
    }

    fun selectSyspromptPreset(index: Int) {
        val name = _uiState.value.systemPromptPresets.getOrNull(index) ?: ""
        _uiState.update { it.copy(selectedSyspromptIndex = index) }
        viewModelScope.launch {
            val content = if (name.isNotBlank()) {
                localRepository.loadSystemPrompt(name)?.content ?: ""
            } else ""
            val isUser = name.isNotBlank() && localRepository.isUserSystemPrompt(name)
            _uiState.update { it.copy(syspromptContent = content, syspromptIsUserPreset = isUser) }
        }
    }

    fun updateSyspromptContent(value: String) {
        _uiState.update { it.copy(syspromptContent = value) }
    }

    fun saveSyspromptPreset() {
        val state = _uiState.value
        val name = state.systemPromptPresets.getOrNull(state.selectedSyspromptIndex) ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(syspromptSaving = true) }
            localRepository.saveSystemPrompt(name, state.syspromptContent)
            // Reload list in case it wasn't a user preset before (now it is)
            val updatedPresets = localRepository.listSystemPrompts()
            val updatedIndex = updatedPresets.indexOfFirst { it == name }.coerceAtLeast(0)
            _uiState.update {
                it.copy(
                    syspromptSaving = false,
                    systemPromptPresets = updatedPresets,
                    selectedSyspromptIndex = updatedIndex,
                    syspromptIsUserPreset = true,
                    saveSuccess = true
                )
            }
        }
    }

    fun deleteSyspromptPreset() {
        val state = _uiState.value
        val name = state.systemPromptPresets.getOrNull(state.selectedSyspromptIndex) ?: return
        if (!state.syspromptIsUserPreset) return
        viewModelScope.launch {
            localRepository.deleteSystemPrompt(name)
            val updatedPresets = localRepository.listSystemPrompts()
            val newIndex = 0
            val newName = updatedPresets.getOrNull(newIndex) ?: ""
            val newContent = if (newName.isNotBlank()) {
                localRepository.loadSystemPrompt(newName)?.content ?: ""
            } else ""
            val newIsUser = newName.isNotBlank() && localRepository.isUserSystemPrompt(newName)
            _uiState.update {
                it.copy(
                    systemPromptPresets = updatedPresets,
                    selectedSyspromptIndex = newIndex,
                    syspromptContent = newContent,
                    syspromptIsUserPreset = newIsUser
                )
            }
        }
    }

    fun showNewSyspromptDialog() {
        _uiState.update { it.copy(showNewSyspromptDialog = true, newSyspromptNameField = "") }
    }

    fun hideNewSyspromptDialog() {
        _uiState.update { it.copy(showNewSyspromptDialog = false) }
    }

    fun updateNewSyspromptName(value: String) {
        _uiState.update { it.copy(newSyspromptNameField = value) }
    }

    fun confirmNewSyspromptPreset() {
        val state = _uiState.value
        val name = state.newSyspromptNameField.trim()
        if (name.isBlank()) {
            _uiState.update { it.copy(error = "Preset name cannot be empty") }
            return
        }
        viewModelScope.launch {
            localRepository.saveSystemPrompt(name, "")
            val updatedPresets = localRepository.listSystemPrompts()
            val newIndex = updatedPresets.indexOfFirst { it == name }.coerceAtLeast(0)
            _uiState.update {
                it.copy(
                    showNewSyspromptDialog = false,
                    systemPromptPresets = updatedPresets,
                    selectedSyspromptIndex = newIndex,
                    syspromptContent = "",
                    syspromptIsUserPreset = true
                )
            }
        }
    }

    fun saveSettings() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val settings = FormattingSettings(
                    instructPresets = state.instructPresets,
                    selectedInstructPreset = state.instructPresets.getOrNull(state.selectedInstructIndex) ?: "",
                    contextPresets = state.contextPresets,
                    selectedContextPreset = state.contextPresets.getOrNull(state.selectedContextIndex) ?: "",
                    systemPromptPresets = state.systemPromptPresets,
                    selectedSystemPromptPreset = state.systemPromptPresets.getOrNull(state.selectedSyspromptIndex) ?: "",
                    customSystemPrompt = ""
                )
                localRepository.saveFormattingSettings(settings)
                _uiState.update { it.copy(isSaving = false, saveSuccess = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = e.message) }
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
