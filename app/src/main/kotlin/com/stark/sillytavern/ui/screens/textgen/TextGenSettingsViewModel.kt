package com.stark.sillytavern.ui.screens.textgen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stark.sillytavern.data.repository.SillyTavernRepository
import com.stark.sillytavern.domain.model.Result
import com.stark.sillytavern.domain.model.TextGenPreset
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TextGenSettingsUiState(
    val presets: List<TextGenPreset> = emptyList(),
    val selectedPresetIndex: Int = 0,
    // Token limits (0 = let server decide)
    val maxTokens: Int = 0,
    val minTokens: Int = 0,
    val truncationLength: Int = 2048,
    // Temperature and sampling
    val temperature: Float = 0.7f,
    val topP: Float = 0.5f,
    val topK: Int = 40,
    val topA: Float = 0f,
    val minP: Float = 0f,
    val typicalP: Float = 1.0f,
    val tfs: Float = 1.0f,
    // Repetition penalty
    val repPen: Float = 1.2f,
    val repPenRange: Int = 0,
    val repPenSlope: Float = 1f,
    val frequencyPenalty: Float = 0f,
    val presencePenalty: Float = 0f,
    // DRY sampler
    val dryMultiplier: Float = 0f,
    val dryBase: Float = 1.75f,
    val dryAllowedLength: Int = 2,
    val dryPenaltyLastN: Int = 0,
    // Mirostat
    val mirostatMode: Int = 0,
    val mirostatTau: Float = 5f,
    val mirostatEta: Float = 0.1f,
    // XTC
    val xtcThreshold: Float = 0.1f,
    val xtcProbability: Float = 0f,
    // Other sampling
    val skew: Float = 0f,
    val smoothingFactor: Float = 0f,
    val smoothingCurve: Float = 1f,
    // Guidance
    val guidanceScale: Float = 1f,
    // Token handling
    val addBosToken: Boolean = true,
    val banEosToken: Boolean = false,
    val skipSpecialTokens: Boolean = true,
    // UI state
    val showAdvanced: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val showSavePresetDialog: Boolean = false,
    val newPresetName: String = "",
    val showDeleteConfirm: Boolean = false,
    val saveSuccess: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class TextGenSettingsViewModel @Inject constructor(
    private val repository: SillyTavernRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TextGenSettingsUiState())
    val uiState: StateFlow<TextGenSettingsUiState> = _uiState.asStateFlow()

    init {
        loadPresets()
    }

    fun loadPresets() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            when (val result = repository.getTextGenPresets()) {
                is Result.Success -> {
                    val presets = result.data.presets
                    val selectedName = result.data.selectedPresetName

                    // Find the index of the selected preset (case-insensitive, handle .json extension)
                    fun normalizePresetName(name: String): String {
                        return name.trim()
                            .removeSuffix(".json")
                            .removeSuffix(".JSON")
                            .lowercase()
                    }

                    val normalizedSelected = normalizePresetName(selectedName)
                    val selectedIndex = if (normalizedSelected.isNotBlank()) {
                        presets.indexOfFirst { normalizePresetName(it.name) == normalizedSelected }
                            .coerceAtLeast(0)
                    } else {
                        0
                    }

                    val preset = presets.getOrNull(selectedIndex)
                    _uiState.update {
                        it.copy(
                            presets = presets,
                            selectedPresetIndex = selectedIndex,
                            isLoading = false
                        ).applyPreset(preset)
                    }
                    // Apply the preset settings to repository so they're used in API requests
                    preset?.let { repository.applyTextGenSettings(it) }
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

    private fun TextGenSettingsUiState.applyPreset(preset: TextGenPreset?): TextGenSettingsUiState {
        return if (preset != null) {
            copy(
                maxTokens = preset.maxNewTokens ?: 0,  // 0 means server decides
                minTokens = preset.minTokens,
                truncationLength = preset.truncationLength,
                temperature = preset.temperature,
                topP = preset.topP,
                topK = preset.topK,
                topA = preset.topA,
                minP = preset.minP,
                typicalP = preset.typicalP,
                tfs = preset.tfs,
                repPen = preset.repPen,
                repPenRange = preset.repPenRange,
                repPenSlope = preset.repPenSlope,
                frequencyPenalty = preset.frequencyPenalty,
                presencePenalty = preset.presencePenalty,
                dryMultiplier = preset.dryMultiplier,
                dryBase = preset.dryBase,
                dryAllowedLength = preset.dryAllowedLength,
                dryPenaltyLastN = preset.dryPenaltyLastN,
                mirostatMode = preset.mirostatMode,
                mirostatTau = preset.mirostatTau,
                mirostatEta = preset.mirostatEta,
                xtcThreshold = preset.xtcThreshold,
                xtcProbability = preset.xtcProbability,
                skew = preset.skew,
                smoothingFactor = preset.smoothingFactor,
                smoothingCurve = preset.smoothingCurve,
                guidanceScale = preset.guidanceScale,
                addBosToken = preset.addBosToken,
                banEosToken = preset.banEosToken,
                skipSpecialTokens = preset.skipSpecialTokens
            )
        } else this
    }

    fun selectPreset(index: Int) {
        val preset = _uiState.value.presets.getOrNull(index) ?: return
        _uiState.update { it.copy(selectedPresetIndex = index).applyPreset(preset) }

        // Apply the preset settings immediately so they're used in API requests
        repository.applyTextGenSettings(preset)

        // Save the selection to the server so it persists
        viewModelScope.launch {
            repository.selectTextGenPreset(preset.name)
        }
    }

    fun toggleAdvanced() {
        _uiState.update { it.copy(showAdvanced = !it.showAdvanced) }
    }

    // Basic settings
    fun updateTemperature(value: Float) = _uiState.update { it.copy(temperature = value) }
    fun updateTopP(value: Float) = _uiState.update { it.copy(topP = value) }
    fun updateMinP(value: Float) = _uiState.update { it.copy(minP = value) }
    fun updateRepPen(value: Float) = _uiState.update { it.copy(repPen = value) }
    fun updateMaxTokens(value: Int) = _uiState.update { it.copy(maxTokens = value.coerceIn(1, 8192)) }

    // Advanced - Token limits
    fun updateMinTokens(value: Int) = _uiState.update { it.copy(minTokens = value.coerceIn(0, 8192)) }
    fun updateTruncationLength(value: Int) = _uiState.update { it.copy(truncationLength = value.coerceIn(512, 32768)) }

    // Advanced - Sampling
    fun updateTopK(value: Int) = _uiState.update { it.copy(topK = value.coerceIn(0, 200)) }
    fun updateTopA(value: Float) = _uiState.update { it.copy(topA = value) }
    fun updateTypicalP(value: Float) = _uiState.update { it.copy(typicalP = value) }
    fun updateTfs(value: Float) = _uiState.update { it.copy(tfs = value) }

    // Advanced - Repetition
    fun updateRepPenRange(value: Int) = _uiState.update { it.copy(repPenRange = value.coerceIn(0, 8192)) }
    fun updateRepPenSlope(value: Float) = _uiState.update { it.copy(repPenSlope = value) }
    fun updateFrequencyPenalty(value: Float) = _uiState.update { it.copy(frequencyPenalty = value) }
    fun updatePresencePenalty(value: Float) = _uiState.update { it.copy(presencePenalty = value) }

    // Advanced - DRY
    fun updateDryMultiplier(value: Float) = _uiState.update { it.copy(dryMultiplier = value) }
    fun updateDryBase(value: Float) = _uiState.update { it.copy(dryBase = value) }
    fun updateDryAllowedLength(value: Int) = _uiState.update { it.copy(dryAllowedLength = value.coerceIn(0, 100)) }
    fun updateDryPenaltyLastN(value: Int) = _uiState.update { it.copy(dryPenaltyLastN = value.coerceIn(0, 8192)) }

    // Advanced - Mirostat
    fun updateMirostatMode(value: Int) = _uiState.update { it.copy(mirostatMode = value.coerceIn(0, 2)) }
    fun updateMirostatTau(value: Float) = _uiState.update { it.copy(mirostatTau = value) }
    fun updateMirostatEta(value: Float) = _uiState.update { it.copy(mirostatEta = value) }

    // Advanced - XTC
    fun updateXtcThreshold(value: Float) = _uiState.update { it.copy(xtcThreshold = value) }
    fun updateXtcProbability(value: Float) = _uiState.update { it.copy(xtcProbability = value) }

    // Advanced - Other
    fun updateSkew(value: Float) = _uiState.update { it.copy(skew = value) }
    fun updateSmoothingFactor(value: Float) = _uiState.update { it.copy(smoothingFactor = value) }
    fun updateSmoothingCurve(value: Float) = _uiState.update { it.copy(smoothingCurve = value) }
    fun updateGuidanceScale(value: Float) = _uiState.update { it.copy(guidanceScale = value) }

    // Advanced - Token handling
    fun updateAddBosToken(value: Boolean) = _uiState.update { it.copy(addBosToken = value) }
    fun updateBanEosToken(value: Boolean) = _uiState.update { it.copy(banEosToken = value) }
    fun updateSkipSpecialTokens(value: Boolean) = _uiState.update { it.copy(skipSpecialTokens = value) }

    fun showSavePresetDialog() {
        val currentPreset = _uiState.value.presets.getOrNull(_uiState.value.selectedPresetIndex)
        _uiState.update {
            it.copy(
                showSavePresetDialog = true,
                newPresetName = currentPreset?.name ?: ""
            )
        }
    }

    fun hideSavePresetDialog() {
        _uiState.update { it.copy(showSavePresetDialog = false, newPresetName = "") }
    }

    fun updateNewPresetName(name: String) {
        _uiState.update { it.copy(newPresetName = name) }
    }

    private fun buildPresetFromState(name: String): TextGenPreset {
        val state = _uiState.value
        return TextGenPreset(
            name = name,
            maxNewTokens = state.maxTokens,
            minTokens = state.minTokens,
            truncationLength = state.truncationLength,
            temperature = state.temperature,
            topP = state.topP,
            topK = state.topK,
            topA = state.topA,
            minP = state.minP,
            typicalP = state.typicalP,
            tfs = state.tfs,
            repPen = state.repPen,
            repPenRange = state.repPenRange,
            repPenSlope = state.repPenSlope,
            frequencyPenalty = state.frequencyPenalty,
            presencePenalty = state.presencePenalty,
            dryMultiplier = state.dryMultiplier,
            dryBase = state.dryBase,
            dryAllowedLength = state.dryAllowedLength,
            dryPenaltyLastN = state.dryPenaltyLastN,
            mirostatMode = state.mirostatMode,
            mirostatTau = state.mirostatTau,
            mirostatEta = state.mirostatEta,
            xtcThreshold = state.xtcThreshold,
            xtcProbability = state.xtcProbability,
            skew = state.skew,
            smoothingFactor = state.smoothingFactor,
            smoothingCurve = state.smoothingCurve,
            guidanceScale = state.guidanceScale,
            addBosToken = state.addBosToken,
            banEosToken = state.banEosToken,
            skipSpecialTokens = state.skipSpecialTokens
        )
    }

    fun savePreset() {
        val state = _uiState.value
        if (state.newPresetName.isBlank()) {
            _uiState.update { it.copy(error = "Preset name cannot be empty") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }

            val preset = buildPresetFromState(state.newPresetName.trim())

            when (val result = repository.saveTextGenPreset(preset)) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            showSavePresetDialog = false,
                            newPresetName = "",
                            saveSuccess = true
                        )
                    }
                    loadPresets()
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

    fun showDeleteConfirm() {
        _uiState.update { it.copy(showDeleteConfirm = true) }
    }

    fun hideDeleteConfirm() {
        _uiState.update { it.copy(showDeleteConfirm = false) }
    }

    fun deleteCurrentPreset() {
        val presetName = _uiState.value.presets.getOrNull(_uiState.value.selectedPresetIndex)?.name
            ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, showDeleteConfirm = false) }

            when (val result = repository.deleteTextGenPreset(presetName)) {
                is Result.Success -> {
                    _uiState.update { it.copy(isSaving = false) }
                    loadPresets()
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

    fun applySettings() {
        val state = _uiState.value
        val presetName = state.presets.getOrNull(state.selectedPresetIndex)?.name ?: "custom"
        val preset = buildPresetFromState(presetName)
        repository.applyTextGenSettings(preset)
        _uiState.update { it.copy(saveSuccess = true) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun resetSaveSuccess() {
        _uiState.update { it.copy(saveSuccess = false) }
    }
}
