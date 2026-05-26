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
import com.pockettavern.app.domain.model.ChatStyle
import com.pockettavern.app.domain.model.Character
import com.pockettavern.app.domain.model.ChatInfo
import com.pockettavern.app.domain.model.Group
import com.pockettavern.app.domain.model.GroupChatMessage
import com.pockettavern.app.domain.model.PromptMessage
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
    val currentModelName: String = "",
    val currentChatFileName: String? = null,
    val availableChats: List<ChatInfo> = emptyList(),
    val showChatSelector: Boolean = false,
    val showPromptEditor: Boolean = false,
    val promptEditorText: String = "",
    val showWorldBookEditor: Boolean = false,
    val worldBookEditorText: String = "",
    val showScanloreDialog: Boolean = false,
    val scanloreEntries: List<String> = emptyList(),
    val scanloreLoading: Boolean = false,
    val scanloreError: String? = null,
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
    private var lastSpeakerFileName: String? = null
    private val maxFollowUps = 3

    fun loadGroup(groupId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val groups = groupStorage.loadGroups()
            val group = groups.firstOrNull { it.id == groupId }
            if (group == null) {
                _uiState.update { it.copy(isLoading = false, error = "Group not found") }
                return@launch
            }

            val chars = group.members.mapNotNull { fileName ->
                characterStorage.getCharacter(fileName)?.let { fileName to it }
            }.toMap()
            loadedCharacters = chars

            val avatarUrls = group.members.associate { fileName ->
                fileName to characterStorage.getAvatarUri(fileName).toString()
            }

            val config = when (val r = localRepository.getApiConfiguration()) {
                is Result.Success -> r.data
                is Result.Error -> ApiConfiguration.DEFAULT
            }

            // Load chat list; pick most recent or create new
            val chats = groupStorage.listChats(groupId)
            val chatFileName = if (chats.isNotEmpty()) {
                chats.first().fileName
            } else {
                groupStorage.createNewChat(groupId)
            }
            val messages = groupStorage.loadMessages(groupId, chatFileName)

            _uiState.update {
                it.copy(
                    group = group,
                    messages = messages,
                    memberAvatarUrls = avatarUrls,
                    isLoading = false,
                    currentApiName = config.displayName,
                    currentModelName = config.currentModel,
                    currentChatFileName = chatFileName,
                    availableChats = chats.ifEmpty { groupStorage.listChats(groupId) }
                )
            }
        }
    }

    // ── Chat selector ─────────────────────────────────────────────────────────

    fun showChatSelector() {
        val groupId = _uiState.value.group?.id ?: return
        viewModelScope.launch {
            val chats = groupStorage.listChats(groupId)
            _uiState.update { it.copy(availableChats = chats, showChatSelector = true) }
        }
    }

    fun dismissChatSelector() {
        _uiState.update { it.copy(showChatSelector = false) }
    }

    fun selectChat(fileName: String) {
        val groupId = _uiState.value.group?.id ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, showChatSelector = false) }
            val messages = groupStorage.loadMessages(groupId, fileName)
            lastSpeakerFileName = null
            _uiState.update {
                it.copy(
                    messages = messages,
                    currentChatFileName = fileName,
                    isLoading = false
                )
            }
        }
    }

    fun deleteChat(fileName: String) {
        val groupId = _uiState.value.group?.id ?: return
        viewModelScope.launch {
            groupStorage.deleteChat(groupId, fileName)
            val chats = groupStorage.listChats(groupId)
            if (fileName == _uiState.value.currentChatFileName) {
                val newFileName = if (chats.isNotEmpty()) chats.first().fileName
                                  else groupStorage.createNewChat(groupId)
                val messages = groupStorage.loadMessages(groupId, newFileName)
                lastSpeakerFileName = null
                _uiState.update {
                    it.copy(
                        messages = messages,
                        currentChatFileName = newFileName,
                        availableChats = groupStorage.listChats(groupId)
                    )
                }
            } else {
                _uiState.update { it.copy(availableChats = chats) }
            }
        }
    }

    fun createNewChat() {
        val groupId = _uiState.value.group?.id ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(showChatSelector = false) }
            val fileName = groupStorage.createNewChat(groupId)
            lastSpeakerFileName = null
            _uiState.update {
                it.copy(
                    messages = emptyList(),
                    currentChatFileName = fileName
                )
            }
        }
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    fun updateInputText(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun sendMessage() {
        val group = _uiState.value.group ?: return
        val chatFileName = _uiState.value.currentChatFileName ?: return
        val text = _uiState.value.inputText.trim()
        if (text.isBlank() || _uiState.value.isGenerating) return

        // /addlore <text> — append to group world book
        if (text.startsWith("/addlore ")) {
            val entry = text.removePrefix("/addlore ").trim()
            _uiState.update { it.copy(inputText = "") }
            if (entry.isNotBlank()) {
                viewModelScope.launch {
                    groupStorage.appendWorldBookEntry(group.id, entry)
                    val updated = groupStorage.loadGroups().firstOrNull { it.id == group.id }
                    if (updated != null) _uiState.update { it.copy(group = updated) }
                    val narratorMsg = GroupChatMessage(
                        content = "* [World Book] Added: $entry *",
                        isUser = false,
                        isSystem = true,
                        senderName = "Narrator"
                    )
                    val msgs = _uiState.value.messages + narratorMsg
                    _uiState.update { it.copy(messages = msgs) }
                    groupStorage.appendMessage(group.id, chatFileName, narratorMsg)
                }
            }
            return
        }

        // /sysauto [hint] — generate a narrator scene description, optionally guided
        if (text.startsWith("/sysauto")) {
            val hint = text.removePrefix("/sysauto").trim()
            _uiState.update { it.copy(inputText = "") }
            viewModelScope.launch { generateNarratorMessage(group, hint.ifBlank { null }) }
            return
        }

        // /scanlore [N] — scan last N messages and extract lore entries
        if (text.startsWith("/scanlore")) {
            val countArg = text.removePrefix("/scanlore").trim().toIntOrNull() ?: 30
            _uiState.update { it.copy(inputText = "", showScanloreDialog = true, scanloreLoading = true, scanloreEntries = emptyList(), scanloreError = null) }
            viewModelScope.launch { runScanlore(group, countArg) }
            return
        }

        viewModelScope.launch {
            val userMsg = GroupChatMessage(content = text, isUser = true)
            val messages = _uiState.value.messages + userMsg
            _uiState.update { it.copy(messages = messages, inputText = "", isSending = false) }
            groupStorage.appendMessage(group.id, chatFileName, userMsg)
            generateResponses(group, messages)
        }
    }

    // ── Generation ────────────────────────────────────────────────────────────

    private suspend fun generateResponses(group: Group, history: List<GroupChatMessage>) {
        val enabled = group.enabledMembers
        if (enabled.isEmpty()) return

        when (group.activationStrategy) {
            ActivationStrategy.LIST -> {
                for (fileName in enabled) {
                    val character = loadedCharacters[fileName] ?: continue
                    generateForCharacter(group, character, fileName)
                    lastSpeakerFileName = fileName
                }
            }
            else -> {
                val pool = enabled.filter { it != lastSpeakerFileName }.ifEmpty { enabled }
                val lastUserText = history.lastOrNull { it.isUser }?.content ?: ""
                val mentioned = detectMentionedCharacter(lastUserText, pool)
                val first = when {
                    mentioned != null -> mentioned
                    group.activationStrategy == ActivationStrategy.POOLED -> pool.random()
                    else -> pickByTalkativeness(pool)
                }
                val firstChar = loadedCharacters[first] ?: return
                generateForCharacter(group, firstChar, first)
                lastSpeakerFileName = first

                var followUps = 0
                while (followUps < maxFollowUps) {
                    val others = enabled.filter { it != lastSpeakerFileName }
                    if (others.isEmpty()) break
                    val guaranteed = followUps == 0
                    val next = pickFollowUp(others, guaranteed) ?: break
                    val nextChar = loadedCharacters[next] ?: break
                    generateForCharacter(group, nextChar, next)
                    lastSpeakerFileName = next
                    followUps++
                }
            }
        }
    }

    /**
     * Stop sequences prevent the model from writing dialogue for anyone other than the
     * current character. Includes the user persona and every other member by name.
     */
    private fun buildStopSequences(
        currentCharacterName: String,
        personaName: String,
        group: Group
    ): List<String> = buildList {
        // Stop if model starts a new "speaker:" line for the user or another character
        add("\n$personaName:")
        add("\n${personaName}: ")
        for (fileName in group.enabledMembers) {
            val name = loadedCharacters[fileName]?.name ?: continue
            if (name == currentCharacterName) continue
            add("\n$name:")
            add("\n$name: ")
        }
        // Stop if model starts dumping a JSON block or code fence
        add("```")
        add("\njson\n{")
        add("\n{\"")
    }

    /** Returns the first candidate whose character name appears as a word in [text], or null. */
    private fun detectMentionedCharacter(text: String, candidates: List<String>): String? {
        for (fileName in candidates) {
            val name = loadedCharacters[fileName]?.name ?: continue
            val regex = Regex("\\b${Regex.escape(name)}\\b", RegexOption.IGNORE_CASE)
            if (regex.containsMatchIn(text)) return fileName
        }
        return null
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

    private fun pickFollowUp(candidates: List<String>, guaranteed: Boolean): String? {
        if (candidates.isEmpty()) return null
        val picked = pickByTalkativeness(candidates)
        if (guaranteed) return picked
        val talk = loadedCharacters[picked]?.talkativeness?.coerceIn(0f, 1f) ?: 0.5f
        return if (Random.nextFloat() < 0.3f + talk * 0.6f) picked else null
    }

    private suspend fun generateForCharacter(
        group: Group,
        character: Character,
        fileName: String
    ) {
        val chatFileName = _uiState.value.currentChatFileName ?: return
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

        val oaiPreset = if (config.usesChatCompletions) localRepository.getCurrentOaiPreset() else null
        val history = _uiState.value.messages
        val prompt = buildGroupPrompt(character, personaName, group, history)
        val stopSequences = buildStopSequences(character.name, personaName, group)
        val oaiMessages = if (config.usesChatCompletions)
            buildGroupOaiMessages(character, fileName, personaName, group, history) else null

        generationJob = viewModelScope.launch {
            llmRepository.generate(prompt, config, preset, stopSequences, oaiMessages, oaiPreset).collect { event ->
                when (event) {
                    is StreamEvent.Token -> {
                        _uiState.update { it.copy(streamingContent = event.accumulated) }
                    }
                    is StreamEvent.Complete -> {
                        val content = cleanResponse(event.fullText.trim(), character.name, personaName)
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
                        groupStorage.appendMessage(group.id, chatFileName, aiMsg)
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
        append("### RULE: You are ${character.name}. You ONLY write ${character.name}'s words and actions. ")
        append("$personaName is the human user — NEVER write their dialogue, thoughts, feelings, or reactions. ")
        append("Stop writing the moment ${character.name}'s turn ends. ###\n\n")
        append("[character(\"${character.name}\")\n")
        if (character.description.isNotBlank()) append("description: ${character.description.take(3000)}\n")
        if (character.personality.isNotBlank()) append("personality: ${character.personality.take(1200)}\n")
        if (character.scenario.isNotBlank()) append("scenario: ${character.scenario.take(800)}\n")
        append("]\n\n")

        if (group.systemPrompt.isNotBlank()) {
            append("[scenario]\n${group.systemPrompt}\n\n")
        }
        if (group.worldBook.isNotBlank()) {
            append("[Shared World Book]\n${group.worldBook}\n\n")
        }

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

        val otherNames = group.enabledMembers.mapNotNull { loadedCharacters[it] }
            .filter { it.name != character.name }.map { it.name }.joinToString("/").ifBlank { "other characters" }
        if (group.chatStyle == ChatStyle.RP) {
            append("You are ${character.name}. The scene is \"${group.name}\".\n")
            append("$personaName is who you are talking to. Always acknowledge or respond to them.\n")
            append("Write in FIRST PERSON using proper Markdown: *asterisks* for actions, \"double quotes\" for all spoken words.\n")
            append("Do NOT refer to yourself in third person. Do NOT describe or control what $otherNames does. Keep it to 1-3 short paragraphs.\n")
        } else {
            append("You are ${character.name}. The setting is \"${group.name}\".\n")
            append("$personaName is who you are talking to. Always acknowledge or respond to them.\n")
            append("Write spoken dialogue in \"double quotes\". No action descriptions. Keep it concise.\n")
        }
        append("Be responsive to what $personaName wants — engage with their direction rather than repeatedly pushing your own agenda. Stay in character, but follow their lead.\n")
        append("IMPORTANT: Write ONLY ${character.name}'s response. Do NOT prefix lines with your name. Do NOT write for $personaName or any other character.\n\n")

        // Show narrator scene context once at the top if present
        val narratorMsg = history.firstOrNull { it.isSystem && it.senderName == "Narrator" }
        if (narratorMsg != null) {
            append("[Scene: ${narratorMsg.content}]\n\n")
        }

        val recent = history.filter { !it.isSystem }.let { msgs ->
            if (msgs.size > 24) msgs.takeLast(24) else msgs
        }
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

    // ── First message ─────────────────────────────────────────────────────────

    fun generateFirstMessage() {
        val group = _uiState.value.group ?: return
        if (_uiState.value.isGenerating) return
        viewModelScope.launch {
            val enabled = group.enabledMembers
            if (enabled.isEmpty()) return@launch

            // Narrator sets the scene first
            generateNarratorMessage(group)

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

    private suspend fun generateNarratorMessage(group: Group, hint: String? = null) {
        val chatFileName = _uiState.value.currentChatFileName ?: return
        val config = when (val r = localRepository.getApiConfiguration()) {
            is Result.Success -> r.data
            is Result.Error -> ApiConfiguration.DEFAULT
        }
        val preset = localRepository.getCurrentTextGenPreset()
        val chars = group.enabledMembers.mapNotNull { loadedCharacters[it] }

        _uiState.update {
            it.copy(
                isGenerating = true,
                streamingCharacterName = "Narrator",
                streamingCharacterAvatar = "",
                streamingContent = ""
            )
        }

        val personaName = settingsDataStore.getUserPersonaName().ifBlank { "User" }
        val oaiPreset = if (config.usesChatCompletions) localRepository.getCurrentOaiPreset() else null
        val prompt = buildNarratorPrompt(group.name, personaName, chars, hint)
        val oaiMessages = if (config.usesChatCompletions)
            buildNarratorOaiMessages(group.name, personaName, chars, hint) else null

        generationJob = viewModelScope.launch {
            llmRepository.generate(prompt, config, preset, emptyList(), oaiMessages, oaiPreset).collect { event ->
                when (event) {
                    is StreamEvent.Token -> {
                        _uiState.update { it.copy(streamingContent = event.accumulated) }
                    }
                    is StreamEvent.Complete -> {
                        val narratorMsg = GroupChatMessage(
                            content = event.fullText.trim(),
                            isUser = false,
                            isSystem = true,
                            senderName = "Narrator"
                        )
                        val newMessages = _uiState.value.messages + narratorMsg
                        _uiState.update {
                            it.copy(
                                messages = newMessages,
                                isGenerating = false,
                                streamingContent = "",
                                streamingCharacterName = "",
                                streamingCharacterAvatar = ""
                            )
                        }
                        groupStorage.appendMessage(group.id, chatFileName, narratorMsg)
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

    private fun buildNarratorPrompt(
        groupName: String,
        personaName: String,
        characters: List<Character>,
        hint: String? = null
    ): String = buildString {
        append("You are a neutral narrator describing a scene.\n\n")
        append("Characters present:\n")
        for (char in characters) {
            append("- ${char.name}")
            val snippet = char.personality.take(200).ifBlank { char.description.take(200) }
            if (snippet.isNotBlank()) append(": $snippet")
            append("\n")
        }
        append("\n")
        if (hint != null) {
            append("Scene direction from the writer: $hint\n\n")
            append("Write a vivid narration (3-5 sentences) based on the above direction. ")
        } else {
            append("Write a vivid scene-setting narration (3-5 sentences) that:\n")
            append("- Describes a specific, random location\n")
            append("- Describes each character's demeanor and what they are doing\n")
            append("- Naturally establishes $personaName's presence in the scene\n")
        }
        append("Do NOT describe $personaName's appearance or personality. Write in third person. Be immersive and creative.\n\n")
        append("Narration:")
    }

    private suspend fun generateFirstMessageFor(
        group: Group,
        character: Character,
        fileName: String
    ) {
        val chatFileName = _uiState.value.currentChatFileName ?: return
        val config = when (val r = localRepository.getApiConfiguration()) {
            is Result.Success -> r.data
            is Result.Error -> ApiConfiguration.DEFAULT
        }
        val preset = localRepository.getCurrentTextGenPreset()
        val personaName = settingsDataStore.getUserPersonaName().ifBlank { "User" }
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

        val oaiPreset = if (config.usesChatCompletions) localRepository.getCurrentOaiPreset() else null
        val prompt = buildFirstMessagePrompt(character, group.name, group.chatStyle, group.systemPrompt, personaName, others, _uiState.value.messages)
        val stopSequences = buildStopSequences(character.name, personaName, group)
        val oaiMessages = if (config.usesChatCompletions)
            buildFirstMessageOaiMessages(character, fileName, group.name, group.chatStyle, group.systemPrompt, personaName, others, _uiState.value.messages) else null

        generationJob = viewModelScope.launch {
            llmRepository.generate(prompt, config, preset, stopSequences, oaiMessages, oaiPreset).collect { event ->
                when (event) {
                    is StreamEvent.Token -> {
                        _uiState.update { it.copy(streamingContent = event.accumulated) }
                    }
                    is StreamEvent.Complete -> {
                        val content = cleanResponse(event.fullText.trim(), character.name, personaName)
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
                        groupStorage.appendMessage(group.id, chatFileName, aiMsg)
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
        chatStyle: Int,
        groupSystemPrompt: String,
        personaName: String,
        others: List<Character>,
        priorMessages: List<GroupChatMessage>
    ): String = buildString {
        append("[character(\"${character.name}\")\n")
        if (character.description.isNotBlank()) append("description: ${character.description.take(3000)}\n")
        if (character.personality.isNotBlank()) append("personality: ${character.personality.take(1200)}\n")
        if (character.scenario.isNotBlank()) append("scenario: ${character.scenario.take(800)}\n")
        append("]\n\n")

        if (groupSystemPrompt.isNotBlank()) {
            append("[scenario]\n$groupSystemPrompt\n\n")
        }

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

        if (chatStyle == ChatStyle.RP) {
            append("You are ${character.name}. The scene is \"$groupName\".\n")
            append("Write in FIRST PERSON using proper Markdown: *asterisks* for actions, \"double quotes\" for all spoken words. Do NOT refer to yourself in third person. Do NOT control other characters' actions. Keep it to 1-3 short paragraphs.\n")
        } else {
            append("You are ${character.name}. The setting is \"$groupName\".\n")
            append("Write spoken dialogue in \"double quotes\". No action descriptions. Keep it concise.\n")
        }
        // Include the narrator message as scene context if present
        val narratorMsg = priorMessages.firstOrNull { it.isSystem && it.senderName == "Narrator" }
        if (narratorMsg != null) {
            append("\n[Scene: ${narratorMsg.content}]\n\n")
        }

        val nonNarratorMessages = priorMessages.filter { !it.isSystem }
        if (nonNarratorMessages.isEmpty()) {
            append("You MUST speak directly to $personaName in your response — address them, react to their presence, or engage with them in whatever way fits the scene. Stay in character. Be creative and concise.\n")
        } else {
            append("The scene is underway. Speak directly to $personaName or react to what they said. Join naturally, in character.\n\n")
            val recent = if (nonNarratorMessages.size > 10) nonNarratorMessages.takeLast(10) else nonNarratorMessages
            for (msg in recent) {
                val role = msg.senderName ?: "Unknown"
                append("$role: ${msg.content}\n")
            }
        }
        append("IMPORTANT: Write ONLY ${character.name}'s response. Do NOT prefix lines with your name. Do NOT write for anyone else.\n")
        append("${character.name}:")
    }

    // ── Other ─────────────────────────────────────────────────────────────────

    fun setActivationStrategy(strategy: Int) {
        val group = _uiState.value.group ?: return
        val updated = group.copy(activationStrategy = strategy)
        _uiState.update { it.copy(group = updated) }
        viewModelScope.launch { groupStorage.saveGroup(updated) }
    }

    /**
     * Strips leading "CharacterName:" prefixes and truncates any content where
     * the model starts narrating or speaking for the user persona.
     */
    private fun cleanResponse(text: String, characterName: String, personaName: String = ""): String {
        val p1 = "$characterName: "
        val p2 = "$characterName:"
        val stripped = text.lines().joinToString("\n") { line ->
            when {
                line.startsWith(p1) -> line.removePrefix(p1)
                line.startsWith(p2) -> line.removePrefix(p2).trimStart()
                else -> line
            }
        }.trim()

        // Discard JSON/code dumps (model echoing character card data)
        val t = stripped.trimStart()
        if (t.startsWith("json\n{") || t.startsWith("```") ||
            (t.startsWith("{") && t.contains("\"name\"") && t.trimEnd().endsWith("}"))) {
            // Try to salvage any text after the closing brace
            val afterBlock = stripped.substringAfterLast("}").trim()
            return if (afterBlock.isNotBlank()) afterBlock else ""
        }

        if (personaName.isBlank()) return stripped

        // Truncate at any paragraph where the model narrates for or speaks as the user.
        // With quotes enforced, anything starting with personaName outside "quotes"/*actions* is wrong.
        val paragraphs = stripped.split("\n\n")
        val result = mutableListOf<String>()
        for (para in paragraphs) {
            val t = para.trimStart()
            val isUserNarration = t.startsWith(personaName, ignoreCase = true) &&
                !t.startsWith("\"") && !t.startsWith("'") && !t.startsWith("*") && !t.startsWith("(")
            val isUserSpeaker = t.startsWith("$personaName:", ignoreCase = true) ||
                t.startsWith("$personaName: ", ignoreCase = true)
            if (isUserNarration || isUserSpeaker) break
            result.add(para)
        }
        return result.joinToString("\n\n").trim()
    }

    fun showPromptEditor() {
        val prompt = _uiState.value.group?.systemPrompt ?: ""
        _uiState.update { it.copy(showPromptEditor = true, promptEditorText = prompt) }
    }

    fun dismissPromptEditor() {
        _uiState.update { it.copy(showPromptEditor = false) }
    }

    fun updatePromptEditorText(text: String) {
        _uiState.update { it.copy(promptEditorText = text) }
    }

    fun saveGroupPrompt() {
        val group = _uiState.value.group ?: return
        val updated = group.copy(systemPrompt = _uiState.value.promptEditorText.trim())
        _uiState.update { it.copy(group = updated, showPromptEditor = false) }
        viewModelScope.launch { groupStorage.saveGroup(updated) }
    }

    fun showWorldBookEditor() {
        val wb = _uiState.value.group?.worldBook ?: ""
        _uiState.update { it.copy(showWorldBookEditor = true, worldBookEditorText = wb) }
    }

    fun dismissWorldBookEditor() {
        _uiState.update { it.copy(showWorldBookEditor = false) }
    }

    fun updateWorldBookEditorText(text: String) {
        _uiState.update { it.copy(worldBookEditorText = text) }
    }

    fun saveWorldBook() {
        val group = _uiState.value.group ?: return
        val updated = group.copy(worldBook = _uiState.value.worldBookEditorText.trim())
        _uiState.update { it.copy(group = updated, showWorldBookEditor = false) }
        viewModelScope.launch { groupStorage.saveWorldBook(group.id, updated.worldBook) }
    }

    fun dismissScanlore() {
        _uiState.update { it.copy(showScanloreDialog = false, scanloreEntries = emptyList(), scanloreError = null, scanloreLoading = false) }
    }

    fun confirmScanlore(entries: List<String>) {
        val group = _uiState.value.group ?: return
        val chatFileName = _uiState.value.currentChatFileName ?: return
        viewModelScope.launch {
            entries.forEach { groupStorage.appendWorldBookEntry(group.id, it) }
            val updated = groupStorage.loadGroups().firstOrNull { it.id == group.id }
            if (updated != null) _uiState.update { it.copy(group = updated) }
            _uiState.update { it.copy(showScanloreDialog = false, scanloreEntries = emptyList()) }
            val summary = if (entries.size == 1) entries[0] else "${entries.size} entries"
            val narratorMsg = GroupChatMessage(
                content = "* [World Book] Added: $summary *",
                isUser = false, isSystem = true, senderName = "Narrator"
            )
            val msgs = _uiState.value.messages + narratorMsg
            _uiState.update { it.copy(messages = msgs) }
            groupStorage.appendMessage(group.id, chatFileName, narratorMsg)
        }
    }

    private suspend fun runScanlore(group: Group, messageCount: Int) {
        val characters = group.enabledMembers.mapNotNull { loadedCharacters[it] }
        val loreHints = characters
            .filter { it.loreHints.isNotBlank() }
            .joinToString("\n\n") { "${it.name}:\n${it.loreHints}" }

        if (loreHints.isBlank()) {
            _uiState.update { it.copy(scanloreLoading = false, scanloreError = "No lore tracking hints set on any character in this group. Edit a character and fill in the Lore Tracking field.") }
            return
        }

        val messages = _uiState.value.messages.takeLast(messageCount)
        val personaName = settingsDataStore.getUserPersonaName().ifBlank { "User" }
        val transcript = messages.joinToString("\n") { msg ->
            val role = when {
                msg.isUser -> personaName
                msg.senderName != null -> msg.senderName
                else -> "Character"
            }
            "$role: ${msg.content.take(500)}"
        }

        val extractionPrompt = """You are a lore extraction assistant. Read the following conversation excerpt and extract notable events worth recording in a shared world log.

TRACKING CRITERIA:
$loreHints

CONVERSATION:
$transcript

OUTPUT FORMAT:
Return ONLY a numbered list of concise lore entries, one per line, in past tense.
Only include events that actually occurred in this conversation.
If nothing notable happened, return exactly: Nothing notable to record.
No preamble, no explanation. Just the numbered list."""

        try {
            val config = when (val r = localRepository.getApiConfiguration()) {
                is Result.Success -> r.data
                is Result.Error -> { _uiState.update { it.copy(scanloreLoading = false, scanloreError = "No API configured.") }; return }
            }
            var fullResponse = ""
            val oaiMessages = if (config.usesChatCompletions)
                listOf(PromptMessage("user", extractionPrompt)) else null
            llmRepository.generate(extractionPrompt, config, null, emptyList(), oaiMessages, null).collect { event ->
                when (event) {
                    is StreamEvent.Token -> fullResponse = event.accumulated
                    is StreamEvent.Complete -> fullResponse = event.fullText
                    else -> {}
                }
            }
            val entries = parseScanloreResponse(fullResponse)
            if (entries.isEmpty()) {
                _uiState.update { it.copy(scanloreLoading = false, scanloreEntries = emptyList(), scanloreError = "Nothing notable found in the last $messageCount messages.") }
            } else {
                _uiState.update { it.copy(scanloreLoading = false, scanloreEntries = entries, scanloreError = null) }
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(scanloreLoading = false, scanloreError = "Scan failed: ${e.message}") }
        }
    }

    private fun parseScanloreResponse(raw: String): List<String> {
        if (raw.contains("nothing notable", ignoreCase = true)) return emptyList()
        return raw.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { line ->
                line.replace(Regex("^[\\d]+\\.\\s*"), "")
                    .replace(Regex("^[-*•]\\s*"), "")
                    .trim()
            }
            .filter { it.isNotBlank() }
    }

    // ── OAI message builders (chat completions mode) ───────────────────────────

    private fun buildGroupOaiMessages(
        character: Character,
        fileName: String,
        personaName: String,
        group: Group,
        history: List<GroupChatMessage>
    ): List<PromptMessage> {
        val otherNames = group.enabledMembers.mapNotNull { loadedCharacters[it] }
            .filter { it.name != character.name }.map { it.name }.joinToString("/").ifBlank { "other characters" }

        val systemContent = buildString {
            append("You are ${character.name}. You ONLY write ${character.name}'s words and actions. ")
            append("$personaName is the human user — NEVER write their dialogue, thoughts, feelings, or reactions. ")
            append("Stop writing the moment ${character.name}'s turn ends.\n\n")

            append("[Character: ${character.name}]\n")
            if (character.description.isNotBlank()) append("description: ${character.description.take(3000)}\n")
            if (character.personality.isNotBlank()) append("personality: ${character.personality.take(1200)}\n")
            if (character.scenario.isNotBlank()) append("scenario: ${character.scenario.take(800)}\n")
            append("\n")

            if (group.systemPrompt.isNotBlank()) append("[Group Scenario]\n${group.systemPrompt}\n\n")
            if (group.worldBook.isNotBlank()) append("[Shared World Book]\n${group.worldBook}\n\n")

            val others = group.enabledMembers.filter { it != fileName }.mapNotNull { loadedCharacters[it] }
            if (others.isNotEmpty()) {
                append("[Other characters present]\n")
                for (other in others) {
                    val snippet = other.personality.take(120).ifBlank { other.description.take(120) }
                    append("${other.name}${if (snippet.isNotBlank()) ": $snippet" else ""}\n")
                }
                append("\n")
            }

            if (group.chatStyle == ChatStyle.RP) {
                append("Write in FIRST PERSON using *asterisks* for actions and \"double quotes\" for spoken words. ")
                append("Do NOT refer to yourself in third person. Do NOT control what $otherNames does. Keep it to 1-3 short paragraphs.\n")
            } else {
                append("Write spoken dialogue in \"double quotes\". No action descriptions. Keep it concise.\n")
            }
            append("Be responsive to $personaName — follow their lead. ")
            append("Write ONLY ${character.name}'s response. Do NOT prefix with your name. Do NOT write for $personaName or any other character.")
        }

        val userContent = buildString {
            val narratorMsg = history.firstOrNull { it.isSystem && it.senderName == "Narrator" }
            if (narratorMsg != null) append("[Scene: ${narratorMsg.content}]\n\n")

            val recent = history.filter { !it.isSystem }.let { msgs ->
                if (msgs.size > 24) msgs.takeLast(24) else msgs
            }
            for (msg in recent) {
                val role = when {
                    msg.isUser -> personaName
                    msg.senderName != null -> msg.senderName
                    else -> "Unknown"
                }
                append("$role: ${msg.content}\n")
            }
            append("\nRespond now as ${character.name}.")
        }

        return listOf(PromptMessage("system", systemContent), PromptMessage("user", userContent))
    }

    private fun buildNarratorOaiMessages(
        groupName: String,
        personaName: String,
        characters: List<Character>,
        hint: String? = null
    ): List<PromptMessage> {
        val content = buildString {
            append("You are a neutral narrator describing a scene.\n\nCharacters present:\n")
            for (char in characters) {
                val snippet = char.personality.take(200).ifBlank { char.description.take(200) }
                append("- ${char.name}${if (snippet.isNotBlank()) ": $snippet" else ""}\n")
            }
            if (hint != null) {
                append("\nScene direction from the writer: $hint\n\nWrite a vivid narration (3-5 sentences) based on the above direction. ")
            } else {
                append("\nWrite a vivid scene-setting narration (3-5 sentences) that:\n")
                append("- Describes a specific, random location\n")
                append("- Describes each character's demeanor and what they are doing\n")
                append("- Naturally establishes $personaName's presence in the scene\n")
            }
            append("Do NOT describe $personaName's appearance or personality. Write in third person. Be immersive and creative.")
        }
        return listOf(PromptMessage("user", content))
    }

    private fun buildFirstMessageOaiMessages(
        character: Character,
        fileName: String,
        groupName: String,
        chatStyle: Int,
        groupSystemPrompt: String,
        personaName: String,
        others: List<Character>,
        priorMessages: List<GroupChatMessage>
    ): List<PromptMessage> {
        val systemContent = buildString {
            append("You are ${character.name}. Write ONLY ${character.name}'s opening response. Do NOT prefix with your name. Do NOT write for anyone else.\n\n")
            append("[Character: ${character.name}]\n")
            if (character.description.isNotBlank()) append("description: ${character.description.take(3000)}\n")
            if (character.personality.isNotBlank()) append("personality: ${character.personality.take(1200)}\n")
            if (character.scenario.isNotBlank()) append("scenario: ${character.scenario.take(800)}\n")
            append("\n")
            if (groupSystemPrompt.isNotBlank()) append("[Group Scenario]\n$groupSystemPrompt\n\n")
            if (others.isNotEmpty()) {
                append("[Other characters present]\n")
                for (other in others) {
                    val snippet = other.personality.take(100).ifBlank { other.description.take(100) }
                    append("${other.name}${if (snippet.isNotBlank()) ": $snippet" else ""}\n")
                }
                append("\n")
            }
            if (chatStyle == ChatStyle.RP) {
                append("Write in FIRST PERSON using *asterisks* for actions and \"double quotes\" for spoken words. Keep it to 1-3 short paragraphs.")
            } else {
                append("Write spoken dialogue in \"double quotes\". Keep it concise.")
            }
        }

        val userContent = buildString {
            val narratorMsg = priorMessages.firstOrNull { it.isSystem && it.senderName == "Narrator" }
            if (narratorMsg != null) append("[Scene: ${narratorMsg.content}]\n\n")

            val nonNarrator = priorMessages.filter { !it.isSystem }
            if (nonNarrator.isEmpty()) {
                append("You MUST speak directly to $personaName — address them, react to their presence. Be creative and concise.\n")
            } else {
                append("The scene is underway. Join naturally, in character.\n\n")
                val recent = if (nonNarrator.size > 10) nonNarrator.takeLast(10) else nonNarrator
                for (msg in recent) {
                    val role = msg.senderName ?: "Unknown"
                    append("$role: ${msg.content}\n")
                }
            }
            append("\nRespond now as ${character.name}. Speak directly to $personaName.")
        }

        return listOf(PromptMessage("system", systemContent), PromptMessage("user", userContent))
    }

    fun setChatStyle(style: Int) {
        val group = _uiState.value.group ?: return
        val updated = group.copy(chatStyle = style)
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
