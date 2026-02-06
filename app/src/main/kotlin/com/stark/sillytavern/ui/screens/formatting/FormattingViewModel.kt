package com.stark.sillytavern.ui.screens.formatting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stark.sillytavern.data.repository.SillyTavernRepository
import com.stark.sillytavern.domain.model.FormattingSettings
import com.stark.sillytavern.domain.model.Result
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
    val customSystemPrompt: String = "",
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val error: String? = null,
    val debugInfo: String = "" // Debug info to show what was loaded
)

@HiltViewModel
class FormattingViewModel @Inject constructor(
    private val repository: SillyTavernRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FormattingUiState())
    val uiState: StateFlow<FormattingUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    fun loadSettings() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            when (val result = repository.getFormattingSettings()) {
                is Result.Success -> {
                    val settings = result.data

                    // Normalize preset names for matching (handle .json extension, case, whitespace)
                    fun normalizePresetName(name: String): String {
                        return name.trim()
                            .removeSuffix(".json")
                            .removeSuffix(".JSON")
                            .lowercase()
                    }

                    // Find indices using normalized matching
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

                    _uiState.update {
                        it.copy(
                            instructPresets = settings.instructPresets,
                            selectedInstructIndex = findPresetIndex(settings.instructPresets, settings.selectedInstructPreset),
                            contextPresets = settings.contextPresets,
                            selectedContextIndex = findPresetIndex(settings.contextPresets, settings.selectedContextPreset),
                            systemPromptPresets = settings.systemPromptPresets,
                            selectedSyspromptIndex = findPresetIndex(settings.systemPromptPresets, settings.selectedSystemPromptPreset),
                            customSystemPrompt = settings.customSystemPrompt,
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
        _uiState.update { it.copy(selectedSyspromptIndex = index) }
    }

    fun updateCustomSystemPrompt(value: String) {
        _uiState.update { it.copy(customSystemPrompt = value) }
    }

    fun saveSettings() {
        val state = _uiState.value

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }

            val instructPreset = state.instructPresets.getOrNull(state.selectedInstructIndex)
            val contextPreset = state.contextPresets.getOrNull(state.selectedContextIndex)
            val syspromptPreset = state.systemPromptPresets.getOrNull(state.selectedSyspromptIndex)

            when (val result = repository.saveFormattingSettings(
                instructPreset = instructPreset,
                contextPreset = contextPreset,
                syspromptPreset = syspromptPreset,
                customSystemPrompt = state.customSystemPrompt.ifBlank { null }
            )) {
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
