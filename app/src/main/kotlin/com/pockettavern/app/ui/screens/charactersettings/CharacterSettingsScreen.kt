package com.pockettavern.app.ui.screens.charactersettings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pockettavern.app.ui.components.CharacterAvatar
import com.pockettavern.app.ui.components.ErrorDialog
import com.pockettavern.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterSettingsScreen(
    onBack: () -> Unit,
    viewModel: CharacterSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Handle save success
    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            viewModel.clearSaveSuccess()
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Character Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    // Favorite toggle
                    IconButton(onClick = { viewModel.updateIsFavorite(!uiState.isFavorite) }) {
                        Icon(
                            imageVector = if (uiState.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = if (uiState.isFavorite) "Remove from favorites" else "Add to favorites",
                            tint = if (uiState.isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    // Save button
                    IconButton(
                        onClick = { viewModel.saveSettings() },
                        enabled = !uiState.isSaving
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Filled.Save, "Save settings")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Character header
                uiState.character?.let { character ->
                    CharacterHeader(
                        character = character,
                        avatarUrl = uiState.avatarUrl
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                // World Info Section
                WorldInfoSection(
                    attachedWorldInfo = uiState.attachedWorldInfo,
                    availableWorldInfo = uiState.availableWorldInfo,
                    onWorldInfoChange = viewModel::updateAttachedWorldInfo
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                // System Prompt Section
                SystemPromptSection(
                    systemPrompt = uiState.systemPrompt,
                    onSystemPromptChange = viewModel::updateSystemPrompt
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                // Author's Note Section (Depth Prompt)
                AuthorsNoteSection(
                    depthPrompt = uiState.depthPrompt,
                    depthPromptDepth = uiState.depthPromptDepth,
                    depthPromptRole = uiState.depthPromptRole,
                    postHistoryInstructions = uiState.postHistoryInstructions,
                    onDepthPromptChange = viewModel::updateDepthPrompt,
                    onDepthChange = viewModel::updateDepthPromptDepth,
                    onRoleChange = viewModel::updateDepthPromptRole,
                    onPostHistoryChange = viewModel::updatePostHistoryInstructions
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                // Talkativeness (for group chats)
                TalkativenessSection(
                    talkativeness = uiState.talkativeness,
                    onTalkativenessChange = viewModel::updateTalkativeness
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                // TTS Voice
                TtsVoiceSection(
                    voiceId = uiState.ttsVoiceId,
                    availableVoices = uiState.availableVoices,
                    onVoiceChange = viewModel::updateTtsVoice,
                    onTestVoice = viewModel::testTtsVoice
                )

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    // Error dialog
    uiState.error?.let { error ->
        ErrorDialog(
            message = error,
            onDismiss = { viewModel.clearError() }
        )
    }
}

@Composable
private fun CharacterHeader(
    character: com.pockettavern.app.domain.model.Character,
    avatarUrl: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CharacterAvatar(
            imageUrl = avatarUrl,
            characterName = character.name,
            size = 64.dp
        )
        Column {
            Text(
                text = character.name,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (character.hasCharacterBook) {
                Text(
                    text = "Has embedded lorebook (${character.characterBookEntryCount} entries)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorldInfoSection(
    attachedWorldInfo: String?,
    availableWorldInfo: List<com.pockettavern.app.domain.model.WorldInfoListItem>,
    onWorldInfoChange: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "World Info / Lorebook",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Text(
            text = "Attach a lorebook to this character. The lorebook entries will be injected into the context when their keywords are detected.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = if (attachedWorldInfo.isNullOrBlank()) "None" else attachedWorldInfo,
                onValueChange = { },
                readOnly = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                label = { Text("Attached World Info") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                // None option - use empty string to explicitly clear the world info
                DropdownMenuItem(
                    text = { Text("None") },
                    onClick = {
                        onWorldInfoChange("")
                        expanded = false
                    }
                )

                availableWorldInfo.forEach { worldInfo ->
                    DropdownMenuItem(
                        text = { Text(worldInfo.name) },
                        onClick = {
                            onWorldInfoChange(worldInfo.name)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SystemPromptSection(
    systemPrompt: String,
    onSystemPromptChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "System Prompt",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Text(
            text = "Character-specific system instructions. This overrides the global system prompt when chatting with this character.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = systemPrompt,
            onValueChange = onSystemPromptChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp),
            label = { Text("System Prompt") },
            placeholder = { Text("Enter character-specific system instructions...") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(8.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthorsNoteSection(
    depthPrompt: String,
    depthPromptDepth: Int,
    depthPromptRole: String,
    postHistoryInstructions: String,
    onDepthPromptChange: (String) -> Unit,
    onDepthChange: (Int) -> Unit,
    onRoleChange: (String) -> Unit,
    onPostHistoryChange: (String) -> Unit
) {
    var roleExpanded by remember { mutableStateOf(false) }
    val roles = listOf("system", "user", "assistant")

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Author's Note",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Text(
            text = "Instructions injected at a specific depth in the conversation. Use this to guide the AI's behavior mid-conversation.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = depthPrompt,
            onValueChange = onDepthPromptChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 100.dp),
            label = { Text("Author's Note Content") },
            placeholder = { Text("Enter author's note...") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Depth
            OutlinedTextField(
                value = depthPromptDepth.toString(),
                onValueChange = { newValue ->
                    newValue.toIntOrNull()?.let { onDepthChange(it) }
                },
                modifier = Modifier.weight(1f),
                label = { Text("Depth") },
                supportingText = { Text("Messages from end") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(8.dp)
            )

            // Role dropdown
            ExposedDropdownMenuBox(
                expanded = roleExpanded,
                onExpandedChange = { roleExpanded = it },
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value = depthPromptRole,
                    onValueChange = { },
                    readOnly = true,
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    label = { Text("Role") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = roleExpanded) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(8.dp)
                )

                ExposedDropdownMenu(
                    expanded = roleExpanded,
                    onDismissRequest = { roleExpanded = false }
                ) {
                    roles.forEach { role ->
                        DropdownMenuItem(
                            text = { Text(role.replaceFirstChar { it.uppercase() }) },
                            onClick = {
                                onRoleChange(role)
                                roleExpanded = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Post-history instructions (legacy author's note)
        Text(
            text = "Post-History Instructions (Legacy)",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = postHistoryInstructions,
            onValueChange = onPostHistoryChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 80.dp),
            label = { Text("Post-History Instructions") },
            placeholder = { Text("Legacy author's note format...") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(8.dp)
        )
    }
}

@Composable
private fun TalkativenessSection(
    talkativeness: Float,
    onTalkativenessChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Talkativeness (Group Chats)",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Text(
            text = "How often this character speaks in group conversations. Higher values mean more frequent responses.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Quiet",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )

            Slider(
                value = talkativeness,
                onValueChange = onTalkativenessChange,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )

            Text(
                text = "Talkative",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }

        Text(
            text = "${(talkativeness * 100).toInt()}%",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TtsVoiceSection(
    voiceId: String?,
    availableVoices: List<com.pockettavern.app.ui.audio.TtsVoice>,
    onVoiceChange: (String?) -> Unit,
    onTestVoice: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "TTS Voice",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Text(
            text = "Assign a specific voice to this character. Leave as 'Default' to use the global TTS voice.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = voiceId ?: "Default (global)",
                onValueChange = { },
                readOnly = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                label = { Text("Voice") },
                leadingIcon = { Icon(Icons.Default.RecordVoiceOver, contentDescription = null) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Default (global)") },
                    onClick = {
                        onVoiceChange(null)
                        expanded = false
                    }
                )
                availableVoices.forEach { voice ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(voice.name)
                                if (voice.language.isNotBlank()) {
                                    Text(
                                        voice.language,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        onClick = {
                            onVoiceChange(voice.id)
                            expanded = false
                        }
                    )
                }
            }
        }

        // Test button
        OutlinedButton(
            onClick = onTestVoice,
            modifier = Modifier.align(Alignment.End)
        ) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Test Voice")
        }
    }
}
