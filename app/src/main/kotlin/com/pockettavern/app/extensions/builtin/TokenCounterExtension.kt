package com.pockettavern.app.extensions.builtin

import com.pockettavern.app.data.local.ExtensionStorage
import com.pockettavern.app.extensions.ExtensionEvent
import com.pockettavern.app.extensions.NativeExtension
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenCounterExtension @Inject constructor(
    private val storage: ExtensionStorage
) : NativeExtension {

    override val id = "token_counter"
    override val displayName = "Token Counter"

    private var _enabled: Boolean = false   // off by default (non-intrusive)
    override val enabled: Boolean get() = _enabled

    fun load() {
        val enabledMap = storage.loadEnabledMap()
        _enabled = enabledMap[id] ?: false
    }

    fun setEnabled(value: Boolean) {
        _enabled = value
        val map = storage.loadEnabledMap().toMutableMap()
        map[id] = value
        storage.saveEnabledMap(map)
    }

    /**
     * Estimate token count for a string using a simple heuristic.
     * 1 token ≈ 4 characters for Latin text (rough GPT tokenizer approximation).
     * Good enough for a live UI counter; not used for hard context limits.
     */
    fun estimateTokens(text: String): Int = (text.length / 4).coerceAtLeast(0)
}
