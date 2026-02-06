package com.stark.sillytavern.ui.screens.formatting

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stark.sillytavern.ui.components.ErrorDialog
import com.stark.sillytavern.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormattingScreen(
    onBack: () -> Unit,
    viewModel: FormattingViewModel = hiltViewModel()
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
                title = { Text("Advanced Formatting") },
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
                // Instruct Mode Section
                FormattingSection(
                    title = "INSTRUCT MODE",
                    description = "Controls how messages are formatted for the AI model",
                    presets = uiState.instructPresets,
                    selectedIndex = uiState.selectedInstructIndex,
                    onPresetSelected = { viewModel.selectInstructPreset(it) }
                )

                HorizontalDivider(color = DarkSurfaceVariant)

                // Context Template Section
                FormattingSection(
                    title = "CONTEXT TEMPLATE",
                    description = "Defines how character info and chat history are structured",
                    presets = uiState.contextPresets,
                    selectedIndex = uiState.selectedContextIndex,
                    onPresetSelected = { viewModel.selectContextPreset(it) }
                )

                HorizontalDivider(color = DarkSurfaceVariant)

                // System Prompt Section
                Text(
                    text = "SYSTEM PROMPT",
                    style = MaterialTheme.typography.titleMedium,
                    color = AccentGreen
                )

                Text(
                    text = "Instructions given to the AI about how to behave",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                // System Prompt Preset Dropdown
                if (uiState.systemPromptPresets.isNotEmpty()) {
                    var syspromptExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = syspromptExpanded,
                        onExpandedChange = { syspromptExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = uiState.systemPromptPresets.getOrNull(uiState.selectedSyspromptIndex)
                                ?: "Select preset",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Preset") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = syspromptExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                            colors = dropdownTextFieldColors()
                        )
                        ExposedDropdownMenu(
                            expanded = syspromptExpanded,
                            onDismissRequest = { syspromptExpanded = false }
                        ) {
                            uiState.systemPromptPresets.forEachIndexed { index, preset ->
                                DropdownMenuItem(
                                    text = { Text(preset) },
                                    onClick = {
                                        viewModel.selectSyspromptPreset(index)
                                        syspromptExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Custom System Prompt
                OutlinedTextField(
                    value = uiState.customSystemPrompt,
                    onValueChange = { viewModel.updateCustomSystemPrompt(it) },
                    label = { Text("Custom System Prompt") },
                    placeholder = { Text("Enter custom instructions for the AI...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp),
                    maxLines = 10,
                    colors = textFieldColors()
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
private fun FormattingSection(
    title: String,
    description: String,
    presets: List<String>,
    selectedIndex: Int,
    onPresetSelected: (Int) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = AccentGreen
        )

        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )

        if (presets.isEmpty()) {
            Text(
                text = "No presets available (loaded: ${presets.size})",
                style = MaterialTheme.typography.bodyMedium,
                color = TextTertiary
            )
        } else {
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                OutlinedTextField(
                    value = presets.getOrNull(selectedIndex) ?: "Select template",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Template") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    colors = dropdownTextFieldColors()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    presets.forEachIndexed { index, preset ->
                        DropdownMenuItem(
                            text = { Text(preset) },
                            onClick = {
                                onPresetSelected(index)
                                expanded = false
                            }
                        )
                    }
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
private fun dropdownTextFieldColors() = OutlinedTextFieldDefaults.colors(
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
