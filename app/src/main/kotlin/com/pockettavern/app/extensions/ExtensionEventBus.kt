package com.pockettavern.app.extensions

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

enum class ExtensionEvent {
    MESSAGE_SENT,
    MESSAGE_RECEIVED,
    MESSAGE_EDITED,
    MESSAGE_DELETED,
    GENERATION_STARTED,
    GENERATION_STOPPED,
    CHAT_CHANGED,
    CHARACTER_CHANGED,
    PROMPT_ABOUT_TO_BUILD
}

/**
 * Global event bus mirroring SillyTavern's eventSource.
 * Handlers are called on a background coroutine scope (SupervisorJob so one
 * failing handler doesn't cancel the others).
 */
object ExtensionEventBus {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val listeners = mutableMapOf<ExtensionEvent, MutableList<suspend (Any?) -> Unit>>()

    /** Subscribe to an event. Returns an unsubscribe function. */
    fun on(event: ExtensionEvent, handler: suspend (Any?) -> Unit): () -> Unit {
        listeners.getOrPut(event) { mutableListOf() }.add(handler)
        return { listeners[event]?.remove(handler) }
    }

    /** Emit an event, calling all subscribers asynchronously. */
    fun emit(event: ExtensionEvent, data: Any? = null) {
        val handlers = listeners[event]?.toList() ?: return
        scope.launch {
            handlers.forEach { it(data) }
        }
    }
}
