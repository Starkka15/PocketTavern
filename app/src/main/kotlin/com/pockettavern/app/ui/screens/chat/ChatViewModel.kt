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
import com.pockettavern.app.data.repository.BackgroundRepository
import com.pockettavern.app.data.repository.ForgeRepository
import com.pockettavern.app.data.repository.LocalRepository
import com.pockettavern.app.data.repository.LlmRepository
import com.pockettavern.app.extensions.ExtensionEvent
import com.pockettavern.app.extensions.ExtensionManager
import com.pockettavern.app.domain.model.ApiConfiguration
import com.pockettavern.app.domain.model.Chat
import com.pockettavern.app.domain.model.Character
import com.pockettavern.app.domain.model.ChatInfo
import com.pockettavern.app.domain.model.ChatMessage
import com.pockettavern.app.domain.model.ChatMessageMetadata
import com.pockettavern.app.domain.model.ForgeGenerationParams
import com.pockettavern.app.domain.model.GenerationState
import com.pockettavern.app.domain.model.QuickReplyButton
import com.pockettavern.app.domain.model.Result
import com.pockettavern.app.domain.model.StreamEvent
import com.pockettavern.app.domain.prompt.PromptBuilder
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
    // Image generation state
    val showImageGenDialog: Boolean = false,
    val imageGenType: ImageGenType = ImageGenType.BACKGROUND,
    val imageGenState: GenerationState = GenerationState.Idle,
    val generatedImageBase64: String? = null,
    val imagePromptPreview: String = "",
    val imageSaved: Boolean = false,
    val backgroundSetSuccess: Boolean = false,
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
    // Quick reply buttons from enabled presets
    val quickReplyButtons: List<QuickReplyButton> = emptyList(),
    // Token counter (shown when extension is enabled)
    val tokenCount: Int = 0,
    val showTokenCount: Boolean = false
)

enum class ImageGenType {
    BACKGROUND,
    CHARACTER
}

