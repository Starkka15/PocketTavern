package com.pockettavern.app.ui.screens.characters

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.pockettavern.app.domain.model.Character
import com.pockettavern.app.domain.model.Group
import com.pockettavern.app.ui.components.*
import com.pockettavern.app.ui.screens.groups.GroupsViewModel
import com.pockettavern.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharactersScreen(
    onBack: () -> Unit,
    onNavigateToChat: (String) -> Unit,
    onNavigateToCreateCharacter: () -> Unit,
    onNavigateToEditCharacter: (String) -> Unit,
    onNavigateToCharacterSettings: (String) -> Unit,
    onNavigateToGroupChat: (String) -> Unit,
    shouldRefresh: Boolean = false,
    onRefreshHandled: () -> Unit = {},
    viewModel: CharactersViewModel = hiltViewModel(),
    groupsViewModel: GroupsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val groupsState by groupsViewModel.uiState.collectAsStateWithLifecycle()

    // Refresh when requested
    LaunchedEffect(shouldRefresh) {
        if (shouldRefresh) {
            viewModel.loadCharacters()
            groupsViewModel.loadGroups()
            onRefreshHandled()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    // Tab dropdown
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        Row(
                            modifier = Modifier.clickable { expanded = true },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (uiState.activeTab == CharactersTab.CHARACTERS) "Characters" else "Groups"
                            )
                            Icon(
                                Icons.Default.ArrowDropDown,
                                contentDescription = "Switch view",
                                tint = TextSecondary
                            )
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Characters", color = TextPrimary) },
                                onClick = {
                                    viewModel.setActiveTab(CharactersTab.CHARACTERS)
                                    expanded = false
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Person, contentDescription = null, tint = IceBlue)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Groups", color = TextPrimary) },
                                onClick = {
                                    viewModel.setActiveTab(CharactersTab.GROUPS)
                                    expanded = false
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Groups, contentDescription = null, tint = IceBlue)
                                }
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (uiState.activeTab == CharactersTab.CHARACTERS) {
                        IconButton(onClick = onNavigateToCreateCharacter) {
                            Icon(Icons.Default.Add, "Create Character")
                        }
                    } else {
                        IconButton(onClick = { groupsViewModel.showCreateDialog() }) {
                            Icon(Icons.Default.Add, "Create Group")
                        }
                    }
                    IconButton(onClick = {
                        if (uiState.activeTab == CharactersTab.CHARACTERS) viewModel.loadCharacters()
                        else groupsViewModel.loadGroups()
                    }) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkSurface
                )
            )
        },
        floatingActionButton = {
            if (uiState.activeTab == CharactersTab.GROUPS) {
                FloatingActionButton(
                    onClick = { groupsViewModel.showCreateDialog() },
                    containerColor = FireOrange
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Create Group", tint = Color.White)
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (uiState.activeTab == CharactersTab.CHARACTERS) {
                CharactersContent(
                    uiState = uiState,
                    viewModel = viewModel,
                    onNavigateToChat = onNavigateToChat,
                    onNavigateToEditCharacter = onNavigateToEditCharacter,
                    onNavigateToCharacterSettings = onNavigateToCharacterSettings
                )
            } else {
                GroupsContent(
                    groupsState = groupsState,
                    onNavigateToGroupChat = onNavigateToGroupChat,
                    onDeleteGroup = { groupsViewModel.showDeleteConfirmation(it) }
                )
            }
        }
    }

    // Character action menu
    uiState.actionMenuCharacter?.let { character ->
        if (uiState.showActionMenu) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissActionMenu() },
                title = { Text(character.name) },
                text = {
                    Column {
                        TextButton(
                            onClick = {
                                val avatarUrl = character.avatar ?: character.name
                                viewModel.dismissActionMenu()
                                onNavigateToEditCharacter(avatarUrl)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Edit Character")
                        }
                        TextButton(
                            onClick = {
                                val avatarUrl = character.avatar ?: character.name
                                viewModel.dismissActionMenu()
                                onNavigateToCharacterSettings(avatarUrl)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Character Settings")
                        }
                        TextButton(
                            onClick = {
                                viewModel.uploadToCharaVault(character)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Upload to CharaVault")
                        }
                        TextButton(
                            onClick = {
                                viewModel.showDeleteConfirmation(character)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.textButtonColors(contentColor = ErrorRed)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Delete Character")
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissActionMenu() }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }

    // Delete confirmation dialog
    if (uiState.showDeleteDialog) {
        ConfirmDialog(
            title = "Delete Character",
            message = "Delete \"${uiState.characterToDelete?.name}\"? This cannot be undone.",
            confirmText = "Delete",
            onConfirm = { viewModel.deleteCharacter() },
            onDismiss = { viewModel.dismissDeleteDialog() },
            isDestructive = true
        )
    }

    // Create Group Dialog
    if (groupsState.showCreateDialog) {
        CreateGroupDialog(
            availableCharacters = groupsState.availableCharacters,
            characterAvatarUrls = groupsState.characterAvatarUrls,
            groupName = groupsState.newGroupName,
            selectedMembers = groupsState.selectedMembers,
            isCreating = groupsState.isCreating,
            onGroupNameChange = { groupsViewModel.updateNewGroupName(it) },
            onToggleMember = { groupsViewModel.toggleMemberSelection(it) },
            onConfirm = { groupsViewModel.createGroup() },
            onDismiss = { groupsViewModel.dismissCreateDialog() }
        )
    }

    // Delete Group Dialog
    if (groupsState.showDeleteDialog) {
        ConfirmDialog(
            title = "Delete Group",
            message = "Delete \"${groupsState.groupToDelete?.name}\"? This cannot be undone.",
            confirmText = "Delete",
            onConfirm = { groupsViewModel.deleteGroup() },
            onDismiss = { groupsViewModel.dismissDeleteDialog() },
            isDestructive = true
        )
    }

    // Error dialog (characters)
    uiState.error?.let { error ->
        ErrorDialog(
            message = error,
            onDismiss = { viewModel.clearError() }
        )
    }

    // Error dialog (groups)
    groupsState.error?.let { error ->
        ErrorDialog(
            message = error,
            onDismiss = { groupsViewModel.clearError() }
        )
    }

    // Upload progress dialog
    if (uiState.isUploading) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Uploading to CharaVault") },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Text("Please wait...")
                }
            },
            confirmButton = {}
        )
    }

    // Upload success snackbar
    uiState.uploadSuccess?.let { message ->
        AlertDialog(
            onDismissRequest = { viewModel.clearUploadSuccess() },
            title = { Text("Success") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearUploadSuccess() }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
private fun CharactersContent(
    uiState: CharactersUiState,
    viewModel: CharactersViewModel,
    onNavigateToChat: (String) -> Unit,
    onNavigateToEditCharacter: (String) -> Unit,
    onNavigateToCharacterSettings: (String) -> Unit
) {
    when {
        uiState.isLoading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        uiState.characters.isEmpty() -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "No characters found",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }

        else -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                items(
                    uiState.characters,
                    key = { it.avatar ?: it.name }
                ) { character ->
                    CharacterListItem(
                        character = character,
                        avatarUrl = uiState.characterAvatarUrls[character.avatar ?: character.name],
                        onClick = {
                            viewModel.selectCharacter(character)
                            onNavigateToChat(character.avatar ?: character.name)
                        },
                        isSelected = uiState.selectedCharacter?.name == character.name,
                        onLongClick = { viewModel.showActionMenu(character) }
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        thickness = 0.5.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupsContent(
    groupsState: com.pockettavern.app.ui.screens.groups.GroupsUiState,
    onNavigateToGroupChat: (String) -> Unit,
    onDeleteGroup: (Group) -> Unit
) {
    when {
        groupsState.isLoading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = IceBlue)
            }
        }

        groupsState.groups.isEmpty() -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.Groups,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = TextSecondary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "No groups yet",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Create a group to chat with multiple characters at once",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary.copy(alpha = 0.7f)
                )
            }
        }

        else -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(groupsState.groups) { group ->
                    GroupCard(
                        group = group,
                        memberAvatarUrls = groupsState.groupAvatarUrls[group.id] ?: emptyList(),
                        onClick = { onNavigateToGroupChat(group.id) },
                        onLongClick = { onDeleteGroup(group) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupCard(
    group: Group,
    memberAvatarUrls: List<String?>,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(IceBlue.copy(alpha = 0.5f), IceBlue.copy(alpha = 0.2f))
                ),
                shape = RoundedCornerShape(12.dp)
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        color = DarkCard,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Stacked avatars
            Box(
                modifier = Modifier.width((32 + (memberAvatarUrls.take(3).size - 1) * 20).dp)
            ) {
                memberAvatarUrls.take(3).forEachIndexed { index, url ->
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        modifier = Modifier
                            .offset(x = (index * 20).dp)
                            .size(32.dp)
                            .clip(CircleShape)
                            .border(2.dp, DarkBackground, CircleShape),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${group.members.size} members | ${group.enabledMembers.size} enabled | id:${group.id.take(8)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 2
                )
            }

            if (group.favorite) {
                Icon(
                    Icons.Default.Groups,
                    contentDescription = "Favorite",
                    tint = FireGold,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateGroupDialog(
    availableCharacters: List<Character>,
    characterAvatarUrls: Map<String, String?>,
    groupName: String,
    selectedMembers: Set<String>,
    isCreating: Boolean,
    onGroupNameChange: (String) -> Unit,
    onToggleMember: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Group") },
        text = {
            Column {
                OutlinedTextField(
                    value = groupName,
                    onValueChange = onGroupNameChange,
                    label = { Text("Group Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = IceBlue,
                        cursorColor = IceBlue
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "Select Members (at least 2)",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier.heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(availableCharacters) { character ->
                        val avatarKey = character.avatar ?: character.name
                        val isSelected = avatarKey in selectedMembers

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onToggleMember(avatarKey) },
                            color = if (isSelected) IceBlue.copy(alpha = 0.2f) else DarkCard,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = characterAvatarUrls[avatarKey],
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )

                                Spacer(modifier = Modifier.width(12.dp))

                                Text(
                                    text = character.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary,
                                    modifier = Modifier.weight(1f)
                                )

                                if (isSelected) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = IceBlue
                                    )
                                }
                            }
                        }
                    }
                }

                if (selectedMembers.size < 2) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Select at least ${2 - selectedMembers.size} more character${if (2 - selectedMembers.size > 1) "s" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = FireOrange
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = groupName.isNotBlank() && selectedMembers.size >= 2 && !isCreating
            ) {
                if (isCreating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Create", color = IceBlue)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
        containerColor = DarkBackground,
        titleContentColor = TextPrimary,
        textContentColor = TextPrimary
    )
}
