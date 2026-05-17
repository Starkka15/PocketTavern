package com.pockettavern.app.ui.screens.charactersettings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pockettavern.app.data.local.JsExtensionStorage
import com.pockettavern.app.data.local.TtsVoiceStorage
import com.pockettavern.app.data.repository.LocalRepository
import com.pockettavern.app.domain.model.Character
import com.pockettavern.app.domain.model.Result
import com.pockettavern.app.domain.model.WorldInfoListItem
import com.pockettavern.app.ui.audio.TtsManager
import com.pockettavern.app.ui.audio.TtsVoice
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CharacterSettingsUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val character: Character? = null,
    val avatarUrl: String = "",
    // Editable fields
    val systemPrompt: String = "",
    val postHistoryInstructions: String = "",
    val attachedWorldInfo: String? = null,
    val depthPrompt: String = "",
    val depthPromptDepth: Int = 4,
    val depthPromptRole: String = "system",
    val talkativeness: Float = 0.5f,
    val isFavorite: Boolean = false,
    val notes: String = "",
    // Available world info files
    val availableWorldInfo: List<WorldInfoListItem> = emptyList(),
    // TTS voice
    val ttsVoiceId: String? = null,
    val ttsProviderOverride: String? = null,
    val availableVoices: List<TtsVoice> = emptyList(),
    // Extension toggles: extensionId → (displayName, enabled)
    val extensionToggles: List<ExtensionToggle> = emptyList(),
    // Messages
    val error: String? = null,
    val saveSuccess: Boolean = false
)

data class ExtensionToggle(
    val id: String,
    val name: String,
    val enabled: Boolean
)

