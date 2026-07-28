package com.pockettavern.app.ui.screens.formatting

import com.pockettavern.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pockettavern.app.ui.components.ErrorDialog
import com.pockettavern.app.ui.theme.*

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
                title = { Text(stringResource(R.string.advanced_formatting)) },
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
                // Instruct Mode Section
                FormattingSection(
                    title = "INSTRUCT MODE",
                    description = "Controls how messages are formatted for the AI model",
                    presets = uiState.instructPresets,
                    selectedIndex = uiState.selectedInstructIndex,
                    onPresetSelected = { viewModel.selectInstructPreset(it) }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                // Context Template Section
                FormattingSection(
                    title = "CONTEXT TEMPLATE",
                    description = "Defines how character info and chat history are structured",
                    presets = uiState.contextPresets,
                    selectedIndex = uiState.selectedContextIndex,
                    onPresetSelected = { viewModel.selectContextPreset(it) }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                // System Prompt Section
                Text(text = stringResource(R.string.system_prompt),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(text = stringResource(R.string.instructions_given_to_the_ai_about_how_to_beh),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                            label = { Text(stringResource(R.string.preset)) },
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

                // Editable preset content
                OutlinedTextField(
                    value = uiState.syspromptContent,
                    onValueChange = { viewModel.updateSyspromptContent(it) },
                    label = { Text(stringResource(R.string.content)) },
                    placeholder = { Text(stringResource(R.string.enter_system_prompt_text)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp),
                    maxLines = 20,
                    colors = textFieldColors()
                )

                // Preset action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.saveSyspromptPreset() },
                        enabled = !uiState.syspromptSaving && uiState.systemPromptPresets.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.save))
                    }
                    OutlinedButton(
                        onClick = { viewModel.showNewSyspromptDialog() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.action_new))
                    }
                    OutlinedButton(
                        onClick = { viewModel.deleteSyspromptPreset() },
                        enabled = uiState.syspromptIsUserPreset,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.delete))
                    }
                }

                if (!uiState.syspromptIsUserPreset && uiState.systemPromptPresets.isNotEmpty()) {
                    Text(text = stringResource(R.string.bundled_preset_save_to_create_an_editable_cop),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

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

    // New System Prompt Dialog
    if (uiState.showNewSyspromptDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.hideNewSyspromptDialog() },
            title = { Text(stringResource(R.string.new_system_prompt)) },
            text = {
                OutlinedTextField(
                    value = uiState.newSyspromptNameField,
                    onValueChange = { viewModel.updateNewSyspromptName(it) },
                    label = { Text(stringResource(R.string.preset_name_2)) },
                    singleLine = true,
                    colors = textFieldColors()
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmNewSyspromptPreset() }) {
                    Text(stringResource(R.string.create))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideNewSyspromptDialog() }) {
                    Text(stringResource(R.string.cancel))
                }
            }
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
            color = MaterialTheme.colorScheme.primary
        )

        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (presets.isEmpty()) {
            Text(
                text = "No presets available (loaded: ${presets.size})",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline
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
                    label = { Text(stringResource(R.string.template)) },
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
private fun dropdownTextFieldColors() = OutlinedTextFieldDefaults.colors(
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
