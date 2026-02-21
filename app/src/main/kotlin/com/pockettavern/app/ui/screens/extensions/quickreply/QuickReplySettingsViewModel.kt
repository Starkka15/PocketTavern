package com.pockettavern.app.ui.screens.extensions.quickreply

import androidx.lifecycle.ViewModel
import com.pockettavern.app.domain.model.QuickReplyButton
import com.pockettavern.app.domain.model.QuickReplyPreset
import com.pockettavern.app.extensions.builtin.QuickReplyExtension
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID
import javax.inject.Inject

data class QuickReplyUiState(
    val presets: List<QuickReplyPreset> = emptyList(),
    // Preset dialog
    val showPresetDialog: Boolean = false,
    val editingPresetId: String? = null,  // null = adding new
    val presetNameField: String = "",
    // Button dialog
    val showButtonDialog: Boolean = false,
    val editingPresetIdForButton: String? = null,
    val editingButtonId: String? = null,  // null = adding new
    val buttonLabelField: String = "",
    val buttonMessageField: String = "",
    val buttonAutoTriggersField: Set<String> = emptySet(),
    val error: String? = null
)

@HiltViewModel
class QuickReplySettingsViewModel @Inject constructor(
    private val quickReplyExtension: QuickReplyExtension
) : ViewModel() {

    private val _uiState = MutableStateFlow(QuickReplyUiState())
    val uiState: StateFlow<QuickReplyUiState> = _uiState.asStateFlow()

    init {
        quickReplyExtension.load()
        _uiState.update { it.copy(presets = quickReplyExtension.presets.value) }
    }

    // ── Preset operations ─────────────────────────────────────────────────────

    fun showAddPresetDialog() {
        _uiState.update { it.copy(showPresetDialog = true, editingPresetId = null, presetNameField = "") }
    }

    fun showEditPresetDialog(preset: QuickReplyPreset) {
        _uiState.update { it.copy(showPresetDialog = true, editingPresetId = preset.id, presetNameField = preset.name) }
    }

    fun hidePresetDialog() {
        _uiState.update { it.copy(showPresetDialog = false, editingPresetId = null, presetNameField = "") }
    }

    fun updatePresetNameField(name: String) = _uiState.update { it.copy(presetNameField = name) }

    fun confirmPreset() {
        val name = _uiState.value.presetNameField.trim()
        if (name.isBlank()) {
            _uiState.update { it.copy(error = "Preset name cannot be empty") }
            return
        }
        val presets = _uiState.value.presets.toMutableList()
        val editId = _uiState.value.editingPresetId
        if (editId != null) {
            val idx = presets.indexOfFirst { it.id == editId }
            if (idx >= 0) presets[idx] = presets[idx].copy(name = name)
        } else {
            presets.add(QuickReplyPreset(name = name))
        }
        save(presets)
        _uiState.update { it.copy(showPresetDialog = false, editingPresetId = null, presetNameField = "") }
    }

    fun togglePresetEnabled(presetId: String) {
        val presets = _uiState.value.presets.map { p ->
            if (p.id == presetId) p.copy(enabled = !p.enabled) else p
        }
        save(presets)
    }

    fun deletePreset(presetId: String) {
        val presets = _uiState.value.presets.filter { it.id != presetId }
        save(presets)
    }

    // ── Button operations ─────────────────────────────────────────────────────

    fun showAddButtonDialog(presetId: String) {
        _uiState.update {
            it.copy(
                showButtonDialog = true,
                editingPresetIdForButton = presetId,
                editingButtonId = null,
                buttonLabelField = "",
                buttonMessageField = "",
                buttonAutoTriggersField = emptySet()
            )
        }
    }

    fun showEditButtonDialog(presetId: String, button: QuickReplyButton) {
        _uiState.update {
            it.copy(
                showButtonDialog = true,
                editingPresetIdForButton = presetId,
                editingButtonId = button.id,
                buttonLabelField = button.label,
                buttonMessageField = button.message,
                buttonAutoTriggersField = button.autoTriggers
            )
        }
    }

    fun hideButtonDialog() {
        _uiState.update { it.copy(showButtonDialog = false) }
    }

    fun updateButtonLabelField(v: String) = _uiState.update { it.copy(buttonLabelField = v) }
    fun updateButtonMessageField(v: String) = _uiState.update { it.copy(buttonMessageField = v) }
    fun updateButtonAutoTriggers(triggers: Set<String>) = _uiState.update { it.copy(buttonAutoTriggersField = triggers) }

    fun confirmButton() {
        val label = _uiState.value.buttonLabelField.trim()
        val message = _uiState.value.buttonMessageField.trim()
        if (label.isBlank() || message.isBlank()) {
            _uiState.update { it.copy(error = "Label and message cannot be empty") }
            return
        }
        val presetId = _uiState.value.editingPresetIdForButton ?: return
        val presets = _uiState.value.presets.map { preset ->
            if (preset.id != presetId) return@map preset
            val buttons = preset.buttons.toMutableList()
            val editBtnId = _uiState.value.editingButtonId
            if (editBtnId != null) {
                val idx = buttons.indexOfFirst { it.id == editBtnId }
                if (idx >= 0) buttons[idx] = buttons[idx].copy(label = label, message = message, autoTriggers = _uiState.value.buttonAutoTriggersField)
            } else {
                buttons.add(QuickReplyButton(label = label, message = message, autoTriggers = _uiState.value.buttonAutoTriggersField))
            }
            preset.copy(buttons = buttons)
        }
        save(presets)
        _uiState.update { it.copy(showButtonDialog = false) }
    }

    fun deleteButton(presetId: String, buttonId: String) {
        val presets = _uiState.value.presets.map { preset ->
            if (preset.id != presetId) preset
            else preset.copy(buttons = preset.buttons.filter { it.id != buttonId })
        }
        save(presets)
    }

    fun clearError() = _uiState.update { it.copy(error = null) }

    private fun save(presets: List<QuickReplyPreset>) {
        quickReplyExtension.savePresets(presets)
        _uiState.update { it.copy(presets = presets) }
    }
}
