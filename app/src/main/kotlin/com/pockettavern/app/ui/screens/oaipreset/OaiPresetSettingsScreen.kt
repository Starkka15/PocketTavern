package com.pockettavern.app.ui.screens.oaipreset

import com.pockettavern.app.R
import androidx.compose.ui.res.stringResource
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pockettavern.app.ui.components.ConfirmDialog
import com.pockettavern.app.ui.components.ErrorDialog
import com.pockettavern.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OaiPresetSettingsScreen(
    onBack: () -> Unit,
    viewModel: OaiPresetSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) viewModel.resetSaveSuccess()
    }

    // File picker for ST JSON import
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val jsonText = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
            if (!jsonText.isNullOrBlank()) viewModel.beginImportFromJson(jsonText)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.chat_completion_presets)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { importLauncher.launch("application/json") }) {
                        Icon(Icons.Default.Download, "Import from SillyTavern", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Preset selector
                OaiSectionHeader(stringResource(R.string.oai_section_preset))
                OaiPresetDropdown(
                    presets = uiState.presets.map { it.name },
                    selectedIndex = uiState.selectedPresetIndex,
                    onSelect = { viewModel.selectPreset(it) }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                Text(text = stringResource(R.string.toggle_each_parameter_on_to_override_the_api),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                // ── Sampling Parameters ──────────────────────────────────────
                OaiSectionHeader(stringResource(R.string.oai_section_sampling))

                OaiParamWithSlider(
                    label = stringResource(R.string.temperature),
                    enabled = uiState.temperatureEnabled,
                    onToggle = { viewModel.toggleTemperatureEnabled(it) },
                    value = uiState.temperature,
                    range = 0f..2f,
                    format = "%.2f",
                    onValueChange = { viewModel.updateTemperature(it) }
                )

                OaiParamWithInput(
                    label = stringResource(R.string.max_response_length),
                    enabled = uiState.maxTokensEnabled,
                    onToggle = { viewModel.toggleMaxTokensEnabled(it) },
                    value = uiState.maxTokens,
                    onValueChange = { viewModel.updateMaxTokens(it) }
                )

                OaiParamWithSlider(
                    label = stringResource(R.string.top_p),
                    enabled = uiState.topPEnabled,
                    onToggle = { viewModel.toggleTopPEnabled(it) },
                    value = uiState.topP,
                    range = 0f..1f,
                    format = "%.2f",
                    onValueChange = { viewModel.updateTopP(it) }
                )

                OaiParamWithInput(
                    label = stringResource(R.string.top_k),
                    enabled = uiState.topKEnabled,
                    onToggle = { viewModel.toggleTopKEnabled(it) },
                    value = uiState.topK,
                    onValueChange = { viewModel.updateTopK(it) }
                )

                OaiParamWithSlider(
                    label = stringResource(R.string.min_p),
                    enabled = uiState.minPEnabled,
                    onToggle = { viewModel.toggleMinPEnabled(it) },
                    value = uiState.minP,
                    range = 0f..1f,
                    format = "%.2f",
                    onValueChange = { viewModel.updateMinP(it) }
                )

                OaiParamWithSlider(
                    label = stringResource(R.string.top_a),
                    enabled = uiState.topAEnabled,
                    onToggle = { viewModel.toggleTopAEnabled(it) },
                    value = uiState.topA,
                    range = 0f..1f,
                    format = "%.2f",
                    onValueChange = { viewModel.updateTopA(it) }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                // ── Penalty Parameters ───────────────────────────────────────
                OaiSectionHeader(stringResource(R.string.oai_section_penalties))

                OaiParamWithSlider(
                    label = stringResource(R.string.frequency_penalty),
                    enabled = uiState.frequencyPenaltyEnabled,
                    onToggle = { viewModel.toggleFrequencyPenaltyEnabled(it) },
                    value = uiState.frequencyPenalty,
                    range = 0f..2f,
                    format = "%.2f",
                    onValueChange = { viewModel.updateFrequencyPenalty(it) }
                )

                OaiParamWithSlider(
                    label = stringResource(R.string.presence_penalty),
                    enabled = uiState.presencePenaltyEnabled,
                    onToggle = { viewModel.togglePresencePenaltyEnabled(it) },
                    value = uiState.presencePenalty,
                    range = 0f..2f,
                    format = "%.2f",
                    onValueChange = { viewModel.updatePresencePenalty(it) }
                )

                OaiParamWithSlider(
                    label = stringResource(R.string.repetition_penalty),
                    enabled = uiState.repetitionPenaltyEnabled,
                    onToggle = { viewModel.toggleRepetitionPenaltyEnabled(it) },
                    value = uiState.repetitionPenalty,
                    range = 1f..2f,
                    format = "%.2f",
                    onValueChange = { viewModel.updateRepetitionPenalty(it) }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                // ── Context / Misc ───────────────────────────────────────────
                OaiSectionHeader(stringResource(R.string.oai_section_context_misc))

                OaiParamWithInput(
                    label = stringResource(R.string.context_size_tokens),
                    enabled = uiState.contextSizeEnabled,
                    onToggle = { viewModel.toggleContextSizeEnabled(it) },
                    value = uiState.contextSize,
                    onValueChange = { viewModel.updateContextSize(it) }
                )

                OaiParamWithInput(
                    label = stringResource(R.string.seed_1_random),
                    enabled = uiState.seedEnabled,
                    onToggle = { viewModel.toggleSeedEnabled(it) },
                    value = uiState.seed,
                    onValueChange = { viewModel.updateSeed(it) },
                    allowNegative = true
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                // ── Prompt Order ─────────────────────────────────────────────
                OaiSectionHeader(stringResource(R.string.oai_section_prompt_order))
                Text(text = stringResource(R.string.control_which_blocks_are_included_and_their_o),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                uiState.promptOrder.forEachIndexed { index, item ->
                    PromptOrderRow(
                        item = item,
                        canMoveUp = index > 0,
                        canMoveDown = index < uiState.promptOrder.lastIndex,
                        onToggle = { viewModel.togglePromptOrderItem(index) },
                        onMoveUp = { viewModel.movePromptOrderItemUp(index) },
                        onMoveDown = { viewModel.movePromptOrderItemDown(index) },
                        onContentChange = { viewModel.updatePromptOrderItemContent(index, it) },
                        onDelete = { viewModel.deleteCustomPromptItem(index) },
                        onRoleChange = { role -> viewModel.updatePromptOrderItemRole(index, role) },
                        onInjectionChange = { pos, depth -> viewModel.updatePromptOrderItemInjection(index, pos, depth) }
                    )
                }

                OutlinedButton(
                    onClick = { viewModel.showAddCustomPromptDialog() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.add_custom_prompt))
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                // ── Action Buttons ───────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.showSaveDialog() },
                        modifier = Modifier.weight(1f),
                        enabled = !uiState.isSaving
                    ) {
                        Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.save_preset))
                    }
                    OutlinedButton(
                        onClick = { viewModel.showDeleteConfirm() },
                        modifier = Modifier.weight(1f),
                        enabled = !uiState.isSaving && uiState.presets.isNotEmpty(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.delete))
                    }
                }

                if (uiState.saveSuccess) {
                    Text(stringResource(R.string.preset_saved), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }

    // Save dialog
    if (uiState.showSaveDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.hideSaveDialog() },
            title = { Text(stringResource(R.string.save_preset)) },
            text = {
                OutlinedTextField(
                    value = uiState.newPresetName,
                    onValueChange = { viewModel.updateNewPresetName(it) },
                    label = { Text(stringResource(R.string.preset_name)) },
                    singleLine = true,
                    colors = oaiTextFieldColors()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.savePreset() },
                    enabled = uiState.newPresetName.isNotBlank() && !uiState.isSaving
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideSaveDialog() }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (uiState.showDeleteConfirm) {
        val name = uiState.presets.getOrNull(uiState.selectedPresetIndex)?.name ?: ""
        ConfirmDialog(
            title = stringResource(R.string.delete_preset),
            message = "Delete preset \"$name\"? This cannot be undone.",
            confirmText = "Delete",
            onConfirm = { viewModel.deleteCurrentPreset() },
            onDismiss = { viewModel.hideDeleteConfirm() },
            isDestructive = true
        )
    }

    // Import name dialog
    if (uiState.showImportNameDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.hideImportNameDialog() },
            title = { Text(stringResource(R.string.import_st_preset)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.save_imported_preset_as),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = uiState.importPresetName,
                        onValueChange = { viewModel.updateImportPresetName(it) },
                        label = { Text(stringResource(R.string.preset_name)) },
                        singleLine = true,
                        colors = oaiTextFieldColors()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.confirmImport() },
                    enabled = uiState.importPresetName.isNotBlank() && !uiState.isSaving
                ) { Text(stringResource(R.string.action_import)) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideImportNameDialog() }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    // Custom prompt add dialog (name-only for adding, content is edited inline after)
    if (uiState.showCustomPromptDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.hideCustomPromptDialog() },
            title = { Text(if (uiState.editingCustomIndex >= 0) "Edit Custom Prompt" else "Add Custom Prompt") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = uiState.customPromptLabel,
                        onValueChange = { viewModel.updateCustomPromptLabel(it) },
                        label = { Text(stringResource(R.string.name)) },
                        placeholder = { Text(stringResource(R.string.e_g_nsfw_policy_jailbreak)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = oaiTextFieldColors()
                    )
                    OutlinedTextField(
                        value = uiState.customPromptContent,
                        onValueChange = { viewModel.updateCustomPromptContent(it) },
                        label = { Text(stringResource(R.string.content)) },
                        placeholder = { Text(stringResource(R.string.prompt_text_use_user_char_macros)) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                        maxLines = 12,
                        colors = oaiTextFieldColors()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.confirmCustomPrompt() },
                    enabled = uiState.customPromptLabel.isNotBlank() && uiState.customPromptContent.isNotBlank()
                ) { Text(if (uiState.editingCustomIndex >= 0) "Save" else "Add") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideCustomPromptDialog() }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    uiState.error?.let { error ->
        ErrorDialog(message = error, onDismiss = { viewModel.clearError() })
    }
}

// ── Reusable composables ──────────────────────────────────────────────────────

@Composable
private fun OaiSectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

/** A parameter row with a label + switch, and an animated slider below when enabled. */
@Composable
private fun OaiParamWithSlider(
    label: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
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
            Text(label, style = MaterialTheme.typography.bodyMedium, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (enabled) {
                    Text(format.format(value), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                )
            }
        }
        AnimatedVisibility(visible = enabled) {
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = range,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    }
}

/** A parameter row with a label + switch, and an animated int text field below when enabled. */
@Composable
private fun OaiParamWithInput(
    label: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    value: Int,
    onValueChange: (Int) -> Unit,
    allowNegative: Boolean = false
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (enabled) {
                    Text(value.toString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                )
            }
        }
        AnimatedVisibility(visible = enabled) {
            var text by remember(value) { mutableStateOf(value.toString()) }
            OutlinedTextField(
                value = text,
                onValueChange = { raw ->
                    if (allowNegative && raw == "-") { text = "-"; return@OutlinedTextField }
                    val filtered = if (allowNegative)
                        raw.filter { it.isDigit() || it == '-' }.let { s ->
                            if (s.count { it == '-' } > 1) s.replace("-", "").let { d -> "-$d" } else s
                        }
                    else raw.filter { it.isDigit() }
                    text = filtered
                    filtered.toIntOrNull()?.let(onValueChange)
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = oaiTextFieldColors()
            )
        }
    }
}

/** A single row in the Prompt Order list. Non-marker blocks expand to show an editable text field. */
@Composable
private fun PromptOrderRow(
    item: com.pockettavern.app.domain.model.OaiPromptOrderItem,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggle: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onContentChange: (String) -> Unit,
    onDelete: () -> Unit,
    onRoleChange: (String) -> Unit,
    onInjectionChange: (position: Int, depth: Int) -> Unit
) {
    var expanded by remember(item.id) { mutableStateOf(false) }
    val hasContent = !item.isMarker

    // Local depth text state for the depth number field — keeps the value as typed
    var depthText by remember(item.id, item.injectionDepth) { mutableStateOf(item.injectionDepth.toString()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (item.enabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.background
        )
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Enable checkbox
                Checkbox(
                    checked = item.enabled,
                    onCheckedChange = { onToggle() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary,
                        uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )

                // Label
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (item.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // Subtitle: show injection/role info at a glance
                    val subtitle = buildString {
                        when {
                            item.isMarker -> append("Dynamic content")
                            item.isCustom -> append("Custom")
                        }
                        if (!item.isMarker) {
                            if (isNotEmpty()) append(" · ")
                            append(item.role)
                            if (item.injectionPosition == 1) append(" @ depth ${item.injectionDepth}")
                        }
                    }
                    if (subtitle.isNotEmpty()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (item.isCustom && item.injectionPosition == 0) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.outline
                        )
                    }
                }

                // Delete for custom prompts
                if (item.isCustom) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f), modifier = Modifier.size(16.dp))
                    }
                }

                // Expand/collapse for content blocks
                if (hasContent) {
                    IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(30.dp)) {
                        Icon(
                            if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            if (expanded) "Collapse" else "Edit content",
                            tint = if (expanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Up/Down reorder
                IconButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.size(30.dp)) {
                    Icon(
                        Icons.Default.KeyboardArrowUp, "Move up",
                        tint = if (canMoveUp) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onMoveDown, enabled = canMoveDown, modifier = Modifier.size(30.dp)) {
                    Icon(
                        Icons.Default.KeyboardArrowDown, "Move down",
                        tint = if (canMoveDown) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Expanded section: role, injection mode, content editor
            AnimatedVisibility(visible = expanded && hasContent) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // --- Role row ---
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.role_2), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(60.dp))
                        listOf("system", "user", "assistant").forEach { role ->
                            val selected = item.role == role
                            FilterChip(
                                selected = selected,
                                onClick = { onRoleChange(role) },
                                label = { Text(role, style = MaterialTheme.typography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                    selectedLabelColor = MaterialTheme.colorScheme.primary,
                                    containerColor = MaterialTheme.colorScheme.background,
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }

                    // --- Injection mode row ---
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.inject), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(60.dp))
                        val inOrder = item.injectionPosition == 0
                        FilterChip(
                            selected = inOrder,
                            onClick = { onInjectionChange(0, item.injectionDepth) },
                            label = { Text(stringResource(R.string.in_order), style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                selectedLabelColor = MaterialTheme.colorScheme.primary,
                                containerColor = MaterialTheme.colorScheme.background,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        FilterChip(
                            selected = !inOrder,
                            onClick = { onInjectionChange(1, item.injectionDepth) },
                            label = { Text(stringResource(R.string.in_chat_depth), style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                selectedLabelColor = MaterialTheme.colorScheme.primary,
                                containerColor = MaterialTheme.colorScheme.background,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        // Depth number field — only shown when in-chat depth is selected
                        AnimatedVisibility(visible = !inOrder) {
                            OutlinedTextField(
                                value = depthText,
                                onValueChange = { raw ->
                                    val filtered = raw.filter { it.isDigit() }
                                    depthText = filtered
                                    filtered.toIntOrNull()?.let { d -> onInjectionChange(1, d) }
                                },
                                modifier = Modifier.width(72.dp),
                                singleLine = true,
                                label = { Text(stringResource(R.string.depth), style = MaterialTheme.typography.labelSmall) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = oaiTextFieldColors()
                            )
                        }
                    }

                    // --- Content editor ---
                    OutlinedTextField(
                        value = item.content ?: "",
                        onValueChange = onContentChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 80.dp),
                        placeholder = { Text(stringResource(R.string.enter_prompt_text_use_user_char_macros), color = MaterialTheme.colorScheme.outline) },
                        minLines = 3,
                        colors = oaiTextFieldColors()
                    )

                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OaiPresetDropdown(
    presets: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = presets.getOrNull(selectedIndex) ?: "No presets",
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
            colors = oaiDropdownColors()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            presets.forEachIndexed { index, name ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = { onSelect(index); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun oaiTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
)

@Composable
private fun oaiDropdownColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    focusedTrailingIconColor = MaterialTheme.colorScheme.primary,
    unfocusedTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
)
