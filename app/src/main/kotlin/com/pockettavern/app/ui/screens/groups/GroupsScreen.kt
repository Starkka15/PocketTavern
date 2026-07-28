package com.pockettavern.app.ui.screens.groups

import com.pockettavern.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Refresh
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
import com.pockettavern.app.ui.theme.*
import com.pockettavern.app.ui.theme.LocalPocketTavernColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToGroupChat: (String) -> Unit,
    viewModel: GroupsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.groups)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadGroups() }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                    IconButton(onClick = { viewModel.showCreateDialog() }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.create_group))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.showCreateDialog() },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.create_group), tint = Color.White)
            }
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
                uiState.groups.isEmpty() -> {
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
                        items(uiState.groups) { group ->
                            GroupCard(
                                group = group,
                                memberAvatarUrls = uiState.groupAvatarUrls[group.id] ?: emptyList(),
                                onClick = { onNavigateToGroupChat(group.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Create Group Dialog
    if (uiState.showCreateDialog) {
        CreateGroupDialog(
            availableCharacters = uiState.availableCharacters,
            characterAvatarUrls = uiState.characterAvatarUrls,
            groupName = uiState.newGroupName,
            selectedMembers = uiState.selectedMembers,
            isCreating = uiState.isCreating,
            onGroupNameChange = { viewModel.updateNewGroupName(it) },
            onToggleMember = { viewModel.toggleMemberSelection(it) },
            onConfirm = { viewModel.createGroup() },
            onDismiss = { viewModel.dismissCreateDialog() }
        )
    }

    // Error snackbar
    uiState.error?.let { error ->
        LaunchedEffect(error) {
            // Show error briefly then clear
            kotlinx.coroutines.delay(3000)
            viewModel.clearError()
        }
    }
}

@Composable
private fun GroupCard(
    group: Group,
    memberAvatarUrls: List<String?>,
    onClick: () -> Unit
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
            .clickable(onClick = onClick),
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
                    text = "${group.members.size} members",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
