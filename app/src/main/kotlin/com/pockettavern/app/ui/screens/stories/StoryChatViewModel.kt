package com.pockettavern.app.ui.screens.stories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pockettavern.app.data.local.SettingsDataStore
import com.pockettavern.app.data.local.StoryStorage
import com.pockettavern.app.data.repository.LlmRepository
import com.pockettavern.app.data.repository.LocalRepository
import com.pockettavern.app.domain.model.ApiConfiguration
import com.pockettavern.app.domain.model.ChatInfo
import com.pockettavern.app.domain.model.Result
import com.pockettavern.app.domain.model.Story
import com.pockettavern.app.domain.model.StoryMessage
import com.pockettavern.app.domain.model.StoryMission
import com.pockettavern.app.domain.model.StoryOpening
import com.pockettavern.app.domain.model.StreamEvent
import com.pockettavern.app.domain.prompt.StoryPromptBuilder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StoryChatUiState(
    val story: Story? = null,
    val chatFileName: String? = null,
    val messages: List<StoryMessage> = emptyList(),
    val openings: List<StoryOpening> = emptyList(),
    val selectedOpening: StoryOpening? = null,
    val isGenerating: Boolean = false,
    val streamingContent: String = "",
    val streamingThinking: String = "",
    val error: String? = null,
    // Multiple playthroughs per Story card
    val chats: List<ChatInfo> = emptyList(),
    val showChatPicker: Boolean = false,
    // Missions + roster (current scene framing)
    val missions: List<StoryMission> = emptyList(),
    val activeMission: StoryMission? = null,
    /** Highlighted lead characters for the scene (V4: leads, not exclusive). Empty = whole household. */
    val roster: Set<String> = emptySet(),
    val showMissionPicker: Boolean = false
)

/**
 * T4/T6 — runs one Story session. ONE chat-completion call per user turn (V1, V2): the whole cast
 * goes into a single system message via [StoryPromptBuilder], the model self-selects who reacts
 * (V4), and its `<think>` reasoning is captured separately as the message's [StoryMessage.reasoning]
 * (V6) — never shown inline. Clean stack: does not reuse GroupChatViewModel's per-character SWAP.
 */
