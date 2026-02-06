package com.stark.sillytavern.ui.screens.chat

import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stark.sillytavern.data.repository.BackgroundRepository
import com.stark.sillytavern.data.repository.ForgeRepository
import com.stark.sillytavern.data.repository.SillyTavernRepository
import com.stark.sillytavern.domain.model.Character
import com.stark.sillytavern.domain.model.ChatInfo
import com.stark.sillytavern.domain.model.ChatMessage
import com.stark.sillytavern.domain.model.ChatMessageMetadata
import com.stark.sillytavern.domain.model.ForgeGenerationParams
import com.stark.sillytavern.domain.model.GenerationState
import com.stark.sillytavern.domain.model.Result
import com.stark.sillytavern.domain.model.StreamEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val currentSwipeIndex: Map<Int, Int> = emptyMap(),  // Current swipe shown for each message
    // Chat background
    val backgroundPath: String? = null,
    // Greeting selection for new chat
    val showGreetingPicker: Boolean = false,
    val availableGreetings: List<String> = emptyList()
)

enum class ImageGenType {
    BACKGROUND,  // Generate based on message content/scene
    CHARACTER    // Generate based on character description
}

@HiltViewModel
class ChatViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: SillyTavernRepository,
    private val forgeRepository: ForgeRepository,
    private val backgroundRepository: BackgroundRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    // Track current generation job for cancellation
    private var generationJob: Job? = null

    fun loadCharacter(avatarUrl: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            // Load current API info for status display
            loadApiInfo()

            // Get character details
            when (val result = repository.getCharacter(avatarUrl)) {
                is Result.Success -> {
                    val character = result.data
                    val avatarUrlBuilt = repository.buildAvatarUrl(character.avatar)

                    // Load background if exists
                    val bgPath = backgroundRepository.getBackgroundPath(avatarUrl)

                    _uiState.update {
                        it.copy(
                            character = character,
                            characterAvatarUrl = avatarUrlBuilt,
                            backgroundPath = bgPath
                        )
                    }

                    // Load existing chats
                    loadChats(character, avatarUrl)
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = result.exception.message
                        )
                    }
                }
            }
        }
    }

    private fun loadApiInfo() {
        val (apiName, modelName) = repository.getCurrentApiInfo()
        _uiState.update {
            it.copy(
                currentApiName = apiName,
                currentModelName = modelName
            )
        }
    }

    private var currentAvatarUrl: String = ""

    private suspend fun loadChats(character: Character, avatarUrl: String) {
        currentAvatarUrl = avatarUrl
        when (val chatsResult = repository.getCharacterChats(avatarUrl)) {
            is Result.Success -> {
                val chats = chatsResult.data
                _uiState.update { it.copy(availableChats = chats) }
                if (chats.isNotEmpty()) {
                    // Load most recent chat
                    val fileName = chats.first().fileName
                    loadExistingChat(character, avatarUrl, fileName)
                } else {
                    // Create new chat with greeting
                    createNewChat()
                }
            }
            is Result.Error -> {
                // No existing chats, create new
                createNewChat()
            }
        }
    }

    fun refreshChatsList() {
        val character = _uiState.value.character ?: return
        viewModelScope.launch {
            when (val chatsResult = repository.getCharacterChats(currentAvatarUrl)) {
                is Result.Success -> {
                    _uiState.update { it.copy(availableChats = chatsResult.data) }
                }
                is Result.Error -> { /* ignore */ }
            }
        }
    }

    /**
     * Reload the character data from the server.
     * Call this after returning from Character Settings to get updated depth prompt, system prompt, etc.
     */
    fun reloadCharacter() {
        if (currentAvatarUrl.isBlank()) return
        viewModelScope.launch {
            when (val result = repository.getCharacter(currentAvatarUrl)) {
                is Result.Success -> {
                    _uiState.update { it.copy(character = result.data) }
                }
                is Result.Error -> { /* ignore, keep existing character */ }
            }
        }
    }

    fun selectChat(fileName: String) {
        val character = _uiState.value.character ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, showChatSelector = false) }
            loadExistingChat(character, currentAvatarUrl, fileName)
        }
    }

    fun showChatSelector() {
        refreshChatsList()
        _uiState.update { it.copy(showChatSelector = true) }
    }

    fun dismissChatSelector() {
        _uiState.update { it.copy(showChatSelector = false) }
    }

    private suspend fun loadExistingChat(character: Character, avatarUrl: String, fileName: String) {
        when (val result = repository.getChat(character.name, avatarUrl, fileName)) {
            is Result.Success -> {
                _uiState.update {
                    it.copy(
                        messages = result.data,
                        currentChatFileName = fileName,
                        isLoading = false
                    )
                }
            }
            is Result.Error -> {
                createNewChat()
            }
        }
    }

    fun createNewChat() {
        val character = _uiState.value.character ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(showChatSelector = false) }

            // Check if character has alternate greetings
            val allGreetings = buildList {
                if (character.firstMessage.isNotBlank()) {
                    add(character.firstMessage)
                }
                addAll(character.alternateGreetings.filter { it.isNotBlank() })
            }

            if (allGreetings.size > 1) {
                // Show greeting picker
                _uiState.update {
                    it.copy(
                        showGreetingPicker = true,
                        availableGreetings = allGreetings,
                        isLoading = false
                    )
                }
            } else {
                // Only one or no greeting, proceed directly
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
            val fileName = repository.generateChatFileName(character.name)

            val messages = if (!greeting.isNullOrBlank()) {
                listOf(ChatMessage(content = greeting, isUser = false))
            } else {
                emptyList()
            }

            _uiState.update {
                it.copy(
                    messages = messages,
                    currentChatFileName = fileName,
                    isLoading = false
                )
            }

            // Save the initial chat if there's a greeting
            if (messages.isNotEmpty()) {
                saveCurrentChat()
                refreshChatsList()
            }
        }
    }

    fun updateInput(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun sendMessage() {
        val character = _uiState.value.character ?: return
        val message = _uiState.value.inputText.trim()
        if (message.isBlank()) return

        // Add user message immediately
        val userMessage = ChatMessage(content = message, isUser = true)
        _uiState.update {
            it.copy(
                messages = it.messages + userMessage,
                inputText = "",
                isGenerating = true,
                streamingContent = ""
            )
        }

        // Generate chat file name if needed
        if (_uiState.value.currentChatFileName == null) {
            val fileName = repository.generateChatFileName(character.name)
            _uiState.update { it.copy(currentChatFileName = fileName) }
        }

        // Start generation with tracked job
        generateResponse(character, message, _uiState.value.messages.dropLast(1))
    }

    private fun generateResponse(character: Character, userMessage: String, history: List<ChatMessage>) {
        generationJob = viewModelScope.launch {
            repository.sendMessageStreaming(userMessage, character, history)
                .collect { event ->
                    when (event) {
                        is StreamEvent.Token -> {
                            _uiState.update {
                                it.copy(streamingContent = event.accumulated)
                            }
                        }
                        is StreamEvent.Complete -> {
                            val assistantMessage = ChatMessage(
                                content = event.fullText,
                                isUser = false
                            )
                            _uiState.update {
                                it.copy(
                                    messages = it.messages + assistantMessage,
                                    isGenerating = false,
                                    streamingContent = ""
                                )
                            }
                            generationJob = null
                            saveCurrentChat()
                        }
                        is StreamEvent.Error -> {
                            _uiState.update {
                                it.copy(
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

    fun stopGeneration() {
        // Cancel the coroutine job
        generationJob?.cancel()
        generationJob = null

        // Send abort request to the server
        viewModelScope.launch {
            repository.abortGeneration()
        }

        // If there was streaming content, save it as the response
        val streamingContent = _uiState.value.streamingContent
        if (streamingContent.isNotBlank()) {
            val assistantMessage = ChatMessage(
                content = streamingContent,
                isUser = false
            )
            _uiState.update {
                it.copy(
                    messages = it.messages + assistantMessage,
                    isGenerating = false,
                    streamingContent = ""
                )
            }
            viewModelScope.launch { saveCurrentChat() }
        } else {
            _uiState.update {
                it.copy(
                    isGenerating = false,
                    streamingContent = ""
                )
            }
        }
    }

    // Message action methods
    fun showMessageActions(messageIndex: Int) {
        _uiState.update {
            it.copy(
                selectedMessageIndex = messageIndex,
                showMessageActions = true
            )
        }
    }

    fun dismissMessageActions() {
        _uiState.update {
            it.copy(
                selectedMessageIndex = null,
                showMessageActions = false
            )
        }
    }

    fun deleteMessage(index: Int) {
        val messages = _uiState.value.messages.toMutableList()
        if (index in messages.indices) {
            messages.removeAt(index)
            _uiState.update {
                it.copy(
                    messages = messages,
                    showMessageActions = false,
                    selectedMessageIndex = null
                )
            }
            viewModelScope.launch { saveCurrentChat() }
        }
    }

    fun regenerateResponse() {
        val messages = _uiState.value.messages
        val character = _uiState.value.character ?: return

        // Find the last assistant message and remove it
        val lastAssistantIndex = messages.indexOfLast { !it.isUser }
        if (lastAssistantIndex == -1) return

        // Find the user message before it
        val userMessageIndex = (lastAssistantIndex - 1 downTo 0).firstOrNull { messages[it].isUser }
        if (userMessageIndex == null) return

        val userMessage = messages[userMessageIndex].content
        val history = messages.subList(0, userMessageIndex)

        // Remove the last assistant message
        _uiState.update {
            it.copy(
                messages = messages.subList(0, lastAssistantIndex),
                isGenerating = true,
                streamingContent = ""
            )
        }

        // Regenerate
        generateResponse(character, userMessage, history)
    }

    // ========== Image Generation ==========

    fun showImageGenerationDialog(messageIndex: Int) {
        val message = _uiState.value.messages.getOrNull(messageIndex) ?: return
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
        // Generate prompt preview based on type
        generatePromptPreview(type)
    }

    private fun generatePromptPreview(type: ImageGenType) {
        val character = _uiState.value.character ?: return
        val messageIndex = _uiState.value.selectedMessageIndex ?: return
        val message = _uiState.value.messages.getOrNull(messageIndex) ?: return

        val prompt = when (type) {
            ImageGenType.CHARACTER -> {
                // Use character description for character portrait
                buildCharacterPrompt(character)
            }
            ImageGenType.BACKGROUND -> {
                // Extract scene/setting from the message
                buildBackgroundPrompt(message.content)
            }
        }

        _uiState.update { it.copy(imagePromptPreview = prompt) }
    }

    private fun buildCharacterPrompt(character: Character): String {
        val parts = mutableListOf<String>()

        // Start with a quality prompt
        parts.add("masterpiece, best quality, highly detailed")

        // Add character name context
        parts.add("portrait of ${character.name}")

        // Extract appearance details from description
        val description = character.description
        if (description.isNotBlank()) {
            // Take first 200 chars of description for appearance
            val shortened = description.take(200)
            parts.add(shortened)
        }

        // Add personality hints if available
        if (character.personality.isNotBlank()) {
            parts.add(character.personality.take(100))
        }

        return parts.joinToString(", ")
    }

    private fun buildBackgroundPrompt(messageContent: String): String {
        // Extract keywords/scene from the message
        // Simple approach: look for descriptive words
        val parts = mutableListOf<String>()

        parts.add("masterpiece, best quality, highly detailed, scenic")
        parts.add("background, environment, landscape")

        // Take relevant parts of the message (skip dialogue, focus on descriptions)
        val descriptionParts = messageContent
            .replace(Regex("\"[^\"]*\""), "") // Remove quoted dialogue
            .replace(Regex("\\*[^*]*\\*"), "") // Keep action descriptions but clean
            .take(300)

        if (descriptionParts.isNotBlank()) {
            parts.add(descriptionParts.trim())
        }

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
            _uiState.update {
                it.copy(
                    imageGenState = GenerationState.Idle
                )
            }
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
                        // Use MediaStore for Android 10+
                        val contentValues = ContentValues().apply {
                            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/SillyTavern")
                        }

                        val uri = context.contentResolver.insert(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            contentValues
                        ) ?: throw Exception("Failed to create media entry")

                        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, outputStream)
                        } ?: throw Exception("Failed to open output stream")
                    } else {
                        // Legacy storage for older Android versions
                        val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                        val sillyTavernDir = File(picturesDir, "SillyTavern")
                        if (!sillyTavernDir.exists()) {
                            sillyTavernDir.mkdirs()
                        }
                        val file = File(sillyTavernDir, filename)
                        FileOutputStream(file).use { outputStream ->
                            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, outputStream)
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
                _uiState.update {
                    it.copy(
                        backgroundPath = bgPath,
                        backgroundSetSuccess = true
                    )
                }
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
                _uiState.update {
                    it.copy(backgroundPath = bgPath)
                }
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
        val avatarUrl = character.avatar ?: character.name

        repository.saveChat(
            characterName = character.name,
            avatarUrl = avatarUrl,
            fileName = fileName,
            messages = _uiState.value.messages
        )
    }

    /**
     * Update the author's note for the current chat.
     * This updates the first message's chat metadata and saves the chat.
     */
    fun updateAuthorsNote(
        content: String,
        depth: Int = 4,
        interval: Int = 1,
        position: Int = 0,
        role: Int = 0
    ) {
        val messages = _uiState.value.messages.toMutableList()
        if (messages.isEmpty()) return

        // Update first message with new author's note metadata
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

        // Save immediately
        viewModelScope.launch {
            saveCurrentChat()
        }
    }

    /**
     * Get the current chat's author's note metadata.
     */
    fun getAuthorsNote(): ChatMessageMetadata? {
        return _uiState.value.messages.firstOrNull()?.chatMetadata
    }

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

            when (repository.deleteChat(
                characterName = character.name,
                avatarUrl = currentAvatarUrl,
                fileName = fileName
            )) {
                is Result.Success -> {
                    // Refresh chat list and load another chat
                    when (val chatsResult = repository.getCharacterChats(currentAvatarUrl)) {
                        is Result.Success -> {
                            val chats = chatsResult.data
                            _uiState.update { it.copy(availableChats = chats) }
                            if (chats.isNotEmpty()) {
                                // Load most recent remaining chat
                                loadExistingChat(character, currentAvatarUrl, chats.first().fileName)
                            } else {
                                // No chats left, create new
                                createNewChat()
                            }
                        }
                        is Result.Error -> {
                            createNewChat()
                        }
                    }
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Failed to delete chat"
                        )
                    }
                }
            }
        }
    }

    fun deleteCharacter() {
        viewModelScope.launch {
            when (repository.deleteCharacter(currentAvatarUrl)) {
                is Result.Success -> {
                    // Character deleted, navigation will handle going back
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(error = "Failed to delete character")
                    }
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
                it.copy(
                    messages = messages,
                    editingMessageIndex = null,
                    editingMessageText = ""
                )
            }
            viewModelScope.launch { saveCurrentChat() }
        }
    }

    fun cancelEditing() {
        _uiState.update {
            it.copy(
                editingMessageIndex = null,
                editingMessageText = ""
            )
        }
    }

    // ========== Continue Generation ==========

    fun continueGeneration() {
        val character = _uiState.value.character ?: return
        val messages = _uiState.value.messages
        if (messages.isEmpty()) return

        // Find the last assistant message
        val lastAssistantIndex = messages.indexOfLast { !it.isUser }
        if (lastAssistantIndex == -1) return

        val lastAssistantMessage = messages[lastAssistantIndex]

        // Set up for generation - we'll append to the existing message
        _uiState.update {
            it.copy(
                isGenerating = true,
                streamingContent = lastAssistantMessage.content // Start with existing content
            )
        }

        // Find the last user message before this assistant response
        val userMessageIndex = (lastAssistantIndex - 1 downTo 0).firstOrNull { messages[it].isUser }
        val userMessage = userMessageIndex?.let { messages[it].content } ?: ""
        val history = messages.subList(0, lastAssistantIndex)

        // Generate continuation
        generationJob = viewModelScope.launch {
            // Remove the last assistant message temporarily - we'll replace it with the extended version
            val messagesWithoutLast = messages.subList(0, lastAssistantIndex)

            repository.sendMessageStreaming(userMessage, character, messagesWithoutLast.toList())
                .collect { event ->
                    when (event) {
                        is StreamEvent.Token -> {
                            // Append to existing content
                            val continued = lastAssistantMessage.content + event.accumulated
                            _uiState.update {
                                it.copy(streamingContent = continued)
                            }
                        }
                        is StreamEvent.Complete -> {
                            // Update the message with the continued content
                            val fullContent = lastAssistantMessage.content + event.fullText
                            val updatedMessages = messages.toMutableList()
                            updatedMessages[lastAssistantIndex] = lastAssistantMessage.copy(content = fullContent)

                            _uiState.update {
                                it.copy(
                                    messages = updatedMessages,
                                    isGenerating = false,
                                    streamingContent = ""
                                )
                            }
                            generationJob = null
                            saveCurrentChat()
                        }
                        is StreamEvent.Error -> {
                            _uiState.update {
                                it.copy(
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

    // ========== Swipes (Alternate Responses) ==========

    fun swipeLeft(messageIndex: Int) {
        val swipes = _uiState.value.messageSwipes[messageIndex] ?: return
        val currentIndex = _uiState.value.currentSwipeIndex[messageIndex] ?: 0

        if (currentIndex > 0) {
            val newIndex = currentIndex - 1
            applySwipe(messageIndex, newIndex, swipes)
        }
    }

    fun swipeRight(messageIndex: Int) {
        val swipes = _uiState.value.messageSwipes[messageIndex] ?: return
        val currentIndex = _uiState.value.currentSwipeIndex[messageIndex] ?: 0

        if (currentIndex < swipes.size - 1) {
            val newIndex = currentIndex + 1
            applySwipe(messageIndex, newIndex, swipes)
        }
    }

    private fun applySwipe(messageIndex: Int, swipeIndex: Int, swipes: List<String>) {
        val messages = _uiState.value.messages.toMutableList()
        if (messageIndex in messages.indices) {
            messages[messageIndex] = messages[messageIndex].copy(content = swipes[swipeIndex])

            val newSwipeIndex = _uiState.value.currentSwipeIndex.toMutableMap()
            newSwipeIndex[messageIndex] = swipeIndex

            _uiState.update {
                it.copy(
                    messages = messages,
                    currentSwipeIndex = newSwipeIndex
                )
            }
            viewModelScope.launch { saveCurrentChat() }
        }
    }

    fun regenerateWithSwipe() {
        val character = _uiState.value.character ?: return
        val messages = _uiState.value.messages

        // Find the last assistant message
        val lastAssistantIndex = messages.indexOfLast { !it.isUser }
        if (lastAssistantIndex == -1) return

        val currentMessage = messages[lastAssistantIndex]

        // Save current content as a swipe option
        val existingSwipes = _uiState.value.messageSwipes[lastAssistantIndex]?.toMutableList()
            ?: mutableListOf(currentMessage.content)

        // Make sure current content is saved if this is first swipe
        if (existingSwipes.isEmpty() || existingSwipes.last() != currentMessage.content) {
            existingSwipes.add(currentMessage.content)
        }

        // Find the user message
        val userMessageIndex = (lastAssistantIndex - 1 downTo 0).firstOrNull { messages[it].isUser }
        if (userMessageIndex == null) return

        val userMessage = messages[userMessageIndex].content
        val history = messages.subList(0, userMessageIndex)

        // Remove the last assistant message for generation
        _uiState.update {
            it.copy(
                messages = messages.subList(0, lastAssistantIndex),
                isGenerating = true,
                streamingContent = ""
            )
        }

        // Generate new response
        generationJob = viewModelScope.launch {
            repository.sendMessageStreaming(userMessage, character, history.toList())
                .collect { event ->
                    when (event) {
                        is StreamEvent.Token -> {
                            _uiState.update {
                                it.copy(streamingContent = event.accumulated)
                            }
                        }
                        is StreamEvent.Complete -> {
                            val newContent = event.fullText
                            val assistantMessage = ChatMessage(
                                content = newContent,
                                isUser = false
                            )

                            // Add new content to swipes
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
                            // Restore the original message on error
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
