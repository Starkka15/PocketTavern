package com.pockettavern.app.ui.screens.chat

import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pockettavern.app.GenerationService
import com.pockettavern.app.data.repository.BackgroundRepository
import com.pockettavern.app.data.repository.ForgeRepository
import com.pockettavern.app.data.repository.ImageGenRepository
import com.pockettavern.app.data.repository.LocalRepository
import com.pockettavern.app.data.repository.LlmRepository
import com.pockettavern.app.extensions.ExtensionEvent
import com.pockettavern.app.extensions.ExtensionManager
import com.pockettavern.app.extensions.JsExtensionHost
import com.pockettavern.app.domain.model.ApiConfiguration
import com.pockettavern.app.domain.model.Chat
import com.pockettavern.app.domain.model.Character
import com.pockettavern.app.domain.model.ChatContext
import com.pockettavern.app.domain.model.ChatInfo
import com.pockettavern.app.domain.model.ChatMessage
import com.pockettavern.app.domain.model.UserPersona
import com.pockettavern.app.domain.model.ChatMessageMetadata
import com.pockettavern.app.domain.model.MessageHeaderEntry
import com.pockettavern.app.domain.model.ForgeGenerationParams
import com.pockettavern.app.domain.model.GenerationState
import com.pockettavern.app.domain.model.QuickReplyButton
import com.pockettavern.app.domain.model.Result
import com.pockettavern.app.domain.model.StreamEvent
import com.pockettavern.app.data.local.GroupStorage
import com.pockettavern.app.data.local.SettingsDataStore
import com.pockettavern.app.domain.prompt.PromptBuilder
import com.pockettavern.app.domain.usecase.SummarizeHistoryUseCase
import com.pockettavern.app.ui.audio.TtsManager
import com.pockettavern.app.util.PngCharacterCard
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

data class ChatUiState(
    val character: Character? = null,
    val characterAvatarUrl: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isLoading: Boolean = true,
    val isGenerating: Boolean = false,
    val streamingContent: String = "",
    val currentChatFileName: String? = null,
    val availableChats: List<ChatInfo> = emptyList(),
    val showChatSelector: Boolean = false,
    val error: String? = null,
    val showDeleteDialog: Boolean = false,
    // Message action menu state
    val selectedMessageIndex: Int? = null,
    val showMessageActions: Boolean = false,
    val imageSaved: Boolean = false,
    // API indicator
    val currentApiName: String = "",
    val currentModelName: String = "",
    // Message editing
    val editingMessageIndex: Int? = null,
    val editingMessageText: String = "",
    // Swipes (alternate responses) - map of message index to list of alternates
    val messageSwipes: Map<Int, List<String>> = emptyMap(),
    val currentSwipeIndex: Map<Int, Int> = emptyMap(),
    // Chat background
    val backgroundPath: String? = null,
    // Greeting selection for new chat
    val showGreetingPicker: Boolean = false,
    val availableGreetings: List<String> = emptyList(),
    // Quick reply buttons from enabled presets + JS-registered buttons
    val quickReplyButtons: List<QuickReplyButton> = emptyList(),
    // Token counter (shown when extension is enabled)
    val tokenCount: Int = 0,
    val showTokenCount: Boolean = false,
    // Message headers set by JS extensions via PT.setMessageHeader(index, text, extensionId)
    val messageHeaders: Map<Int, List<MessageHeaderEntry>> = emptyMap(),
    // Inline header buttons registered by extensions (extensionId → actions)
    val headerButtons: Map<String, List<JsExtensionHost.HeaderAction>> = emptyMap(),
    // Header context menus registered by extensions (extensionId → actions)
    val headerMenus: Map<String, List<JsExtensionHost.HeaderAction>> = emptyMap(),
    // Which (messageIndex, extensionId) pairs have visible inline buttons
    val visibleHeaderButtons: Set<Pair<Int, String>> = emptySet(),
    // Edit dialog requested by JS extension via PT.showEditDialog()
    val editDialogRequest: JsExtensionHost.EditDialogRequest? = null,
    // TTS
    val isTtsSpeaking: Boolean = false,
    val isTtsEnabled: Boolean = false,
    // Message context menu actions from JS extensions
    val messageActions: List<JsExtensionHost.HeaderAction> = emptyList(),
    // Image gallery
    val showGallery: Boolean = false,
    val galleryImages: List<GalleryImage> = emptyList(),
    // Message search
    val isSearching: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<Int> = emptyList(),
    val currentSearchResultIndex: Int = 0,
    // Context window usage (estimated tokens)
    val contextUsedTokens: Int = 0,
    // Shared world book (from linked group)
    val linkedGroupName: String? = null,
    val hasWorldBook: Boolean = false,
    // Scanlore dialog
    val showScanloreDialog: Boolean = false,
    val scanloreEntries: List<String> = emptyList(),
    val scanloreLoading: Boolean = false,
    val scanloreError: String? = null,
    // Model picker
    val showModelPicker: Boolean = false,
    val availableModels: List<String> = emptyList(),
    val modelPickerLoading: Boolean = false,
)

data class GalleryImage(
    val imagePath: String,
    val chatFileName: String,
    val timestamp: Long,
    val messageIndex: Int
)

private const val CONTINUE_PROMPT =
    "(OOC: Please continue the story from where it left off, maintaining the current tone and situation.)"