@HiltViewModel
class ChatViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val localRepository: LocalRepository,
    private val llmRepository: LlmRepository,
    private val forgeRepository: ForgeRepository,
    private val backgroundRepository: BackgroundRepository,
    private val extensionManager: ExtensionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var generationJob: Job? = null

    // Auto-continue state
    private var autoContinueEnabled = false
    private var autoContinueMinLength = 200
    private var autoContinueCount = 0

    init {
        extensionManager.load()
        // Observe quick reply buttons
        viewModelScope.launch {
            extensionManager.quickReply.activeButtons.collect { buttons ->
                _uiState.update { it.copy(quickReplyButtons = buttons) }
            }
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
        // Collect quick reply auto-triggers
        viewModelScope.launch {
            extensionManager.quickReply.autoTriggerFlow.collect { button ->
                if (_uiState.value.character != null && !_uiState.value.isGenerating) {
                    sendQuickReply(button)
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
    }

    // Last known API config — updated when generation starts, used for abort
    @Volatile private var _currentConfig: ApiConfiguration = ApiConfiguration.DEFAULT

    // The PNG filename of the current character (e.g. "seraphina.png")
    private var currentAvatarUrl: String = ""

    fun loadCharacter(avatarUrl: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

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
                _uiState.update {
                    it.copy(
                        messages = result.data.messages,
                        currentChatFileName = fileName,
                        isLoading = false
                    )
                }
                extensionManager.emit(ExtensionEvent.CHAT_CHANGED, fileName)
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
            val fileName = localRepository.generateChatFileName(character.name)
            val messages = if (!greeting.isNullOrBlank()) {
                listOf(ChatMessage(content = greeting, isUser = false))
            } else emptyList()
            _uiState.update {
                it.copy(messages = messages, currentChatFileName = fileName, isLoading = false)
            }
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
        val text = button.message.trim()
        if (text.isBlank()) return
        sendMessageText(text)
    }

    fun insertNarratorMessage(text: String) {
        val narratorMessage = ChatMessage(content = text, isUser = false, isNarrator = true)
        _uiState.update { it.copy(messages = it.messages + narratorMessage) }
        viewModelScope.launch { saveCurrentChat() }
    }

    private fun sendMessageText(rawText: String) {
        val character = _uiState.value.character ?: return

        // /sys prefix — insert narrator message without sending to LLM
        if (rawText.startsWith("/sys ")) {
            val narratorText = rawText.removePrefix("/sys ").trim()
            if (narratorText.isNotBlank()) insertNarratorMessage(narratorText)
            _uiState.update { it.copy(inputText = "") }
            return
        }

        autoContinueCount = 0

        // Apply input regex rules
        val message = extensionManager.processInput(rawText)
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
                        val processed = extensionManager.processOutput(event.fullText)
                        val assistantMessage = ChatMessage(content = processed, isUser = false)
                        _uiState.update {
                            it.copy(
                                messages = it.messages + assistantMessage,
                                isGenerating = false,
                                streamingContent = ""
                            )
                        }
                        extensionManager.emit(ExtensionEvent.MESSAGE_RECEIVED, processed)
                        extensionManager.emit(ExtensionEvent.GENERATION_STOPPED)
                        generationJob = null
                        saveCurrentChat()
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
        val mainPromptItem = oaiPreset?.promptOrder?.find { it.id == "main_prompt" }
        val mainPromptOverride = if (config.usesChatCompletions && mainPromptItem?.enabled == true)
            mainPromptItem.content ?: "" else ""
        val builder = PromptBuilder(character, chatContext, userName, mainPromptOverride)
        val prompt = builder.buildPrompt(history, userMessage)

        // For chat completion APIs, also build structured messages for proper role formatting.
        val messages = if (config.usesChatCompletions) {
            val promptOrder = oaiPreset?.promptOrder ?: com.pockettavern.app.domain.model.OaiPromptOrderItem.defaultOrder()
            builder.buildChatCompletionMessages(history, userMessage, promptOrder)
        } else null

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
    }

    fun dismissMessageActions() {
        _uiState.update { it.copy(selectedMessageIndex = null, showMessageActions = false) }
    }

    fun deleteMessage(index: Int) {
        val messages = _uiState.value.messages.toMutableList()
        if (index in messages.indices) {
            messages.removeAt(index)
            _uiState.update {
                it.copy(messages = messages, showMessageActions = false, selectedMessageIndex = null)
            }
            viewModelScope.launch { saveCurrentChat() }
        }
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

    // ========== Image Generation ==========

    fun showImageGenerationDialog(messageIndex: Int) {
        _uiState.update {
            it.copy(
                showMessageActions = false,
                showImageGenDialog = true,
                selectedMessageIndex = messageIndex,
                imageGenState = GenerationState.Idle,
                generatedImageBase64 = null
            )
        }
    }

    fun selectImageGenType(type: ImageGenType) {
        _uiState.update { it.copy(imageGenType = type) }
        generatePromptPreview(type)
    }

    private fun generatePromptPreview(type: ImageGenType) {
        val character = _uiState.value.character ?: return
        val messageIndex = _uiState.value.selectedMessageIndex ?: return
        val message = _uiState.value.messages.getOrNull(messageIndex) ?: return

        val prompt = when (type) {
            ImageGenType.CHARACTER -> buildCharacterPrompt(character)
            ImageGenType.BACKGROUND -> buildBackgroundPrompt(message.content)
        }
        _uiState.update { it.copy(imagePromptPreview = prompt) }
    }

    private fun buildCharacterPrompt(character: Character): String {
        val parts = mutableListOf("masterpiece, best quality, highly detailed", "portrait of ${character.name}")
        if (character.description.isNotBlank()) parts.add(character.description.take(200))
        if (character.personality.isNotBlank()) parts.add(character.personality.take(100))
        return parts.joinToString(", ")
    }

    private fun buildBackgroundPrompt(messageContent: String): String {
        val parts = mutableListOf("masterpiece, best quality, highly detailed, scenic", "background, environment, landscape")
        val desc = messageContent
            .replace(Regex("\"[^\"]*\""), "")
            .replace(Regex("\\*[^*]*\\*"), "")
            .take(300)
        if (desc.isNotBlank()) parts.add(desc.trim())
        return parts.joinToString(", ")
    }

    fun updateImagePrompt(prompt: String) {
        _uiState.update { it.copy(imagePromptPreview = prompt) }
    }

    fun startImageGeneration() {
        val prompt = _uiState.value.imagePromptPreview
        if (prompt.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(imageGenState = GenerationState.Starting) }

            val params = ForgeGenerationParams(
                prompt = prompt,
                negativePrompt = "blurry, low quality, distorted, deformed, bad anatomy, worst quality, watermark, text",
                width = if (_uiState.value.imageGenType == ImageGenType.CHARACTER) 512 else 768,
                height = if (_uiState.value.imageGenType == ImageGenType.CHARACTER) 768 else 512,
                steps = 20,
                cfgScale = 7f
            )

            forgeRepository.generateImageWithProgress(params).collect { state ->
                _uiState.update { it.copy(imageGenState = state) }
                if (state is GenerationState.Complete) {
                    _uiState.update { it.copy(generatedImageBase64 = state.imageBase64) }
                }
            }
        }
    }

    fun cancelImageGeneration() {
        viewModelScope.launch {
            forgeRepository.interrupt()
            _uiState.update { it.copy(imageGenState = GenerationState.Idle) }
        }
    }

    fun saveGeneratedImage() {
        val base64 = _uiState.value.generatedImageBase64 ?: return
        val characterName = _uiState.value.character?.name ?: "Generated"
        val imageType = _uiState.value.imageGenType.name.lowercase()

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val imageBytes = Base64.decode(base64, Base64.DEFAULT)
                    val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                        ?: throw Exception("Failed to decode image")
                    val filename = "${characterName}_${imageType}_${System.currentTimeMillis()}.png"

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

    fun setGeneratedImageAsBackground() {
        val base64 = _uiState.value.generatedImageBase64 ?: return
        viewModelScope.launch {
            val success = backgroundRepository.saveBackgroundFromBase64(currentAvatarUrl, base64)
            if (success) {
                val bgPath = backgroundRepository.getBackgroundPath(currentAvatarUrl)
                _uiState.update { it.copy(backgroundPath = bgPath, backgroundSetSuccess = true) }
            } else {
                _uiState.update { it.copy(error = "Failed to set background") }
            }
        }
    }

    fun clearBackgroundSetSuccess() {
        _uiState.update { it.copy(backgroundSetSuccess = false) }
    }

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

    fun dismissImageGenDialog() {
        _uiState.update {
            it.copy(
                showImageGenDialog = false,
                imageGenState = GenerationState.Idle,
                generatedImageBase64 = null,
                imagePromptPreview = "",
                imageSaved = false
            )
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

    fun clearError() {
        _uiState.update { it.copy(error = null) }
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

        val lastAssistantIndex = messages.indexOfLast { !it.isUser }
        if (lastAssistantIndex == -1) return
        val lastAssistantMessage = messages[lastAssistantIndex]

        _uiState.update {
            it.copy(isGenerating = true, streamingContent = lastAssistantMessage.content)
        }

        val userMessageIndex = (lastAssistantIndex - 1 downTo 0).firstOrNull { messages[it].isUser }
        val userMessage = userMessageIndex?.let { messages[it].content } ?: ""
        val historyWithoutLast = messages.subList(0, lastAssistantIndex).toList()

        generationJob = viewModelScope.launch {
            doGenerate(historyWithoutLast, userMessage).collect { event ->
                when (event) {
                    is StreamEvent.Token -> {
                        val continued = lastAssistantMessage.content + event.accumulated
                        _uiState.update { it.copy(streamingContent = continued) }
                    }
                    is StreamEvent.Complete -> {
                        val processed = extensionManager.processOutput(event.fullText)
                        val fullContent = lastAssistantMessage.content + processed
                        val updatedMessages = messages.toMutableList()
                        updatedMessages[lastAssistantIndex] = lastAssistantMessage.copy(content = fullContent)
                        _uiState.update {
                            it.copy(messages = updatedMessages, isGenerating = false, streamingContent = "")
                        }
                        extensionManager.emit(ExtensionEvent.GENERATION_STOPPED)
                        generationJob = null
                        saveCurrentChat()
                        // Auto-continue: check new tokens added in this continuation
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
                        val newContent = extensionManager.processOutput(event.fullText)
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
}
