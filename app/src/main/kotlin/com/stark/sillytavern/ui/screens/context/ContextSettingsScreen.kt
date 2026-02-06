package com.stark.sillytavern.ui.screens.context

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
import com.stark.sillytavern.ui.components.ErrorDialog
import com.stark.sillytavern.ui.theme.*

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
                title = { Text("Context Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkSurface
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
                    Text("Save Settings")
                }

                // Success message
                if (uiState.saveSuccess) {
                    Text(
                        text = "Settings saved successfully!",
                        color = AccentGreen,
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
                tint = AccentBlue
            )
            Text(
                text = "AUTHOR'S NOTE",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = AccentBlue
            )
        }

        Text(
            text = "A note injected into the context to guide the AI's response style or focus.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )

        // Author's Note Content
        OutlinedTextField(
            value = content,
            onValueChange = onContentChange,
            label = { Text("Author's Note") },
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
                label = { Text("Position") },
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
                    text = "Depth: $depth messages from bottom",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary
                )
                Slider(
                    value = depth.toFloat(),
                    onValueChange = { onDepthChange(it.toInt()) },
                    valueRange = 0f..10f,
                    steps = 9,
                    colors = SliderDefaults.colors(
                        thumbColor = AccentBlue,
                        activeTrackColor = AccentBlue
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
            Text(
                text = "Interval:",
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary
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
                color = TextTertiary
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
                label = { Text("Role") },
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
private fun textFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = DarkInputBackground,
    unfocusedContainerColor = DarkInputBackground,
    focusedBorderColor = AccentGreen,
    unfocusedBorderColor = DarkSurfaceVariant,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedLabelColor = AccentGreen,
    unfocusedLabelColor = TextSecondary,
    focusedPlaceholderColor = TextTertiary,
    unfocusedPlaceholderColor = TextTertiary
)

@Composable
private fun dropdownColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = DarkInputBackground,
    unfocusedContainerColor = DarkInputBackground,
    focusedBorderColor = AccentGreen,
    unfocusedBorderColor = DarkSurfaceVariant,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedLabelColor = AccentGreen,
    unfocusedLabelColor = TextSecondary,
    focusedTrailingIconColor = AccentGreen,
    unfocusedTrailingIconColor = TextSecondary
)
