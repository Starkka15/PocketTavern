package com.pockettavern.app.ui.screens.charactersettings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pockettavern.app.data.local.JsExtensionStorage
import com.pockettavern.app.data.local.TtsVoiceStorage
import com.pockettavern.app.data.repository.ImageGenRepository
import com.pockettavern.app.data.repository.LocalRepository
import com.pockettavern.app.domain.model.Character
import com.pockettavern.app.domain.model.Result
import com.pockettavern.app.domain.model.WorldInfoListItem
import com.pockettavern.app.extensions.ExtensionManager
import com.pockettavern.app.extensions.JsExtensionHost
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
    // Available world info files
    val availableWorldInfo: List<WorldInfoListItem> = emptyList(),
    // TTS voice
    val ttsVoiceId: String? = null,
    val ttsProviderOverride: String? = null,
    val availableVoices: List<TtsVoice> = emptyList(),
    // Extension toggles: extensionId → (displayName, enabled)
    val extensionToggles: List<ExtensionToggle> = emptyList(),
    // Extension per-character settings: extensionId → list of fields with current values
    val extensionCharSettings: Map<String, List<ExtCharSettingField>> = emptyMap(),
    // Dynamic options fetched from image gen backend: source key → options list
    val dynamicOptions: Map<String, List<DynamicSelectOption>> = emptyMap(),
    // Messages
    val error: String? = null,
    val saveSuccess: Boolean = false
)

data class ExtensionToggle(
    val id: String,
    val name: String,
    val enabled: Boolean
)

data class ExtCharSettingField(
    val key: String,
    val label: String,
    val type: String,
    val currentValue: String,
    val default: String,
    val options: List<DynamicSelectOption>? = null,
    val source: String? = null
)

data class DynamicSelectOption(
    val label: String,
    val value: String
)

/** Bundled default art styles, always available regardless of backend connection. */
private val BUNDLED_ART_STYLES = listOf(
    "digital painting, highly detailed, dramatic lighting",
    "anime, cel shaded, vibrant colors",
    "photorealistic, 8k, studio lighting",
    "oil painting, classical, rich colors",
    "watercolor, soft, pastel tones",
    "pixel art, retro, 16-bit",
    "comic book, bold lines, halftone",
    "concept art, matte painting",
    "pencil sketch, detailed linework",
    "3D render, Pixar style, subsurface scattering"
)

@HiltViewModel
class CharacterSettingsViewModel @Inject constructor(
    private val localRepository: LocalRepository,
    private val ttsVoiceStorage: TtsVoiceStorage,
    private val ttsManager: TtsManager,
    private val jsExtensionStorage: JsExtensionStorage,
    private val extensionManager: ExtensionManager,
    private val imageGenRepository: ImageGenRepository,
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
        loadExtensionCharSettings()
        observeCharSettingsDefs()
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
                            isFavorite = character.isFavorite
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
                isFavorite = state.isFavorite
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

    // ── Extension Per-Character Settings ──────────────────────────────────

    /**
     * Observe charSettingsDefs so that if extensions register their fields after
     * the ViewModel init (e.g. JS sandbox still loading), we pick them up.
     */
    private fun observeCharSettingsDefs() {
        if (fileName.isBlank()) return
        viewModelScope.launch {
            extensionManager.charSettingsDefs.collect { defs ->
                if (defs.isNotEmpty()) {
                    applyCharSettingsDefs(defs)
                }
            }
        }
    }

    private fun loadExtensionCharSettings() {
        if (fileName.isBlank()) return

        val defs = extensionManager.charSettingsDefs.value
        if (defs.isEmpty()) return
        applyCharSettingsDefs(defs)
    }

    private fun applyCharSettingsDefs(defs: Map<String, List<JsExtensionHost.CharSettingField>>) {
        if (fileName.isBlank()) return

        val allSources = mutableSetOf<String>()
        val settings = defs.mapValues { (extId, fields) ->
            fields.map { field ->
                if (field.source != null) allSources.add(field.source)
                val savedValue = jsExtensionStorage.getCharacterExtensionSetting(
                    fileName, extId, field.key
                )
                ExtCharSettingField(
                    key = field.key,
                    label = field.label,
                    type = field.type,
                    currentValue = savedValue ?: field.default,
                    default = field.default,
                    options = field.options?.map { DynamicSelectOption(it.label, it.value) },
                    source = field.source
                )
            }
        }

        _uiState.update { it.copy(extensionCharSettings = settings) }

        // Fetch dynamic options for any source keys
        if (allSources.isNotEmpty()) {
            fetchDynamicOptions(allSources)
        }
    }

    private fun fetchDynamicOptions(sources: Set<String>) {
        viewModelScope.launch {
            val options = mutableMapOf<String, List<DynamicSelectOption>>()

            for (source in sources) {
                val result: Result<List<String>>? = when (source) {
                    "sd_samplers" -> imageGenRepository.getSamplers()
                    "sd_models" -> imageGenRepository.getModels()
                    "sd_schedulers" -> imageGenRepository.getSchedulers()
                    "sd_styles" -> imageGenRepository.getStyles()
                    else -> null
                }

                val apiList = when (result) {
                    is Result.Success -> result.data
                    else -> emptyList()
                }

                val optionsList = if (source == "sd_styles") {
                    // Merge bundled defaults with API styles
                    val bundled = BUNDLED_ART_STYLES.map { DynamicSelectOption(it, it) }
                    val api = apiList
                        .filter { name -> name !in BUNDLED_ART_STYLES }
                        .map { DynamicSelectOption(it, it) }
                    bundled + api
                } else {
                    apiList.map { DynamicSelectOption(it, it) }
                }

                if (optionsList.isNotEmpty()) {
                    options[source] = optionsList
                }
            }

            _uiState.update { it.copy(dynamicOptions = options) }
        }
    }

    fun updateExtensionCharSetting(extensionId: String, key: String, value: String) {
        // Save immediately to storage
        jsExtensionStorage.setCharacterExtensionSetting(fileName, extensionId, key, value)

        // Update local UI state
        _uiState.update { state ->
            val updated = state.extensionCharSettings.toMutableMap()
            updated[extensionId] = updated[extensionId]?.map { field ->
                if (field.key == key) field.copy(currentValue = value) else field
            } ?: emptyList()
            state.copy(extensionCharSettings = updated)
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
