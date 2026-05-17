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
    private var lastSpeakerFileName: String? = null   // avoid same char replying twice in a row
    private val maxFollowUps = 2  // max auto follow-up exchanges per user turn

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

        when (group.activationStrategy) {
            ActivationStrategy.LIST -> {
                // Every enabled character responds in order
                for (fileName in enabled) {
                    val character = loadedCharacters[fileName] ?: continue
                    generateForCharacter(group, character, fileName)
                    lastSpeakerFileName = fileName
                }
            }
            else -> {
                // NATURAL / POOLED / MANUAL: one primary responder, then optional follow-ups
                val first = when (group.activationStrategy) {
                    ActivationStrategy.POOLED -> enabled.filter { it != lastSpeakerFileName }
                        .ifEmpty { enabled }.random()
                    else -> pickByTalkativeness(enabled.filter { it != lastSpeakerFileName }.ifEmpty { enabled })
                }
                val firstChar = loadedCharacters[first] ?: return
                generateForCharacter(group, firstChar, first)
                lastSpeakerFileName = first

                // Probabilistic follow-ups: other characters may chime in
                var followUps = 0
                while (followUps < maxFollowUps) {
                    val others = enabled.filter { it != lastSpeakerFileName }
                    if (others.isEmpty()) break
                    // Each character's chance to jump in = talkativeness * 0.6
                    val next = pickFollowUp(others) ?: break
                    val nextChar = loadedCharacters[next] ?: break
                    generateForCharacter(group, nextChar, next)
                    lastSpeakerFileName = next
                    followUps++
                }
            }
        }
    }

    private fun pickByTalkativeness(candidates: List<String>): String {
        if (candidates.size == 1) return candidates.first()
        val weights = candidates.map { fileName ->
            loadedCharacters[fileName]?.talkativeness?.coerceIn(0.01f, 1f) ?: 0.5f
        }
        val total = weights.sum()
        var r = Random.nextFloat() * total
        for (i in weights.indices) {
            r -= weights[i]
            if (r <= 0f) return candidates[i]
        }
        return candidates.last()
    }

    // Returns a character that probabilistically decides to chime in, or null if no one does.
    private fun pickFollowUp(candidates: List<String>): String? {
        // Shuffle so every candidate gets a fair chance
        val shuffled = candidates.shuffled()
        for (fileName in shuffled) {
            val talk = loadedCharacters[fileName]?.talkativeness?.coerceIn(0f, 1f) ?: 0.5f
            // Probability of joining = talkativeness * 0.55 (max ~55% for talkativeness=1)
            if (Random.nextFloat() < talk * 0.55f) return fileName
        }
        return null
    }

    private suspend fun generateForCharacter(
        group: Group,
        character: Character,
        fileName: String
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

        // Read live messages so each character sees what the previous one just said
        val history = _uiState.value.messages
        val prompt = buildGroupPrompt(character, personaName, group, history)

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
        group: Group,
        history: List<GroupChatMessage>
    ): String = buildString {
        // Character card for the current speaker
        append("[character(\"${character.name}\")\n")
        if (character.description.isNotBlank()) append("description: ${character.description.take(600)}\n")
        if (character.personality.isNotBlank()) append("personality: ${character.personality.take(300)}\n")
        if (character.scenario.isNotBlank()) append("scenario: ${character.scenario.take(300)}\n")
        append("]\n\n")

        // Brief profiles of the other characters so this char knows who they're talking to
        val others = group.enabledMembers.filter { it != character.avatar }
            .mapNotNull { loadedCharacters[it] }
        if (others.isNotEmpty()) {
            append("[other characters in this conversation]\n")
            for (other in others) {
                append("${other.name}")
                val snippet = other.personality.take(120).ifBlank { other.description.take(120) }
                if (snippet.isNotBlank()) append(": $snippet")
                append("\n")
            }
            append("\n")
        }

        // Conversation framing
        val groupName = group.name
        append("You are ${character.name} in a group chat called \"$groupName\".\n")
        append("Respond naturally in character. You may address $personaName or the other characters.\n")
        append("Keep your reply concise and conversational.\n\n")

        // Conversation history (last 24 messages)
        val recent = if (history.size > 24) history.takeLast(24) else history
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
            // In LIST mode, all characters introduce themselves in order.
            // Otherwise one character sets the scene (weighted by talkativeness).
            val openers = if (group.activationStrategy == ActivationStrategy.LIST) {
                enabled
            } else {
                listOf(pickByTalkativeness(enabled))
            }
            for (fileName in openers) {
                val character = loadedCharacters[fileName] ?: continue
                generateFirstMessageFor(group, character, fileName)
                lastSpeakerFileName = fileName
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
        val others = group.enabledMembers.mapNotNull { f -> loadedCharacters[f] }
            .filter { it.name != character.name }

        _uiState.update {
            it.copy(
                isGenerating = true,
                streamingCharacterName = character.name,
                streamingCharacterAvatar = fileName,
                streamingContent = ""
            )
        }

        val prompt = buildFirstMessagePrompt(character, group.name, others, _uiState.value.messages)

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
        others: List<Character>,
        priorMessages: List<GroupChatMessage>
    ): String = buildString {
        append("[character(\"${character.name}\")\n")
        if (character.description.isNotBlank()) append("description: ${character.description.take(600)}\n")
        if (character.personality.isNotBlank()) append("personality: ${character.personality.take(300)}\n")
        if (character.scenario.isNotBlank()) append("scenario: ${character.scenario.take(300)}\n")
        append("]\n\n")

        if (others.isNotEmpty()) {
            append("[other characters present]\n")
            for (other in others) {
                append("${other.name}")
                val snippet = other.personality.take(100).ifBlank { other.description.take(100) }
                if (snippet.isNotBlank()) append(": $snippet")
                append("\n")
            }
            append("\n")
        }

        append("You are ${character.name} in a group chat called \"$groupName\".\n")
        if (priorMessages.isEmpty()) {
            // True opening — set the scene
            append("Write an in-character opening message that sets the scene and greets the group. ")
            append("Be creative and true to your personality. Keep it concise.\n\n")
        } else {
            // A later character joining after others have already spoken
            append("The conversation has already started. Join in naturally, in character.\n\n")
            val recent = if (priorMessages.size > 10) priorMessages.takeLast(10) else priorMessages
            for (msg in recent) {
                val role = msg.senderName ?: "Unknown"
                append("$role: ${msg.content}\n")
            }
        }
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
