package com.stark.sillytavern.ui.screens.chat

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
import com.stark.sillytavern.ui.components.*
import com.stark.sillytavern.domain.model.GenerationState
import com.stark.sillytavern.ui.theme.AccentGreen
import com.stark.sillytavern.ui.theme.DarkSurface
import com.stark.sillytavern.ui.theme.DarkSurfaceVariant
import com.stark.sillytavern.ui.theme.ErrorRed
import com.stark.sillytavern.ui.theme.TextPrimary
import com.stark.sillytavern.ui.theme.TextSecondary
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.asImageBitmap
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
                                text = { Text("Delete Chat") },
                                onClick = {
                                    showSettingsMenu = false
                                    viewModel.showDeleteDialog()
                                },
                                leadingIcon = { Icon(Icons.Default.Delete, null, tint = ErrorRed) }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete Character") },
                                onClick = {
                                    showSettingsMenu = false
                                    showDeleteCharacterDialog = true
                                },
                                leadingIcon = { Icon(Icons.Default.Delete, null, tint = ErrorRed) }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkSurface
                )
            )
        },
        bottomBar = {
            Column {
                // API indicator bar
                if (uiState.currentApiName.isNotBlank()) {
                    Surface(
                        color = DarkSurfaceVariant,
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
                                color = AccentGreen
                            )
                            if (uiState.currentModelName.isNotBlank()) {
                                Text(
                                    text = " \u2022 ",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                                Text(
                                    text = uiState.currentModelName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary,
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
                            colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
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
                            MessageWithActions(
                                message = message,
                                characterName = uiState.character?.name ?: "Assistant",
                                swipeInfo = swipeInfo,
                                onLongPress = { viewModel.showMessageActions(index) },
                                onSwipeLeft = { viewModel.swipeLeft(index) },
                                onSwipeRight = { viewModel.swipeRight(index) }
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
}

@Composable
private fun ChatSelectorDialog(
    chats: List<com.stark.sillytavern.domain.model.ChatInfo>,
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
    message: com.stark.sillytavern.domain.model.ChatMessage,
    characterName: String,
    swipeInfo: Pair<Int, Int>?,
    onLongPress: () -> Unit,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit
) {
    Column {
        Box(
            modifier = Modifier.combinedClickable(
                onClick = { },
                onLongClick = onLongPress
            )
        ) {
            ChatBubble(
                message = message,
                characterName = characterName
            )
        }

        // Show swipe indicator for assistant messages with multiple swipes
        if (!message.isUser && swipeInfo != null && swipeInfo.second > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, top = 4.dp),
                horizontalArrangement = Arrangement.Start
            ) {
                SwipeIndicator(
                    currentSwipe = swipeInfo.first,
                    totalSwipes = swipeInfo.second,
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
                Text("Image Type", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
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
                Text("Prompt", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
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
                                        .border(1.dp, DarkSurfaceVariant, RoundedCornerShape(8.dp)),
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
                                            tint = AccentGreen,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            "Saved to Pictures/SillyTavern",
                                            color = AccentGreen,
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
                                            tint = AccentGreen,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            "Set as chat background",
                                            color = AccentGreen,
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
                                Text("Failed to display image", color = ErrorRed)
                            }
                        }
                    }
                    is GenerationState.Error -> {
                        Text(
                            "Error: ${generationState.message}",
                            color = ErrorRed,
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
                        tint = ErrorRed,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Delete Message", color = ErrorRed)
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
                            tint = AccentGreen,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Regenerate", color = AccentGreen)
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
                tint = if (currentSwipe > 1) TextPrimary else TextSecondary.copy(alpha = 0.3f)
            )
        }
        Text(
            text = "$currentSwipe/$totalSwipes",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
        IconButton(
            onClick = onSwipeRight,
            enabled = currentSwipe < totalSwipes,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Next",
                modifier = Modifier.size(18.dp),
                tint = if (currentSwipe < totalSwipes) TextPrimary else TextSecondary.copy(alpha = 0.3f)
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
                            containerColor = DarkSurfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                text = if (index == 0) "Default Greeting" else "Alternate ${index}",
                                style = MaterialTheme.typography.labelMedium,
                                color = AccentGreen
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = greeting.take(200) + if (greeting.length > 200) "..." else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
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
