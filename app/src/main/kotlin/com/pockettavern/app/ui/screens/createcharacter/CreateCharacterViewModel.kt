package com.pockettavern.app.ui.screens.createcharacter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.pockettavern.app.data.repository.ForgeRepository
import com.pockettavern.app.data.repository.LlmRepository
import com.pockettavern.app.data.repository.LocalRepository
import com.pockettavern.app.data.repository.SettingsRepository
import com.pockettavern.app.domain.model.ApiConfiguration
import com.pockettavern.app.domain.model.Character
import com.pockettavern.app.domain.model.ForgeGenerationParams
import com.pockettavern.app.domain.model.GenerationState
import com.pockettavern.app.domain.model.PromptMessage
import com.pockettavern.app.domain.model.Result
import com.pockettavern.app.domain.model.StreamEvent
import kotlinx.coroutines.Job
import com.pockettavern.app.util.AvatarAnalyzer
import com.pockettavern.app.util.PngCharacterCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class CardField {
    DESCRIPTION, PERSONALITY, SCENARIO, FIRST_MESSAGE, MESSAGE_EXAMPLE
}

enum class Lewdness(val label: String, val description: String) {
    SFW("SFW", "Safe, no sexual content"),
    MILD("Mild", "Light flirting, innocent romance"),
    SUGGESTIVE("Suggestive", "Innuendo, teasing, tension"),
    MATURE("Mature", "Sexual themes, fade-to-black"),
    EXPLICIT("Explicit", "Fully graphic, no restrictions")
}

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
    val cardPngBytes: ByteArray? = null,

    // AI field generation state
    val generatingField: CardField? = null,
    val llmAvailable: Boolean = false,
    val lewdness: Lewdness = Lewdness.SFW
)

