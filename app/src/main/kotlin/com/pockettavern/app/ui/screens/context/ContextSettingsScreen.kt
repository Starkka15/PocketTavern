package com.pockettavern.app.ui.screens.context

import com.pockettavern.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pockettavern.app.ui.components.ErrorDialog
import com.pockettavern.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContextSettingsScreen(
    onBack: () -> Unit,
    viewModel: ContextSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Handle save success
    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            viewModel.resetSaveSuccess()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.context_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
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
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Author's Note Section
                AuthorsNoteSection(
                    content = uiState.authorsNoteContent,
                    interval = uiState.authorsNoteInterval,
                    depth = uiState.authorsNoteDepth,
                    position = uiState.authorsNotePosition,
                    role = uiState.authorsNoteRole,
                    onContentChange = { viewModel.updateAuthorsNoteContent(it) },
                    onIntervalChange = { viewModel.updateAuthorsNoteInterval(it) },
                    onDepthChange = { viewModel.updateAuthorsNoteDepth(it) },
                    onPositionChange = { viewModel.updateAuthorsNotePosition(it) },
                    onRoleChange = { viewModel.updateAuthorsNoteRole(it) }
                )

                HorizontalDivider()

                // Auto-Continue Section
                AutoContinueSection(
                    enabled = uiState.autoContinueEnabled,
                    minLength = uiState.autoContinueMinLength,
                    onEnabledChange = viewModel::updateAutoContinueEnabled,
                    onMinLengthChange = viewModel::updateAutoContinueMinLength
                )

                HorizontalDivider()

                // Long-Term Memory Section
                LongTermMemorySection(
                    enabled = uiState.memoryEnabled,
                    onEnabledChange = viewModel::updateMemoryEnabled
                )

                HorizontalDivider()

                // Roleplay Behavior Section
                RoleplayBehaviorSection(
                    noSpeakForUser = uiState.noSpeakForUser,
                    onNoSpeakForUserChange = viewModel::updateNoSpeakForUser
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Save Button
                Button(
                    onClick = { viewModel.saveSettings() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isSaving
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(stringResource(R.string.save_settings))
                }

                // Success message
                if (uiState.saveSuccess) {
                    Text(text = stringResource(R.string.settings_saved_successfully),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }

    // Error Dialog
    uiState.error?.let { error ->
        ErrorDialog(
            message = error,
            onDismiss = { viewModel.clearError() }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthorsNoteSection(
    content: String,
    interval: Int,
    depth: Int,
    position: Int,
    role: Int,
    onContentChange: (String) -> Unit,
    onIntervalChange: (Int) -> Unit,
    onDepthChange: (Int) -> Unit,
    onPositionChange: (Int) -> Unit,
    onRoleChange: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.StickyNote2,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(text = stringResource(R.string.author_s_note),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Text(text = stringResource(R.string.a_note_injected_into_the_context_to_guide_the),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Author's Note Content
        OutlinedTextField(
            value = content,
            onValueChange = onContentChange,
            label = { Text(stringResource(R.string.author_s_note_2)) },
            placeholder = { Text("[Style: vivid, detailed]\n[Focus: character emotions]") },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 100.dp),
            maxLines = 8,
            colors = textFieldColors()
        )

        // Position dropdown
        var positionExpanded by remember { mutableStateOf(false) }
        val positionOptions = listOf(
            "After Scenario" to 0,
            "In-Chat @ Depth" to 1,
            "Before Scenario" to 2
        )

        ExposedDropdownMenuBox(
            expanded = positionExpanded,
            onExpandedChange = { positionExpanded = it }
        ) {
            OutlinedTextField(
                value = positionOptions.find { it.second == position }?.first ?: "After Scenario",
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.position)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = positionExpanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                colors = dropdownColors()
            )
            ExposedDropdownMenu(
                expanded = positionExpanded,
                onDismissRequest = { positionExpanded = false }
            ) {
                positionOptions.forEach { (label, value) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onPositionChange(value)
                            positionExpanded = false
                        }
                    )
                }
            }
        }

        // Depth slider (for in-chat position)
        if (position == 1) {
            Column {
                Text(
                    text = stringResource(R.string.depth_messages_from_bottom, depth),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Slider(
                    value = depth.toFloat(),
                    onValueChange = { onDepthChange(it.toInt()) },
                    valueRange = 0f..10f,
                    steps = 9,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }

        // Interval
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = stringResource(R.string.interval),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            OutlinedTextField(
                value = interval.toString(),
                onValueChange = {
                    it.toIntOrNull()?.let { value ->
                        onIntervalChange(value.coerceIn(0, 100))
                    }
                },
                modifier = Modifier.width(80.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = textFieldColors()
            )
            Text(
                text = if (interval == 0) "every message" else "every $interval messages",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }

        // Role dropdown
        var roleExpanded by remember { mutableStateOf(false) }
        val roleOptions = listOf(
            "System" to 0,
            "User" to 1,
            "Assistant" to 2
        )

        ExposedDropdownMenuBox(
            expanded = roleExpanded,
            onExpandedChange = { roleExpanded = it }
        ) {
            OutlinedTextField(
                value = roleOptions.find { it.second == role }?.first ?: "System",
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.role)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = roleExpanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                colors = dropdownColors()
            )
            ExposedDropdownMenu(
                expanded = roleExpanded,
                onDismissRequest = { roleExpanded = false }
            ) {
                roleOptions.forEach { (label, value) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onRoleChange(value)
                            roleExpanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AutoContinueSection(
    enabled: Boolean,
    minLength: Int,
    onEnabledChange: (Boolean) -> Unit,
    onMinLengthChange: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.Autorenew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(text = stringResource(R.string.auto_continue),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Text(text = stringResource(R.string.automatically_request_more_when_the_ai_stops),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = stringResource(R.string.enable_auto_continue),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Switch(checked = enabled, onCheckedChange = onEnabledChange)
        }

        if (enabled) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(text = stringResource(R.string.min_length),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                OutlinedTextField(
                    value = minLength.toString(),
                    onValueChange = { it.toIntOrNull()?.let { v -> onMinLengthChange(v.coerceIn(0, 5000)) } },
                    modifier = Modifier.width(100.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = textFieldColors()
                )
                Text(text = stringResource(R.string.tokens),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(text = stringResource(R.string.max_3_auto_continues_per_message),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun LongTermMemorySection(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = stringResource(R.string.long_term_memory),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(text = stringResource(R.string.summarize_old_messages_so_the_ai_remembers_pa) +
                "When chat history exceeds ~3,000 tokens the oldest turns are " +
                "compressed into a bullet-point memory block injected at the start of every prompt.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = stringResource(R.string.enable_long_term_memory),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Switch(checked = enabled, onCheckedChange = onEnabledChange)
        }
    }
}

@Composable
private fun RoleplayBehaviorSection(
    noSpeakForUser: Boolean,
    onNoSpeakForUserChange: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = stringResource(R.string.roleplay_behavior),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = stringResource(R.string.don_t_speak_or_act_for_user),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(text = stringResource(R.string.instructs_the_ai_to_never_write_dialogue_or_a),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Switch(checked = noSpeakForUser, onCheckedChange = onNoSpeakForUserChange)
        }
    }
}

@Composable
private fun textFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    focusedPlaceholderColor = MaterialTheme.colorScheme.outline,
    unfocusedPlaceholderColor = MaterialTheme.colorScheme.outline
)

@Composable
private fun dropdownColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    focusedTrailingIconColor = MaterialTheme.colorScheme.primary,
    unfocusedTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
)
