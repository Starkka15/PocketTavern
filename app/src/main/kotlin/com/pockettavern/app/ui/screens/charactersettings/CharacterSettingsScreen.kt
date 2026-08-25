package com.pockettavern.app.ui.screens.charactersettings

import com.pockettavern.app.R
import androidx.compose.ui.res.stringResource
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
import com.pockettavern.app.ui.components.ImageViewerDialog
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
                title = { Text(stringResource(R.string.character_settings)) },
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

                // Personal Notes Section
                NotesSection(
                    notes = uiState.notes,
                    onNotesChange = viewModel::updateNotes
                )

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

                // Extensions Section
                if (uiState.extensionToggles.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                    ExtensionsSection(
                        toggles = uiState.extensionToggles,
                        onToggle = viewModel::setExtensionEnabled
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                // TTS Voice
                TtsVoiceSection(
                    voiceId = uiState.ttsVoiceId,
                    providerOverride = uiState.ttsProviderOverride,
                    availableVoices = uiState.availableVoices,
                    onVoiceChange = viewModel::updateTtsVoice,
                    onProviderChange = viewModel::updateTtsProviderOverride,
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
private fun NotesSection(
    notes: String,
    onNotesChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = stringResource(R.string.personal_notes),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(text = stringResource(R.string.private_notes_about_this_character_not_sent_t),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = notes,
            onValueChange = onNotesChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.add_your_notes_here)) },
            minLines = 3,
            maxLines = 8,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}

@Composable
private fun CharacterHeader(
    character: com.pockettavern.app.domain.model.Character,
    avatarUrl: String
) {
    var showAvatarViewer by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CharacterAvatar(
            imageUrl = avatarUrl,
            characterName = character.name,
            size = 64.dp,
            onClick = { showAvatarViewer = true }
        )
        Column {
            Text(
                text = character.name,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (character.hasCharacterBook) {
                Text(
                    text = stringResource(R.string.has_embedded_lorebook_entries, character.characterBookEntryCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }

    if (showAvatarViewer) {
        ImageViewerDialog(
            onDismiss = { showAvatarViewer = false },
            model = avatarUrl,
            contentDescription = character.name
        )
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
        Text(text = stringResource(R.string.world_info_lorebook),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Text(text = stringResource(R.string.attach_a_lorebook_to_this_character_the_loreb),
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
                label = { Text(stringResource(R.string.attached_world_info)) },
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
                    text = { Text(stringResource(R.string.none)) },
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
        Text(text = stringResource(R.string.system_prompt_2),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Text(text = stringResource(R.string.character_specific_system_instructions_this_o),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = systemPrompt,
            onValueChange = onSystemPromptChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp),
            label = { Text(stringResource(R.string.system_prompt_2)) },
            placeholder = { Text(stringResource(R.string.enter_character_specific_system_instructions)) },
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
        Text(text = stringResource(R.string.author_s_note_2),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Text(text = stringResource(R.string.instructions_injected_at_a_specific_depth_in),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = depthPrompt,
            onValueChange = onDepthPromptChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 100.dp),
            label = { Text(stringResource(R.string.author_s_note_content)) },
            placeholder = { Text(stringResource(R.string.enter_author_s_note)) },
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
                label = { Text(stringResource(R.string.depth)) },
                supportingText = { Text(stringResource(R.string.messages_from_end)) },
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
                    label = { Text(stringResource(R.string.role)) },
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
        Text(text = stringResource(R.string.post_history_instructions_legacy),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = postHistoryInstructions,
            onValueChange = onPostHistoryChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 80.dp),
            label = { Text(stringResource(R.string.post_history_instructions)) },
            placeholder = { Text(stringResource(R.string.legacy_author_s_note_format)) },
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
        Text(text = stringResource(R.string.talkativeness_group_chats),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Text(text = stringResource(R.string.how_often_this_character_speaks_in_group_conv),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = stringResource(R.string.quiet),
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

            Text(text = stringResource(R.string.talkative),
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
    providerOverride: String?,
    availableVoices: List<com.pockettavern.app.ui.audio.TtsVoice>,
    onVoiceChange: (String?) -> Unit,
    onProviderChange: (String?) -> Unit,
    onTestVoice: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var providerExpanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = stringResource(R.string.tts_voice),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Text(text = stringResource(R.string.assign_a_specific_voice_to_this_character_lea),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Per-character TTS provider override (default = global provider)
        ExposedDropdownMenuBox(
            expanded = providerExpanded,
            onExpandedChange = { providerExpanded = it }
        ) {
            OutlinedTextField(
                value = when (providerOverride) {
                    "system" -> "System TTS"
                    "openai" -> "OpenAI-compatible"
                    else -> "Default (global)"
                },
                onValueChange = { },
                readOnly = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                label = { Text(stringResource(R.string.provider)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = providerExpanded) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
            ExposedDropdownMenu(
                expanded = providerExpanded,
                onDismissRequest = { providerExpanded = false }
            ) {
                listOf(null to "Default (global)", "system" to "System TTS", "openai" to "OpenAI-compatible")
                    .forEach { (value, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                onProviderChange(value)
                                providerExpanded = false
                            }
                        )
                    }
            }
        }

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
                label = { Text(stringResource(R.string.voice)) },
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
                    text = { Text(stringResource(R.string.default_global)) },
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
            Text(stringResource(R.string.test_voice))
        }
    }
}

@Composable
private fun ExtensionsSection(
    toggles: List<ExtensionToggle>,
    onToggle: (String, Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = stringResource(R.string.extensions),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Text(text = stringResource(R.string.enable_or_disable_extensions_for_this_charact),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        toggles.forEach { toggle ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = toggle.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = toggle.enabled,
                    onCheckedChange = { onToggle(toggle.id, it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
        }
    }
}
