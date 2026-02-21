package com.pockettavern.app.ui.screens.extensions.quickreply

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pockettavern.app.domain.model.QuickReplyButton
import com.pockettavern.app.domain.model.QuickReplyPreset
import com.pockettavern.app.ui.theme.AccentGreen
import com.pockettavern.app.ui.theme.DarkSurface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickReplySettingsScreen(
    onBack: () -> Unit,
    viewModel: QuickReplySettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    uiState.error?.let { err ->
        LaunchedEffect(err) {
            viewModel.clearError()
        }
    }

    if (uiState.showPresetDialog) {
        PresetDialog(
            isEdit = uiState.editingPresetId != null,
            nameValue = uiState.presetNameField,
            onNameChange = viewModel::updatePresetNameField,
            onConfirm = viewModel::confirmPreset,
            onDismiss = viewModel::hidePresetDialog
        )
    }

    if (uiState.showButtonDialog) {
        ButtonDialog(
            isEdit = uiState.editingButtonId != null,
            labelValue = uiState.buttonLabelField,
            messageValue = uiState.buttonMessageField,
            autoTriggersValue = uiState.buttonAutoTriggersField,
            onLabelChange = viewModel::updateButtonLabelField,
            onMessageChange = viewModel::updateButtonMessageField,
            onAutoTriggersChange = viewModel::updateButtonAutoTriggers,
            onConfirm = viewModel::confirmButton,
            onDismiss = viewModel::hideButtonDialog
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Quick Reply") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::showAddPresetDialog) {
                        Icon(Icons.Default.Add, contentDescription = "Add preset")
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.presets.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No presets yet", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = viewModel::showAddPresetDialog) { Text("Add Preset") }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uiState.presets, key = { it.id }) { preset ->
                    PresetCard(
                        preset = preset,
                        onToggle = { viewModel.togglePresetEnabled(preset.id) },
                        onEdit = { viewModel.showEditPresetDialog(preset) },
                        onDelete = { viewModel.deletePreset(preset.id) },
                        onAddButton = { viewModel.showAddButtonDialog(preset.id) },
                        onEditButton = { btn -> viewModel.showEditButtonDialog(preset.id, btn) },
                        onDeleteButton = { btn -> viewModel.deleteButton(preset.id, btn.id) }
                    )
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun PresetCard(
    preset: QuickReplyPreset,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onAddButton: () -> Unit,
    onEditButton: (QuickReplyButton) -> Unit,
    onDeleteButton: (QuickReplyButton) -> Unit
) {
    Surface(
        color = DarkSurface,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(preset.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${preset.buttons.size} button${if (preset.buttons.size != 1) "s" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit preset", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete preset", modifier = Modifier.size(18.dp))
                }
                Switch(checked = preset.enabled, onCheckedChange = { onToggle() })
            }

            if (preset.buttons.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                preset.buttons.forEach { button ->
                    ButtonRow(
                        button = button,
                        onEdit = { onEditButton(button) },
                        onDelete = { onDeleteButton(button) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            TextButton(
                onClick = onAddButton,
                modifier = Modifier.align(Alignment.End)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Button")
            }
        }
    }
}

@Composable
private fun ButtonRow(
    button: QuickReplyButton,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(button.label, style = MaterialTheme.typography.bodyMedium, color = AccentGreen)
            Text(
                button.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        }
        IconButton(onClick = onEdit) {
            Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp))
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun PresetDialog(
    isEdit: Boolean,
    nameValue: String,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEdit) "Edit Preset" else "New Preset") },
        text = {
            OutlinedTextField(
                value = nameValue,
                onValueChange = onNameChange,
                label = { Text("Preset name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ButtonDialog(
    isEdit: Boolean,
    labelValue: String,
    messageValue: String,
    autoTriggersValue: Set<String>,
    onLabelChange: (String) -> Unit,
    onMessageChange: (String) -> Unit,
    onAutoTriggersChange: (Set<String>) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val triggerOptions = listOf(
        "CHAT_CHANGED" to "On chat load",
        "MESSAGE_RECEIVED" to "After AI message",
        "MESSAGE_SENT" to "After user message",
        "GENERATION_STOPPED" to "After generation"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEdit) "Edit Button" else "New Button") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = labelValue,
                    onValueChange = onLabelChange,
                    label = { Text("Button label") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = messageValue,
                    onValueChange = onMessageChange,
                    label = { Text("Message to send") },
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Supports {{user}} and {{char}} macros.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider()
                Text(
                    "Auto-triggers",
                    style = MaterialTheme.typography.labelMedium,
                    color = AccentGreen
                )
                triggerOptions.forEach { (event, label) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = event in autoTriggersValue,
                            onCheckedChange = { checked ->
                                val updated = if (checked) autoTriggersValue + event else autoTriggersValue - event
                                onAutoTriggersChange(updated)
                            }
                        )
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