@HiltViewModel
class ChatViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val localRepository: LocalRepository,
    private val llmRepository: LlmRepository,
    private val forgeRepository: ForgeRepository,
    private val imageGenRepository: ImageGenRepository,
    private val backgroundRepository: BackgroundRepository,
    private val extensionManager: ExtensionManager,
    private val ttsManager: TtsManager,
    private val settingsDataStore: SettingsDataStore,
    private val characterDao: com.pockettavern.app.data.local.db.dao.CharacterDao,
    private val spriteStorage: com.pockettavern.app.data.local.SpriteStorage,
    private val summarizeHistoryUseCase: SummarizeHistoryUseCase,
    private val groupStorage: GroupStorage,
    private val cardExtensionSettings: com.pockettavern.app.data.local.CardExtensionSettings
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var generationJob: Job? = null

    // Auto-continue state
    private var autoContinueEnabled = false
    private var autoContinueMinLength = 200
    private var autoContinueCount = 0

    // Long-term memory
    private var memoryEnabled = true
    private var _currentMemoryBlock: String = ""
    private var _currentSummarizedTurnCount: Int = 0

    // TTS auto-play state
    private var ttsAutoPlay = false

    // Track which card file is currently open so we can disable it on back-out
    private var currentCardFileName: String? = null

    init {
        extensionManager.load()
        // Wire JS sendMessage callback so PT.sendMessage() routes through the normal pipeline
        extensionManager.jsHost.sendMessageCallback = { text -> sendMessageText(text) }
        // Observe native quick reply buttons + JS-registered buttons combined
        viewModelScope.launch {
            extensionManager.quickReply.activeButtons.collect { nativeButtons ->
                val jsButtons = extensionManager.jsButtonSets.value.values.flatten()
                _uiState.update { it.copy(quickReplyButtons = nativeButtons + jsButtons) }
            }
        }
        viewModelScope.launch {
            extensionManager.jsButtonSets.collect { jsSets ->
                val nativeButtons = extensionManager.quickReply.activeButtons.value
                val jsButtons = jsSets.values.flatten()
                _uiState.update { it.copy(quickReplyButtons = nativeButtons + jsButtons) }
            }
        }
        // Observe JS message headers — persist onto messages and save to disk
        viewModelScope.launch {
            extensionManager.messageHeaders.collect { headers ->
                _uiState.update { it.copy(messageHeaders = headers) }
                // Persist headers onto message objects and save so they survive chat reload
                // Only persist if we actually have messages in the current chat
                if (_uiState.value.messages.isNotEmpty() && persistExtensionHeaders()) {
                    saveCurrentChat()
                }
            }
        }
        // Observe inline header buttons registered by JS extensions
        viewModelScope.launch {
            extensionManager.headerButtons.collect { buttons ->
                _uiState.update { it.copy(headerButtons = buttons) }
            }
        }
        // Observe header context menus registered by JS extensions
        viewModelScope.launch {
            extensionManager.headerMenus.collect { menus ->
                _uiState.update { it.copy(headerMenus = menus) }
            }
        }
        // Observe message context menu actions from JS extensions
        viewModelScope.launch {
            extensionManager.messageActions.collect { actionsMap ->
                val allActions = actionsMap.values.flatten()
                _uiState.update { it.copy(messageActions = allActions) }
            }
        }
        // Observe edit dialog requests from JS extensions
        viewModelScope.launch {
            extensionManager.jsHost.editDialogRequest.collect { request ->
                _uiState.update { it.copy(editDialogRequest = request) }
            }
        }
        // Wire model list callback so PT.getAvailableModels() works
        extensionManager.jsHost.getAvailableModelsCallback = { callbackId ->
            viewModelScope.launch { doExtensionGetModels(callbackId) }
        }
        // Wire model set callback so PT.setCurrentModel() works
        extensionManager.jsHost.setCurrentModelCallback = { modelName, callbackId ->
            viewModelScope.launch { doExtensionSetModel(modelName, callbackId) }
        }
        // Wire hidden generate callback so PT.generateHidden() works
        extensionManager.jsHost.hiddenGenerateCallback = { prompt, callbackId ->
            viewModelScope.launch { doHiddenGenerate(prompt, callbackId) }
        }
        // Wire image generate callback so PT.generateImage() works
        extensionManager.jsHost.imageGenerateCallback = { prompt, optionsJson, callbackId ->
            viewModelScope.launch { doExtensionImageGenerate(prompt, optionsJson, callbackId) }
        }
        // Wire insert message callback so PT.insertMessage() works
        extensionManager.jsHost.insertMessageCallback = { content, optionsJson ->
            viewModelScope.launch { doExtensionInsertMessage(content, optionsJson) }
        }
        // Observe token counter enabled state
        _uiState.update { it.copy(showTokenCount = extensionManager.tokenCounter.enabled) }
        // Observe auto-continue settings
        viewModelScope.launch {
            localRepository.autoContinueFlow.collect { (enabled, minLength) ->
                autoContinueEnabled = enabled
                autoContinueMinLength = minLength
            }
        }
        // Observe long-term memory setting
        viewModelScope.launch {
            localRepository.memoryEnabledFlow.collect { enabled -> memoryEnabled = enabled }
        }
        // Collect quick reply auto-triggers
        viewModelScope.launch {
            extensionManager.quickReply.autoTriggerFlow.collect { button ->
                if (_uiState.value.character != null && !_uiState.value.isGenerating) {
                    sendQuickReply(button)
                }
            }
        }
        // Start/stop foreground service to keep CPU alive during generation
        viewModelScope.launch {
            var serviceRunning = false
            _uiState.collect { state ->
                val needsService = state.isGenerating

                if (needsService && !serviceRunning) {
                    GenerationService.start(context, "Generating response...")
                    serviceRunning = true
                } else if (!needsService && serviceRunning) {
                    GenerationService.stop(context)
                    serviceRunning = false
                }
            }
        }
        // Reactively track API config so the indicator updates when profiles are activated
        viewModelScope.launch {
            localRepository.apiConfigFlow.collect { config ->
                _currentConfig = config
                _uiState.update {
                    it.copy(
                        currentApiName = config.displayName,
                        currentModelName = config.currentModel
                    )
                }
            }
        }
        // Observe TTS speaking state
        viewModelScope.launch {
            ttsManager.speakingState.collect { speaking ->
                _uiState.update { it.copy(isTtsSpeaking = speaking) }
            }
        }
        // Load TTS enabled state
        viewModelScope.launch {
            settingsDataStore.ttsConfigFlow.collect { config ->
                _uiState.update { it.copy(isTtsEnabled = config.enabled) }
                ttsAutoPlay = config.enabled && config.autoPlay
            }
        }
    }

    // Last known API config — updated when generation starts, used for abort
    @Volatile private var _currentConfig: ApiConfiguration = ApiConfiguration.DEFAULT
    // Last known persona name/description — updated when generation starts
    @Volatile private var _currentUserName: String = "User"
    @Volatile private var _currentPersonaDescription: String = ""
    // Shared world book from linked group (empty if character not in any group)
    @Volatile private var _currentGroupId: String? = null
    @Volatile private var _currentWorldBook: String = ""

    /**
     * Rebuild the context JSON that JS extensions see via PT.getContext().
     * Includes the current character, recent messages (with index, text, isUser),
     * persona name, and API type.  Called whenever messages or character change.
     */
    private fun pushExtensionContext() {
        val state = _uiState.value
        val character = state.character
        val messages = state.messages

        val sb = StringBuilder()
        sb.append("{")
        // character
        if (character != null) {
            sb.append("\"character\":{")
            sb.append("\"name\":").append(jsonString(character.name)).append(",")
            sb.append("\"description\":").append(jsonString(character.description)).append(",")
            sb.append("\"personality\":").append(jsonString(character.personality)).append(",")
            sb.append("\"scenario\":").append(jsonString(character.scenario))
            sb.append("},")
        }
        // recentMessages — include raw text (before output filter) so extensions can parse tags
        sb.append("\"recentMessages\":[")
        for (i in messages.indices) {
            if (i > 0) sb.append(",")
            val msg = messages[i]
            val text = msg.rawContent ?: msg.content
            sb.append("{\"index\":").append(i)
            sb.append(",\"text\":").append(jsonString(text))
            sb.append(",\"isUser\":").append(msg.isUser)
            sb.append("}")
        }
        sb.append("],")
        sb.append("\"personaName\":").append(jsonString(_currentUserName)).append(",")
        sb.append("\"apiType\":").append(jsonString(_currentConfig.displayName))
        sb.append("}")

        extensionManager.updateContext(sb.toString())
    }

    /** JSON-escape a string value (with surrounding quotes). */
    private fun jsonString(value: String): String {
        val escaped = value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        return "\"$escaped\""
    }

    // The PNG filename of the current character (e.g. "seraphina.png")
    private var currentAvatarUrl: String = ""

    fun loadCharacter(avatarUrl: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            // Set persona name early so extensions see it before first generation
            val personaName = settingsDataStore.getUserPersonaName()
            if (personaName.isNotBlank()) _currentUserName = personaName

            when (val result = localRepository.getCharacter(avatarUrl)) {
                is Result.Success -> {
                    val character = result.data
                    val avatarUri = localRepository.getAvatarUri(
                        character.avatar ?: "${character.name}.png"
                    ).toString()
                    val bgPath = backgroundRepository.getBackgroundPath(avatarUrl)

                    _uiState.update {
                        it.copy(
                            character = character,
                            characterAvatarUrl = avatarUri,
                            backgroundPath = bgPath
                        )
                    }
                    // Update per-character extension filter
                    extensionManager.updateCharacterFilter(avatarUrl)
                    // Load card-embedded extension script (if present)
                    loadCardExtension(character)
                    // Load linked group world book
                    val charFileName = character.avatar ?: "$avatarUrl"
                    val linkedGroup = groupStorage.getGroupsForCharacter(charFileName).firstOrNull()
                    _currentGroupId = linkedGroup?.id
                    _currentWorldBook = linkedGroup?.worldBook ?: ""
                    _uiState.update { it.copy(
                        linkedGroupName = linkedGroup?.name,
                        hasWorldBook = linkedGroup?.worldBook?.isNotBlank() == true
                    )}
                    loadChats(character, avatarUrl)
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(isLoading = false, error = result.exception.message)
                    }
                }
            }
        }
    }

    private suspend fun loadCardExtension(character: Character) {
        val fileName = character.avatar ?: return
        withContext(Dispatchers.IO) {
            try {
                val bytesResult = localRepository.exportCharacterCard(fileName)
                val bytes = (bytesResult as? Result.Success)?.data ?: return@withContext
                val card = PngCharacterCard.extractCharacterData(bytes) ?: return@withContext
                val ptExtJson = card.data.extensions["pockettavern"] ?: run {
                    cardExtensionSettings.disable(fileName)
                    extensionManager.unloadCardScript()
                    return@withContext
                }
                val ptExt = org.json.JSONObject(ptExtJson.toString())
                val script = ptExt.optString("script", "")
                if (script.isBlank()) {
                    cardExtensionSettings.disable(fileName)
                    extensionManager.unloadCardScript()
                    return@withContext
                }
                // Card has a script — auto-enable and load
                cardExtensionSettings.setEnabled(fileName, true)
                currentCardFileName = fileName
                val scriptName = ptExt.optString("script_name", character.name)
                com.pockettavern.app.util.DebugLogger.log("[ChatViewModel] Card script found: '$scriptName' (${script.length} chars)")
                extensionManager.loadCardScript(script, scriptName, character.name)
            } catch (e: Exception) {
                com.pockettavern.app.util.DebugLogger.log("[ChatViewModel] Card script load error: ${e.message}")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        currentCardFileName?.let { cardExtensionSettings.disable(it) }
        currentCardFileName = null
    }

    private suspend fun loadChats(character: Character, avatarUrl: String) {
        currentAvatarUrl = avatarUrl
        when (val chatsResult = localRepository.getCharacterChats(character.name)) {
            is Result.Success -> {
                val chats = chatsResult.data
                _uiState.update { it.copy(availableChats = chats) }
                if (chats.isNotEmpty()) {
                    loadExistingChat(character, chats.first().fileName)
                } else {
                    createNewChat()
                }
            }
            is Result.Error -> createNewChat()
        }
    }

    fun refreshChatsList() {
        val character = _uiState.value.character ?: return
        viewModelScope.launch {
            when (val chatsResult = localRepository.getCharacterChats(character.name)) {
                is Result.Success -> _uiState.update { it.copy(availableChats = chatsResult.data) }
                is Result.Error -> { /* ignore */ }
            }
        }
    }

    fun reloadCharacter() {
        if (currentAvatarUrl.isBlank()) return
        viewModelScope.launch {
            when (val result = localRepository.getCharacter(currentAvatarUrl)) {
                is Result.Success -> _uiState.update { it.copy(character = result.data) }
                is Result.Error -> { /* keep existing */ }
            }
        }
    }

    fun selectChat(fileName: String) {
        val character = _uiState.value.character ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, showChatSelector = false) }
            loadExistingChat(character, fileName)
        }
    }

    fun showChatSelector() {
        refreshChatsList()
        _uiState.update { it.copy(showChatSelector = true) }
    }

    fun dismissChatSelector() {
        _uiState.update { it.copy(showChatSelector = false) }
    }

    private suspend fun loadExistingChat(character: Character, fileName: String) {
        when (val result = localRepository.getChat(character.name, fileName)) {
            is Result.Success -> {
                val chat = result.data
                // Load memory state
                _currentMemoryBlock = chat.memoryBlock
                _currentSummarizedTurnCount = chat.summarizedTurnCount
                val messages = chat.messages
                // Restore persisted extension headers
                val restoredHeaders = mutableMapOf<Int, List<MessageHeaderEntry>>()
                messages.forEachIndexed { index, msg ->
                    if (msg.extensionHeaders.isNotEmpty()) {
                        restoredHeaders[index] = msg.extensionHeaders
                    }
                }
                // Clear stale state from previous chat before loading new one
                extensionManager.clearMessageHeaders()
                _uiState.update {
                    it.copy(
                        messages = messages,
                        currentChatFileName = fileName,
                        isLoading = false,
                        messageHeaders = restoredHeaders,
                        visibleHeaderButtons = emptySet()
                    )
                }
                // Load vars for this chat (must happen before CHAT_CHANGED fires)
                val charName = character.name
                withContext(Dispatchers.IO) {
                    extensionManager.varsLoad(charName, fileName)
                }
                pushExtensionContext()
                extensionManager.emit(ExtensionEvent.CHAT_CHANGED, fileName)
                extensionManager.restoreMessageHeaders(restoredHeaders)
            }
            is Result.Error -> createNewChat()
        }
    }

    fun createNewChat() {
        val character = _uiState.value.character ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(showChatSelector = false) }
            val allGreetings = buildList {
                if (character.firstMessage.isNotBlank()) add(character.firstMessage)
                addAll(character.alternateGreetings.filter { it.isNotBlank() })
            }
            if (allGreetings.size > 1) {
                _uiState.update {
                    it.copy(
                        showGreetingPicker = true,
                        availableGreetings = allGreetings,
                        isLoading = false
                    )
                }
            } else {
                startNewChatWithGreeting(allGreetings.firstOrNull())
            }
        }
    }

    fun dismissGreetingPicker() {
        _uiState.update { it.copy(showGreetingPicker = false, availableGreetings = emptyList()) }
    }

    fun selectGreeting(greeting: String?) {
        _uiState.update { it.copy(showGreetingPicker = false, availableGreetings = emptyList()) }
        startNewChatWithGreeting(greeting)
    }

    private fun startNewChatWithGreeting(greeting: String?) {
        val character = _uiState.value.character ?: return
        viewModelScope.launch {
            _currentMemoryBlock = ""
            _currentSummarizedTurnCount = 0
            val fileName = localRepository.generateChatFileName(character.name)
            val userName = settingsDataStore.getUserPersonaName().ifBlank { "User" }
            val messages = if (!greeting.isNullOrBlank()) {
                val substituted = greeting
                    .replace("{{user}}", userName, ignoreCase = true)
                    .replace("{{username}}", userName, ignoreCase = true)
                    .replace("{{char}}", character.name, ignoreCase = true)
                    .replace("{{charname}}", character.name, ignoreCase = true)
                listOf(ChatMessage(content = substituted, isUser = false))
            } else emptyList()
            // Clear stale headers from previous chat
            extensionManager.clearMessageHeaders()
            _uiState.update {
                it.copy(
                    messages = messages,
                    currentChatFileName = fileName,
                    isLoading = false,
                    messageHeaders = emptyMap(),
                    visibleHeaderButtons = emptySet()
                )
            }
            // New chat — clear vars store (fresh state)
            withContext(Dispatchers.IO) {
                extensionManager.varsLoad(character.name, fileName)
            }
            pushExtensionContext()
            extensionManager.emit(ExtensionEvent.CHAT_CHANGED, fileName)
            extensionManager.emit(ExtensionEvent.CHAT_STARTED, fileName)
            if (messages.isNotEmpty()) {
                saveCurrentChat()
                refreshChatsList()
            }
        }
    }

    fun updateInput(text: String) {
        val tokenCount = if (extensionManager.tokenCounter.enabled)
            extensionManager.tokenCounter.estimateTokens(text) else 0
        _uiState.update { it.copy(inputText = text, tokenCount = tokenCount) }
    }

    /** Send the current input text as a message. */
    fun sendMessage() {
        val character = _uiState.value.character ?: return
        val rawMessage = _uiState.value.inputText.trim()
        if (rawMessage.isBlank()) return
        sendMessageText(rawMessage)
    }

    /** Send a quick-reply button message directly (bypasses inputText). */
    fun sendQuickReply(button: QuickReplyButton) {
        if (_uiState.value.character == null) return
        // Action buttons dispatch BUTTON_CLICKED event to JS instead of sending a message
        if (button.action.isNotBlank()) {
            val safeAction = button.action.replace("\\", "\\\\").replace("\"", "\\\"")
            val safeLabel = button.label.replace("\\", "\\\\").replace("\"", "\\\"")
            extensionManager.emitJson(
                ExtensionEvent.BUTTON_CLICKED,
                "{\"action\":\"$safeAction\",\"label\":\"$safeLabel\"}"
            )
            return
        }
        val text = button.message.trim()
        if (text.isBlank()) return
        sendMessageText(text)
    }

    fun insertNarratorMessage(text: String) {
        val narratorMessage = ChatMessage(content = text, isUser = false, isNarrator = true)
        _uiState.update { it.copy(messages = it.messages + narratorMessage) }
        viewModelScope.launch { saveCurrentChat() }
    }

    fun dismissScanlore() {
        _uiState.update { it.copy(showScanloreDialog = false, scanloreEntries = emptyList(), scanloreError = null, scanloreLoading = false) }
    }

    fun confirmScanlore(entries: List<String>) {
        val groupId = _currentGroupId ?: return
        viewModelScope.launch {
            entries.forEach { groupStorage.appendWorldBookEntry(groupId, it) }
            val updatedGroup = groupStorage.getGroupsForCharacter(
                _uiState.value.character?.avatar ?: ""
            ).firstOrNull { it.id == groupId }
            _currentWorldBook = updatedGroup?.worldBook ?: _currentWorldBook
            _uiState.update { it.copy(showScanloreDialog = false, scanloreEntries = emptyList(), hasWorldBook = _currentWorldBook.isNotBlank()) }
            val summary = if (entries.size == 1) entries[0] else "${entries.size} entries"
            insertNarratorMessage("* [World Book] Added: $summary *")
        }
    }

    private suspend fun runScanlore(messageCount: Int) {
        val character = _uiState.value.character
        val groupId = _currentGroupId
        if (groupId == null) {
            _uiState.update { it.copy(scanloreLoading = false, scanloreError = "No group linked — /scanlore requires a group world book.") }
            return
        }
        val loreHints = character?.loreHints ?: ""
        if (loreHints.isBlank()) {
            _uiState.update { it.copy(scanloreLoading = false, scanloreError = "No lore tracking hints set on this character. Edit the character and fill in the Lore Tracking field.") }
            return
        }

        val messages = _uiState.value.messages.takeLast(messageCount)
        val transcript = messages.joinToString("\n") { msg ->
            val role = when {
                msg.isNarrator -> "Narrator"
                msg.isUser -> _currentUserName
                else -> character?.name ?: "Character"
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
            val config = _currentConfig
            var fullResponse = ""
            val oaiMessages = if (config.usesChatCompletions)
                listOf(com.pockettavern.app.domain.model.PromptMessage("user", extractionPrompt))
            else null
            llmRepository.generate(extractionPrompt, config, null, emptyList(), oaiMessages, null).collect { event ->
                when (event) {
                    is com.pockettavern.app.domain.model.StreamEvent.Token -> fullResponse = event.accumulated
                    is com.pockettavern.app.domain.model.StreamEvent.Complete -> fullResponse = event.fullText
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
                // Strip leading "1. " "- " "* " etc.
                line.replace(Regex("^[\\d]+\\.\\s*"), "")
                    .replace(Regex("^[-*•]\\s*"), "")
                    .trim()
            }
            .filter { it.isNotBlank() }
    }

    private fun sendMessageText(rawText: String) {
        val character = _uiState.value.character ?: return

        // /sys <text> — insert narrator message without sending to LLM
        if (rawText.startsWith("/sys ")) {
            val narratorText = rawText.removePrefix("/sys ").trim()
            if (narratorText.isNotBlank()) insertNarratorMessage(narratorText)
            _uiState.update { it.copy(inputText = "") }
            return
        }

        // /ooc <text> — send OOC message to LLM without showing user bubble
        if (rawText.startsWith("/ooc ")) {
            val oocText = rawText.removePrefix("/ooc ").trim()
            if (oocText.isNotBlank()) {
                _uiState.update { it.copy(inputText = "") }
                sendHiddenUserTurn("(OOC: $oocText)")
            }
            return
        }

        // /addlore <text> — append entry to linked group's shared world book
        if (rawText.startsWith("/addlore ")) {
            val entry = rawText.removePrefix("/addlore ").trim()
            _uiState.update { it.copy(inputText = "") }
            if (entry.isNotBlank()) {
                val groupId = _currentGroupId
                if (groupId != null) {
                    viewModelScope.launch {
                        groupStorage.appendWorldBookEntry(groupId, entry)
                        val updatedGroup = groupStorage.getGroupsForCharacter(
                            _uiState.value.character?.avatar ?: ""
                        ).firstOrNull { it.id == groupId }
                        _currentWorldBook = updatedGroup?.worldBook ?: _currentWorldBook
                        _uiState.update { it.copy(hasWorldBook = _currentWorldBook.isNotBlank()) }
                        insertNarratorMessage("* [World Book] Added: $entry *")
                    }
                } else {
                    insertNarratorMessage("* [World Book] No group linked to this character *")
                }
            }
            return
        }

        // /scanlore [N] — scan last N messages and extract lore entries
        if (rawText.startsWith("/scanlore")) {
            val countArg = rawText.removePrefix("/scanlore").trim().toIntOrNull() ?: 30
            _uiState.update { it.copy(inputText = "", showScanloreDialog = true, scanloreLoading = true, scanloreEntries = emptyList(), scanloreError = null) }
            viewModelScope.launch { runScanlore(countArg) }
            return
        }

        // /persona <name> — temporarily override persona name for the session
        if (rawText.startsWith("/persona ")) {
            val personaName = rawText.removePrefix("/persona ").trim()
            if (personaName.isNotBlank()) {
                _currentUserName = personaName
                insertNarratorMessage("* Persona changed to: $personaName *")
            }
            _uiState.update { it.copy(inputText = "") }
            return
        }

        autoContinueCount = 0

        // Apply input regex rules, then full macro substitution
        val processed = extensionManager.processInput(rawText)
        val macroContext = ChatContext(userPersona = UserPersona(name = _currentUserName, description = _currentPersonaDescription))
        val macroBuilder = PromptBuilder(character, macroContext, _currentUserName)
        val message = macroBuilder.applyUserMacros(processed, _uiState.value.messages)
        val userMessage = ChatMessage(content = message, isUser = true)
        _uiState.update {
            it.copy(
                messages = it.messages + userMessage,
                inputText = "",
                tokenCount = 0,
                isGenerating = true,
                streamingContent = ""
            )
        }
        pushExtensionContext()
        extensionManager.emit(ExtensionEvent.MESSAGE_SENT, message)

        if (_uiState.value.currentChatFileName == null) {
            val fileName = localRepository.generateChatFileName(character.name)
            _uiState.update { it.copy(currentChatFileName = fileName) }
        }

        generateResponse(character, message, _uiState.value.messages.dropLast(1))
    }

    private fun generateResponse(character: Character, userMessage: String, history: List<ChatMessage>) {
        extensionManager.emit(ExtensionEvent.GENERATION_STARTED)
        generationJob = viewModelScope.launch {
            doGenerate(history, userMessage).collect { event ->
                when (event) {
                    is StreamEvent.Token -> {
                        _uiState.update { it.copy(streamingContent = event.accumulated) }
                    }
                    is StreamEvent.Complete -> {
                        // Step 1: apply regex rules + multi-turn trim (keeps extension tags intact)
                        val processed = trimMultiTurn(extensionManager.processOutput(event.fullText))
                        // Step 2: add message with raw text so we can emit MESSAGE_RECEIVED first
                        val rawMessage = ChatMessage(content = processed, isUser = false)
                        _uiState.update {
                            it.copy(
                                messages = it.messages + rawMessage,
                                isGenerating = false,
                                streamingContent = ""
                            )
                        }
                        // Step 3: update extension context, then emit MESSAGE_RECEIVED
                        pushExtensionContext()
                        val msgIndex = _uiState.value.messages.lastIndex
                        val safeText = processed
                            .replace("\\", "\\\\")
                            .replace("\"", "\\\"")
                            .replace("\n", "\\n")
                        extensionManager.emitJson(
                            ExtensionEvent.MESSAGE_RECEIVED,
                            "{\"text\":\"$safeText\",\"index\":$msgIndex,\"isUser\":false}"
                        )
                        // Step 4: apply JS output filters to strip metadata tags from display
                        val displayText = extensionManager.applyOutputFilters(processed)
                        if (displayText != processed) {
                            val messages = _uiState.value.messages.toMutableList()
                            messages[msgIndex] = messages[msgIndex].copy(
                                content = displayText,
                                rawContent = processed
                            )
                            _uiState.update { it.copy(messages = messages) }
                        }
                        // Step 5: refresh context (rawContent now set), persist headers
                        pushExtensionContext()
                        persistExtensionHeaders()
                        updateContextEstimate()
                        extensionManager.emit(ExtensionEvent.GENERATION_STOPPED)
                        generationJob = null
                        saveCurrentChat()
                        // Trigger long-term memory summarization if threshold exceeded (T14)
                        triggerMemorySummarizationIfNeeded()
                        // Auto-play TTS for new AI message
                        if (ttsAutoPlay) {
                            val ttsText = extensionManager.applyOutputFilters(processed)
                            val charFile = _uiState.value.character?.avatar
                                ?: "${_uiState.value.character?.name ?: "unknown"}.png"
                            viewModelScope.launch { ttsManager.speak(ttsText, charFile) }
                        }
                        // Auto-continue: if response is shorter than min length, request more
                        val estimatedTokens = extensionManager.tokenCounter.estimateTokens(processed)
                        if (autoContinueEnabled && autoContinueCount < 3 && estimatedTokens < autoContinueMinLength) {
                            autoContinueCount++
                            continueGeneration()
                        }
                    }
                    is StreamEvent.Error -> {
                        _uiState.update {
                            it.copy(isGenerating = false, streamingContent = "", error = event.message)
                        }
                        extensionManager.emit(ExtensionEvent.GENERATION_STOPPED)
                        generationJob = null
                    }
                }
            }
        }
    }

    /**
     * Build and stream a generation from the given history + current user message.
     * Loads ChatContext, builds prompt via PromptBuilder, calls LlmRepository.
     */
    private fun doGenerate(
        history: List<ChatMessage>,
        userMessage: String
    ): Flow<StreamEvent> = flow {
        val character = _uiState.value.character
        if (character == null) {
            emit(StreamEvent.Error("No character loaded"))
            return@flow
        }

        val charFileName = character.avatar ?: "${character.name}.png"
        val chatContext = when (val r = localRepository.loadChatContext(
            characterFileName = charFileName,
            chatFileName = _uiState.value.currentChatFileName
        )) {
            is Result.Success -> r.data
            is Result.Error -> {
                emit(StreamEvent.Error("Failed to load context: ${r.exception.message}"))
                return@flow
            }
        }

        val config = when (val r = localRepository.getApiConfiguration()) {
            is Result.Success -> r.data
            is Result.Error -> ApiConfiguration.DEFAULT
        }
        _currentConfig = config

        val preset = if (!config.usesChatCompletions) localRepository.getCurrentTextGenPreset() else null
        val oaiPreset = if (config.usesChatCompletions) localRepository.getCurrentOaiPreset() else null
        val userName = chatContext.userPersona.name.ifBlank { "User" }
        _currentUserName = userName
        _currentPersonaDescription = chatContext.userPersona.description
        val mainPromptItem = oaiPreset?.promptOrder?.find { it.id == "main_prompt" }
        val mainPromptOverride = if (config.usesChatCompletions && mainPromptItem?.enabled == true)
            mainPromptItem.content ?: "" else ""
        val extensionInjections = extensionManager.getPromptInjections()
        val builder = PromptBuilder(character, chatContext, userName, mainPromptOverride, extensionInjections, _currentMemoryBlock, _currentWorldBook)
        val prompt = builder.buildPrompt(history, userMessage)

        // For chat completion APIs, also build structured messages for proper role formatting.
        val messages = if (config.usesChatCompletions) {
            val promptOrder = oaiPreset?.promptOrder ?: com.pockettavern.app.domain.model.OaiPromptOrderItem.defaultOrder()
            builder.buildChatCompletionMessages(history, userMessage, promptOrder)
        } else null

        // Notify extensions that a prompt is about to be sent (T23)
        extensionManager.fireBeforePromptSend(prompt, messages?.size ?: 0)

        // Stop sequences: instruct template markers only apply to text completion backends.
        // Chat completion APIs handle turn boundaries themselves — sending [INST]/</s>/etc.
        // as stop sequences is meaningless noise and can cause premature truncation.
        val stopSequences = if (config.usesChatCompletions) {
            emptyList()
        } else {
            buildList {
                chatContext.instructTemplate?.let { t ->
                    if (t.inputSequence.isNotBlank()) add(t.inputSequence)
                    if (t.stopSequence.isNotBlank()) add(t.stopSequence)
                }
            }
        }

        llmRepository.generate(prompt, config, preset, stopSequences, messages, oaiPreset).collect { emit(it) }
    }.flowOn(Dispatchers.IO)

    fun stopGeneration() {
        generationJob?.cancel()
        generationJob = null
        extensionManager.emit(ExtensionEvent.GENERATION_STOPPED)

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            llmRepository.abortGeneration(_currentConfig)
        }

        val streamingContent = _uiState.value.streamingContent
        if (streamingContent.isNotBlank()) {
            val assistantMessage = ChatMessage(content = streamingContent, isUser = false)
            _uiState.update {
                it.copy(
                    messages = it.messages + assistantMessage,
                    isGenerating = false,
                    streamingContent = ""
                )
            }
            viewModelScope.launch { saveCurrentChat() }
        } else {
            _uiState.update { it.copy(isGenerating = false, streamingContent = "") }
        }
    }

    // ========== Message Actions ==========

    fun showMessageActions(messageIndex: Int) {
        _uiState.update {
            it.copy(selectedMessageIndex = messageIndex, showMessageActions = true)
        }
        // Dispatch MESSAGE_LONG_PRESSED so extensions can register context menu actions
        extensionManager.emitJson(
            ExtensionEvent.MESSAGE_LONG_PRESSED,
            "{\"messageIndex\":$messageIndex}"
        )
    }

    fun dismissMessageActions() {
        _uiState.update { it.copy(selectedMessageIndex = null, showMessageActions = false) }
    }

    fun saveImageMessageToGallery(messageIndex: Int) {
        val message = _uiState.value.messages.getOrNull(messageIndex) ?: return
        val imagePath = message.imagePath ?: return
        val characterName = _uiState.value.character?.name ?: "Image"

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val imageFile = File(context.filesDir, imagePath)
                    if (!imageFile.exists()) throw Exception("Image file not found")

                    val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
                        ?: throw Exception("Failed to decode image")
                    val filename = "${characterName}_scene_${System.currentTimeMillis()}.png"

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val contentValues = ContentValues().apply {
                            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PocketTavern")
                        }
                        val uri = context.contentResolver.insert(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues
                        ) ?: throw Exception("Failed to create media entry")
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                        } ?: throw Exception("Failed to open output stream")
                    } else {
                        val dir = File(
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                            "PocketTavern"
                        ).also { it.mkdirs() }
                        FileOutputStream(File(dir, filename)).use { out ->
                            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                        }
                    }
                    _uiState.update { it.copy(imageSaved = true) }
                } catch (e: Exception) {
                    _uiState.update { it.copy(error = "Failed to save image: ${e.message}") }
                }
            }
        }
    }

    // ── Image Gallery ────────────────────────────────────────────────────────

    fun showGallery() {
        viewModelScope.launch {
            val characterName = _uiState.value.character?.name ?: return@launch
            val images = withContext(Dispatchers.IO) { collectCharacterImages(characterName) }
            _uiState.update { it.copy(showGallery = true, galleryImages = images) }
        }
    }

    fun dismissGallery() {
        _uiState.update { it.copy(showGallery = false) }
    }

    // ── LLM model picker ──────────────────────────────────────────────────

    fun showModelPicker() {
        viewModelScope.launch {
            _uiState.update { it.copy(showModelPicker = true, modelPickerLoading = true, availableModels = emptyList()) }
            val config = when (val r = localRepository.getApiConfiguration()) {
                is Result.Success -> r.data
                else -> { _uiState.update { it.copy(modelPickerLoading = false) }; return@launch }
            }
            val models = try {
                withContext(Dispatchers.IO) { llmRepository.getAvailableModels(config) }.map { it.id }
            } catch (e: Exception) {
                emptyList()
            }
            _uiState.update { it.copy(availableModels = models, modelPickerLoading = false) }
        }
    }

    fun dismissModelPicker() {
        _uiState.update { it.copy(showModelPicker = false) }
    }

    fun applyModelChange(modelName: String) {
        _uiState.update { it.copy(showModelPicker = false) }
        viewModelScope.launch {
            val config = when (val r = localRepository.getApiConfiguration()) {
                is Result.Success -> r.data
                else -> return@launch
            }
            localRepository.saveApiConfiguration(config.copy(currentModel = modelName))
            // apiConfigFlow updates currentModelName in UiState automatically
        }
    }

    private suspend fun collectCharacterImages(characterName: String): List<GalleryImage> {
        val chats = localRepository.getCharacterChats(characterName).getOrNull() ?: return emptyList()
        val images = mutableListOf<GalleryImage>()
        for (chatInfo in chats) {
            val chat = localRepository.getChat(characterName, chatInfo.fileName).getOrNull() ?: continue
            chat.messages.forEachIndexed { index, message ->
                val path = message.imagePath ?: return@forEachIndexed
                val file = File(context.filesDir, path)
                if (!file.exists()) return@forEachIndexed
                val ts = file.name.removeSuffix(".png").toLongOrNull() ?: file.lastModified()
                images.add(GalleryImage(path, chatInfo.fileName, ts, index))
            }
        }
        return images.sortedByDescending { it.timestamp }
    }

    fun saveGalleryImageToDevice(image: GalleryImage) {
        val characterName = _uiState.value.character?.name ?: "Image"
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val imageFile = File(context.filesDir, image.imagePath)
                    if (!imageFile.exists()) throw Exception("Image file not found")
                    val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
                        ?: throw Exception("Failed to decode image")
                    val filename = "${characterName}_scene_${image.timestamp}.png"

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val contentValues = ContentValues().apply {
                            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PocketTavern")
                        }
                        val uri = context.contentResolver.insert(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues
                        ) ?: throw Exception("Failed to create media entry")
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                        } ?: throw Exception("Failed to open output stream")
                    } else {
                        val dir = File(
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                            "PocketTavern"
                        ).also { it.mkdirs() }
                        FileOutputStream(File(dir, filename)).use { out ->
                            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                        }
                    }
                    _uiState.update { it.copy(imageSaved = true) }
                } catch (e: Exception) {
                    _uiState.update { it.copy(error = "Failed to save image: ${e.message}") }
                }
            }
        }
    }

    fun deleteGalleryImage(image: GalleryImage) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                // Delete image file
                val imageFile = File(context.filesDir, image.imagePath)
                imageFile.delete()
            }
            // Refresh gallery
            val characterName = _uiState.value.character?.name ?: return@launch
            val images = withContext(Dispatchers.IO) { collectCharacterImages(characterName) }
            _uiState.update { it.copy(galleryImages = images) }
        }
    }

    // ── TTS ──────────────────────────────────────────────────────────────────

    fun speakMessage(index: Int) {
        val message = _uiState.value.messages.getOrNull(index) ?: return
        val charFile = _uiState.value.character?.avatar
            ?: "${_uiState.value.character?.name ?: "unknown"}.png"
        viewModelScope.launch { ttsManager.speak(message.content, charFile) }
    }

    fun stopTts() {
        ttsManager.stop()
    }

    fun deleteMessage(index: Int) {
        val messages = _uiState.value.messages.toMutableList()
        if (index in messages.indices) {
            messages.removeAt(index)
            // Shift headers: remove deleted index, shift higher indices down by 1
            val oldHeaders = _uiState.value.messageHeaders
            val newHeaders = mutableMapOf<Int, List<MessageHeaderEntry>>()
            oldHeaders.forEach { (idx, entries) ->
                when {
                    idx < index -> newHeaders[idx] = entries
                    idx > index -> newHeaders[idx - 1] = entries
                    // idx == index: dropped (deleted message)
                }
            }
            // Also shift visibleHeaderButtons
            val newVisibleBtns = _uiState.value.visibleHeaderButtons
                .filter { it.first != index }
                .map { if (it.first > index) (it.first - 1) to it.second else it }
                .toSet()
            extensionManager.replaceMessageHeaders(newHeaders)
            _uiState.update {
                it.copy(
                    messages = messages,
                    showMessageActions = false,
                    selectedMessageIndex = null,
                    messageHeaders = newHeaders,
                    visibleHeaderButtons = newVisibleBtns
                )
            }
            viewModelScope.launch { saveCurrentChat() }
        }
    }

    fun deleteMessagesFromIndex(index: Int) {
        val messages = _uiState.value.messages.toMutableList()
        if (index !in messages.indices) return

        // Remove this message and everything after it
        val removed = messages.size - index
        while (messages.size > index) {
            messages.removeAt(messages.size - 1)
        }

        // Rebuild headers: keep only indices before the cutoff
        val oldHeaders = _uiState.value.messageHeaders
        val newHeaders = oldHeaders.filterKeys { it < index }
        val newVisibleBtns = _uiState.value.visibleHeaderButtons
            .filter { it.first < index }
            .toSet()

        extensionManager.replaceMessageHeaders(newHeaders)
        _uiState.update {
            it.copy(
                messages = messages,
                showMessageActions = false,
                selectedMessageIndex = null,
                messageHeaders = newHeaders,
                visibleHeaderButtons = newVisibleBtns
            )
        }
        viewModelScope.launch { saveCurrentChat() }
        // Notify extensions
        extensionManager.emit(ExtensionEvent.MESSAGE_DELETED, index)
    }

    fun regenerateResponse() {
        val messages = _uiState.value.messages
        val character = _uiState.value.character ?: return

        val lastAssistantIndex = messages.indexOfLast { !it.isUser }
        if (lastAssistantIndex == -1) return

        val userMessageIndex = (lastAssistantIndex - 1 downTo 0).firstOrNull { messages[it].isUser }
            ?: return

        val userMessage = messages[userMessageIndex].content
        val history = messages.subList(0, userMessageIndex)

        _uiState.update {
            it.copy(
                messages = messages.subList(0, lastAssistantIndex),
                isGenerating = true,
                streamingContent = ""
            )
        }
        generateResponse(character, userMessage, history)
    }

    // ── Chat Background ───────────────────────────────────────────────────

    fun uploadBackgroundFromUri(uri: android.net.Uri) {
        viewModelScope.launch {
            val success = backgroundRepository.saveBackgroundFromUri(currentAvatarUrl, uri)
            if (success) {
                val bgPath = backgroundRepository.getBackgroundPath(currentAvatarUrl)
                _uiState.update { it.copy(backgroundPath = bgPath) }
            } else {
                _uiState.update { it.copy(error = "Failed to set background from image") }
            }
        }
    }

    fun clearBackground() {
        viewModelScope.launch {
            backgroundRepository.deleteBackground(currentAvatarUrl)
            _uiState.update { it.copy(backgroundPath = null) }
        }
    }

    private suspend fun saveCurrentChat() {
        val character = _uiState.value.character ?: return
        val fileName = _uiState.value.currentChatFileName ?: return
        val chat = Chat(
            fileName = fileName,
            characterName = character.name,
            messages = _uiState.value.messages
        )
        localRepository.saveChat(chat)
    }

    private fun triggerMemorySummarizationIfNeeded() {
        if (!memoryEnabled) return
        val character = _uiState.value.character ?: return
        val fileName = _uiState.value.currentChatFileName ?: return
        val messages = _uiState.value.messages
        val unsummarized = messages.drop(_currentSummarizedTurnCount)
        val charCount = unsummarized.sumOf { it.content.length }
        if (charCount < 12_000) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val config = when (val r = localRepository.getApiConfiguration()) {
                    is com.pockettavern.app.domain.model.Result.Success -> r.data
                    else -> return@launch
                }
                val summary = summarizeHistoryUseCase.summarize(unsummarized, config)
                if (summary.isBlank()) return@launch
                val newBlock = if (_currentMemoryBlock.isBlank()) summary
                    else "$_currentMemoryBlock\n$summary"
                val newCount = messages.size
                _currentMemoryBlock = newBlock
                _currentSummarizedTurnCount = newCount
                localRepository.updateChatMemoryBlock(character.name, fileName, newBlock, newCount)
                com.pockettavern.app.util.DebugLogger.log("ChatViewModel: memory summarized $newCount turns, block=${newBlock.length} chars")
            } catch (e: Exception) {
                com.pockettavern.app.util.DebugLogger.logError("ChatViewModel", "Memory summarization failed", e)
            }
        }
    }

    /** Snapshot current extension headers onto the corresponding ChatMessage objects. Returns true if anything changed. */
    private fun persistExtensionHeaders(): Boolean {
        val headers = _uiState.value.messageHeaders
        val messages = _uiState.value.messages.toMutableList()
        var changed = false

        // Apply current headers to messages, and clear headers from messages
        // that no longer have entries (e.g. after clearing or shifting)
        for (index in messages.indices) {
            val msg = messages[index]
            val entries = headers[index] ?: emptyList()
            if (msg.extensionHeaders != entries) {
                messages[index] = msg.copy(extensionHeaders = entries)
                changed = true
            }
        }

        if (changed) {
            _uiState.update { it.copy(messages = messages) }
        }
        return changed
    }

    // ── Header long-press ─────────────────────────────────────────────────

    /**
     * Priority: inline buttons → context menu → HEADER_LONG_PRESSED event.
     * Returns "buttons" or "menu" so ChatBubble knows which UX to show,
     * or null if neither is registered (fallback to event).
     */
    fun onHeaderLongPressed(messageIndex: Int, extensionId: String) {
        if (extensionId.isBlank()) return
        val state = _uiState.value

        // Priority 1: toggle inline buttons
        if (state.headerButtons.containsKey(extensionId)) {
            val key = messageIndex to extensionId
            val current = state.visibleHeaderButtons
            _uiState.update {
                it.copy(visibleHeaderButtons = if (key in current) current - key else current + key)
            }
            return
        }

        // Priority 2: context menu — handled in ChatBubble via headerMenus state
        if (state.headerMenus.containsKey(extensionId)) {
            // Menu popup is managed by ChatBubble's local state.
            // Returning here means we don't fire the event.
            return
        }

        // Priority 3: fallback — dispatch HEADER_LONG_PRESSED event
        val safeId = extensionId.replace("\"", "\\\"")
        val jsonData = "{\"messageIndex\":$messageIndex,\"extensionId\":\"$safeId\"}"
        extensionManager.emitJson(ExtensionEvent.HEADER_LONG_PRESSED, jsonData)
    }

    /** Dispatch BUTTON_CLICKED when an inline header button or menu item is tapped. */
    fun onHeaderActionClicked(action: String, label: String) {
        val safeAction = action.replace("\"", "\\\"")
        val safeLabel = label.replace("\"", "\\\"")
        val jsonData = "{\"action\":\"$safeAction\",\"label\":\"$safeLabel\"}"
        extensionManager.emitJson(ExtensionEvent.BUTTON_CLICKED, jsonData)
    }

    // ── Edit dialog (JS extension) ─────────────────────────────────────────

    fun submitEditDialog(results: Map<String, String>) {
        val request = _uiState.value.editDialogRequest ?: return
        extensionManager.jsHost.completeEditDialog(request.callbackId, results)
    }

    fun cancelEditDialog() {
        extensionManager.jsHost.cancelEditDialog()
    }

    // ── Hidden generation (JS extension) ─────────────────────────────────

    private suspend fun doHiddenGenerate(prompt: String, callbackId: String) {
        try {
            val config = when (val r = localRepository.getApiConfiguration()) {
                is Result.Success -> r.data
                is Result.Error -> ApiConfiguration.DEFAULT
            }
            val preset = localRepository.getCurrentTextGenPreset()

            // Build a context-aware prompt: include character info and recent chat
            // history so the LLM has full scene context for hidden generation.
            val messages = _uiState.value.messages
            val character = _uiState.value.character
            val contextPrompt = buildString {
                if (character != null) {
                    append("Character: ").append(character.name).append("\n")
                    if (character.description.isNotBlank()) {
                        append("Description: ").append(character.description.take(1000)).append("\n")
                    }
                    if (character.personality.isNotBlank()) {
                        append("Personality: ").append(character.personality.take(500)).append("\n")
                    }
                    if (character.scenario.isNotBlank()) {
                        append("Scenario: ").append(character.scenario.take(500)).append("\n")
                    }
                    append("\n")
                }
                // Include recent messages for context (up to 20, 2000 chars each)
                val recent = if (messages.size > 20) messages.takeLast(20) else messages
                if (recent.isNotEmpty()) {
                    append("Recent conversation:\n")
                    for (msg in recent) {
                        val role = if (msg.isUser) _currentUserName else (character?.name ?: "Assistant")
                        val text = msg.rawContent ?: msg.content
                        append(role).append(": ").append(text.take(2000)).append("\n")
                    }
                    append("\n")
                }
                append(prompt)
            }

            // For text completion backends (KoboldAI), wrap the prompt with the
            // instruct template so the model knows it needs to generate a response.
            // Without this, the model sees a completed document and immediately
            // outputs EOS.  Chat completion backends handle this automatically.
            val finalPrompt = if (!config.usesChatCompletions) {
                val charFileName = character?.avatar ?: "${character?.name ?: "char"}.png"
                val instructTemplate = when (val r = localRepository.loadChatContext(
                    characterFileName = charFileName,
                    chatFileName = _uiState.value.currentChatFileName
                )) {
                    is Result.Success -> r.data.instructTemplate
                    is Result.Error -> null
                }
                if (instructTemplate != null) {
                    buildString {
                        // Wrap as: [input_sequence]prompt[input_suffix][output_sequence]
                        append(instructTemplate.inputSequence)
                        append(contextPrompt)
                        append(instructTemplate.inputSuffix)
                        append(instructTemplate.outputSequence)
                    }
                } else {
                    // No instruct template — add a generic response marker
                    contextPrompt + "\n\nResponse:\n"
                }
            } else {
                contextPrompt
            }

            var resultText = ""
            llmRepository.generate(finalPrompt, config, preset).collect { event ->
                when (event) {
                    is StreamEvent.Complete -> resultText = event.fullText
                    is StreamEvent.Error -> resultText = ""
                    is StreamEvent.Token -> { /* ignore streaming tokens */ }
                }
            }
            extensionManager.jsHost.completeHiddenGenerate(callbackId, resultText)
        } catch (e: Exception) {
            extensionManager.jsHost.completeHiddenGenerate(callbackId, "")
        }
    }

    // ── Image generation (JS extension) ──────────────────────────────────

    private suspend fun doExtensionImageGenerate(prompt: String, optionsJson: String, callbackId: String) {
        try {
            val imageGenConfig = settingsDataStore.getImageGenConfig()

            // Parse optional overrides from the extension
            val options = try { org.json.JSONObject(optionsJson) } catch (_: Exception) { org.json.JSONObject() }
            val width = options.optInt("width", imageGenConfig.width)
            val height = options.optInt("height", imageGenConfig.height)
            val negativePrompt = options.optString("negativePrompt", imageGenConfig.negativePrompt)
            val seed = options.optInt("seed", imageGenConfig.seed)
            val sourceImageBase64 = options.optString("sourceImageBase64").ifEmpty { null }
            val denoisingStrength = options.optDouble("denoisingStrength", 0.55).toFloat()

            val params = ForgeGenerationParams(
                prompt = prompt,
                negativePrompt = negativePrompt,
                width = width,
                height = height,
                steps = imageGenConfig.steps,
                cfgScale = imageGenConfig.cfgScale,
                sampler = imageGenConfig.sampler,
                seed = seed,
                sourceImageBase64 = sourceImageBase64,
                denoisingStrength = denoisingStrength
            )

            var resultBase64 = ""
            imageGenRepository.generateImageWithProgress(params).collect { state ->
                if (state is GenerationState.Complete) {
                    resultBase64 = state.imageBase64
                }
            }
            extensionManager.jsHost.completeImageGenerate(callbackId, resultBase64)
        } catch (e: Exception) {
            extensionManager.jsHost.completeImageGenerate(callbackId, "")
        }
    }

    // ── Model get/set (JS extension) ─────────────────────────────────────

    private suspend fun doExtensionGetModels(callbackId: String) {
        try {
            // Try active ImageGen backend first; fall back to ForgeRepository if empty/failed
            val models: List<String> = run {
                val r = imageGenRepository.getModels()
                if (r is com.pockettavern.app.domain.model.Result.Success && r.data.isNotEmpty()) return@run r.data
                val fr = forgeRepository.getModels()
                if (fr is com.pockettavern.app.domain.model.Result.Success) fr.data else emptyList()
            }
            val json = "[" + models.joinToString(",") { "\"${it.replace("\"", "\\\"")}\"" } + "]"
            extensionManager.jsHost.completeGetModels(callbackId, json)
        } catch (e: Exception) {
            extensionManager.jsHost.completeGetModels(callbackId, "[]")
        }
    }

    private suspend fun doExtensionSetModel(modelName: String, callbackId: String) {
        try {
            // Try active ImageGen backend first; fall back to ForgeRepository
            val r = imageGenRepository.setModel(modelName)
            val success = if (r is com.pockettavern.app.domain.model.Result.Success) {
                true
            } else {
                forgeRepository.setCurrentModel(modelName) is com.pockettavern.app.domain.model.Result.Success
            }
            extensionManager.jsHost.completeSetModel(callbackId, success)
        } catch (e: Exception) {
            extensionManager.jsHost.completeSetModel(callbackId, false)
        }
    }

    // ── Insert message (JS extension) ────────────────────────────────────

    private suspend fun doExtensionInsertMessage(content: String, optionsJson: String) {
        val options = try { org.json.JSONObject(optionsJson) } catch (_: Exception) { org.json.JSONObject() }
        val type = options.optString("type", "narrator")
        val imageBase64 = options.optString("imageBase64", "")

        when (type) {
            "image" -> {
                if (imageBase64.isBlank()) return
                // Save image to file, then insert a narrator message with imagePath
                val imagePath = withContext(Dispatchers.IO) {
                    saveExtensionImage(imageBase64)
                }
                if (imagePath != null) {
                    val imageMessage = ChatMessage(
                        content = content.ifBlank { "" },
                        isUser = false,
                        isNarrator = true,
                        imagePath = imagePath
                    )
                    _uiState.update { it.copy(messages = it.messages + imageMessage) }
                    saveCurrentChat()
                }
            }
            else -> {
                // Narrator text message
                if (content.isNotBlank()) {
                    insertNarratorMessage(content)
                }
            }
        }
    }

    /** Save a base64 image to the chat_images directory and return the relative path. */
    private fun saveExtensionImage(base64: String): String? {
        return try {
            val imageBytes = Base64.decode(base64, Base64.DEFAULT)
            val chatFileName = _uiState.value.currentChatFileName
            val dir = File(context.filesDir, "chat_images/$chatFileName").also { it.mkdirs() }
            val filename = "${System.currentTimeMillis()}.png"
            val file = File(dir, filename)
            file.writeBytes(imageBytes)
            "chat_images/$chatFileName/$filename"
        } catch (e: Exception) {
            null
        }
    }

    fun updateAuthorsNote(
        content: String,
        depth: Int = 4,
        interval: Int = 1,
        position: Int = 0,
        role: Int = 0
    ) {
        val messages = _uiState.value.messages.toMutableList()
        if (messages.isEmpty()) return

        val firstMessage = messages[0]
        val updatedMetadata = ChatMessageMetadata(
            notePrompt = content.ifBlank { null },
            noteInterval = interval,
            noteDepth = depth,
            notePosition = position,
            noteRole = role
        )
        messages[0] = firstMessage.copy(chatMetadata = updatedMetadata)
        _uiState.update { it.copy(messages = messages) }
        viewModelScope.launch { saveCurrentChat() }
    }

    fun getAuthorsNote(): ChatMessageMetadata? =
        _uiState.value.messages.firstOrNull()?.chatMetadata

    fun showDeleteDialog() {
        _uiState.update { it.copy(showDeleteDialog = true) }
    }

    fun dismissDeleteDialog() {
        _uiState.update { it.copy(showDeleteDialog = false) }
    }

    fun deleteCurrentChat() {
        val character = _uiState.value.character ?: return
        val fileName = _uiState.value.currentChatFileName ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(showDeleteDialog = false, isLoading = true) }
            withContext(Dispatchers.IO) {
                extensionManager.varsDeleteForChat(character.name, fileName)
            }
            when (localRepository.deleteChat(character.name, fileName)) {
                is Result.Success -> {
                    when (val chatsResult = localRepository.getCharacterChats(character.name)) {
                        is Result.Success -> {
                            val chats = chatsResult.data
                            _uiState.update { it.copy(availableChats = chats) }
                            if (chats.isNotEmpty()) {
                                loadExistingChat(character, chats.first().fileName)
                            } else {
                                createNewChat()
                            }
                        }
                        is Result.Error -> createNewChat()
                    }
                }
                is Result.Error -> {
                    _uiState.update { it.copy(isLoading = false, error = "Failed to delete chat") }
                }
            }
        }
    }

    fun deleteCharacter() {
        viewModelScope.launch {
            when (localRepository.deleteCharacter(currentAvatarUrl)) {
                is Result.Success -> { /* navigation handles going back */ }
                is Result.Error -> {
                    _uiState.update { it.copy(error = "Failed to delete character") }
                }
            }
        }
    }

    fun exportCurrentChat(uri: android.net.Uri) {
        val charName = _uiState.value.character?.name ?: return
        val chatFileName = _uiState.value.currentChatFileName ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val sanitized = charName.replace(Regex("[^a-zA-Z0-9_\\-. ]"), "_").trim().take(64)
                val chatFile = java.io.File(context.filesDir, "chats/$sanitized/$chatFileName")
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    chatFile.inputStream().use { it.copyTo(out) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Export failed: ${e.message}") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    // ========== Message Search ==========

    fun toggleSearch() {
        val searching = _uiState.value.isSearching
        _uiState.update {
            it.copy(
                isSearching = !searching,
                searchQuery = "",
                searchResults = emptyList(),
                currentSearchResultIndex = 0
            )
        }
    }

    fun updateSearchQuery(query: String) {
        val results = if (query.isBlank()) emptyList() else {
            _uiState.value.messages.indices.filter { i ->
                _uiState.value.messages[i].content.contains(query, ignoreCase = true)
            }
        }
        val idx = if (results.isNotEmpty()) results.size - 1 else 0
        _uiState.update {
            it.copy(searchQuery = query, searchResults = results, currentSearchResultIndex = idx)
        }
    }

    fun navigateSearchResult(delta: Int) {
        val results = _uiState.value.searchResults
        if (results.isEmpty()) return
        val current = _uiState.value.currentSearchResultIndex
        val newIdx = (current + delta + results.size) % results.size
        _uiState.update { it.copy(currentSearchResultIndex = newIdx) }
    }

    // ========== Context Usage ==========

    private fun updateContextEstimate() {
        val state = _uiState.value
        val character = state.character
        val messages = state.messages

        var chars = 0
        if (character != null) {
            chars += character.description.length + character.personality.length +
                     character.scenario.length + character.systemPrompt.length
        }
        chars += messages.sumOf { it.content.length }
        _uiState.update { it.copy(contextUsedTokens = chars / 4) }
    }

    // ========== Message Editing ==========

    fun startEditingMessage(index: Int) {
        val message = _uiState.value.messages.getOrNull(index) ?: return
        _uiState.update {
            it.copy(
                editingMessageIndex = index,
                editingMessageText = message.content,
                showMessageActions = false
            )
        }
    }

    fun updateEditingText(text: String) {
        _uiState.update { it.copy(editingMessageText = text) }
    }

    fun saveEditedMessage() {
        val index = _uiState.value.editingMessageIndex ?: return
        val newText = _uiState.value.editingMessageText
        val messages = _uiState.value.messages.toMutableList()
        if (index in messages.indices) {
            messages[index] = messages[index].copy(content = newText)
            _uiState.update {
                it.copy(messages = messages, editingMessageIndex = null, editingMessageText = "")
            }
            pushExtensionContext()
            viewModelScope.launch { saveCurrentChat() }
        }
    }

    fun cancelEditing() {
        _uiState.update { it.copy(editingMessageIndex = null, editingMessageText = "") }
    }

    // ========== Continue Generation ==========

    fun continueGeneration() {
        val character = _uiState.value.character ?: return
        val messages = _uiState.value.messages
        if (messages.isEmpty()) return

        _uiState.update { it.copy(isGenerating = true, streamingContent = "") }

        generationJob = viewModelScope.launch {
            // Full history as context, hidden continue prompt as the "user" turn
            doGenerate(messages, CONTINUE_PROMPT).collect { event ->
                when (event) {
                    is StreamEvent.Token -> {
                        _uiState.update { it.copy(streamingContent = event.accumulated) }
                    }
                    is StreamEvent.Complete -> {
                        val processed = trimMultiTurn(extensionManager.processOutput(event.fullText))
                        val newMessage = ChatMessage(content = processed, isUser = false)
                        _uiState.update {
                            it.copy(
                                messages = it.messages + newMessage,
                                isGenerating = false,
                                streamingContent = ""
                            )
                        }
                        // Apply JS output filters
                        val displayContent = extensionManager.applyOutputFilters(processed)
                        if (displayContent != processed) {
                            val msgs = _uiState.value.messages.toMutableList()
                            val idx = msgs.indexOfLast { !it.isUser }
                            if (idx >= 0) {
                                msgs[idx] = msgs[idx].copy(content = displayContent, rawContent = processed)
                                _uiState.update { it.copy(messages = msgs) }
                            }
                        }
                        pushExtensionContext()
                        persistExtensionHeaders()
                        extensionManager.emit(ExtensionEvent.GENERATION_STOPPED)
                        generationJob = null
                        saveCurrentChat()
                        val estimatedTokens = extensionManager.tokenCounter.estimateTokens(processed)
                        if (autoContinueEnabled && autoContinueCount < 3 && estimatedTokens < autoContinueMinLength) {
                            autoContinueCount++
                            continueGeneration()
                        }
                    }
                    is StreamEvent.Error -> {
                        _uiState.update {
                            it.copy(isGenerating = false, streamingContent = "", error = event.message)
                        }
                        extensionManager.emit(ExtensionEvent.GENERATION_STOPPED)
                        generationJob = null
                    }
                }
            }
        }
    }

    // Send a hidden user turn (e.g. OOC) — no user bubble, generates a new AI message
    private fun sendHiddenUserTurn(userTurn: String) {
        if (_uiState.value.character == null) return
        val messages = _uiState.value.messages
        _uiState.update { it.copy(isGenerating = true, streamingContent = "") }
        extensionManager.emit(ExtensionEvent.GENERATION_STARTED)

        generationJob = viewModelScope.launch {
            doGenerate(messages, userTurn).collect { event ->
                when (event) {
                    is StreamEvent.Token -> {
                        _uiState.update { it.copy(streamingContent = event.accumulated) }
                    }
                    is StreamEvent.Complete -> {
                        val processed = trimMultiTurn(extensionManager.processOutput(event.fullText))
                        val newMessage = ChatMessage(content = processed, isUser = false)
                        _uiState.update {
                            it.copy(
                                messages = it.messages + newMessage,
                                isGenerating = false,
                                streamingContent = ""
                            )
                        }
                        val displayContent = extensionManager.applyOutputFilters(processed)
                        if (displayContent != processed) {
                            val msgs = _uiState.value.messages.toMutableList()
                            val idx = msgs.indexOfLast { !it.isUser }
                            if (idx >= 0) {
                                msgs[idx] = msgs[idx].copy(content = displayContent, rawContent = processed)
                                _uiState.update { it.copy(messages = msgs) }
                            }
                        }
                        pushExtensionContext()
                        persistExtensionHeaders()
                        extensionManager.emit(ExtensionEvent.GENERATION_STOPPED)
                        generationJob = null
                        saveCurrentChat()
                    }
                    is StreamEvent.Error -> {
                        _uiState.update {
                            it.copy(isGenerating = false, streamingContent = "", error = event.message)
                        }
                        extensionManager.emit(ExtensionEvent.GENERATION_STOPPED)
                        generationJob = null
                    }
                }
            }
        }
    }

    // ========== Swipes (Alternate Responses) ==========

    fun swipeLeft(messageIndex: Int) {
        val swipes = _uiState.value.messageSwipes[messageIndex] ?: return
        val currentIndex = _uiState.value.currentSwipeIndex[messageIndex] ?: 0
        if (currentIndex > 0) applySwipe(messageIndex, currentIndex - 1, swipes)
    }

    fun swipeRight(messageIndex: Int) {
        val swipes = _uiState.value.messageSwipes[messageIndex] ?: return
        val currentIndex = _uiState.value.currentSwipeIndex[messageIndex] ?: 0
        if (currentIndex < swipes.size - 1) applySwipe(messageIndex, currentIndex + 1, swipes)
    }

    private fun applySwipe(messageIndex: Int, swipeIndex: Int, swipes: List<String>) {
        val messages = _uiState.value.messages.toMutableList()
        if (messageIndex in messages.indices) {
            messages[messageIndex] = messages[messageIndex].copy(content = swipes[swipeIndex])
            val newSwipeIndex = _uiState.value.currentSwipeIndex.toMutableMap()
            newSwipeIndex[messageIndex] = swipeIndex
            _uiState.update { it.copy(messages = messages, currentSwipeIndex = newSwipeIndex) }
            viewModelScope.launch { saveCurrentChat() }
        }
    }

    fun regenerateWithSwipe() {
        val character = _uiState.value.character ?: return
        val messages = _uiState.value.messages

        val lastAssistantIndex = messages.indexOfLast { !it.isUser }
        if (lastAssistantIndex == -1) return

        val currentMessage = messages[lastAssistantIndex]
        val existingSwipes = _uiState.value.messageSwipes[lastAssistantIndex]?.toMutableList()
            ?: mutableListOf(currentMessage.content)
        if (existingSwipes.isEmpty() || existingSwipes.last() != currentMessage.content) {
            existingSwipes.add(currentMessage.content)
        }

        val userMessageIndex = (lastAssistantIndex - 1 downTo 0).firstOrNull { messages[it].isUser }
            ?: return

        val userMessage = messages[userMessageIndex].content
        val history = messages.subList(0, userMessageIndex).toList()

        _uiState.update {
            it.copy(
                messages = messages.subList(0, lastAssistantIndex),
                isGenerating = true,
                streamingContent = ""
            )
        }

        generationJob = viewModelScope.launch {
            doGenerate(history, userMessage).collect { event ->
                when (event) {
                    is StreamEvent.Token -> {
                        _uiState.update { it.copy(streamingContent = event.accumulated) }
                    }
                    is StreamEvent.Complete -> {
                        val newContent = trimMultiTurn(extensionManager.processOutput(event.fullText))
                        val assistantMessage = ChatMessage(content = newContent, isUser = false)
                        existingSwipes.add(newContent)

                        val newSwipes = _uiState.value.messageSwipes.toMutableMap()
                        newSwipes[lastAssistantIndex] = existingSwipes

                        val newSwipeIndex = _uiState.value.currentSwipeIndex.toMutableMap()
                        newSwipeIndex[lastAssistantIndex] = existingSwipes.size - 1

                        _uiState.update {
                            it.copy(
                                messages = it.messages + assistantMessage,
                                isGenerating = false,
                                streamingContent = "",
                                messageSwipes = newSwipes,
                                currentSwipeIndex = newSwipeIndex
                            )
                        }
                        generationJob = null
                        saveCurrentChat()
                    }
                    is StreamEvent.Error -> {
                        _uiState.update {
                            it.copy(
                                messages = messages,
                                isGenerating = false,
                                streamingContent = "",
                                error = event.message
                            )
                        }
                        generationJob = null
                    }
                }
            }
        }
    }

    fun getSwipeInfo(messageIndex: Int): Pair<Int, Int>? {
        val swipes = _uiState.value.messageSwipes[messageIndex] ?: return null
        val currentIndex = _uiState.value.currentSwipeIndex[messageIndex] ?: 0
        return currentIndex + 1 to swipes.size
    }

    /**
     * Strips any multi-turn continuation the model generated past the character's first response.
     * Models sometimes write "User: ..." or "PersonaName: ..." after their response, poisoning
     * chat history. We cut at the first occurrence of a user-role marker on its own line.
     */
    private fun trimMultiTurn(text: String): String {
        val personaName = _currentUserName.trim()
        val extras = if (personaName.isNotBlank() && personaName != "User") {
            "|${Regex.escape(personaName)}"
        } else ""
        val stopPattern = Regex("""\n\s*(User|You|Human$extras)\s*:""")
        val match = stopPattern.find(text) ?: return text
        return text.substring(0, match.range.first).trimEnd()
    }

    // Extract the last <img src=(name)> sprite tag from message text
    fun getSpriteFile(spriteName: String): java.io.File? {
        val charName = _uiState.value.character?.name ?: return null
        return spriteStorage.getFile(charName, spriteName)
    }
}
