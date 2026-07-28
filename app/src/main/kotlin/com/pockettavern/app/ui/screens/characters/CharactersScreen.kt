package com.pockettavern.app.ui.screens.characters

import com.pockettavern.app.R
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.pockettavern.app.domain.usecase.TranslateCardUseCase
import com.pockettavern.app.ui.components.*
import com.pockettavern.app.ui.screens.groups.GroupsViewModel
import com.pockettavern.app.ui.theme.*
import com.pockettavern.app.ui.theme.LocalPocketTavernColors

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

    // File picker for local PNG/.charx import
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.importLocalCharacter(it) }
    }

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
                                contentDescription = stringResource(R.string.switch_view),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.characters), color = MaterialTheme.colorScheme.onSurface) },
                                onClick = {
                                    viewModel.setActiveTab(CharactersTab.CHARACTERS)
                                    expanded = false
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.groups), color = MaterialTheme.colorScheme.onSurface) },
                                onClick = {
                                    viewModel.setActiveTab(CharactersTab.GROUPS)
                                    expanded = false
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Groups, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
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
                        IconButton(onClick = { importLauncher.launch("*/*") }) {
                            Icon(Icons.Default.FileOpen, "Import character file")
                        }
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
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            if (uiState.activeTab == CharactersTab.GROUPS) {
                FloatingActionButton(
                    onClick = { groupsViewModel.showCreateDialog() },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.create_group), tint = Color.White)
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
                            Text(stringResource(R.string.edit_character))
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
                            Text(stringResource(R.string.character_settings))
                        }
                        TextButton(
                            onClick = {
                                viewModel.requestTranslateExisting(character)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Translate, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.translate_to_english))
                        }
                        TextButton(
                            onClick = {
                                viewModel.uploadToCharaVault(character)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.upload_to_charavault))
                        }
                        TextButton(
                            onClick = {
                                viewModel.showDeleteConfirmation(character)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.delete_character))
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissActionMenu() }) {
                        Text(stringResource(R.string.cancel))
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
            title = { Text(stringResource(R.string.uploading_to_charavault)) },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Text(stringResource(R.string.please_wait))
                }
            },
            confirmButton = {}
        )
    }

    // Upload success snackbar
    uiState.uploadSuccess?.let { message ->
        AlertDialog(
            onDismissRequest = { viewModel.clearUploadSuccess() },
            title = { Text(stringResource(R.string.success)) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearUploadSuccess() }) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }

    // Import progress indicator
    if (uiState.isImportingLocal) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.importing)) },
            text = { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) },
            confirmButton = {}
        )
    }

    // Translation dialog (V8: shown only when non-ASCII ratio > 0.15)
    if (uiState.showTranslateDialog) {
        TranslationDialog(
            isTranslating = uiState.isTranslating,
            error = uiState.translateError,
            initialFields = uiState.pendingTranslateFields,
            onTranslate = { fields -> viewModel.translateImportedCard(fields) },
            onDismiss = { viewModel.dismissTranslateDialog() }
        )
    }

    // Translate success
    if (uiState.translateSuccess) {
        LaunchedEffect(Unit) { viewModel.clearTranslateSuccess() }
        AlertDialog(
            onDismissRequest = { viewModel.clearTranslateSuccess() },
            title = { Text(stringResource(R.string.translated)) },
            text = { Text(stringResource(R.string.card_fields_translated_successfully)) },
            confirmButton = { TextButton(onClick = { viewModel.clearTranslateSuccess() }) { Text(stringResource(R.string.ok)) } }
        )
    }
}

@Composable
private fun TranslationDialog(
    isTranslating: Boolean,
    error: String?,
    initialFields: Set<TranslateCardUseCase.Field>,
    onTranslate: (List<TranslateCardUseCase.Field>) -> Unit,
    onDismiss: () -> Unit
) {
    val allFields = TranslateCardUseCase.Field.entries
    val selected = remember(initialFields) { mutableStateMapOf<TranslateCardUseCase.Field, Boolean>().also { m ->
        allFields.forEach { m[it] = it in initialFields }
    } }

    AlertDialog(
        onDismissRequest = { if (!isTranslating) onDismiss() },
        title = { Text(stringResource(R.string.non_english_card_detected)) },
        text = {
            Column {
                Text(stringResource(R.string.translate_card_fields_using_your_configured_a),
                    style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(12.dp))
                allFields.forEach { field ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = selected[field] == true,
                            onCheckedChange = { selected[field] = it },
                            enabled = !isTranslating
                        )
                        Text(field.label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (isTranslating) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                error?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onTranslate(allFields.filter { selected[it] == true }) },
                enabled = !isTranslating && selected.values.any { it }
            ) { Text(stringResource(R.string.translate)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isTranslating) { Text(stringResource(R.string.skip)) }
        }
    )
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
                Text(text = stringResource(R.string.no_characters_found),
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
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
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
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.no_groups_yet),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.create_a_group_to_chat_with_multiple_characte),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
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
    val avatarShape = LocalPocketTavernColors.current.avatarShape.toShape()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                ),
                shape = RoundedCornerShape(12.dp)
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
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
                            .clip(avatarShape)
                            .border(2.dp, MaterialTheme.colorScheme.background, avatarShape),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${group.members.size} members | ${group.enabledMembers.size} enabled | id:${group.id.take(8)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }

            if (group.favorite) {
                Icon(
                    Icons.Default.Groups,
                    contentDescription = stringResource(R.string.favorite),
                    tint = MaterialTheme.colorScheme.primary,
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
    val avatarShape = LocalPocketTavernColors.current.avatarShape.toShape()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.create_group)) },
        text = {
            Column {
                OutlinedTextField(
                    value = groupName,
                    onValueChange = onGroupNameChange,
                    label = { Text(stringResource(R.string.group_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        cursorColor = MaterialTheme.colorScheme.primary
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(stringResource(R.string.select_members_at_least_2),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceContainerLow,
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
                                        .clip(avatarShape),
                                    contentScale = ContentScale.Crop
                                )

                                Spacer(modifier = Modifier.width(12.dp))

                                Text(
                                    text = character.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )

                                if (isSelected) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = stringResource(R.string.selected),
                                        tint = MaterialTheme.colorScheme.primary
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
                        color = MaterialTheme.colorScheme.primary
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
                    Text(stringResource(R.string.create), color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface
    )
}
