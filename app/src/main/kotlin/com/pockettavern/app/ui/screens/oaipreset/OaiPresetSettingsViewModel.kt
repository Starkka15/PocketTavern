package com.pockettavern.app.ui.screens.oaipreset

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pockettavern.app.data.repository.LocalRepository
import com.pockettavern.app.domain.model.OaiPreset
import com.pockettavern.app.domain.model.OaiPromptOrderItem
import com.pockettavern.app.domain.model.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OaiPresetUiState(
    val presets: List<OaiPreset> = emptyList(),
    val selectedPresetIndex: Int = 0,

    // Sampling parameters
    val temperature: Float = 1.0f,
    val temperatureEnabled: Boolean = true,
    val topP: Float = 1.0f,
    val topPEnabled: Boolean = false,
    val topK: Int = 0,
    val topKEnabled: Boolean = false,
    val maxTokens: Int = 500,
    val maxTokensEnabled: Boolean = true,
    val frequencyPenalty: Float = 0f,
    val frequencyPenaltyEnabled: Boolean = false,
    val presencePenalty: Float = 0f,
    val presencePenaltyEnabled: Boolean = false,
    val repetitionPenalty: Float = 1.0f,
    val repetitionPenaltyEnabled: Boolean = false,
    val minP: Float = 0f,
    val minPEnabled: Boolean = false,
    val topA: Float = 0f,
    val topAEnabled: Boolean = false,
    val contextSize: Int = 4096,
    val contextSizeEnabled: Boolean = false,
    val seed: Int = -1,
    val seedEnabled: Boolean = false,

    // Prompt ordering + content (all prompt block content lives here)
    val promptOrder: List<OaiPromptOrderItem> = OaiPromptOrderItem.defaultOrder(),

    // UI state
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val showSaveDialog: Boolean = false,
    val newPresetName: String = "",
    val showDeleteConfirm: Boolean = false,
    val error: String? = null,
    val saveSuccess: Boolean = false,

    // Custom prompt add dialog
    val showCustomPromptDialog: Boolean = false,
    val editingCustomIndex: Int = -1,   // -1 = adding new, >=0 = editing existing
    val customPromptLabel: String = "",
    val customPromptContent: String = "",

    // Import dialog
    val showImportNameDialog: Boolean = false,
    val pendingImportJson: String = "",
    val importPresetName: String = ""
)

