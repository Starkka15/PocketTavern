package com.pockettavern.app.extensions.builtin

import com.pockettavern.app.data.local.ExtensionStorage
import com.pockettavern.app.domain.model.QuickReplyButton
import com.pockettavern.app.domain.model.QuickReplyPreset
import com.pockettavern.app.extensions.ExtensionEvent
import com.pockettavern.app.extensions.ExtensionEventBus
import com.pockettavern.app.extensions.NativeExtension
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuickReplyExtension @Inject constructor(
    private val storage: ExtensionStorage
) : NativeExtension {

    override val id = "quick_reply"
    override val displayName = "Quick Reply"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _presets = MutableStateFlow<List<QuickReplyPreset>>(emptyList())
    val presets: StateFlow<List<QuickReplyPreset>> = _presets.asStateFlow()

    private var _enabled: Boolean = true
    override val enabled: Boolean get() = _enabled

    /** Flat list of buttons across all enabled presets — what the chat UI shows. */
    private val _activeButtons = MutableStateFlow<List<QuickReplyButton>>(emptyList())
    val activeButtons: StateFlow<List<QuickReplyButton>> get() = _activeButtons

    /**
     * Emits a button whenever one of its autoTriggers fires.
     * ChatViewModel collects this and calls sendMessageText().
     */
    private val _autoTriggerFlow = MutableSharedFlow<QuickReplyButton>()
    val autoTriggerFlow: SharedFlow<QuickReplyButton> = _autoTriggerFlow.asSharedFlow()

    // Unsubscribe handles from ExtensionEventBus (cleared and re-registered on each load()).
    private val unsubscribers = mutableListOf<() -> Unit>()

    fun load() {
        val enabledMap = storage.loadEnabledMap()
        _enabled = enabledMap[id] ?: true
        val loaded = storage.loadQuickReplyPresets()
        _presets.value = loaded
        refreshActiveButtons()
        registerAutoTriggers()
    }

    fun setEnabled(value: Boolean) {
        _enabled = value
        persistEnabled()
        refreshActiveButtons()
    }

    fun savePresets(presets: List<QuickReplyPreset>) {
        _presets.value = presets
        storage.saveQuickReplyPresets(presets)
        refreshActiveButtons()
    }

    private fun refreshActiveButtons() {
        _activeButtons.value = if (_enabled) {
            _presets.value.filter { it.enabled }.flatMap { it.buttons }
        } else {
            emptyList()
        }
    }

    private fun registerAutoTriggers() {
        unsubscribers.forEach { it() }
        unsubscribers.clear()

        val triggerableEvents = listOf(
            ExtensionEvent.CHAT_CHANGED,
            ExtensionEvent.MESSAGE_RECEIVED,
            ExtensionEvent.MESSAGE_SENT,
            ExtensionEvent.GENERATION_STOPPED
        )

        triggerableEvents.forEach { event ->
            val unsubscribe = ExtensionEventBus.on(event) {
                if (!_enabled) return@on
                _presets.value
                    .filter { it.enabled }
                    .flatMap { it.buttons }
                    .filter { event.name in it.autoTriggers }
                    .forEach { btn -> _autoTriggerFlow.emit(btn) }
            }
            unsubscribers.add(unsubscribe)
        }
    }

    private fun persistEnabled() {
        val map = storage.loadEnabledMap().toMutableMap()
        map[id] = _enabled
        storage.saveEnabledMap(map)
    }
}
