package com.pockettavern.app.ui.screens.groups

import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.pockettavern.app.domain.model.ActivationStrategy
import com.pockettavern.app.domain.model.Character
import com.pockettavern.app.domain.model.ChatInfo
import com.pockettavern.app.domain.model.ChatStyle
import com.pockettavern.app.domain.model.GroupChatMessage
import com.pockettavern.app.ui.components.ScanloreConfirmDialog
import com.pockettavern.app.ui.theme.*
import com.pockettavern.app.ui.theme.LocalPocketTavernColors
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupChatScreen(
    groupId: String,
    onNavigateBack: () -> Unit,
    viewModel: GroupChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(groupId) {
        viewModel.loadGroup(groupId)
    }

    // Calculate total item count (messages + optional streaming item)
    val hasStreamingItem = uiState.isGenerating && uiState.streamingCharacterName.isNotBlank()
    val totalItems = uiState.messages.size + if (hasStreamingItem) 1 else 0

    // Track whether the user has intentionally scrolled away from the bottom.
    // Upward scroll during streaming disables auto-follow; scrolling back to the
    // bottom (or a new generation starting) re-enables it.
    var userScrolledAway by remember { mutableStateOf(false) }
    LaunchedEffect(listState) {
        var prevIndex = listState.firstVisibleItemIndex
        var prevOffset = listState.firstVisibleItemScrollOffset
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                val scrolledUp = index < prevIndex || (index == prevIndex && offset < prevOffset)
                if (scrolledUp && listState.isScrollInProgress) userScrolledAway = true
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
    // Reset when a new generation starts
    LaunchedEffect(uiState.isGenerating) {
        if (uiState.isGenerating) userScrolledAway = false
    }
    // Auto-follow during streaming unless the user has scrolled away
    LaunchedEffect(uiState.streamingContent) {
        if (hasStreamingItem && totalItems > 0 && !userScrolledAway) {
            listState.scrollToItem(totalItems - 1, scrollOffset = Int.MAX_VALUE)
        }
    }

    // When a message is committed (or chat loads): smooth scroll to bottom
    LaunchedEffect(uiState.messages.size) {
        if (totalItems > 0) {
            listState.animateScrollToItem(totalItems - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = uiState.group?.name ?: "Group Chat",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (uiState.isGenerating && uiState.streamingCharacterName.isNotBlank()) {
                            Text(
                                text = "${uiState.streamingCharacterName} is typing...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1
                            )
                        } else if (uiState.currentModelName.isNotBlank()) {
                            Text(
                                text = uiState.currentModelName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Show member count
                    uiState.group?.let { group ->
                        Row(
                            modifier = Modifier.padding(end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Groups,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "${group.members.size}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    // Overflow menu with activation strategy
                    var showMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "Options",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Edit Group") },
                                leadingIcon = { Icon(Icons.Default.PersonAdd, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    viewModel.showEditGroup()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Generate Scene Image") },
                                leadingIcon = {
                                    if (uiState.isGeneratingImage)
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    else
                                        Icon(Icons.Default.Image, contentDescription = null)
                                },
                                onClick = {
                                    showMenu = false
                                    viewModel.generateGroupSceneImage()
                                },
                                enabled = !uiState.isGeneratingImage && !uiState.isGenerating
                            )
                            DropdownMenuItem(
                                text = { Text("Edit Prompt") },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    viewModel.showPromptEditor()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("World Book") },
                                leadingIcon = { Icon(Icons.Default.MenuBook, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    viewModel.showWorldBookEditor()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Chat History") },
                                leadingIcon = { Icon(Icons.Default.History, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    viewModel.showChatSelector()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("New Chat") },
                                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    viewModel.createNewChat()
                                }
                            )
                            HorizontalDivider()
                            Text(
                                "Chat Style",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                            val currentStyle = uiState.group?.chatStyle ?: ChatStyle.DIALOGUE
                            listOf(
                                ChatStyle.DIALOGUE to "Dialogue",
                                ChatStyle.RP to "RP (actions + dialogue)"
                            ).forEach { (value, label) ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            RadioButton(
                                                selected = currentStyle == value,
                                                onClick = null,
                                                colors = RadioButtonDefaults.colors(
                                                    selectedColor = MaterialTheme.colorScheme.primary,
                                                    unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(label, color = MaterialTheme.colorScheme.onSurface)
                                        }
                                    },
                                    onClick = {
                                        viewModel.setChatStyle(value)
                                        showMenu = false
                                    }
                                )
                            }
                            HorizontalDivider()
                            Text(
                                "Activation Strategy",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                            val currentStrategy = uiState.group?.activationStrategy ?: ActivationStrategy.NATURAL
                            listOf(
                                ActivationStrategy.NATURAL to "Natural",
                                ActivationStrategy.LIST to "List (All respond)",
                                ActivationStrategy.POOLED to "Pooled",
                                ActivationStrategy.MANUAL to "Manual"
                            ).forEach { (value, label) ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            RadioButton(
                                                selected = currentStrategy == value,
                                                onClick = null,
                                                colors = RadioButtonDefaults.colors(
                                                    selectedColor = MaterialTheme.colorScheme.primary,
                                                    unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(label, color = MaterialTheme.colorScheme.onSurface)
                                        }
                                    },
                                    onClick = {
                                        viewModel.setActivationStrategy(value)
                                        showMenu = false
                                    }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            GroupChatInputBar(
                inputText = uiState.inputText,
                isSending = uiState.isSending,
                isGenerating = uiState.isGenerating,
                onInputChange = { viewModel.updateInputText(it) },
                onSend = { viewModel.sendMessage() },
                onStop = { viewModel.stopGeneration() }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                uiState.messages.isEmpty() && !uiState.isGenerating -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Groups,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Start the conversation!",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Send a message, or let the characters introduce themselves.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(onClick = { viewModel.generateFirstMessage() }) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Generate Opening Message")
                        }
                    }
                }
                uiState.messages.isEmpty() && uiState.isGenerating -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (uiState.streamingCharacterName == "Narrator") {
                            NarratorBubble(
                                content = uiState.streamingContent.ifBlank { "…" },
                                isStreaming = true
                            )
                        } else {
                            StreamingBubble(
                                characterName = uiState.streamingCharacterName,
                                avatarUrl = uiState.memberAvatarUrls[uiState.streamingCharacterAvatar],
                                content = uiState.streamingContent.ifBlank { "…" }
                            )
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(uiState.messages, key = { it.id }) { message ->
                            val index = uiState.messages.indexOf(message)
                            if (message.isSystem && message.imagePath == null) {
                                NarratorBubble(content = message.content)
                            } else {
                                GroupMessageBubble(
                                    message = message,
                                    avatarUrl = uiState.memberAvatarUrls[message.senderAvatar],
                                    onLongPress = { viewModel.showMessageActions(index) }
                                )
                            }
                        }

                        // Streaming bubble
                        if (hasStreamingItem) {
                            item(key = "streaming") {
                                if (uiState.streamingCharacterName == "Narrator") {
                                    NarratorBubble(
                                        content = uiState.streamingContent.ifBlank { "…" },
                                        isStreaming = true
                                    )
                                } else {
                                    StreamingBubble(
                                        characterName = uiState.streamingCharacterName,
                                        avatarUrl = uiState.memberAvatarUrls[uiState.streamingCharacterAvatar],
                                        content = uiState.streamingContent
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Group prompt editor
    if (uiState.showPromptEditor) {
        GroupPromptEditorDialog(
            text = uiState.promptEditorText,
            onTextChange = { viewModel.updatePromptEditorText(it) },
            onSave = { viewModel.saveGroupPrompt() },
            onDismiss = { viewModel.dismissPromptEditor() }
        )
    }

    // World book editor
    if (uiState.showWorldBookEditor) {
        WorldBookEditorDialog(
            text = uiState.worldBookEditorText,
            onTextChange = { viewModel.updateWorldBookEditorText(it) },
            onSave = { viewModel.saveWorldBook() },
            onDismiss = { viewModel.dismissWorldBookEditor() }
        )
    }

    // Scanlore dialog
    if (uiState.showScanloreDialog) {
        ScanloreConfirmDialog(
            entries = uiState.scanloreEntries,
            isLoading = uiState.scanloreLoading,
            error = uiState.scanloreError,
            onConfirm = { viewModel.confirmScanlore(it) },
            onDismiss = { viewModel.dismissScanlore() }
        )
    }

    // Message actions dialog
    uiState.selectedMessageIndex?.let { msgIdx ->
        if (uiState.showMessageActions) {
            val msg = uiState.messages.getOrNull(msgIdx)
            val isLastAi = msgIdx == uiState.messages.indexOfLast { !it.isUser && !it.isSystem }
            GroupMessageActionsDialog(
                isUserMessage = msg?.isUser == true,
                isLastAiMessage = isLastAi && msg?.isUser == false && msg.isSystem == false,
                onEdit = { viewModel.startEditingMessage(msgIdx) },
                onDelete = { viewModel.deleteMessage(msgIdx) },
                onDeleteFromHere = { viewModel.deleteMessagesFromIndex(msgIdx) },
                onRegenerate = {
                    viewModel.dismissMessageActions()
                    viewModel.regenerateLastResponse()
                },
                onDismiss = { viewModel.dismissMessageActions() }
            )
        }
    }

    // Edit message dialog
    uiState.editingMessageIndex?.let {
        GroupEditMessageDialog(
            text = uiState.editingMessageText,
            onTextChange = { viewModel.updateEditingText(it) },
            onSave = { viewModel.saveEditedMessage() },
            onDismiss = { viewModel.cancelEditing() }
        )
    }

    // Edit group dialog
    if (uiState.showEditGroup) {
        EditGroupDialog(
            groupName = uiState.editGroupName,
            selectedMembers = uiState.editGroupMembers,
            availableCharacters = uiState.availableCharacters,
            characterAvatarUrls = uiState.characterAvatarUrls,
            onGroupNameChange = { viewModel.updateEditGroupName(it) },
            onToggleMember = { viewModel.toggleEditGroupMember(it) },
            onConfirm = { viewModel.saveEditGroup() },
            onDismiss = { viewModel.dismissEditGroup() }
        )
    }

    // Chat history selector
    if (uiState.showChatSelector) {
        GroupChatSelectorDialog(
            chats = uiState.availableChats,
            currentChatFileName = uiState.currentChatFileName,
            onSelectChat = { viewModel.selectChat(it) },
            onNewChat = { viewModel.createNewChat() },
            onDeleteChat = { viewModel.deleteChat(it) },
            onDismiss = { viewModel.dismissChatSelector() }
        )
    }

    // Error handling
    uiState.error?.let { error ->
        LaunchedEffect(error) {
            kotlinx.coroutines.delay(3000)
            viewModel.clearError()
        }
    }
}

@Composable
private fun NarratorBubble(
    content: String,
    isStreaming: Boolean = false
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                )
                Text(
                    text = if (isStreaming) "Narrator…" else "Narrator",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun StreamingBubble(
    characterName: String,
    avatarUrl: String?,
    content: String
) {
    val avatarShape = LocalPocketTavernColors.current.avatarShape.toShape()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        // Character avatar
        AsyncImage(
            model = avatarUrl,
            contentDescription = characterName,
            modifier = Modifier
                .size(36.dp)
                .clip(avatarShape)
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), avatarShape),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(8.dp))

        Column(
            modifier = Modifier.widthIn(max = 280.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = characterName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
            )

            Surface(
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = 4.dp,
                    bottomEnd = 16.dp
                ),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                    ),
                    shape = RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = 4.dp,
                        bottomEnd = 16.dp
                    )
                )
            ) {
                if (content.isBlank()) {
                    // Typing indicator dots
                    TypingIndicator(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                } else {
                    Text(
                        text = content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TypingIndicator(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, delayMillis = index * 200),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot$index"
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha))
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupMessageBubble(
    message: GroupChatMessage,
    avatarUrl: String?,
    onLongPress: () -> Unit
) {
    val avatarShape = LocalPocketTavernColors.current.avatarShape.toShape()
    val isUser = message.isUser
    val context = LocalContext.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = message.senderName,
                modifier = Modifier
                    .size(36.dp)
                    .clip(avatarShape)
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), avatarShape),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            modifier = Modifier.widthIn(max = 280.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            if (!isUser && message.senderName != null) {
                Text(
                    text = message.senderName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                )
            }

            val bubbleShape = RoundedCornerShape(
                topStart = 16.dp, topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            )

            if (message.imagePath != null) {
                // Image message
                val imageFile = File(context.filesDir, message.imagePath)
                AsyncImage(
                    model = imageFile,
                    contentDescription = "Scene image",
                    modifier = Modifier
                        .widthIn(max = 260.dp)
                        .clip(bubbleShape)
                        .combinedClickable(onClick = {}, onLongClick = onLongPress),
                    contentScale = ContentScale.FillWidth
                )
            } else {
                Surface(
                    shape = bubbleShape,
                    color = if (isUser) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                            else MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier
                        .border(
                            width = 1.dp,
                            brush = Brush.linearGradient(
                                colors = if (isUser)
                                    listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                                else
                                    listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                            ),
                            shape = bubbleShape
                        )
                        .combinedClickable(onClick = {}, onLongClick = onLongPress)
                ) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        if (isUser) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(avatarShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Text("You", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun GroupChatInputBar(
    inputText: String,
    isSending: Boolean,
    isGenerating: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit
) {
    Surface(
        color = Color.Black,
        tonalElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = onInputChange,
                placeholder = { Text("Type a message...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                modifier = Modifier.weight(1f),
                maxLines = 4,
                enabled = !isGenerating && !isSending,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    disabledBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                    disabledTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    cursorColor = MaterialTheme.colorScheme.primary,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                ),
                shape = RoundedCornerShape(24.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            if (isGenerating) {
                // Stop button during generation
                IconButton(
                    onClick = onStop,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.Red.copy(alpha = 0.8f))
                ) {
                    Icon(
                        Icons.Default.Stop,
                        contentDescription = "Stop generation",
                        tint = Color.White
                    )
                }
            } else {
                // Send button
                IconButton(
                    onClick = onSend,
                    enabled = inputText.isNotBlank() && !isSending,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (inputText.isNotBlank() && !isSending) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                ) {
                    if (isSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupChatSelectorDialog(
    chats: List<ChatInfo>,
    currentChatFileName: String?,
    onSelectChat: (String) -> Unit,
    onNewChat: () -> Unit,
    onDeleteChat: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var pendingDelete by remember { mutableStateOf<ChatInfo?>(null) }

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
                                Row(
                                    modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = formatGroupChatFileName(chat.fileName),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (isSelected)
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            else
                                                MaterialTheme.colorScheme.onSurface
                                        )
                                        chat.lastMessage?.let { lastMsg ->
                                            Text(
                                                text = lastMsg.take(50) + if (lastMsg.length > 50) "…" else "",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Text(
                                            text = "${chat.messageCount} messages",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = { pendingDelete = chat }) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete chat",
                                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                            modifier = Modifier.size(20.dp)
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
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("New Chat")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )

    // Delete confirmation
    pendingDelete?.let { chat ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete chat?") },
            text = { Text("This will permanently delete \"${formatGroupChatFileName(chat.fileName)}\". This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteChat(chat.fileName)
                        pendingDelete = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun GroupPromptEditorDialog(
    text: String,
    onTextChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Group Prompt") },
        text = {
            Column {
                Text(
                    text = "Injected into every generation for this group. Describe the scenario, rules, or tone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp, max = 320.dp),
                    placeholder = {
                        Text(
                            "e.g. This is a fantasy tavern setting. Characters should stay in a medieval tone...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    },
                    minLines = 6,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onSave) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun WorldBookEditorDialog(
    text: String,
    onTextChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("World Book") },
        text = {
            Column {
                Text(
                    text = "Shared lore injected into every generation — group and solo chats. Use /addlore to append entries from any chat.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp, max = 400.dp),
                    placeholder = {
                        Text(
                            "e.g. [2026-05-25] Gulara processed the Vashenko brothers. +30 lbs.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    },
                    minLines = 8,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onSave) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun GroupMessageActionsDialog(
    isUserMessage: Boolean,
    isLastAiMessage: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDeleteFromHere: () -> Unit,
    onRegenerate: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Message Actions") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { onEdit(); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Edit Message")
                    Spacer(Modifier.weight(1f))
                }
                TextButton(onClick = { onDelete(); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Delete Message", color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.weight(1f))
                }
                TextButton(onClick = { onDeleteFromHere(); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Delete From Here", color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.weight(1f))
                }
                if (!isUserMessage && isLastAiMessage) {
                    TextButton(onClick = onRegenerate, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Regenerate", color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = MaterialTheme.colorScheme.background,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun GroupEditMessageDialog(
    text: String,
    onTextChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Message") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                maxLines = 12,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    cursorColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        confirmButton = { TextButton(onClick = onSave) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = MaterialTheme.colorScheme.background,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditGroupDialog(
    groupName: String,
    selectedMembers: Set<String>,
    availableCharacters: List<Character>,
    characterAvatarUrls: Map<String, String?>,
    onGroupNameChange: (String) -> Unit,
    onToggleMember: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val avatarShape = LocalPocketTavernColors.current.avatarShape.toShape()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Group") },
        text = {
            Column {
                OutlinedTextField(
                    value = groupName,
                    onValueChange = onGroupNameChange,
                    label = { Text("Group Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        cursorColor = MaterialTheme.colorScheme.primary
                    )
                )
                Spacer(Modifier.height(16.dp))
                Text("Members (at least 2)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(availableCharacters) { character ->
                        val avatarKey = character.avatar ?: "${character.name}.png"
                        val isSelected = avatarKey in selectedMembers
                        Surface(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onToggleMember(avatarKey) },
                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                AsyncImage(
                                    model = characterAvatarUrls[avatarKey],
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp).clip(avatarShape),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(text = character.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                                if (isSelected) Icon(Icons.Default.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                if (selectedMembers.size < 2) {
                    Spacer(Modifier.height(8.dp))
                    Text("Select at least ${2 - selectedMembers.size} more", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = groupName.isNotBlank() && selectedMembers.size >= 2) {
                Text("Save", color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) } },
        containerColor = MaterialTheme.colorScheme.background,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface
    )
}

private fun formatGroupChatFileName(fileName: String): String {
    if (fileName == "chat_imported") return "Imported Chat"
    return try {
        val parts = fileName.removePrefix("chat_").split("_")
        if (parts.size >= 2) {
            val sdf = SimpleDateFormat("yyyyMMdd HHmmss", Locale.getDefault())
            val date = sdf.parse("${parts[0]} ${parts[1]}")
            val out = SimpleDateFormat("MMM d, yyyy  h:mm a", Locale.getDefault())
            date?.let { out.format(it) } ?: fileName
        } else fileName
    } catch (_: Exception) {
        fileName
    }
}