@HiltViewModel
class OaiPresetSettingsViewModel @Inject constructor(
    private val localRepository: LocalRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OaiPresetUiState())
    val uiState: StateFlow<OaiPresetUiState> = _uiState.asStateFlow()

    init {
        loadPresets()
    }

    fun loadPresets() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            when (val result = localRepository.getOaiPresetsWithSelected()) {
                is Result.Success -> {
                    val (presets, selectedName) = result.data
                    val selectedIndex = if (selectedName.isNotBlank())
                        presets.indexOfFirst { it.name.equals(selectedName, ignoreCase = true) }.coerceAtLeast(0)
                    else 0
                    val preset = presets.getOrNull(selectedIndex)
                    _uiState.update {
                        it.copy(presets = presets, selectedPresetIndex = selectedIndex, isLoading = false)
                            .applyPreset(preset)
                    }
                }
                is Result.Error -> _uiState.update { it.copy(isLoading = false, error = result.exception.message) }
            }
        }
    }

    private fun OaiPresetUiState.applyPreset(preset: OaiPreset?): OaiPresetUiState {
        return if (preset != null) copy(
            temperature = preset.temperature,
            temperatureEnabled = preset.temperatureEnabled,
            topP = preset.topP,
            topPEnabled = preset.topPEnabled,
            topK = preset.topK,
            topKEnabled = preset.topKEnabled,
            maxTokens = preset.maxTokens,
            maxTokensEnabled = preset.maxTokensEnabled,
            frequencyPenalty = preset.frequencyPenalty,
            frequencyPenaltyEnabled = preset.frequencyPenaltyEnabled,
            presencePenalty = preset.presencePenalty,
            presencePenaltyEnabled = preset.presencePenaltyEnabled,
            repetitionPenalty = preset.repetitionPenalty,
            repetitionPenaltyEnabled = preset.repetitionPenaltyEnabled,
            minP = preset.minP,
            minPEnabled = preset.minPEnabled,
            topA = preset.topA,
            topAEnabled = preset.topAEnabled,
            contextSize = preset.contextSize,
            contextSizeEnabled = preset.contextSizeEnabled,
            seed = preset.seed,
            seedEnabled = preset.seedEnabled,
            promptOrder = preset.promptOrder
        ) else this
    }

    fun selectPreset(index: Int) {
        val preset = _uiState.value.presets.getOrNull(index) ?: return
        _uiState.update { it.copy(selectedPresetIndex = index).applyPreset(preset) }
        viewModelScope.launch { localRepository.selectOaiPreset(preset.name) }
    }

    // Sampling value updaters
    fun updateTemperature(v: Float) = _uiState.update { it.copy(temperature = v) }
    fun updateTopP(v: Float) = _uiState.update { it.copy(topP = v) }
    fun updateTopK(v: Int) = _uiState.update { it.copy(topK = v) }
    fun updateMaxTokens(v: Int) = _uiState.update { it.copy(maxTokens = v) }
    fun updateFrequencyPenalty(v: Float) = _uiState.update { it.copy(frequencyPenalty = v) }
    fun updatePresencePenalty(v: Float) = _uiState.update { it.copy(presencePenalty = v) }
    fun updateRepetitionPenalty(v: Float) = _uiState.update { it.copy(repetitionPenalty = v) }
    fun updateMinP(v: Float) = _uiState.update { it.copy(minP = v) }
    fun updateTopA(v: Float) = _uiState.update { it.copy(topA = v) }
    fun updateContextSize(v: Int) = _uiState.update { it.copy(contextSize = v) }
    fun updateSeed(v: Int) = _uiState.update { it.copy(seed = v) }

    // Enable toggles
    fun toggleTemperatureEnabled(v: Boolean) = _uiState.update { it.copy(temperatureEnabled = v) }
    fun toggleTopPEnabled(v: Boolean) = _uiState.update { it.copy(topPEnabled = v) }
    fun toggleTopKEnabled(v: Boolean) = _uiState.update { it.copy(topKEnabled = v) }
    fun toggleMaxTokensEnabled(v: Boolean) = _uiState.update { it.copy(maxTokensEnabled = v) }
    fun toggleFrequencyPenaltyEnabled(v: Boolean) = _uiState.update { it.copy(frequencyPenaltyEnabled = v) }
    fun togglePresencePenaltyEnabled(v: Boolean) = _uiState.update { it.copy(presencePenaltyEnabled = v) }
    fun toggleRepetitionPenaltyEnabled(v: Boolean) = _uiState.update { it.copy(repetitionPenaltyEnabled = v) }
    fun toggleMinPEnabled(v: Boolean) = _uiState.update { it.copy(minPEnabled = v) }
    fun toggleTopAEnabled(v: Boolean) = _uiState.update { it.copy(topAEnabled = v) }
    fun toggleContextSizeEnabled(v: Boolean) = _uiState.update { it.copy(contextSizeEnabled = v) }
    fun toggleSeedEnabled(v: Boolean) = _uiState.update { it.copy(seedEnabled = v) }

    // Prompt order operations
    fun togglePromptOrderItem(index: Int) {
        _uiState.update { state ->
            val newOrder = state.promptOrder.toMutableList()
            if (index in newOrder.indices) {
                newOrder[index] = newOrder[index].copy(enabled = !newOrder[index].enabled)
            }
            state.copy(promptOrder = newOrder)
        }
    }

    fun updatePromptOrderItemContent(index: Int, content: String) {
        _uiState.update { state ->
            val newOrder = state.promptOrder.toMutableList()
            if (index in newOrder.indices) {
                newOrder[index] = newOrder[index].copy(content = content)
            }
            state.copy(promptOrder = newOrder)
        }
    }

    fun movePromptOrderItemUp(index: Int) {
        if (index <= 0) return
        _uiState.update { state ->
            val newOrder = state.promptOrder.toMutableList()
            val temp = newOrder[index]
            newOrder[index] = newOrder[index - 1]
            newOrder[index - 1] = temp
            state.copy(promptOrder = newOrder)
        }
    }

    fun movePromptOrderItemDown(index: Int) {
        _uiState.update { state ->
            if (index >= state.promptOrder.size - 1) return@update state
            val newOrder = state.promptOrder.toMutableList()
            val temp = newOrder[index]
            newOrder[index] = newOrder[index + 1]
            newOrder[index + 1] = temp
            state.copy(promptOrder = newOrder)
        }
    }

    // Custom prompt add dialog
    fun showAddCustomPromptDialog() {
        _uiState.update { it.copy(showCustomPromptDialog = true, editingCustomIndex = -1, customPromptLabel = "", customPromptContent = "") }
    }

    fun hideCustomPromptDialog() {
        _uiState.update { it.copy(showCustomPromptDialog = false, editingCustomIndex = -1, customPromptLabel = "", customPromptContent = "") }
    }

    fun updateCustomPromptLabel(v: String) = _uiState.update { it.copy(customPromptLabel = v) }
    fun updateCustomPromptContent(v: String) = _uiState.update { it.copy(customPromptContent = v) }

    fun confirmCustomPrompt() {
        val state = _uiState.value
        val label = state.customPromptLabel.trim()
        val content = state.customPromptContent.trim()
        if (label.isBlank() || content.isBlank()) {
            _uiState.update { it.copy(error = "Name and content cannot be empty") }
            return
        }
        _uiState.update { s ->
            val newOrder = s.promptOrder.toMutableList()
            if (s.editingCustomIndex >= 0 && s.editingCustomIndex < newOrder.size) {
                val existing = newOrder[s.editingCustomIndex]
                newOrder[s.editingCustomIndex] = existing.copy(customLabel = label, content = content)
            } else {
                val insertAt = newOrder.indexOfFirst { it.id == "chat_history" }.let { if (it < 0) newOrder.size else it }
                newOrder.add(insertAt, OaiPromptOrderItem.custom(label, content))
            }
            s.copy(promptOrder = newOrder, showCustomPromptDialog = false, editingCustomIndex = -1, customPromptLabel = "", customPromptContent = "")
        }
    }

    fun updatePromptOrderItemRole(index: Int, role: String) {
        _uiState.update { state ->
            val newOrder = state.promptOrder.toMutableList()
            if (index in newOrder.indices) {
                newOrder[index] = newOrder[index].copy(role = role)
            }
            state.copy(promptOrder = newOrder)
        }
    }

    fun updatePromptOrderItemInjection(index: Int, position: Int, depth: Int) {
        _uiState.update { state ->
            val newOrder = state.promptOrder.toMutableList()
            if (index in newOrder.indices) {
                newOrder[index] = newOrder[index].copy(injectionPosition = position, injectionDepth = depth)
            }
            state.copy(promptOrder = newOrder)
        }
    }

    fun deleteCustomPromptItem(index: Int) {
        _uiState.update { state ->
            val item = state.promptOrder.getOrNull(index)
            if (item?.isCustom != true) return@update state
            val newOrder = state.promptOrder.toMutableList()
            newOrder.removeAt(index)
            state.copy(promptOrder = newOrder)
        }
    }

    // Import from SillyTavern JSON
    fun beginImportFromJson(jsonText: String) {
        val currentPresetName = _uiState.value.presets.getOrNull(_uiState.value.selectedPresetIndex)?.name ?: "Imported"
        _uiState.update { it.copy(showImportNameDialog = true, pendingImportJson = jsonText, importPresetName = "Imported - $currentPresetName") }
    }

    fun updateImportPresetName(name: String) = _uiState.update { it.copy(importPresetName = name) }

    fun hideImportNameDialog() = _uiState.update { it.copy(showImportNameDialog = false, pendingImportJson = "", importPresetName = "") }

    fun confirmImport() {
        val state = _uiState.value
        val name = state.importPresetName.trim()
        if (name.isBlank()) { _uiState.update { it.copy(error = "Preset name cannot be empty") }; return }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, showImportNameDialog = false) }
            when (val result = localRepository.importStOaiPreset(name, state.pendingImportJson)) {
                is Result.Success -> {
                    _uiState.update { it.copy(isSaving = false, pendingImportJson = "", importPresetName = "", saveSuccess = true) }
                    loadPresets()
                }
                is Result.Error -> _uiState.update { it.copy(isSaving = false, error = result.exception.message) }
            }
        }
    }

    // Save / Delete
    fun showSaveDialog() {
        val currentPreset = _uiState.value.presets.getOrNull(_uiState.value.selectedPresetIndex)
        _uiState.update { it.copy(showSaveDialog = true, newPresetName = currentPreset?.name ?: "") }
    }

    fun hideSaveDialog() = _uiState.update { it.copy(showSaveDialog = false, newPresetName = "") }
    fun updateNewPresetName(name: String) = _uiState.update { it.copy(newPresetName = name) }

    fun savePreset() {
        val state = _uiState.value
        if (state.newPresetName.isBlank()) {
            _uiState.update { it.copy(error = "Preset name cannot be empty") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val preset = buildPresetFromState(state.newPresetName.trim())
            when (val result = localRepository.saveOaiPreset(preset)) {
                is Result.Success -> {
                    _uiState.update { it.copy(isSaving = false, showSaveDialog = false, newPresetName = "", saveSuccess = true) }
                    loadPresets()
                }
                is Result.Error -> _uiState.update { it.copy(isSaving = false, error = result.exception.message) }
            }
        }
    }

    fun showDeleteConfirm() = _uiState.update { it.copy(showDeleteConfirm = true) }
    fun hideDeleteConfirm() = _uiState.update { it.copy(showDeleteConfirm = false) }

    fun deleteCurrentPreset() {
        val name = _uiState.value.presets.getOrNull(_uiState.value.selectedPresetIndex)?.name ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, showDeleteConfirm = false) }
            when (val result = localRepository.deleteOaiPreset(name)) {
                is Result.Success -> { _uiState.update { it.copy(isSaving = false) }; loadPresets() }
                is Result.Error -> _uiState.update { it.copy(isSaving = false, error = result.exception.message) }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }
    fun resetSaveSuccess() = _uiState.update { it.copy(saveSuccess = false) }

    private fun buildPresetFromState(name: String): OaiPreset {
        val s = _uiState.value
        return OaiPreset(
            name = name,
            temperature = s.temperature,
            temperatureEnabled = s.temperatureEnabled,
            topP = s.topP,
            topPEnabled = s.topPEnabled,
            topK = s.topK.coerceIn(0, 200),
            topKEnabled = s.topKEnabled,
            maxTokens = s.maxTokens.coerceIn(1, 32768),
            maxTokensEnabled = s.maxTokensEnabled,
            frequencyPenalty = s.frequencyPenalty,
            frequencyPenaltyEnabled = s.frequencyPenaltyEnabled,
            presencePenalty = s.presencePenalty,
            presencePenaltyEnabled = s.presencePenaltyEnabled,
            repetitionPenalty = s.repetitionPenalty,
            repetitionPenaltyEnabled = s.repetitionPenaltyEnabled,
            minP = s.minP,
            minPEnabled = s.minPEnabled,
            topA = s.topA,
            topAEnabled = s.topAEnabled,
            contextSize = s.contextSize.coerceIn(512, 131072),
            contextSizeEnabled = s.contextSizeEnabled,
            seed = s.seed,
            seedEnabled = s.seedEnabled,
            promptOrder = s.promptOrder
        )
    }
}
