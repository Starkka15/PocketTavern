package com.pockettavern.app.extensions

/**
 * Base interface for all native (Kotlin) PocketTavern extensions.
 */
interface NativeExtension {
    /** Stable identifier used for settings persistence. */
    val id: String

    /** Human-readable name shown in the Extensions hub screen. */
    val displayName: String

    /** Whether this extension is currently active. */
    val enabled: Boolean

    /**
     * Called by ExtensionManager when an event fires.
     * Implementations should be lightweight; offload heavy work to coroutines.
     */
    fun onEvent(event: ExtensionEvent, data: Any?) {}

    /**
     * Return a string to inject into the prompt before each generation, or null.
     * Called synchronously on the generation path — keep it fast.
     */
    fun getPromptInjection(): String? = null
}
