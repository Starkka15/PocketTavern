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
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
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
import com.pockettavern.app.domain.model.QuickReplyButton
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.speech.RecognizerIntent
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
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

    val exportChatLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/jsonl")
    ) { uri ->
        uri?.let { viewModel.exportCurrentChat(it) }
    }

    // STT launcher
    val sttLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull() ?: ""
            if (spoken.isNotBlank()) {
                val current = uiState.inputText
                viewModel.updateInput(if (current.isBlank()) spoken else "$current $spoken")
            }
        }
    }

    fun launchStt() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        }
        try { sttLauncher.launch(intent) } catch (_: Exception) {}
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

    // Scroll to current search result
    LaunchedEffect(uiState.currentSearchResultIndex, uiState.searchResults) {
        val results = uiState.searchResults
        if (results.isNotEmpty()) {
            val msgIndex = results[uiState.currentSearchResultIndex]
            listState.animateScrollToItem(msgIndex)
        }
    }

    // Track whether the user has intentionally scrolled away from the bottom.
    // Upward scroll during streaming disables auto-follow; scrolling back to the
    // bottom (or a new generation starting) re-enables it.
    var userScrolledAway by remember { mutableStateOf(false) }
    LaunchedEffect(listState) {
        var prevIndex = listState.firstVisibleItemIndex
        var prevOffset = listState.firstVisibleItemScrollOffset
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { pair ->
                val index = pair.first
                val offset = pair.second
                // Upward scroll can only come from the user — auto-scroll only goes downward
                val scrolledUp = index < prevIndex || (index == prevIndex && offset < prevOffset)
                if (scrolledUp && listState.isScrollInProgress) {
                    userScrolledAway = true
                }
                // Re-enable if last item's bottom is within the viewport
                val info = listState.layoutInfo
                val lastItem = info.visibleItemsInfo.lastOrNull()
                if (lastItem != null &&
                    lastItem.index >= info.totalItemsCount - 1 &&
                    lastItem.offset + lastItem.size <= info.viewportEndOffset + 4) {
                    userScrolledAway = false
                }
                prevIndex = index
                prevOffset = offset
            }
    }
    // Reset when a new generation starts (user sent a message)
    LaunchedEffect(uiState.isGenerating) {
        if (uiState.isGenerating) userScrolledAway = false
    }
    // Auto-follow during streaming unless the user has scrolled away
    LaunchedEffect(uiState.streamingContent) {
        if (uiState.isGenerating && uiState.streamingContent.isNotEmpty() && !userScrolledAway) {
            val itemCount = uiState.messages.size + 1
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
                            Column {
                                Text(char.name, style = MaterialTheme.typography.titleMedium)
                                uiState.linkedGroupName?.let { groupName ->
                                    Text(
                                        text = "Group: $groupName",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        } ?: Text("Loading...")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleSearch() }) {
                        Icon(
                            if (uiState.isSearching) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = if (uiState.isSearching) "Close search" else "Search messages"
                        )
                    }
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
                            DropdownMenuItem(
                                text = { Text("Export Chat") },
                                onClick = {
                                    showSettingsMenu = false
                                    val charName = uiState.character?.name ?: "chat"
                                    val chatFile = uiState.currentChatFileName ?: "chat.jsonl"
                                    exportChatLauncher.launch("$charName - $chatFile")
                                },
                                leadingIcon = { Icon(Icons.Default.Save, null) }
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
                            DropdownMenuItem(
                                text = { Text("Change Model") },
                                onClick = {
                                    showSettingsMenu = false
                                    viewModel.showModelPicker()
                                },
                                leadingIcon = { Icon(Icons.Default.AutoAwesome, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Image Gallery") },
                                onClick = {
                                    showSettingsMenu = false
                                    viewModel.showGallery()
                                },
                                leadingIcon = { Icon(Icons.Default.Collections, null) }
                            )
                            HorizontalDivider()
                            if (uiState.apiShowThoughtsEnabled) {
                                DropdownMenuItem(
                                    text = { Text(if (uiState.showReasoningBubbles) "Hide Reasoning" else "Show Reasoning") },
                                    onClick = {
                                        showSettingsMenu = false
                                        viewModel.toggleReasoningBubbles()
                                    },
                                    leadingIcon = { Icon(Icons.Default.AutoAwesome, null) }
                                )
                            }
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
                // Message search bar
                if (uiState.isSearching) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextField(
                                value = uiState.searchQuery,
                                onValueChange = { viewModel.updateSearchQuery(it) },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("Search messages...") },
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                                    unfocusedIndicatorColor = MaterialTheme.colorScheme.surfaceVariant
                                ),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = {})
                            )
                            if (uiState.searchResults.isNotEmpty()) {
                                Text(
                                    text = "${uiState.currentSearchResultIndex + 1}/${uiState.searchResults.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                IconButton(onClick = { viewModel.navigateSearchResult(-1) }) {
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous result")
                                }
                                IconButton(onClick = { viewModel.navigateSearchResult(1) }) {
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next result")
                                }
                            }
                        }
                    }
                }

                // Context window usage indicator
                if (uiState.contextUsedTokens > 0) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Context: ~${uiState.contextUsedTokens} tokens",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

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
                } else if (uiState.messages.isNotEmpty() && uiState.messages.last().isUser.not() && !uiState.messages.last().isNarrator) {
                    // Show regenerate and continue buttons after AI response (not after narrator inserts)
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
                    enabled = !uiState.isGenerating && !uiState.isLoading && uiState.editingMessageIndex == null,
                    onVoiceInput = { launchStt() }
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
                                },
                                getSpriteFile = { viewModel.getSpriteFile(it) },
                                showReasoning = uiState.showReasoningBubbles
                            )
                        }

                        // Show streaming content or typing indicator when generating
                        if (uiState.isGenerating) {
                            item {
                                Column {
                                    // Streaming thinking block (R1 reasoning)
                                    if (uiState.streamingThinking.isNotEmpty() && uiState.showReasoningBubbles) {
                                        StreamingThinkingBubble(content = uiState.streamingThinking)
                                    }
                                    if (uiState.streamingContent.isNotEmpty()) {
                                        StreamingChatBubble(
                                            content = uiState.streamingContent,
                                            characterName = uiState.character?.name ?: "Assistant"
                                        )
                                    } else if (uiState.streamingThinking.isEmpty()) {
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

    // Image gallery
    if (uiState.showModelPicker) {
        ModelPickerDialog(
            models = uiState.availableModels,
            currentModel = uiState.currentModelName,
            isLoading = uiState.modelPickerLoading,
            onSelect = { viewModel.applyModelChange(it) },
            onDismiss = { viewModel.dismissModelPicker() }
        )
    }

    if (uiState.showGallery) {
        ImageGalleryDialog(
            images = uiState.galleryImages,
            onSaveImage = { viewModel.saveGalleryImageToDevice(it) },
            onDeleteImage = { viewModel.deleteGalleryImage(it) },
            onDismiss = { viewModel.dismissGallery() }
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

    if (uiState.showScanloreDialog) {
        com.pockettavern.app.ui.components.ScanloreConfirmDialog(
            entries = uiState.scanloreEntries,
            isLoading = uiState.scanloreLoading,
            error = uiState.scanloreError,
            onConfirm = { viewModel.confirmScanlore(it) },
            onDismiss = { viewModel.dismissScanlore() }
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
                isImageMessage = selectedMessage?.imagePath != null,
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
                onDeleteFromHere = {
                    viewModel.deleteMessagesFromIndex(messageIndex)
                },
                onSaveImage = {
                    viewModel.saveImageMessageToGallery(messageIndex)
                    viewModel.dismissMessageActions()
                },
                onSpeakTts = {
                    viewModel.speakMessage(messageIndex)
                    viewModel.dismissMessageActions()
                },
                onStopTts = {
                    viewModel.stopTts()
                    viewModel.dismissMessageActions()
                },
                isTtsEnabled = uiState.isTtsEnabled,
                isTtsSpeaking = uiState.isTtsSpeaking,
                extensionActions = uiState.messageActions,
                onExtensionAction = { action, label ->
                    viewModel.onHeaderActionClicked(action, label)
                    viewModel.dismissMessageActions()
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
    onSwipeRight: () -> Unit,
    getSpriteFile: ((String) -> File?)? = null,
    showReasoning: Boolean = true
) {
    Column {
        Box(
            modifier = Modifier
                .pointerInput(swipeInfo, isLastAssistantMessage) {
                    if (message.isUser) return@pointerInput
                    val threshold = 60.dp.toPx()
                    val directionSlop = 10.dp.toPx()
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var accX = 0f
                        var accY = 0f
                        var locked = false   // true = horizontal lock confirmed
                        var cancelled = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) break
                            accX += change.position.x - change.previousPosition.x
                            accY += change.position.y - change.previousPosition.y
                            val absX = kotlin.math.abs(accX)
                            val absY = kotlin.math.abs(accY)
                            if (!locked) {
                                // Vertical motion dominates → cancel gesture
                                if (absY > directionSlop && absY >= absX) {
                                    cancelled = true; break
                                }
                                // Horizontal motion dominates → lock in
                                if (absX > directionSlop && absX > absY) {
                                    locked = true
                                }
                            }
                            if (locked) change.consume()
                        }
                        if (!cancelled && locked) {
                            val absX = kotlin.math.abs(accX)
                            val absY = kotlin.math.abs(accY)
                            if (absX > threshold && absX > absY) {
                                if (accX < 0) onSwipeLeft() else onSwipeRight()
                            }
                        }
                    }
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
                onHeaderActionClick = onHeaderActionClick,
                onBubbleLongPress = onLongPress,
                onImageAction = if (message.imagePath != null) onLongPress else null,
                getSpriteFile = getSpriteFile,
                showReasoning = showReasoning
            )
        }

        // Show swipe indicator on the last assistant message (always) and on any
        // assistant message that already has multiple alternatives stored
        val showIndicator = !message.isUser && !message.isNarrator &&
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
private fun MessageActionsDialog(
    isUserMessage: Boolean,
    isLastAssistantMessage: Boolean,
    isImageMessage: Boolean = false,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDeleteFromHere: () -> Unit,
    onRegenerate: () -> Unit,
    onSaveImage: () -> Unit = {},
    onSpeakTts: () -> Unit = {},
    onStopTts: () -> Unit = {},
    isTtsEnabled: Boolean = false,
    isTtsSpeaking: Boolean = false,
    extensionActions: List<com.pockettavern.app.extensions.JsExtensionHost.HeaderAction> = emptyList(),
    onExtensionAction: (String, String) -> Unit = { _, _ -> },
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isImageMessage) "Image Actions" else "Message Actions") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (isImageMessage) {
                    // Save Image to gallery
                    TextButton(
                        onClick = onSaveImage,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.Save,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Save Image")
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    // Delete image message
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
                        Text("Delete Image", color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.weight(1f))
                    }
                } else {
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

                // Delete from here - removes this message and all after it
                TextButton(
                    onClick = onDeleteFromHere,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.DeleteForever,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Delete From Here", color = MaterialTheme.colorScheme.error)
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

                // TTS - play/stop for any message when TTS is enabled
                if (isTtsEnabled) {
                    if (isTtsSpeaking) {
                        TextButton(
                            onClick = onStopTts,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Default.Stop,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Stop TTS", color = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    } else {
                        TextButton(
                            onClick = onSpeakTts,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Default.RecordVoiceOver,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Play TTS")
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
                // Extension actions from JS extensions via PT.registerMessageActions()
                if (extensionActions.isNotEmpty()) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    )
                    extensionActions.forEach { action ->
                        TextButton(
                            onClick = { onExtensionAction(action.action, action.label) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(action.label)
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
                } // end else (non-image messages)
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

@OptIn(ExperimentalMaterial3Api::class)
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
    val expandedDropdown = remember { mutableStateMapOf<String, Boolean>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                fields.forEach { field ->
                    if (field.type == "select" && field.options.isNotEmpty()) {
                        val isExpanded = expandedDropdown[field.key] ?: false
                        ExposedDropdownMenuBox(
                            expanded = isExpanded,
                            onExpandedChange = { expandedDropdown[field.key] = it },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = fieldValues[field.key] ?: field.options.firstOrNull() ?: "",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(field.label) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExpanded) },
                                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = isExpanded,
                                onDismissRequest = { expandedDropdown[field.key] = false }
                            ) {
                                field.options.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option) },
                                        onClick = {
                                            fieldValues[field.key] = option
                                            expandedDropdown[field.key] = false
                                        }
                                    )
                                }
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = fieldValues[field.key] ?: "",
                            onValueChange = { fieldValues[field.key] = it },
                            label = { Text(field.label) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
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

@Composable
private fun ModelPickerDialog(
    models: List<String>,
    currentModel: String,
    isLoading: Boolean,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change Model") },
        text = {
            if (isLoading) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (models.isEmpty()) {
                Text("No models available. Check API URL, API key, and Debug Log.")
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(models) { model ->
                        val isSelected = model == currentModel
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(model) }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { onSelect(model) }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = model,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ImageGalleryDialog(
    images: List<GalleryImage>,
    onSaveImage: (GalleryImage) -> Unit,
    onDeleteImage: (GalleryImage) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Image Gallery") },
        text = {
            if (images.isEmpty()) {
                Text("No images yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.heightIn(max = 500.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(images) { image ->
                        GalleryThumbnail(
                            image = image,
                            onSave = { onSaveImage(image) },
                            onDelete = { onDeleteImage(image) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GalleryThumbnail(
    image: GalleryImage,
    onSave: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val bitmap = remember(image.imagePath) {
        BitmapFactory.decodeFile(File(context.filesDir, image.imagePath).absolutePath)
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(
                onClick = { },
                onLongClick = { showMenu = true }
            )
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        // Small action button at bottom-right
        IconButton(
            onClick = { showMenu = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp)
                .size(28.dp)
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    CircleShape
                )
        ) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        }
        // Context menu
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(
                text = { Text("Save to Device") },
                onClick = {
                    showMenu = false
                    onSave()
                },
                leadingIcon = { Icon(Icons.Default.Save, null) }
            )
            DropdownMenuItem(
                text = { Text("Delete") },
                onClick = {
                    showMenu = false
                    onDelete()
                },
                leadingIcon = {
                    Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                }
            )
        }
    }
}