@HiltViewModel
class StoryChatViewModel @Inject constructor(
    private val storyStorage: StoryStorage,
    private val llmRepository: LlmRepository,
    private val localRepository: LocalRepository,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(StoryChatUiState())
    val uiState: StateFlow<StoryChatUiState> = _uiState.asStateFlow()

    private var generationJob: Job? = null
    private var storyId: String = ""

    fun open(storyId: String, chatFileName: String?) {
        this.storyId = storyId
        viewModelScope.launch {
            val story = storyStorage.loadStory(storyId) ?: run {
                _uiState.update { it.copy(error = "Story not found") }; return@launch
            }
            // Resume the most recent playthrough (listChats is newest-first); create one only if none.
            val chats = storyStorage.listChats(storyId)
            val chat = chatFileName ?: chats.firstOrNull()?.fileName ?: storyStorage.createChat(storyId)
            _uiState.update {
                it.copy(
                    story = story,
                    chatFileName = chat,
                    messages = storyStorage.loadMessages(storyId, chat),
                    openings = story.openings,
                    selectedOpening = story.openings.firstOrNull(),
                    chats = storyStorage.listChats(storyId),
                    missions = story.missions
                )
            }
        }
    }

    // ── missions + roster ───────────────────────────────────────────────────

    fun toggleMissionPicker(show: Boolean) = _uiState.update { it.copy(showMissionPicker = show) }

    /** Launch a card-authored mission: sets the scene framing + pre-selects its leads (V4 highlight). */
    fun launchMission(mission: StoryMission) = _uiState.update {
        it.copy(activeMission = mission, roster = mission.cast.toSet(), showMissionPicker = false)
    }

    fun clearMission() = _uiState.update { it.copy(activeMission = null, roster = emptySet()) }

    /** Toggle a character in/out of the highlighted lead roster. */
    fun toggleRoster(name: String) = _uiState.update {
        it.copy(roster = if (name in it.roster) it.roster - name else it.roster + name)
    }

    // ── multiple playthroughs per card ──────────────────────────────────────

    fun toggleChatPicker(show: Boolean) = viewModelScope.launch {
        _uiState.update { it.copy(showChatPicker = show, chats = if (show) storyStorage.listChats(storyId) else it.chats) }
    }

    /** Start a fresh playthrough of this Story (new empty chat) and switch to it. */
    fun newPlaythrough() = viewModelScope.launch {
        if (_uiState.value.isGenerating) return@launch
        val chat = storyStorage.createChat(storyId)
        _uiState.update {
            it.copy(chatFileName = chat, messages = emptyList(), showChatPicker = false,
                chats = storyStorage.listChats(storyId), selectedOpening = it.openings.firstOrNull())
        }
    }

    fun switchChat(fileName: String) = viewModelScope.launch {
        if (_uiState.value.isGenerating) return@launch
        _uiState.update {
            it.copy(chatFileName = fileName, messages = storyStorage.loadMessages(storyId, fileName), showChatPicker = false)
        }
    }

    fun deleteChat(fileName: String) = viewModelScope.launch {
        storyStorage.deleteChat(storyId, fileName)
        val remaining = storyStorage.listChats(storyId)
        val cur = _uiState.value.chatFileName
        if (cur == fileName) {
            val next = remaining.firstOrNull()?.fileName ?: storyStorage.createChat(storyId)
            _uiState.update { it.copy(chatFileName = next, messages = storyStorage.loadMessages(storyId, next), chats = storyStorage.listChats(storyId)) }
        } else {
            _uiState.update { it.copy(chats = remaining) }
        }
    }

    fun selectOpening(opening: StoryOpening) = _uiState.update { it.copy(selectedOpening = opening) }

    fun sendMessage(text: String) {
        val state = _uiState.value
        val story = state.story ?: return
        val chat = state.chatFileName ?: return
        if (text.isBlank() || state.isGenerating) return

        val userMsg = StoryMessage(content = text.trim(), isUser = true, timestamp = now())
        val withUser = state.messages + userMsg
        _uiState.update { it.copy(messages = withUser, isGenerating = true, streamingContent = "", streamingThinking = "", error = null) }
        viewModelScope.launch { storyStorage.appendMessage(storyId, chat, userMsg) }

        generate(story, chat, withUser, state.selectedOpening)
    }

    /** Regenerate the cast's response to the last player beat (drops the last assistant turn). */
    fun regenerate() {
        val state = _uiState.value
        val story = state.story ?: return
        val chat = state.chatFileName ?: return
        if (state.isGenerating) return
        val trimmed = state.messages.dropLastWhile { !it.isUser }
        _uiState.update { it.copy(messages = trimmed, isGenerating = true, streamingContent = "", streamingThinking = "") }
        viewModelScope.launch { storyStorage.saveMessages(storyId, chat, trimmed) }
        generate(story, chat, trimmed, state.selectedOpening)
    }

    private fun generate(story: Story, chat: String, history: List<StoryMessage>, opening: StoryOpening?) {
        generationJob = viewModelScope.launch {
            // Resolve config/preset/persona inside the coroutine (these are suspend functions).
            val config = when (val r = localRepository.getApiConfiguration()) {
                is Result.Success -> r.data
                is Result.Error -> ApiConfiguration.DEFAULT
            }
            val preset = localRepository.getCurrentTextGenPreset()
            val oaiPreset = if (config.usesChatCompletions) localRepository.getCurrentOaiPreset() else null
            val personaName = settingsDataStore.getUserPersonaName().ifBlank { "User" }

            // The single-call ensemble prompt (V1). Opening seed only matters on a fresh scene.
            val builder = StoryPromptBuilder(story, personaName)
            val fullHistory = history.map { it.toChatMessage() }
            val s = _uiState.value
            // Context Size budget (parity with solo/group): measure non-history overhead
            // with an empty-history build, then keep the newest messages that fit.
            val tokenBudget = if (config.usesChatCompletions) {
                oaiPreset?.takeIf { it.contextSizeEnabled }?.let { p ->
                    (p.contextSize - (if (p.maxTokensEnabled) p.maxTokens else 0)).coerceAtLeast(256)
                }
            } else {
                preset?.let { p -> (p.truncationLength - (p.maxNewTokens ?: 0)).coerceAtLeast(256) }
            }
            val chatHistory = if (tokenBudget == null) fullHistory else {
                val overhead = builder.buildMessages(emptyList(), opening, s.activeMission, s.roster)
                    .sumOf { it.content.length / 3 + 4 }
                var remaining = (tokenBudget - overhead).coerceAtLeast(0)
                val kept = ArrayList<com.pockettavern.app.domain.model.ChatMessage>()
                for (msg in fullHistory.asReversed()) {
                    val t = msg.content.length / 3 + 4
                    if (t > remaining) break
                    kept.add(msg); remaining -= t
                }
                kept.reversed()
            }
            val messages = builder.buildMessages(chatHistory, opening, s.activeMission, s.roster)
            val promptText = messages.joinToString("\n\n") { "${it.role.uppercase()}: ${it.content}" }

            llmRepository.generate(promptText, config, preset, emptyList(), messages, oaiPreset, config.showThoughts)
                .collect { event ->
                    when (event) {
                        is StreamEvent.Token ->
                            _uiState.update { it.copy(streamingContent = event.accumulated) }
                        is StreamEvent.ThinkingToken ->
                            _uiState.update { it.copy(streamingThinking = event.accumulatedThinking) }
                        is StreamEvent.Complete -> {
                            val aiMsg = StoryMessage(
                                content = event.fullText.trim(),
                                isUser = false,
                                reasoning = event.thinkingText.ifBlank { null },   // V6: director reasoning kept separate
                                timestamp = now()
                            )
                            val newMessages = _uiState.value.messages + aiMsg
                            _uiState.update { it.copy(messages = newMessages, isGenerating = false, streamingContent = "", streamingThinking = "") }
                            storyStorage.appendMessage(storyId, chat, aiMsg)
                            generationJob = null
                        }
                        is StreamEvent.Error -> {
                            _uiState.update { it.copy(isGenerating = false, streamingContent = "", streamingThinking = "", error = event.message) }
                            generationJob = null
                        }
                    }
                }
        }
    }

    fun stopGeneration() {
        generationJob?.cancel(); generationJob = null
        _uiState.update { it.copy(isGenerating = false) }
    }

    private fun now() = System.currentTimeMillis()
}
