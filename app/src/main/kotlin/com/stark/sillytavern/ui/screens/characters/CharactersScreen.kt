package com.stark.sillytavern.ui.screens.characters

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stark.sillytavern.ui.components.*
import com.stark.sillytavern.ui.theme.DarkSurface
import com.stark.sillytavern.ui.theme.ErrorRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharactersScreen(
    onBack: () -> Unit,
    onNavigateToChat: (String) -> Unit,
    onNavigateToCreateCharacter: () -> Unit,
    onNavigateToEditCharacter: (String) -> Unit,
    onNavigateToCharacterSettings: (String) -> Unit,
    shouldRefresh: Boolean = false,
    onRefreshHandled: () -> Unit = {},
    viewModel: CharactersViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Refresh when requested
    LaunchedEffect(shouldRefresh) {
        if (shouldRefresh) {
            viewModel.loadCharacters()
            onRefreshHandled()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Characters") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToCreateCharacter) {
                        Icon(Icons.Default.Add, "Create Character")
                    }
                    IconButton(onClick = { viewModel.loadCharacters() }) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkSurface
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
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
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onNavigateToCreateCharacter) {
                            Text("Create Character")
                        }
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
                                viewModel.uploadToCardVault(character)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Upload to CardVault")
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

    // Error dialog
    uiState.error?.let { error ->
        ErrorDialog(
            message = error,
            onDismiss = { viewModel.clearError() }
        )
    }

    // Upload progress dialog
    if (uiState.isUploading) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Uploading to CardVault") },
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
