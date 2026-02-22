package com.pockettavern.app.ui.screens.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pockettavern.app.domain.model.MessageHeaderEntry
import com.pockettavern.app.extensions.JsExtensionHost
import com.pockettavern.app.ui.components.*
import com.pockettavern.app.domain.model.GenerationState
import com.pockettavern.app.domain.model.QuickReplyButton
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    characterAvatar: String,
    onBack: () -> Unit,
    onNavigateToEditCharacter: (String) -> Unit = {},
    onNavigateToCharacterSettings: (String) -> Unit = {},
    onNavigateToDebugLog: () -> Unit = {},
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var showSettingsMenu by remember { mutableStateOf(false) }
    var showDeleteCharacterDialog by remember { mutableStateOf(false) }

    // Image picker for background upload
    val backgroundPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.uploadBackgroundFromUri(it) }
    }

    // Load character on first composition
    LaunchedEffect(characterAvatar) {
        viewModel.loadCharacter(characterAvatar)
    }

    // Scroll to bottom when new messages arrive
    LaunchedEffect(uiState.messages.size) {
        val itemCount = uiState.messages.size + (if (uiState.isGenerating) 1 else 0)
        if (itemCount > 0) {
            // Use a large offset to scroll to the bottom of the last item
            listState.animateScrollToItem(itemCount - 1, scrollOffset = Int.MAX_VALUE)
        }
    }

    // Keep scrolled to bottom during streaming - scroll to bottom of the streaming message
    LaunchedEffect(uiState.streamingContent) {
        if (uiState.isGenerating && uiState.streamingContent.isNotEmpty()) {
            val itemCount = uiState.messages.size + 1
            // Use a large offset to always show the bottom of the streaming content
            listState.scrollToItem(itemCount - 1, scrollOffset = Int.MAX_VALUE)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        uiState.character?.let { char ->
                            CharacterAvatar(
                                imageUrl = uiState.characterAvatarUrl,
                                characterName = char.name,
                                size = 36.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(char.name)
                        } ?: Text("Loading...")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showSettingsMenu = true }) {
                            Icon(Icons.Default.MoreVert, "Settings")
                        }
                        DropdownMenu(
                            expanded = showSettingsMenu,
                            onDismissRequest = { showSettingsMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Chat History") },
                                onClick = {
                                    showSettingsMenu = false
                                    viewModel.showChatSelector()
                                },
                                leadingIcon = { Icon(Icons.Default.History, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("New Chat") },
                                onClick = {
                                    showSettingsMenu = false
                                    viewModel.createNewChat()
                                },
                                leadingIcon = { Icon(Icons.Default.Add, null) }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Character Settings") },
                                onClick = {
                                    showSettingsMenu = false
                                    onNavigateToCharacterSettings(characterAvatar)
                                },
                                leadingIcon = { Icon(Icons.Default.Tune, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Edit Character") },
                                onClick = {
                                    showSettingsMenu = false
                                    onNavigateToEditCharacter(characterAvatar)
                                },
                                leadingIcon = { Icon(Icons.Default.Edit, null) }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Upload Background") },
                                onClick = {
                                    showSettingsMenu = false
                                    backgroundPickerLauncher.launch("image/*")
                                },
                                leadingIcon = { Icon(Icons.Default.Image, null) }
                            )
                            if (uiState.backgroundPath != null) {
                                DropdownMenuItem(
                                    text = { Text("Clear Background") },
                                    onClick = {
                                        showSettingsMenu = false
                                        viewModel.clearBackground()
                                    },
                                    leadingIcon = { Icon(Icons.Default.Delete, null) }
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Debug Log") },
                                onClick = {
                                    showSettingsMenu = false
                                    onNavigateToDebugLog()
                                },
                                leadingIcon = { Icon(Icons.Default.Settings, null) }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Delete Chat") },
                                onClick = {
                                    showSettingsMenu = false
                                    viewModel.showDeleteDialog()
                                },
                                leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete Character") },
                                onClick = {
                                    showSettingsMenu = false
                                    showDeleteCharacterDialog = true
                                },
                                leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Column {
                // API indicator bar
                if (uiState.currentApiName.isNotBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = uiState.currentApiName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (uiState.currentModelName.isNotBlank()) {
                                Text(
                                    text = " \u2022 ",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = uiState.currentModelName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                // Show stop/regenerate/continue buttons when generating or after generation
                if (uiState.isGenerating) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Button(
                            onClick = { viewModel.stopGeneration() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Stop")
                        }
                    }
                } else if (uiState.messages.isNotEmpty() && uiState.messages.last().isUser.not()) {
                    // Show regenerate and continue buttons after AI response
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.regenerateWithSwipe() }
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Regenerate")
                        }
                        OutlinedButton(
                            onClick = { viewModel.continueGeneration() }
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Continue")
                        }
                    }
                }

                // Quick Reply buttons (shown when at least one preset is enabled)
                if (uiState.quickReplyButtons.isNotEmpty() && !uiState.isGenerating) {
                    QuickReplyBar(
                        buttons = uiState.quickReplyButtons,
                        onButtonClick = { viewModel.sendQuickReply(it) }
                    )
                }

                // Token counter chip above input (shown when extension is enabled)
                if (uiState.showTokenCount && uiState.inputText.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = "~${uiState.tokenCount} tokens",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                MessageInput(
                    value = uiState.inputText,
                    onValueChange = { viewModel.updateInput(it) },
                    onSend = { viewModel.sendMessage() },
                    enabled = !uiState.isGenerating && !uiState.isLoading && uiState.editingMessageIndex == null
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Background image layer
            uiState.backgroundPath?.let { path ->
                AsyncImage(
                    model = File(path),
                    contentDescription = "Chat background",
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(0.3f),
                    contentScale = ContentScale.Crop
                )
            }

            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                uiState.messages.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Start a conversation",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(
                            uiState.messages,
                            key = { _, msg -> msg.id }
                        ) { index, message ->
                            val swipeInfo = viewModel.getSwipeInfo(index)
                            val lastAsstIndex = uiState.messages.indexOfLast { !it.isUser }
                            val isLastAsstMsg = !message.isUser && index == lastAsstIndex
                            val msgHeaders = uiState.messageHeaders[index] ?: emptyList()
                            val visibleBtns = uiState.visibleHeaderButtons
                                .filter { it.first == index }
                                .map { it.second }
                                .toSet()
                            MessageWithActions(
                                message = message,
                                characterName = uiState.character?.name ?: "Assistant",
                                swipeInfo = swipeInfo,
                                isLastAssistantMessage = isLastAsstMsg,
                                headers = msgHeaders,
                                headerButtons = uiState.headerButtons,
                                visibleButtonExtensions = visibleBtns,
                                headerMenus = uiState.headerMenus,
                                onLongPress = { viewModel.showMessageActions(index) },
                                onHeaderLongPress = if (msgHeaders.isNotEmpty()) {
                                    { extensionId -> viewModel.onHeaderLongPressed(index, extensionId) }
                                } else null,
                                onHeaderActionClick = { action, label -> viewModel.onHeaderActionClicked(action, label) },
                                onSwipeLeft = { viewModel.swipeLeft(index) },
                                onSwipeRight = {
                                    val info = viewModel.getSwipeInfo(index)
                                    val atLastAlt = info == null || info.first >= info.second
                                    if (isLastAsstMsg && atLastAlt && !uiState.isGenerating) {
                                        viewModel.regenerateWithSwipe()
                                    } else {
                                        viewModel.swipeRight(index)
                                    }
                                }
                            )
                        }

                        // Show streaming content or typing indicator when generating
                        if (uiState.isGenerating) {
                            item {
                                if (uiState.streamingContent.isNotEmpty()) {
                                    // Show streaming response as it comes in
                                    StreamingChatBubble(
                                        content = uiState.streamingContent,
                                        characterName = uiState.character?.name ?: "Assistant"
                                    )
                                } else {
                                    // Initial typing indicator before first token
                                    Row(
                                        modifier = Modifier.padding(start = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "${uiState.character?.name ?: "Assistant"} is typing...",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Delete chat confirmation
    if (uiState.showDeleteDialog) {
        ConfirmDialog(
            title = "Delete Chat",
            message = "Delete this conversation? This cannot be undone.",
            confirmText = "Delete",
            onConfirm = { viewModel.deleteCurrentChat() },
            onDismiss = { viewModel.dismissDeleteDialog() },
            isDestructive = true
        )
    }

    // Delete character confirmation
    if (showDeleteCharacterDialog) {
        ConfirmDialog(
            title = "Delete Character",
            message = "Delete \"${uiState.character?.name}\" and all their chats? This cannot be undone.",
            confirmText = "Delete",
            onConfirm = {
                showDeleteCharacterDialog = false
                viewModel.deleteCharacter()
                onBack()
            },
            onDismiss = { showDeleteCharacterDialog = false },
            isDestructive = true
        )
    }

    // Error dialog
    uiState.error?.let { error ->
        ErrorDialog(
            message = error,
            onDismiss = { viewModel.clearError() }
        )
    }

    // Chat history selector
    if (uiState.showChatSelector) {
        ChatSelectorDialog(
            chats = uiState.availableChats,
            currentChatFileName = uiState.currentChatFileName,
            onSelectChat = { viewModel.selectChat(it) },
            onNewChat = { viewModel.createNewChat() },
            onDismiss = { viewModel.dismissChatSelector() }
        )
    }

    // Greeting picker for new chat
    if (uiState.showGreetingPicker) {
        GreetingPickerDialog(
            greetings = uiState.availableGreetings,
            onSelectGreeting = { viewModel.selectGreeting(it) },
            onDismiss = { viewModel.dismissGreetingPicker() }
        )
    }

    // Message actions menu
    uiState.selectedMessageIndex?.let { messageIndex ->
        if (uiState.showMessageActions) {
            val selectedMessage = uiState.messages.getOrNull(messageIndex)
            val isLastAssistantMessage = messageIndex == uiState.messages.indexOfLast { !it.isUser }

            MessageActionsDialog(
                isUserMessage = selectedMessage?.isUser == true,
                isLastAssistantMessage = isLastAssistantMessage,
                onEdit = {
                    viewModel.startEditingMessage(messageIndex)
                },
                onDelete = {
                    viewModel.deleteMessage(messageIndex)
                },
                onRegenerate = {
                    viewModel.dismissMessageActions()
                    viewModel.regenerateWithSwipe()
                },
                onGenerateImage = {
                    viewModel.showImageGenerationDialog(messageIndex)
                },
                onDismiss = { viewModel.dismissMessageActions() }
            )
        }
    }

    // Message edit dialog
    uiState.editingMessageIndex?.let { editIndex ->
        EditMessageDialog(
            messageText = uiState.editingMessageText,
            onTextChange = { viewModel.updateEditingText(it) },
            onSave = { viewModel.saveEditedMessage() },
            onDismiss = { viewModel.cancelEditing() }
        )
    }

    // Image generation dialog
    if (uiState.showImageGenDialog) {
        ImageGenerationDialog(
            imageGenType = uiState.imageGenType,
            promptPreview = uiState.imagePromptPreview,
            generationState = uiState.imageGenState,
            generatedImageBase64 = uiState.generatedImageBase64,
            imageSaved = uiState.imageSaved,
            backgroundSetSuccess = uiState.backgroundSetSuccess,
            onSelectType = { viewModel.selectImageGenType(it) },
            onUpdatePrompt = { viewModel.updateImagePrompt(it) },
            onGenerate = { viewModel.startImageGeneration() },
            onCancel = { viewModel.cancelImageGeneration() },
            onSave = { viewModel.saveGeneratedImage() },
            onSetAsBackground = { viewModel.setGeneratedImageAsBackground() },
            onDismiss = {
                viewModel.clearBackgroundSetSuccess()
                viewModel.dismissImageGenDialog()
            }
        )
    }

    // Extension edit dialog (PT.showEditDialog)
    uiState.editDialogRequest?.let { request ->
        ExtensionEditDialog(
            title = request.title,
            fields = request.fields,
            onSave = { results -> viewModel.submitEditDialog(results) },
            onDismiss = { viewModel.cancelEditDialog() }
        )
    }
}

@Composable
private fun ChatSelectorDialog(
    chats: List<com.pockettavern.app.domain.model.ChatInfo>,
    currentChatFileName: String?,
    onSelectChat: (String) -> Unit,
    onNewChat: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chat History") },
        text = {
            Column {
                if (chats.isEmpty()) {
                    Text(
                        text = "No chat history",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(chats) { chat ->
                            val isSelected = chat.fileName == currentChatFileName
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectChat(chat.fileName) },
                                color = if (isSelected)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.surface,
                                shape = MaterialTheme.shapes.small
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Text(
                                        text = formatChatFileName(chat.fileName),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isSelected)
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        else
                                            MaterialTheme.colorScheme.onSurface
                                    )
                                    chat.lastMessage?.let { lastMsg ->
                                        Text(
                                            text = lastMsg.take(50) + if (lastMsg.length > 50) "..." else "",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onNewChat) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("New Chat")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun formatChatFileName(fileName: String): String {
    // Format: "CharName - 2024-01-15@14h30m45s123ms" -> "Jan 15, 2024 2:30 PM"
    val regex = Regex(".*- (\\d{4})-(\\d{2})-(\\d{2})@(\\d{2})h(\\d{2})m.*")
    val match = regex.find(fileName)

    return if (match != null) {
        val (year, month, day, hour, minute) = match.destructured
        val monthName = when (month) {
            "01" -> "Jan"; "02" -> "Feb"; "03" -> "Mar"; "04" -> "Apr"
            "05" -> "May"; "06" -> "Jun"; "07" -> "Jul"; "08" -> "Aug"
            "09" -> "Sep"; "10" -> "Oct"; "11" -> "Nov"; "12" -> "Dec"
            else -> month
        }
        val hourInt = hour.toIntOrNull() ?: 0
        val amPm = if (hourInt >= 12) "PM" else "AM"
        val hour12 = when {
            hourInt == 0 -> 12
            hourInt > 12 -> hourInt - 12
            else -> hourInt
        }
        "$monthName ${day.toInt()}, $year $hour12:$minute $amPm"
    } else {
        fileName
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageWithActions(
    message: com.pockettavern.app.domain.model.ChatMessage,
    characterName: String,
    swipeInfo: Pair<Int, Int>?,
    isLastAssistantMessage: Boolean,
    headers: List<MessageHeaderEntry> = emptyList(),
    headerButtons: Map<String, List<JsExtensionHost.HeaderAction>> = emptyMap(),
    visibleButtonExtensions: Set<String> = emptySet(),
    headerMenus: Map<String, List<JsExtensionHost.HeaderAction>> = emptyMap(),
    onLongPress: () -> Unit,
    onHeaderLongPress: ((String) -> Unit)? = null,
    onHeaderActionClick: ((String, String) -> Unit)? = null,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit
) {
    Column {
        Box(
            modifier = Modifier
                .combinedClickable(
                    onClick = { },
                    onLongClick = onLongPress
                )
                .pointerInput(swipeInfo, isLastAssistantMessage) {
                    if (message.isUser) return@pointerInput
                    var accumulated = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { accumulated = 0f },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            accumulated += dragAmount
                            val threshold = 60.dp.toPx()
                            if (accumulated < -threshold) {
                                accumulated = 0f
                                onSwipeLeft()
                            } else if (accumulated > threshold) {
                                accumulated = 0f
                                onSwipeRight()
                            }
                        }
                    )
                }
        ) {
            ChatBubble(
                message = message,
                characterName = characterName,
                headers = headers,
                headerButtons = headerButtons,
                visibleButtonExtensions = visibleButtonExtensions,
                headerMenus = headerMenus,
                onHeaderLongPress = onHeaderLongPress,
                onHeaderActionClick = onHeaderActionClick
            )
        }

        // Show swipe indicator on the last assistant message (always) and on any
        // assistant message that already has multiple alternatives stored
        val showIndicator = !message.isUser &&
                (isLastAssistantMessage || (swipeInfo != null && swipeInfo.second > 1))
        if (showIndicator) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, top = 4.dp),
                horizontalArrangement = Arrangement.Start
            ) {
                val atLastAlt = swipeInfo == null || swipeInfo.first >= swipeInfo.second
                SwipeIndicator(
                    currentSwipe = swipeInfo?.first ?: 1,
                    totalSwipes = swipeInfo?.second ?: 1,
                    isAtLastAlt = atLastAlt,
                    onSwipeLeft = onSwipeLeft,
                    onSwipeRight = onSwipeRight
                )
            }
        }
    }
}

@Composable
private fun ImageGenerationDialog(
    imageGenType: ImageGenType,
    promptPreview: String,
    generationState: GenerationState,
    generatedImageBase64: String?,
    imageSaved: Boolean,
    backgroundSetSuccess: Boolean,
    onSelectType: (ImageGenType) -> Unit,
    onUpdatePrompt: (String) -> Unit,
    onGenerate: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    onSetAsBackground: () -> Unit,
    onDismiss: () -> Unit
) {
    val isGenerating = generationState is GenerationState.Starting ||
            generationState is GenerationState.InProgress

    AlertDialog(
        onDismissRequest = { if (!isGenerating) onDismiss() },
        title = { Text("Generate Image") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Type selector
                Text("Image Type", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = imageGenType == ImageGenType.BACKGROUND,
                        onClick = { onSelectType(ImageGenType.BACKGROUND) },
                        label = { Text("Background") },
                        enabled = !isGenerating
                    )
                    FilterChip(
                        selected = imageGenType == ImageGenType.CHARACTER,
                        onClick = { onSelectType(ImageGenType.CHARACTER) },
                        label = { Text("Character") },
                        enabled = !isGenerating
                    )
                }

                // Prompt preview/editor
                Text("Prompt", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = promptPreview,
                    onValueChange = onUpdatePrompt,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp, max = 150.dp),
                    enabled = !isGenerating,
                    maxLines = 6,
                    textStyle = MaterialTheme.typography.bodySmall
                )

                // Generation state display
                when (generationState) {
                    is GenerationState.Starting -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text("Starting generation...", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    is GenerationState.InProgress -> {
                        Column {
                            LinearProgressIndicator(
                                progress = { generationState.progress },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                "Generating... ${(generationState.progress * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    is GenerationState.Complete -> {
                        // Show generated image
                        generatedImageBase64?.let { base64 ->
                            val bitmap = remember(base64) {
                                try {
                                    val imageBytes = Base64.decode(base64, Base64.DEFAULT)
                                    BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                                } catch (e: Exception) {
                                    null
                                }
                            }
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Generated image",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 200.dp)
                                        .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Fit
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                // Save button
                                if (imageSaved) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            "Saved to Pictures/SillyTavern",
                                            color = MaterialTheme.colorScheme.primary,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                } else if (backgroundSetSuccess) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            "Set as chat background",
                                            color = MaterialTheme.colorScheme.primary,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                } else {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = onSave,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(
                                                Icons.Default.Save,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Save to Gallery")
                                        }
                                        OutlinedButton(
                                            onClick = onSetAsBackground,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(
                                                Icons.Default.Image,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Set as Chat Background")
                                        }
                                    }
                                }
                            } else {
                                Text("Failed to display image", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    is GenerationState.Error -> {
                        Text(
                            "Error: ${generationState.message}",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    else -> { /* Idle state, nothing to show */ }
                }
            }
        },
        confirmButton = {
            if (isGenerating) {
                TextButton(onClick = onCancel) {
                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Cancel")
                }
            } else if (generationState is GenerationState.Complete) {
                TextButton(onClick = onDismiss) {
                    Text("Done")
                }
            } else {
                TextButton(
                    onClick = onGenerate,
                    enabled = promptPreview.isNotBlank()
                ) {
                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Generate")
                }
            }
        },
        dismissButton = {
            if (!isGenerating && generationState !is GenerationState.Complete) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            } else if (generationState is GenerationState.Complete) {
                TextButton(onClick = onGenerate) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Regenerate")
                }
            }
        }
    )
}

@Composable
private fun MessageActionsDialog(
    isUserMessage: Boolean,
    isLastAssistantMessage: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRegenerate: () -> Unit,
    onGenerateImage: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Message Actions") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Edit - available for all messages
                TextButton(
                    onClick = onEdit,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Edit Message")
                    Spacer(modifier = Modifier.weight(1f))
                }

                // Delete - available for all messages
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Delete Message", color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.weight(1f))
                }

                // Regenerate - only for last assistant message
                if (!isUserMessage && isLastAssistantMessage) {
                    TextButton(
                        onClick = onRegenerate,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Regenerate", color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }

                // Generate Image - available for assistant messages
                if (!isUserMessage) {
                    TextButton(
                        onClick = onGenerateImage,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.Image,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Generate Image")
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun EditMessageDialog(
    messageText: String,
    onTextChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Message") },
        text = {
            OutlinedTextField(
                value = messageText,
                onValueChange = onTextChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 150.dp, max = 300.dp),
                maxLines = 15,
                textStyle = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(
                onClick = onSave,
                enabled = messageText.isNotBlank()
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun SwipeIndicator(
    currentSwipe: Int,
    totalSwipes: Int,
    isAtLastAlt: Boolean,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        IconButton(
            onClick = onSwipeLeft,
            enabled = currentSwipe > 1,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Previous",
                modifier = Modifier.size(18.dp),
                tint = if (currentSwipe > 1) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
            )
        }
        Text(
            text = "$currentSwipe/$totalSwipes",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        // Right button: navigates to next alt, or generates a new one when at the end
        IconButton(
            onClick = onSwipeRight,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                if (isAtLastAlt) Icons.Filled.Refresh
                else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = if (isAtLastAlt) "Generate new response" else "Next",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isAtLastAlt) 0.6f else 1f)
            )
        }
    }
}

@Composable
private fun GreetingPickerDialog(
    greetings: List<String>,
    onSelectGreeting: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Greeting") },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 400.dp)
            ) {
                itemsIndexed(greetings) { index, greeting ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectGreeting(greeting) },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                text = if (index == 0) "Default Greeting" else "Alternate ${index}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = greeting.take(200) + if (greeting.length > 200) "..." else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 4
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickReplyBar(
    buttons: List<QuickReplyButton>,
    onButtonClick: (QuickReplyButton) -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            buttons.forEach { button ->
                AssistChip(
                    onClick = { onButtonClick(button) },
                    label = {
                        Text(
                            text = button.label,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun ExtensionEditDialog(
    title: String,
    fields: List<JsExtensionHost.EditField>,
    onSave: (Map<String, String>) -> Unit,
    onDismiss: () -> Unit
) {
    val fieldValues = remember(fields) {
        mutableStateMapOf<String, String>().apply {
            fields.forEach { put(it.key, it.value) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                fields.forEach { field ->
                    OutlinedTextField(
                        value = fieldValues[field.key] ?: "",
                        onValueChange = { fieldValues[field.key] = it },
                        label = { Text(field.label) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(fieldValues.toMap()) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
