package com.pockettavern.app.ui.screens.groups

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pockettavern.app.data.local.CharacterStorage
import com.pockettavern.app.data.local.GroupStorage
import com.pockettavern.app.data.local.SettingsDataStore
import com.pockettavern.app.data.repository.LlmRepository
import com.pockettavern.app.data.repository.LocalRepository
import com.pockettavern.app.domain.model.ActivationStrategy
import com.pockettavern.app.domain.model.ApiConfiguration
import com.pockettavern.app.domain.model.Character
import com.pockettavern.app.domain.model.Group
import com.pockettavern.app.domain.model.GroupChatMessage
import com.pockettavern.app.domain.model.Result
import com.pockettavern.app.domain.model.StreamEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random

data class GroupChatUiState(
    val group: Group? = null,
    val messages: List<GroupChatMessage> = emptyList(),
    val memberAvatarUrls: Map<String, String?> = emptyMap(),
    val inputText: String = "",
    val isLoading: Boolean = false,
    val isSending: Boolean = false,
    val isGenerating: Boolean = false,
    val streamingContent: String = "",
    val streamingCharacterName: String = "",
    val streamingCharacterAvatar: String = "",
    val error: String? = null,
    val currentApiName: String = "",
    val currentModelName: String = ""
)

@HiltViewModel
class GroupChatViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val groupStorage: GroupStorage,
    private val characterStorage: CharacterStorage,
    private val llmRepository: LlmRepository,
    private val localRepository: LocalRepository,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(GroupChatUiState())
    val uiState: StateFlow<GroupChatUiState> = _uiState.asStateFlow()

    private var generationJob: Job? = null
    private var loadedCharacters: Map<String, Character> = emptyMap()
    private var listIndex: Int = 0  // for LIST strategy round-robin

    fun loadGroup(groupId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val groups = groupStorage.loadGroups()
            val group = groups.firstOrNull { it.id == groupId }
            if (group == null) {
                _uiState.update { it.copy(isLoading = false, error = "Group not found") }
                return@launch
            }

            // Load member characters
            val chars = group.members.mapNotNull { fileName ->
                characterStorage.getCharacter(fileName)?.let { fileName to it }
            }.toMap()
            loadedCharacters = chars

            // Build avatar URL map
            val avatarUrls = group.members.associate { fileName ->
                fileName to characterStorage.getAvatarUri(fileName).toString()
            }

            // Load messages
            val messages = groupStorage.loadMessages(groupId)

            // Load API config for display
            val config = when (val r = localRepository.getApiConfiguration()) {
                is Result.Success -> r.data
                is Result.Error -> ApiConfiguration.DEFAULT
            }

            _uiState.update {
                it.copy(
                    group = group,
                    messages = messages,
                    memberAvatarUrls = avatarUrls,
                    isLoading = false,
                    currentApiName = config.displayName,
                    currentModelName = config.currentModel
                )
            }
        }
    }

    fun updateInputText(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun sendMessage() {
        val group = _uiState.value.group ?: return
        val text = _uiState.value.inputText.trim()
        if (text.isBlank() || _uiState.value.isGenerating) return

        viewModelScope.launch {
            val userMsg = GroupChatMessage(content = text, isUser = true)
            val messages = _uiState.value.messages + userMsg
            _uiState.update { it.copy(messages = messages, inputText = "", isSending = false) }
            groupStorage.appendMessage(group.id, userMsg)

            // Generate responses based on activation strategy
            generateResponses(group, messages)
        }
    }

    private suspend fun generateResponses(group: Group, history: List<GroupChatMessage>) {
        val enabled = group.enabledMembers
        if (enabled.isEmpty()) return

        val strategy = group.activationStrategy
        val responders = pickResponders(strategy, enabled, history)

        for (fileName in responders) {
            val character = loadedCharacters[fileName] ?: continue
            generateForCharacter(group, character, fileName, _uiState.value.messages)
        }
    }

    private fun pickResponders(
        strategy: Int,
        enabled: List<String>,
        history: List<GroupChatMessage>
    ): List<String> = when (strategy) {
        ActivationStrategy.LIST -> {
            // Round-robin: each enabled member responds in turn
            enabled
        }
        ActivationStrategy.MANUAL -> {
            // Manual: pick one at random for now (UI picker could be added later)
            listOf(enabled.random())
        }
        ActivationStrategy.POOLED -> {
            // Weighted random, ignoring talkativeness (any can respond)
            listOf(enabled.random())
        }
        else -> {
            // NATURAL: weighted by talkativeness
            listOf(pickByTalkativeness(enabled))
        }
    }

    private fun pickByTalkativeness(enabled: List<String>): String {
        if (enabled.size == 1) return enabled.first()
        val weights = enabled.map { fileName ->
            loadedCharacters[fileName]?.talkativeness?.coerceIn(0.01f, 1f) ?: 0.5f
        }
        val total = weights.sum()
        var r = Random.nextFloat() * total
        for (i in weights.indices) {
            r -= weights[i]
            if (r <= 0f) return enabled[i]
        }
        return enabled.last()
    }

    private suspend fun generateForCharacter(
        group: Group,
        character: Character,
        fileName: String,
        history: List<GroupChatMessage>
    ) {
        val config = when (val r = localRepository.getApiConfiguration()) {
            is Result.Success -> r.data
            is Result.Error -> ApiConfiguration.DEFAULT
        }
        val preset = localRepository.getCurrentTextGenPreset()
        val personaName = settingsDataStore.getUserPersonaName().ifBlank { "User" }

        _uiState.update {
            it.copy(
                isGenerating = true,
                streamingCharacterName = character.name,
                streamingCharacterAvatar = fileName,
                streamingContent = ""
            )
        }

        val prompt = buildGroupPrompt(character, personaName, history)

        generationJob = viewModelScope.launch {
            llmRepository.generate(prompt, config, preset).collect { event ->
                when (event) {
                    is StreamEvent.Token -> {
                        _uiState.update { it.copy(streamingContent = event.accumulated) }
                    }
                    is StreamEvent.Complete -> {
                        val content = event.fullText.trim()
                        val aiMsg = GroupChatMessage(
                            content = content,
                            isUser = false,
                            senderName = character.name,
                            senderAvatar = fileName
                        )
                        val newMessages = _uiState.value.messages + aiMsg
                        _uiState.update {
                            it.copy(
                                messages = newMessages,
                                isGenerating = false,
                                streamingContent = "",
                                streamingCharacterName = "",
                                streamingCharacterAvatar = ""
                            )
                        }
                        groupStorage.appendMessage(group.id, aiMsg)
                        generationJob = null
                    }
                    is StreamEvent.Error -> {
                        _uiState.update {
                            it.copy(
                                isGenerating = false,
                                streamingContent = "",
                                streamingCharacterName = "",
                                streamingCharacterAvatar = "",
                                error = event.message
                            )
                        }
                        generationJob = null
                    }
                }
            }
        }
        generationJob?.join()
    }

    private fun buildGroupPrompt(
        character: Character,
        personaName: String,
        history: List<GroupChatMessage>
    ): String = buildString {
        append("[character(\"${character.name}\")\n")
        if (character.description.isNotBlank()) append("description: ${character.description.take(800)}\n")
        if (character.personality.isNotBlank()) append("personality: ${character.personality.take(400)}\n")
        if (character.scenario.isNotBlank()) append("scenario: ${character.scenario.take(400)}\n")
        append("]\n\n")

        // Group chat context
        val groupName = _uiState.value.group?.name ?: "Group Chat"
        append("This is a group chat named \"$groupName\". ")
        append("You are ${character.name}. Respond in character.\n\n")

        // History (last 20 messages)
        val recent = if (history.size > 20) history.takeLast(20) else history
        for (msg in recent) {
            val role = when {
                msg.isUser -> personaName
                msg.senderName != null -> msg.senderName
                else -> "Unknown"
            }
            append("$role: ${msg.content}\n")
        }
        append("${character.name}:")
    }

    fun generateFirstMessage() {
        val group = _uiState.value.group ?: return
        if (_uiState.value.isGenerating) return
        viewModelScope.launch {
            val enabled = group.enabledMembers
            if (enabled.isEmpty()) return@launch
            // Pick opener(s) — in LIST mode all members greet; otherwise just one
            val openers = if (group.activationStrategy == ActivationStrategy.LIST) {
                enabled
            } else {
                listOf(pickByTalkativeness(enabled))
            }
            for (fileName in openers) {
                val character = loadedCharacters[fileName] ?: continue
                generateFirstMessageFor(group, character, fileName)
            }
        }
    }

    private suspend fun generateFirstMessageFor(
        group: Group,
        character: Character,
        fileName: String
    ) {
        val config = when (val r = localRepository.getApiConfiguration()) {
            is Result.Success -> r.data
            is Result.Error -> ApiConfiguration.DEFAULT
        }
        val preset = localRepository.getCurrentTextGenPreset()
        val members = group.enabledMembers.mapNotNull { f -> loadedCharacters[f]?.name }

        _uiState.update {
            it.copy(
                isGenerating = true,
                streamingCharacterName = character.name,
                streamingCharacterAvatar = fileName,
                streamingContent = ""
            )
        }

        val prompt = buildFirstMessagePrompt(character, group.name, members)

        generationJob = viewModelScope.launch {
            llmRepository.generate(prompt, config, preset).collect { event ->
                when (event) {
                    is StreamEvent.Token -> {
                        _uiState.update { it.copy(streamingContent = event.accumulated) }
                    }
                    is StreamEvent.Complete -> {
                        val content = event.fullText.trim()
                        val aiMsg = GroupChatMessage(
                            content = content,
                            isUser = false,
                            senderName = character.name,
                            senderAvatar = fileName
                        )
                        val newMessages = _uiState.value.messages + aiMsg
                        _uiState.update {
                            it.copy(
                                messages = newMessages,
                                isGenerating = false,
                                streamingContent = "",
                                streamingCharacterName = "",
                                streamingCharacterAvatar = ""
                            )
                        }
                        groupStorage.appendMessage(group.id, aiMsg)
                        generationJob = null
                    }
                    is StreamEvent.Error -> {
                        _uiState.update {
                            it.copy(
                                isGenerating = false,
                                streamingContent = "",
                                streamingCharacterName = "",
                                streamingCharacterAvatar = "",
                                error = event.message
                            )
                        }
                        generationJob = null
                    }
                }
            }
        }
        generationJob?.join()
    }

    private fun buildFirstMessagePrompt(
        character: Character,
        groupName: String,
        memberNames: List<String>
    ): String = buildString {
        append("[character(\"${character.name}\")\n")
        if (character.description.isNotBlank()) append("description: ${character.description.take(800)}\n")
        if (character.personality.isNotBlank()) append("personality: ${character.personality.take(400)}\n")
        if (character.scenario.isNotBlank()) append("scenario: ${character.scenario.take(400)}\n")
        append("]\n\n")

        val others = memberNames.filter { it != character.name }
        append("This is a group chat named \"$groupName\" with ")
        if (others.isNotEmpty()) {
            append("${character.name} and ${others.joinToString(", ")}. ")
        } else {
            append("${character.name}. ")
        }
        append("Write an in-character opening message for ${character.name} that sets the scene and greets the group. ")
        append("Be creative and true to the character's personality.\n\n")
        append("${character.name}:")
    }

    fun setActivationStrategy(strategy: Int) {
        val group = _uiState.value.group ?: return
        val updated = group.copy(activationStrategy = strategy)
        _uiState.update { it.copy(group = updated) }
        viewModelScope.launch { groupStorage.saveGroup(updated) }
    }

    fun stopGeneration() {
        generationJob?.cancel()
        generationJob = null
        _uiState.update {
            it.copy(
                isGenerating = false,
                streamingContent = "",
                streamingCharacterName = "",
                streamingCharacterAvatar = ""
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