@HiltViewModel
class CharacterSettingsViewModel @Inject constructor(
    private val localRepository: LocalRepository,
    private val ttsVoiceStorage: TtsVoiceStorage,
    private val ttsManager: TtsManager,
    private val jsExtensionStorage: JsExtensionStorage,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(CharacterSettingsUiState())
    val uiState: StateFlow<CharacterSettingsUiState> = _uiState.asStateFlow()

    private val fileName: String = savedStateHandle.get<String>("avatarUrl") ?: ""

    init {
        loadCharacter()
        loadAvailableWorldInfo()
        loadTtsVoice()
        loadExtensionToggles()
    }

    private fun loadCharacter() {
        if (fileName.isBlank()) {
            _uiState.update { it.copy(isLoading = false, error = "No character specified") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, avatarUrl = fileName) }

            when (val result = localRepository.getCharacter(fileName)) {
                is Result.Success -> {
                    val character = result.data
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            character = character,
                            systemPrompt = character.systemPrompt,
                            postHistoryInstructions = character.postHistoryInstructions,
                            attachedWorldInfo = character.attachedWorldInfo,
                            depthPrompt = character.depthPrompt,
                            depthPromptDepth = character.depthPromptDepth,
                            depthPromptRole = character.depthPromptRole,
                            talkativeness = character.talkativeness,
                            isFavorite = character.isFavorite,
                            notes = character.notes
                        )
                    }
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(isLoading = false, error = result.exception.message)
                    }
                }
            }
        }
    }

    private fun loadAvailableWorldInfo() {
        viewModelScope.launch {
            when (val result = localRepository.getWorldInfoList()) {
                is Result.Success -> {
                    val items = result.data.map { name -> WorldInfoListItem(fileId = name, name = name) }
                    _uiState.update { it.copy(availableWorldInfo = items) }
                }
                is Result.Error -> {
                    // Not critical, ignore
                }
            }
        }
    }

    fun updateSystemPrompt(value: String) {
        _uiState.update { it.copy(systemPrompt = value) }
    }

    fun updatePostHistoryInstructions(value: String) {
        _uiState.update { it.copy(postHistoryInstructions = value) }
    }

    fun updateAttachedWorldInfo(value: String?) {
        _uiState.update { it.copy(attachedWorldInfo = value) }
    }

    fun updateDepthPrompt(value: String) {
        _uiState.update { it.copy(depthPrompt = value) }
    }

    fun updateDepthPromptDepth(value: Int) {
        _uiState.update { it.copy(depthPromptDepth = value.coerceIn(0, 999)) }
    }

    fun updateDepthPromptRole(value: String) {
        _uiState.update { it.copy(depthPromptRole = value) }
    }

    fun updateTalkativeness(value: Float) {
        _uiState.update { it.copy(talkativeness = value.coerceIn(0f, 1f)) }
    }

    fun updateIsFavorite(value: Boolean) {
        _uiState.update { it.copy(isFavorite = value) }
    }

    fun updateNotes(value: String) {
        _uiState.update { it.copy(notes = value) }
    }

    fun saveSettings() {
        val state = _uiState.value
        val base = state.character ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }

            val updated = base.copy(
                systemPrompt = state.systemPrompt,
                postHistoryInstructions = state.postHistoryInstructions,
                attachedWorldInfo = state.attachedWorldInfo,
                depthPrompt = state.depthPrompt,
                depthPromptDepth = state.depthPromptDepth,
                depthPromptRole = state.depthPromptRole,
                talkativeness = state.talkativeness,
                isFavorite = state.isFavorite,
                notes = state.notes
            )

            when (val result = localRepository.editCharacter(fileName, updated)) {
                is Result.Success -> {
                    _uiState.update { it.copy(isSaving = false, saveSuccess = true, character = updated) }
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(isSaving = false, error = result.exception.message)
                    }
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearSaveSuccess() {
        _uiState.update { it.copy(saveSuccess = false) }
    }

    // ── Extension Toggles ──────────────────────────────────────────────────

    private fun loadExtensionToggles() {
        if (fileName.isBlank()) return
        val extensions = jsExtensionStorage.listExtensions().filter { it.enabled }
        val charOverrides = jsExtensionStorage.getCharacterOverrides(fileName)
        val toggles = extensions.map { ext ->
            ExtensionToggle(
                id = ext.id,
                name = ext.name,
                enabled = charOverrides[ext.id] ?: true  // default to enabled
            )
        }
        _uiState.update { it.copy(extensionToggles = toggles) }
    }

    fun setExtensionEnabled(extensionId: String, enabled: Boolean) {
        jsExtensionStorage.setCharacterExtensionEnabled(fileName, extensionId, enabled)
        // Update local UI state
        _uiState.update { state ->
            state.copy(
                extensionToggles = state.extensionToggles.map { toggle ->
                    if (toggle.id == extensionId) toggle.copy(enabled = enabled) else toggle
                }
            )
        }
    }

    // ── TTS Voice ──────────────────────────────────────────────────────────

    private fun loadTtsVoice() {
        if (fileName.isBlank()) return
        val voiceId = ttsVoiceStorage.getVoiceId(fileName)
        val providerOverride = ttsVoiceStorage.getProviderOverride(fileName)
        _uiState.update { it.copy(ttsVoiceId = voiceId, ttsProviderOverride = providerOverride) }
        // Load available voices
        viewModelScope.launch {
            val voices = ttsManager.getVoices()
            _uiState.update { it.copy(availableVoices = voices) }
        }
    }

    fun updateTtsVoice(voiceId: String?) {
        _uiState.update { it.copy(ttsVoiceId = voiceId) }
        if (voiceId != null) {
            ttsVoiceStorage.setVoiceId(fileName, voiceId)
        } else {
            ttsVoiceStorage.clearVoice(fileName)
        }
    }

    fun updateTtsProviderOverride(provider: String?) {
        _uiState.update { it.copy(ttsProviderOverride = provider) }
        ttsVoiceStorage.setProviderOverride(fileName, provider)
        // Reload voices for the new provider
        viewModelScope.launch {
            val voices = if (provider != null) {
                ttsManager.getVoicesForProvider(provider)
            } else {
                ttsManager.getVoices()
            }
            _uiState.update { it.copy(availableVoices = voices) }
        }
    }

    fun testTtsVoice() {
        viewModelScope.launch {
            ttsManager.speak("Hello, this is a voice test for this character.", fileName)
        }
    }
}
