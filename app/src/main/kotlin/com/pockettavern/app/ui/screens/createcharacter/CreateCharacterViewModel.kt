package com.pockettavern.app.ui.screens.createcharacter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.pockettavern.app.data.repository.ForgeRepository
import com.pockettavern.app.data.repository.LocalRepository
import com.pockettavern.app.data.repository.SettingsRepository
import com.pockettavern.app.domain.model.Character
import com.pockettavern.app.domain.model.ForgeGenerationParams
import com.pockettavern.app.domain.model.GenerationState
import com.pockettavern.app.domain.model.Result
import com.pockettavern.app.util.PngCharacterCard
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
    private val localRepository: LocalRepository,
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
            when (val result = localRepository.getCharacter(avatarUrl)) {
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
                            systemPrompt = character.systemPrompt,
                            postHistoryInstructions = character.postHistoryInstructions,
                            creatorNotes = character.creatorNotes,
                            alternateGreetings = character.alternateGreetings,
                            tags = character.tags,
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
        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)

        // Try to extract character card data from PNG
        val cardData = PngCharacterCard.extractCharacterData(bytes)

        _uiState.update {
            if (cardData != null) {
                val data = cardData.data
                val hasBook = data.characterBook != null && data.characterBook.entries.isNotEmpty()

                it.copy(
                    avatarBase64 = base64,
                    generationState = GenerationState.Idle,
                    name = data.name,
                    description = data.description,
                    personality = data.personality,
                    scenario = data.scenario,
                    firstMessage = data.firstMes,
                    messageExample = data.mesExample,
                    systemPrompt = data.systemPrompt,
                    postHistoryInstructions = data.postHistoryInstructions,
                    creatorNotes = data.creatorNotes,
                    alternateGreetings = data.alternateGreetings,
                    tags = data.tags,
                    creator = data.creator,
                    hasCharacterBook = hasBook,
                    characterBookEntryCount = data.characterBook?.entries?.size ?: 0,
                    isCardImport = true,
                    cardPngBytes = bytes
                )
            } else {
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
            val result = when {
                state.isEditMode && state.editAvatarUrl != null -> {
                    // Edit existing character — load the base, update fields, save
                    val base = when (val r = localRepository.getCharacter(state.editAvatarUrl)) {
                        is Result.Success -> r.data
                        is Result.Error -> Character(name = state.name, avatar = state.editAvatarUrl)
                    }
                    val updated = base.copy(
                        name = state.name.trim(),
                        description = state.description.trim(),
                        personality = state.personality.trim(),
                        scenario = state.scenario.trim(),
                        firstMessage = state.firstMessage.trim(),
                        messageExample = state.messageExample.trim(),
                        systemPrompt = state.systemPrompt.trim(),
                        postHistoryInstructions = state.postHistoryInstructions.trim(),
                        creatorNotes = state.creatorNotes.trim(),
                        alternateGreetings = state.alternateGreetings,
                        tags = state.tags
                    )
                    localRepository.editCharacter(state.editAvatarUrl, updated)
                }
                state.isCardImport && state.cardPngBytes != null -> {
                    // Import card PNG directly (preserves all embedded data including lorebooks)
                    val fileName = state.name.trim().replace(Regex("[^a-zA-Z0-9]"), "_") + ".png"
                    localRepository.importCharacterCardBytes(state.cardPngBytes, fileName)
                }
                else -> {
                    // Create new character from scratch
                    val avatarBase64 = state.avatarBase64
                    val avatarBitmap: Bitmap? = avatarBase64?.let { b64 ->
                        val bytes = Base64.decode(b64, Base64.DEFAULT)
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }
                    val character = Character(
                        name = state.name.trim(),
                        avatar = null,
                        description = state.description.trim(),
                        personality = state.personality.trim(),
                        scenario = state.scenario.trim(),
                        firstMessage = state.firstMessage.trim(),
                        messageExample = state.messageExample.trim(),
                        systemPrompt = state.systemPrompt.trim(),
                        postHistoryInstructions = state.postHistoryInstructions.trim(),
                        creatorNotes = state.creatorNotes.trim(),
                        alternateGreetings = state.alternateGreetings,
                        tags = state.tags
                    )
                    // Use createCharacterWithBitmap to pass avatar image
                    localRepository.createCharacterWithBitmap(character, avatarBitmap)
                }
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
