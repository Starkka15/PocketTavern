package com.pockettavern.app.domain.model

import java.util.UUID

data class RegexRule(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val enabled: Boolean = true,
    /** Pattern string. Treated as regex when isRegex=true, plain text otherwise. */
    val pattern: String,
    val isRegex: Boolean = false,
    /** Replacement string. Supports $1, $2 capture group references when isRegex=true. */
    val replacement: String,
    /** Apply this rule to AI output messages. */
    val applyToOutput: Boolean = true,
    /** Apply this rule to user input before sending. */
    val applyToInput: Boolean = false,
    /** Case-insensitive matching. */
    val caseInsensitive: Boolean = false
)