@HiltViewModel
class CreateCharacterViewModel @Inject constructor(
    private val localRepository: LocalRepository,
    private val forgeRepository: ForgeRepository,
    private val settingsRepository: SettingsRepository,
    private val llmRepository: LlmRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateCharacterUiState())
    val uiState: StateFlow<CreateCharacterUiState> = _uiState.asStateFlow()

    private var fieldGenJob: Job? = null

    init {
        checkForgeAvailability()
        checkLlmAvailability()
    }

    private fun checkForgeAvailability() {
        viewModelScope.launch {
            val settings = settingsRepository.getSettings()
            _uiState.update { it.copy(forgeAvailable = settings.forgeUrl.isNotBlank()) }
        }
    }

    private fun checkLlmAvailability() {
        viewModelScope.launch {
            val available = when (val r = localRepository.getApiConfiguration()) {
                is Result.Success -> r.data.apiServer.isNotBlank() || r.data.apiKey.isNotBlank()
                is Result.Error -> false
            }
            _uiState.update { it.copy(llmAvailable = available) }
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

    fun updateLewdness(lewdness: Lewdness) {
        _uiState.update { it.copy(lewdness = lewdness) }
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

    // ── AI Field Generation ──────────────────────────────────────────────

    fun generateField(field: CardField) {
        val state = _uiState.value
        if (state.name.isBlank()) {
            _uiState.update { it.copy(error = "Enter a character name before generating") }
            return
        }
        if (state.generatingField != null) return

        fieldGenJob?.cancel()
        fieldGenJob = viewModelScope.launch {
            generateFieldSuspend(field)
        }
    }

    fun cancelFieldGeneration() {
        fieldGenJob?.cancel()
        fieldGenJob = null
        viewModelScope.launch {
            val config = when (val r = localRepository.getApiConfiguration()) {
                is Result.Success -> r.data
                is Result.Error -> ApiConfiguration.DEFAULT
            }
            llmRepository.abortGeneration(config)
        }
        _uiState.update { it.copy(generatingField = null) }
    }

    fun generateAllFields() {
        val state = _uiState.value
        if (state.name.isBlank()) {
            _uiState.update { it.copy(error = "Enter a character name before generating") }
            return
        }
        if (state.generatingField != null) return

        fieldGenJob?.cancel()
        fieldGenJob = viewModelScope.launch {
            val fieldsToGenerate = CardField.entries.filter { field ->
                when (field) {
                    CardField.DESCRIPTION -> _uiState.value.description.isBlank()
                    CardField.PERSONALITY -> _uiState.value.personality.isBlank()
                    CardField.SCENARIO -> _uiState.value.scenario.isBlank()
                    CardField.FIRST_MESSAGE -> _uiState.value.firstMessage.isBlank()
                    CardField.MESSAGE_EXAMPLE -> _uiState.value.messageExample.isBlank()
                }
            }
            for (field in fieldsToGenerate) {
                generateFieldSuspend(field)
            }
        }
    }

    private suspend fun generateFieldSuspend(field: CardField) {
        _uiState.update { it.copy(generatingField = field) }

        try {
            val config = when (val r = localRepository.getApiConfiguration()) {
                is Result.Success -> r.data
                is Result.Error -> {
                    _uiState.update { it.copy(generatingField = null, error = "No LLM configured") }
                    return
                }
            }
            val preset = localRepository.getCurrentTextGenPreset()
            val oaiPreset = localRepository.getCurrentOaiPreset()

            val state = _uiState.value

            // If generating Description, analyze the avatar image on-device.
            // Uses Palette (colors) + ML Kit (labels + face) — works with any backend.
            // Falls back to avatar prompt text if no image is set.
            val avatarAnalysis = if (field == CardField.DESCRIPTION) {
                if (state.avatarBase64 != null) {
                    try {
                        val bytes = Base64.decode(state.avatarBase64, Base64.DEFAULT)
                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        val analysis = if (bitmap != null) AvatarAnalyzer.analyze(bitmap) else ""
                        analysis.ifBlank { state.avatarPrompt.ifBlank { null } }
                    } catch (_: Exception) {
                        state.avatarPrompt.ifBlank { null }
                    }
                } else {
                    state.avatarPrompt.ifBlank { null }
                }
            } else null

            val systemPrompt = buildSystemPrompt(state.lewdness)
            val userPrompt = buildFieldPrompt(field, state, avatarAnalysis)

            val messages = if (config.usesChatCompletions) {
                listOf(
                    PromptMessage("system", systemPrompt),
                    PromptMessage("user", userPrompt)
                )
            } else {
                null
            }

            val finalPrompt = if (!config.usesChatCompletions) {
                "$systemPrompt\n\n$userPrompt\n\nResponse:\n"
            } else {
                userPrompt
            }

            llmRepository.generate(
                prompt = finalPrompt,
                config = config,
                preset = preset,
                stopSequences = emptyList(),
                messages = messages,
                oaiPreset = oaiPreset
            ).collect { event ->
                when (event) {
                    is StreamEvent.Token -> {
                        _uiState.update { applyFieldContent(it, field, event.accumulated) }
                    }
                    is StreamEvent.Complete -> {
                        _uiState.update {
                            applyFieldContent(it, field, event.fullText.trim())
                                .copy(generatingField = null)
                        }
                    }
                    is StreamEvent.Error -> {
                        _uiState.update {
                            it.copy(generatingField = null, error = "Generation failed: ${event.message}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            _uiState.update { it.copy(generatingField = null, error = e.message) }
        }
    }

    private fun applyFieldContent(
        state: CreateCharacterUiState,
        field: CardField,
        content: String
    ): CreateCharacterUiState = when (field) {
        CardField.DESCRIPTION -> state.copy(description = content)
        CardField.PERSONALITY -> state.copy(personality = content)
        CardField.SCENARIO -> state.copy(scenario = content)
        CardField.FIRST_MESSAGE -> state.copy(firstMessage = content)
        CardField.MESSAGE_EXAMPLE -> state.copy(messageExample = content)
    }

    private fun buildSystemPrompt(lewdness: Lewdness): String {
        val base = "You are a character card writer for AI roleplay. " +
            "Output ONLY the requested field content. " +
            "No headers, no labels, no markdown formatting, no explanations, no preamble. " +
            "Use {{char}} to refer to the character and {{user}} to refer to the user."

        val lewdnessDirective = when (lewdness) {
            Lewdness.SFW -> " Keep all content safe for work. No sexual content, innuendo, or nudity."
            Lewdness.MILD -> " Light flirting and innocent romance are allowed. No explicit content or graphic descriptions."
            Lewdness.SUGGESTIVE -> " Innuendo, teasing, romantic tension, and suggestive descriptions are encouraged. Avoid explicit sexual acts."
            Lewdness.MATURE -> " Sexual themes and romantic/erotic tension are welcome. Intimate scenes can be implied or fade-to-black. Avoid fully graphic depictions."
            Lewdness.EXPLICIT -> " Fully explicit NSFW content is expected. Write graphic sexual descriptions, anatomy, and acts without censorship or euphemism. Be vivid and uninhibited."
        }

        return base + lewdnessDirective
    }

    private fun buildFieldPrompt(
        field: CardField,
        state: CreateCharacterUiState,
        avatarAnalysis: String? = null
    ): String {
        val context = buildString {
            append("Character name: ${state.name}\n")
            if (avatarAnalysis != null)
                append("Character appearance: $avatarAnalysis\n")
            if (state.description.isNotBlank() && field != CardField.DESCRIPTION)
                append("Description: ${state.description}\n")
            if (state.personality.isNotBlank() && field != CardField.PERSONALITY)
                append("Personality: ${state.personality}\n")
            if (state.scenario.isNotBlank() && field != CardField.SCENARIO)
                append("Scenario: ${state.scenario}\n")
            if (state.firstMessage.isNotBlank() && field != CardField.FIRST_MESSAGE)
                append("First message: ${state.firstMessage}\n")
            if (state.messageExample.isNotBlank() && field != CardField.MESSAGE_EXAMPLE)
                append("Example dialogue: ${state.messageExample}\n")
            if (state.tags.isNotEmpty())
                append("Tags: ${state.tags.joinToString(", ")}\n")
        }

        val lewdnessHint = when {
            state.lewdness >= Lewdness.SUGGESTIVE && field == CardField.DESCRIPTION ->
                " Include alluring physical details and sensual characteristics where appropriate."
            state.lewdness >= Lewdness.MATURE && field == CardField.SCENARIO ->
                " The scenario can include romantic or sexual tension between the characters."
            state.lewdness >= Lewdness.MATURE && field == CardField.FIRST_MESSAGE ->
                " The opening can include flirtatious or sexually charged elements."
            else -> ""
        }

        val instruction = when (field) {
            CardField.DESCRIPTION -> {
                val avatarHint = if (avatarAnalysis != null)
                    " Base the physical appearance on the character appearance description provided above."
                else ""
                "Write a detailed character description for {{char}} (${state.name}). " +
                "Include physical appearance, background, and key traits. " +
                "Write 2-4 paragraphs. Use {{char}} to refer to the character.$avatarHint$lewdnessHint"
            }

            CardField.PERSONALITY ->
                "Write a personality summary for {{char}} (${state.name}). " +
                "List key personality traits, behavioral tendencies, speech patterns, and quirks. " +
                "This should be concise — a paragraph or comma-separated trait list."

            CardField.SCENARIO ->
                "Write a scenario/setting for a roleplay conversation with {{char}} (${state.name}). " +
                "Describe the situation where {{user}} meets or interacts with {{char}}. " +
                "Keep it to 1-2 paragraphs. Use {{char}} and {{user}} as placeholders.$lewdnessHint"

            CardField.FIRST_MESSAGE ->
                "Write an opening roleplay message from {{char}} (${state.name}) to {{user}}. " +
                "This is the first message in the conversation — set the scene and establish {{char}}'s voice. " +
                "Write in third-person narrative with dialogue. Use asterisks for actions (*action*) and quotes for speech. " +
                "Write 2-4 paragraphs.$lewdnessHint"

            CardField.MESSAGE_EXAMPLE ->
                "Write example dialogue exchanges between {{char}} (${state.name}) and {{user}}. " +
                "Format as:\n<START>\n{{user}}: [user message]\n{{char}}: [character response with actions and dialogue]\n" +
                "<START>\n{{user}}: [different user message]\n{{char}}: [character response]\n\n" +
                "Write 2-3 exchanges that showcase {{char}}'s personality and speech style."
        }

        return "$context\n$instruction"
    }
}
