package com.pockettavern.app.extensions

import com.pockettavern.app.extensions.builtin.QuickReplyExtension
import com.pockettavern.app.extensions.builtin.RegexExtension
import com.pockettavern.app.extensions.builtin.TokenCounterExtension
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central coordinator for all PocketTavern extensions.
 * Injected as a singleton; call [load] once at app start (from Application or the first ViewModel
 * that needs it).
 */
@Singleton
class ExtensionManager @Inject constructor(
    val quickReply: QuickReplyExtension,
    val regex: RegexExtension,
    val tokenCounter: TokenCounterExtension
) {
    val all: List<NativeExtension> get() = listOf(quickReply, regex, tokenCounter)

    /** Load persisted settings for all extensions. Call once after DI graph is ready. */
    fun load() {
        quickReply.load()
        regex.load()
        tokenCounter.load()
    }

    /** Process a received AI message through all enabled output regex rules. */
    fun processOutput(text: String): String = regex.processOutput(text)

    /** Process a user input message through all enabled input regex rules. */
    fun processInput(text: String): String = regex.processInput(text)

    /** Emit an event to all native extensions and the global event bus. */
    fun emit(event: ExtensionEvent, data: Any? = null) {
        all.forEach { ext ->
            if (ext.enabled) ext.onEvent(event, data)
        }
        ExtensionEventBus.emit(event, data)
    }

    /** Collect prompt injections from all enabled extensions. */
    fun getPromptInjections(): List<String> =
        all.filter { it.enabled }.mapNotNull { it.getPromptInjection() }
}
