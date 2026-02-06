package com.stark.sillytavern.ui.screens.createcharacter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stark.sillytavern.data.repository.ForgeRepository
import com.stark.sillytavern.data.repository.SettingsRepository
import com.stark.sillytavern.data.repository.SillyTavernRepository
import com.stark.sillytavern.domain.model.ForgeGenerationParams
import com.stark.sillytavern.domain.model.GenerationState
import com.stark.sillytavern.domain.model.Result
import com.stark.sillytavern.util.PngCharacterCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CreateCharacterUiState(
    val name: String = "",
    val description: String = "",
    val personality: String = "",
    val scenario: String = "",
    val firstMessage: String = "",
    val messageExample: String = "",
    val avatarPrompt: String = "",
    val avatarBase64: String? = null,
    val generationState: GenerationState = GenerationState.Idle,
    val forgeAvailable: Boolean = false,
    val isCreating: Boolean = false,
    val createSuccess: Boolean = false,
    val error: String? = null,
    // Edit mode fields
    val isEditMode: Boolean = false,
    val editAvatarUrl: String? = null,
    val isLoadingCharacter: Boolean = false,

    // V2 extended fields
    val systemPrompt: String = "",
    val postHistoryInstructions: String = "",
    val creatorNotes: String = "",
    val alternateGreetings: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val creator: String = "",

    // Embedded lorebook from card (for display/import)
    val hasCharacterBook: Boolean = false,
    val characterBookEntryCount: Int = 0,

    // Flag indicating this is a full card import (use PNG directly)
    val isCardImport: Boolean = false,
    val cardPngBytes: ByteArray? = null
)

