package com.stark.sillytavern.ui.screens.textgen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stark.sillytavern.ui.components.ConfirmDialog
import com.stark.sillytavern.ui.components.ErrorDialog
import com.stark.sillytavern.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextGenSettingsScreen(
    onBack: () -> Unit,
    viewModel: TextGenSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            viewModel.resetSaveSuccess()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Text Generation") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
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
                // Preset Selector
                SectionHeader("Preset")
                PresetDropdown(
                    presets = uiState.presets.map { it.name },
                    selectedIndex = uiState.selectedPresetIndex,
                    onSelect = { viewModel.selectPreset(it) }
                )

                HorizontalDivider(color = DarkSurfaceVariant)

                // Basic Settings
                SectionHeader("Basic Settings")

                SliderSetting("Temperature", uiState.temperature, 0f..2f, "%.2f") { viewModel.updateTemperature(it) }
                SliderSetting("Top P", uiState.topP, 0f..1f, "%.2f") { viewModel.updateTopP(it) }
                SliderSetting("Min P", uiState.minP, 0f..1f, "%.2f") { viewModel.updateMinP(it) }
                SliderSetting("Repetition Penalty", uiState.repPen, 1f..2f, "%.2f") { viewModel.updateRepPen(it) }

                IntInputField("Max New Tokens", uiState.maxTokens) { viewModel.updateMaxTokens(it) }

                HorizontalDivider(color = DarkSurfaceVariant)

                // Advanced Settings Toggle
                OutlinedButton(
                    onClick = { viewModel.toggleAdvanced() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        if (uiState.showAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (uiState.showAdvanced) "Hide Advanced Settings" else "Show Advanced Settings")
                }

                // Advanced Settings
                AnimatedVisibility(visible = uiState.showAdvanced) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        // Token Limits
                        SectionHeader("Token Limits")
                        IntInputField("Min Tokens", uiState.minTokens) { viewModel.updateMinTokens(it) }
                        IntInputField("Context Length", uiState.truncationLength) { viewModel.updateTruncationLength(it) }

                        HorizontalDivider(color = DarkSurfaceVariant)

                        // Sampling
                        SectionHeader("Sampling")
                        IntInputField("Top K", uiState.topK) { viewModel.updateTopK(it) }
                        SliderSetting("Top A", uiState.topA, 0f..1f, "%.2f") { viewModel.updateTopA(it) }
                        SliderSetting("Typical P", uiState.typicalP, 0f..1f, "%.2f") { viewModel.updateTypicalP(it) }
                        SliderSetting("TFS", uiState.tfs, 0f..1f, "%.2f") { viewModel.updateTfs(it) }

                        HorizontalDivider(color = DarkSurfaceVariant)

                        // Repetition
                        SectionHeader("Repetition Penalty")
                        IntInputField("Rep Pen Range", uiState.repPenRange) { viewModel.updateRepPenRange(it) }
                        SliderSetting("Rep Pen Slope", uiState.repPenSlope, 0f..10f, "%.1f") { viewModel.updateRepPenSlope(it) }
                        SliderSetting("Frequency Penalty", uiState.frequencyPenalty, 0f..2f, "%.2f") { viewModel.updateFrequencyPenalty(it) }
                        SliderSetting("Presence Penalty", uiState.presencePenalty, 0f..2f, "%.2f") { viewModel.updatePresencePenalty(it) }

                        HorizontalDivider(color = DarkSurfaceVariant)

                        // DRY Sampler
                        SectionHeader("DRY Sampler")
                        SliderSetting("DRY Multiplier", uiState.dryMultiplier, 0f..2f, "%.2f") { viewModel.updateDryMultiplier(it) }
                        SliderSetting("DRY Base", uiState.dryBase, 1f..4f, "%.2f") { viewModel.updateDryBase(it) }
                        IntInputField("DRY Allowed Length", uiState.dryAllowedLength) { viewModel.updateDryAllowedLength(it) }
                        IntInputField("DRY Penalty Last N", uiState.dryPenaltyLastN) { viewModel.updateDryPenaltyLastN(it) }

                        HorizontalDivider(color = DarkSurfaceVariant)

                        // Mirostat
                        SectionHeader("Mirostat")
                        IntInputField("Mirostat Mode (0-2)", uiState.mirostatMode) { viewModel.updateMirostatMode(it) }
                        SliderSetting("Mirostat Tau", uiState.mirostatTau, 0f..10f, "%.1f") { viewModel.updateMirostatTau(it) }
                        SliderSetting("Mirostat Eta", uiState.mirostatEta, 0f..1f, "%.2f") { viewModel.updateMirostatEta(it) }

                        HorizontalDivider(color = DarkSurfaceVariant)

                        // XTC
                        SectionHeader("XTC")
                        SliderSetting("XTC Threshold", uiState.xtcThreshold, 0f..1f, "%.2f") { viewModel.updateXtcThreshold(it) }
                        SliderSetting("XTC Probability", uiState.xtcProbability, 0f..1f, "%.2f") { viewModel.updateXtcProbability(it) }

                        HorizontalDivider(color = DarkSurfaceVariant)

                        // Other
                        SectionHeader("Other")
                        SliderSetting("Skew", uiState.skew, -5f..5f, "%.2f") { viewModel.updateSkew(it) }
                        SliderSetting("Smoothing Factor", uiState.smoothingFactor, 0f..10f, "%.2f") { viewModel.updateSmoothingFactor(it) }
                        SliderSetting("Smoothing Curve", uiState.smoothingCurve, 0f..10f, "%.2f") { viewModel.updateSmoothingCurve(it) }
                        SliderSetting("Guidance Scale (CFG)", uiState.guidanceScale, 0f..3f, "%.2f") { viewModel.updateGuidanceScale(it) }

                        HorizontalDivider(color = DarkSurfaceVariant)

                        // Token Handling
                        SectionHeader("Token Handling")
                        SwitchSetting("Add BOS Token", uiState.addBosToken) { viewModel.updateAddBosToken(it) }
                        SwitchSetting("Ban EOS Token", uiState.banEosToken) { viewModel.updateBanEosToken(it) }
                        SwitchSetting("Skip Special Tokens", uiState.skipSpecialTokens) { viewModel.updateSkipSpecialTokens(it) }
                    }
                }

                HorizontalDivider(color = DarkSurfaceVariant)

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.showSavePresetDialog() },
                        modifier = Modifier.weight(1f),
                        enabled = !uiState.isSaving
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save Preset")
                    }

                    OutlinedButton(
                        onClick = { viewModel.showDeleteConfirm() },
                        modifier = Modifier.weight(1f),
                        enabled = !uiState.isSaving && uiState.presets.isNotEmpty(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete")
                    }
                }

                Button(
                    onClick = { viewModel.applySettings() },
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
                    Text("Apply Settings")
                }

                if (uiState.saveSuccess) {
                    Text(
                        text = "Settings applied successfully!",
                        color = AccentGreen,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    // Save Preset Dialog
    if (uiState.showSavePresetDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.hideSavePresetDialog() },
            title = { Text("Save Preset") },
            text = {
                OutlinedTextField(
                    value = uiState.newPresetName,
                    onValueChange = { viewModel.updateNewPresetName(it) },
                    label = { Text("Preset Name") },
                    singleLine = true,
                    colors = textFieldColors()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.savePreset() },
                    enabled = uiState.newPresetName.isNotBlank() && !uiState.isSaving
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideSavePresetDialog() }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Confirmation
    if (uiState.showDeleteConfirm) {
        val presetName = uiState.presets.getOrNull(uiState.selectedPresetIndex)?.name ?: ""
        ConfirmDialog(
            title = "Delete Preset",
            message = "Delete preset \"$presetName\"? This cannot be undone.",
            confirmText = "Delete",
            onConfirm = { viewModel.deleteCurrentPreset() },
            onDismiss = { viewModel.hideDeleteConfirm() },
            isDestructive = true
        )
    }

    // Error Dialog
    uiState.error?.let { error ->
        ErrorDialog(
            message = error,
            onDismiss = { viewModel.clearError() }
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = AccentGreen
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PresetDropdown(
    presets: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = presets.getOrNull(selectedIndex) ?: "Select preset",
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
            colors = dropdownColors()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            presets.forEachIndexed { index, preset ->
                DropdownMenuItem(
                    text = { Text(preset) },
                    onClick = {
                        onSelect(index)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun SliderSetting(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    format: String,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            Text(format.format(value), style = MaterialTheme.typography.bodyMedium, color = AccentGreen)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = AccentGreen,
                activeTrackColor = AccentGreen,
                inactiveTrackColor = DarkSurfaceVariant
            )
        )
    }
}

@Composable
private fun IntInputField(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit
) {
    OutlinedTextField(
        value = value.toString(),
        onValueChange = { it.toIntOrNull()?.let(onValueChange) },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        colors = textFieldColors()
    )
}

@Composable
private fun SwitchSetting(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = AccentGreen,
                checkedTrackColor = AccentGreen.copy(alpha = 0.5f)
            )
        )
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
    unfocusedLabelColor = TextSecondary
)

@Composable
private fun dropdownColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = DarkInputBackground,
    unfocusedContainerColor = DarkInputBackground,
    focusedBorderColor = AccentGreen,
    unfocusedBorderColor = DarkSurfaceVariant,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedTrailingIconColor = AccentGreen,
    unfocusedTrailingIconColor = TextSecondary
)
