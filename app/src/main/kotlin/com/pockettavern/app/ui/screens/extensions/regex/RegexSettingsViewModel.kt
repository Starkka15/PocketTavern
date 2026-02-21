package com.pockettavern.app.ui.screens.extensions.regex

import androidx.lifecycle.ViewModel
import com.pockettavern.app.domain.model.RegexRule
import com.pockettavern.app.extensions.builtin.RegexExtension
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class RegexSettingsUiState(
    val rules: List<RegexRule> = emptyList(),
    val showDialog: Boolean = false,
    val editingRuleId: String? = null,    // null = adding new
    val nameField: String = "",
    val patternField: String = "",
    val isRegexField: Boolean = false,
    val replacementField: String = "",
    val applyToOutputField: Boolean = true,
    val applyToInputField: Boolean = false,
    val caseInsensitiveField: Boolean = false,
    val testInput: String = "",
    val testOutput: String = "",
    val error: String? = null
)

@HiltViewModel
class RegexSettingsViewModel @Inject constructor(
    private val regexExtension: RegexExtension
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegexSettingsUiState())
    val uiState: StateFlow<RegexSettingsUiState> = _uiState.asStateFlow()

    init {
        regexExtension.load()
        _uiState.update { it.copy(rules = regexExtension.rules.value) }
    }

    fun showAddDialog() {
        _uiState.update {
            it.copy(
                showDialog = true, editingRuleId = null,
                nameField = "", patternField = "", isRegexField = false,
                replacementField = "", applyToOutputField = true,
                applyToInputField = false, caseInsensitiveField = false,
                testInput = "", testOutput = ""
            )
        }
    }

    fun showEditDialog(rule: RegexRule) {
        _uiState.update {
            it.copy(
                showDialog = true, editingRuleId = rule.id,
                nameField = rule.name, patternField = rule.pattern,
                isRegexField = rule.isRegex, replacementField = rule.replacement,
                applyToOutputField = rule.applyToOutput, applyToInputField = rule.applyToInput,
                caseInsensitiveField = rule.caseInsensitive,
                testInput = "", testOutput = ""
            )
        }
    }

    fun hideDialog() = _uiState.update { it.copy(showDialog = false) }

    fun updateName(v: String) = _uiState.update { it.copy(nameField = v) }
    fun updatePattern(v: String) {
        _uiState.update { it.copy(patternField = v) }
        runPreview()
    }
    fun toggleIsRegex(v: Boolean) {
        _uiState.update { it.copy(isRegexField = v) }
        runPreview()
    }
    fun updateReplacement(v: String) {
        _uiState.update { it.copy(replacementField = v) }
        runPreview()
    }
    fun toggleApplyToOutput(v: Boolean) = _uiState.update { it.copy(applyToOutputField = v) }
    fun toggleApplyToInput(v: Boolean) = _uiState.update { it.copy(applyToInputField = v) }
    fun toggleCaseInsensitive(v: Boolean) {
        _uiState.update { it.copy(caseInsensitiveField = v) }
        runPreview()
    }
    fun updateTestInput(v: String) {
        _uiState.update { it.copy(testInput = v) }
        runPreview()
    }

    private fun runPreview() {
        val s = _uiState.value
        if (s.testInput.isBlank() || s.patternField.isBlank()) {
            _uiState.update { it.copy(testOutput = "") }
            return
        }
        val output = try {
            if (s.isRegexField) {
                val flags = if (s.caseInsensitiveField) setOf(RegexOption.IGNORE_CASE) else emptySet()
                Regex(s.patternField, flags).replace(s.testInput, s.replacementField)
            } else {
                s.testInput.replace(s.patternField, s.replacementField, ignoreCase = s.caseInsensitiveField)
            }
        } catch (_: Exception) {
            "[Invalid pattern]"
        }
        _uiState.update { it.copy(testOutput = output) }
    }

    fun confirmRule() {
        val s = _uiState.value
        if (s.nameField.isBlank()) { _uiState.update { it.copy(error = "Name cannot be empty") }; return }
        if (s.patternField.isBlank()) { _uiState.update { it.copy(error = "Pattern cannot be empty") }; return }
        val rules = s.rules.toMutableList()
        val newRule = RegexRule(
            id = s.editingRuleId ?: java.util.UUID.randomUUID().toString(),
            name = s.nameField.trim(),
            enabled = rules.find { it.id == s.editingRuleId }?.enabled ?: true,
            pattern = s.patternField,
            isRegex = s.isRegexField,
            replacement = s.replacementField,
            applyToOutput = s.applyToOutputField,
            applyToInput = s.applyToInputField,
            caseInsensitive = s.caseInsensitiveField
        )
        val editIdx = rules.indexOfFirst { it.id == s.editingRuleId }
        if (editIdx >= 0) rules[editIdx] = newRule else rules.add(newRule)
        save(rules)
        _uiState.update { it.copy(showDialog = false) }
    }

    fun toggleRuleEnabled(ruleId: String) {
        val rules = _uiState.value.rules.map { if (it.id == ruleId) it.copy(enabled = !it.enabled) else it }
        save(rules)
    }

    fun deleteRule(ruleId: String) {
        val rules = _uiState.value.rules.filter { it.id != ruleId }
        save(rules)
    }

    fun clearError() = _uiState.update { it.copy(error = null) }

    private fun save(rules: List<RegexRule>) {
        regexExtension.saveRules(rules)
        _uiState.update { it.copy(rules = rules) }
    }
}
