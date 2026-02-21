package com.pockettavern.app.extensions.builtin

import com.pockettavern.app.data.local.ExtensionStorage
import com.pockettavern.app.domain.model.RegexRule
import com.pockettavern.app.extensions.ExtensionEvent
import com.pockettavern.app.extensions.NativeExtension
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RegexExtension @Inject constructor(
    private val storage: ExtensionStorage
) : NativeExtension {

    override val id = "regex"
    override val displayName = "Regex"

    private val _rules = MutableStateFlow<List<RegexRule>>(emptyList())
    val rules: StateFlow<List<RegexRule>> = _rules.asStateFlow()

    private var _enabled: Boolean = true
    override val enabled: Boolean get() = _enabled

    fun load() {
        val enabledMap = storage.loadEnabledMap()
        _enabled = enabledMap[id] ?: true
        _rules.value = storage.loadRegexRules()
    }

    fun setEnabled(value: Boolean) {
        _enabled = value
        val map = storage.loadEnabledMap().toMutableMap()
        map[id] = value
        storage.saveEnabledMap(map)
    }

    fun saveRules(rules: List<RegexRule>) {
        _rules.value = rules
        storage.saveRegexRules(rules)
    }

    /** Apply all enabled output rules to a received AI message. */
    fun processOutput(text: String): String {
        if (!_enabled) return text
        return applyRules(text, applyToOutput = true)
    }

    /** Apply all enabled input rules to a user message before sending. */
    fun processInput(text: String): String {
        if (!_enabled) return text
        return applyRules(text, applyToOutput = false)
    }

    private fun applyRules(text: String, applyToOutput: Boolean): String {
        var result = text
        for (rule in _rules.value) {
            if (!rule.enabled) continue
            val applies = if (applyToOutput) rule.applyToOutput else rule.applyToInput
            if (!applies) continue
            result = try {
                if (rule.isRegex) {
                    val flags = if (rule.caseInsensitive) setOf(RegexOption.IGNORE_CASE) else emptySet()
                    Regex(rule.pattern, flags).replace(result, rule.replacement)
                } else {
                    if (rule.caseInsensitive) {
                        result.replace(rule.pattern, rule.replacement, ignoreCase = true)
                    } else {
                        result.replace(rule.pattern, rule.replacement)
                    }
                }
            } catch (_: Exception) {
                result // if the regex is malformed, skip silently
            }
        }
        return result
    }
}
