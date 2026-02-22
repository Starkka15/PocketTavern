package com.pockettavern.app.extensions

import com.pockettavern.app.domain.model.MessageHeaderEntry
import com.pockettavern.app.domain.model.QuickReplyButton
import com.pockettavern.app.extensions.builtin.QuickReplyExtension
import com.pockettavern.app.extensions.builtin.RegexExtension
import com.pockettavern.app.extensions.builtin.TokenCounterExtension
import kotlinx.coroutines.flow.StateFlow
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

    /**
     * Message headers set by JS extensions via PT.setMessageHeader().
     * Map of messageIndex → list of headers (multiple extensions can each set one).
     */
    val messageHeaders: StateFlow<Map<Int, List<MessageHeaderEntry>>> get() = jsHost.messageHeaders

    /**
     * Buttons registered by JS extensions via PT.registerButtons().
     * Map of extensionId → button list. Combine with native quick reply for the full set.
     */
    val jsButtonSets: StateFlow<Map<String, List<QuickReplyButton>>> get() = jsHost.jsButtonSets

    /** Inline header buttons registered by extensions. extensionId → action list. */
    val headerButtons: StateFlow<Map<String, List<JsExtensionHost.HeaderAction>>> get() = jsHost.headerButtons

    /** Header context menus registered by extensions. extensionId → action list. */
    val headerMenus: StateFlow<Map<String, List<JsExtensionHost.HeaderAction>>> get() = jsHost.headerMenus

    /** Load persisted settings and initialise the JS sandbox. Call once at app start. */
    fun load() {
        quickReply.load()
        regex.load()
        tokenCounter.load()
        jsHost.init()
    }

    /** Process a received AI message through all enabled output regex rules. */
    fun processOutput(text: String): String = regex.processOutput(text)

    /** Apply JS extension output filters to strip metadata tags from displayed text. */
    fun applyOutputFilters(text: String): String = jsHost.applyOutputFilters(text)

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

    /**
     * Emit an event with a structured JSON payload (not a quoted string).
     * Used for MESSAGE_RECEIVED which carries { text, index } so JS extensions
     * can call PT.setMessageHeader(data.index, ...).
     */
    fun emitJson(event: ExtensionEvent, jsonData: String) {
        all.forEach { ext -> if (ext.enabled) ext.onEvent(event, jsonData) }
        ExtensionEventBus.emit(event, jsonData)
        jsHost.dispatchEventJson(event, jsonData)
    }

    /** Collect prompt injections from all enabled native extensions and JS extensions. */
    fun getPromptInjections(): List<String> =
        all.filter { it.enabled }.mapNotNull { it.getPromptInjection() } +
        jsHost.getInjections()

    /** Clear all JS message headers without reloading the sandbox (call when changing chat). */
    fun clearMessageHeaders() = jsHost.clearMessageHeaders()

    /** Restore persisted message headers when loading an existing chat. */
    fun restoreMessageHeaders(headers: Map<Int, List<MessageHeaderEntry>>) =
        jsHost.restoreMessageHeaders(headers)
}
