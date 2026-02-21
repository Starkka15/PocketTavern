package com.pockettavern.app.extensions

import com.pockettavern.app.extensions.builtin.QuickReplyExtension
import com.pockettavern.app.extensions.builtin.RegexExtension
import com.pockettavern.app.extensions.builtin.TokenCounterExtension
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central coordinator for all PocketTavern extensions — both native (Kotlin) and
 * JavaScript extensions running in the WebView sandbox.
 */
@Singleton
class ExtensionManager @Inject constructor(
    val quickReply: QuickReplyExtension,
    val regex: RegexExtension,
    val tokenCounter: TokenCounterExtension,
    val jsHost: JsExtensionHost
) {
    val all: List<NativeExtension> get() = listOf(quickReply, regex, tokenCounter)

    /** Load persisted settings and initialise the JS sandbox. Call once at app start. */
    fun load() {
        quickReply.load()
        regex.load()
        tokenCounter.load()
        jsHost.init()
    }

    /** Process a received AI message through all enabled output regex rules. */
    fun processOutput(text: String): String = regex.processOutput(text)

    /** Process a user input message through all enabled input regex rules. */
    fun processInput(text: String): String = regex.processInput(text)

    /** Update the context JSON available to JS extensions via PT.getContext(). */
    fun updateContext(json: String) = jsHost.updateContext(json)

    /** Emit an event to all native extensions, the global bus, and the JS sandbox. */
    fun emit(event: ExtensionEvent, data: Any? = null) {
        all.forEach { ext -> if (ext.enabled) ext.onEvent(event, data) }
        ExtensionEventBus.emit(event, data)
        jsHost.dispatchEvent(event, data)
    }

    /** Collect prompt injections from all enabled native extensions and JS extensions. */
    fun getPromptInjections(): List<String> =
        all.filter { it.enabled }.mapNotNull { it.getPromptInjection() } +
        jsHost.getInjections()
}