@HiltViewModel
class CreateCharacterViewModel @Inject constructor(
    private val stRepository: SillyTavernRepository,
    private val forgeRepository: ForgeRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateCharacterUiState())
    val uiState: StateFlow<CreateCharacterUiState> = _uiState.asStateFlow()

    init {
        checkForgeAvailability()
    }

    private fun checkForgeAvailability() {
        viewModelScope.launch {
            val settings = settingsRepository.getSettings()
            _uiState.update { it.copy(forgeAvailable = settings.forgeUrl.isNotBlank()) }
        }
    }

    fun updateName(value: String) {
        _uiState.update { it.copy(name = value) }
    }

    fun updateDescription(value: String) {
        _uiState.update { it.copy(description = value) }
    }

    fun updatePersonality(value: String) {
        _uiState.update { it.copy(personality = value) }
    }

    fun updateScenario(value: String) {
        _uiState.update { it.copy(scenario = value) }
    }

    fun updateFirstMessage(value: String) {
        _uiState.update { it.copy(firstMessage = value) }
    }

    fun updateMessageExample(value: String) {
        _uiState.update { it.copy(messageExample = value) }
    }

    fun updateAvatarPrompt(value: String) {
        _uiState.update { it.copy(avatarPrompt = value) }
    }

    // V2 extended field updates
    fun updateSystemPrompt(value: String) {
        _uiState.update { it.copy(systemPrompt = value) }
    }

    fun updatePostHistoryInstructions(value: String) {
        _uiState.update { it.copy(postHistoryInstructions = value) }
    }

    fun updateCreatorNotes(value: String) {
        _uiState.update { it.copy(creatorNotes = value) }
    }

    fun updateCreator(value: String) {
        _uiState.update { it.copy(creator = value) }
    }

    fun updateTags(tags: List<String>) {
        _uiState.update { it.copy(tags = tags) }
    }

    fun addTag(tag: String) {
        if (tag.isNotBlank() && tag !in _uiState.value.tags) {
            _uiState.update { it.copy(tags = it.tags + tag.trim()) }
        }
    }

    fun removeTag(tag: String) {
        _uiState.update { it.copy(tags = it.tags - tag) }
    }

    fun updateAlternateGreetings(greetings: List<String>) {
        _uiState.update { it.copy(alternateGreetings = greetings) }
    }

    fun addAlternateGreeting(greeting: String = "") {
        _uiState.update { it.copy(alternateGreetings = it.alternateGreetings + greeting) }
    }

    fun updateAlternateGreeting(index: Int, value: String) {
        val updated = _uiState.value.alternateGreetings.toMutableList()
        if (index in updated.indices) {
            updated[index] = value
            _uiState.update { it.copy(alternateGreetings = updated) }
        }
    }

    fun removeAlternateGreeting(index: Int) {
        val updated = _uiState.value.alternateGreetings.toMutableList()
        if (index in updated.indices) {
            updated.removeAt(index)
            _uiState.update { it.copy(alternateGreetings = updated) }
        }
    }

    fun loadCharacterForEdit(avatarUrl: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingCharacter = true, isEditMode = true, editAvatarUrl = avatarUrl) }
            when (val result = stRepository.getCharacter(avatarUrl)) {
                is Result.Success -> {
                    val character = result.data
                    _uiState.update {
                        it.copy(
                            name = character.name,
                            description = character.description,
                            personality = character.personality,
                            scenario = character.scenario,
                            firstMessage = character.firstMessage,
                            messageExample = character.messageExample,
                            isLoadingCharacter = false
                        )
                    }
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoadingCharacter = false,
                            error = result.exception.message
                        )
                    }
                }
            }
        }
    }

    fun generateAvatar() {
        val prompt = _uiState.value.avatarPrompt.ifBlank {
            // Generate default prompt from character info
            val name = _uiState.value.name
            val desc = _uiState.value.description.take(100)
            "portrait of $name, $desc, high quality, detailed, fantasy character art"
        }

        viewModelScope.launch {
            val params = ForgeGenerationParams(
                prompt = prompt,
                width = 512,
                height = 768,
                steps = 20
            )

            forgeRepository.generateImageWithProgress(params).collect { state ->
                _uiState.update { it.copy(generationState = state) }

                when (state) {
                    is GenerationState.Complete -> {
                        _uiState.update { it.copy(avatarBase64 = state.imageBase64) }
                    }
                    is GenerationState.Error -> {
                        _uiState.update { it.copy(error = state.message) }
                    }
                    else -> { }
                }
            }
        }
    }

    fun cancelGeneration() {
        viewModelScope.launch {
            forgeRepository.interrupt()
            _uiState.update { it.copy(generationState = GenerationState.Idle) }
        }
    }

    fun clearAvatar() {
        _uiState.update {
            it.copy(
                avatarBase64 = null,
                generationState = GenerationState.Idle
            )
        }
    }

    fun setAvatarFromBytes(bytes: ByteArray) {
        val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)

        // Try to extract character card data from PNG
        val cardData = PngCharacterCard.extractCharacterData(bytes)

        _uiState.update {
            if (cardData != null) {
                val data = cardData.data
                val hasBook = data.characterBook != null && data.characterBook.entries.isNotEmpty()

                // Populate all fields from character card
                it.copy(
                    avatarBase64 = base64,
                    generationState = GenerationState.Idle,
                    // Basic fields
                    name = data.name,
                    description = data.description,
                    personality = data.personality,
                    scenario = data.scenario,
                    firstMessage = data.firstMes,
                    messageExample = data.mesExample,
                    // V2 extended fields
                    systemPrompt = data.systemPrompt,
                    postHistoryInstructions = data.postHistoryInstructions,
                    creatorNotes = data.creatorNotes,
                    alternateGreetings = data.alternateGreetings,
                    tags = data.tags,
                    creator = data.creator,
                    // Character book info
                    hasCharacterBook = hasBook,
                    characterBookEntryCount = data.characterBook?.entries?.size ?: 0,
                    // Store PNG for direct import to preserve all data
                    isCardImport = true,
                    cardPngBytes = bytes
                )
            } else {
                // Just set the avatar (regular image, not a card)
                it.copy(
                    avatarBase64 = base64,
                    generationState = GenerationState.Idle,
                    isCardImport = false,
                    cardPngBytes = null
                )
            }
        }
    }

    fun createCharacter() {
        if (_uiState.value.name.isBlank()) {
            _uiState.update { it.copy(error = "Character name is required") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isCreating = true) }

            val state = _uiState.value
            val result = if (state.isEditMode && state.editAvatarUrl != null) {
                // Edit existing character
                stRepository.editCharacter(
                    avatarUrl = state.editAvatarUrl,
                    name = state.name.trim(),
                    description = state.description.trim(),
                    personality = state.personality.trim(),
                    scenario = state.scenario.trim(),
                    firstMessage = state.firstMessage.trim(),
                    messageExample = state.messageExample.trim()
                )
            } else if (state.isCardImport && state.cardPngBytes != null) {
                // Import character card directly (preserves all data including lorebooks)
                val fileName = state.name.trim().replace(Regex("[^a-zA-Z0-9]"), "_") + ".png"
                stRepository.importCharacterCard(state.cardPngBytes, fileName)
            } else {
                // Create new character from scratch
                stRepository.createCharacter(
                    name = state.name.trim(),
                    description = state.description.trim(),
                    personality = state.personality.trim(),
                    scenario = state.scenario.trim(),
                    firstMessage = state.firstMessage.trim(),
                    messageExample = state.messageExample.trim(),
                    avatarBase64 = state.avatarBase64
                )
            }

            when (result) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            isCreating = false,
                            createSuccess = true
                        )
                    }
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            isCreating = false,
                            error = result.exception.message
                        )
                    }
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
