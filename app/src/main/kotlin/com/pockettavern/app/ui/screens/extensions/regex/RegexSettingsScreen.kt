package com.pockettavern.app.ui.screens.extensions.regex

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.pockettavern.app.domain.model.RegexRule

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegexSettingsScreen(
    onBack: () -> Unit,
    viewModel: RegexSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    if (uiState.showDialog) {
        RegexRuleDialog(uiState, viewModel)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Regex Rules") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::showAddDialog) {
                        Icon(Icons.Default.Add, contentDescription = "Add rule")
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.rules.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No rules yet", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Rules are applied to AI output before display,\nor to your messages before sending.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = viewModel::showAddDialog) { Text("Add Rule") }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.rules, key = { it.id }) { rule ->
                    RegexRuleCard(
                        rule = rule,
                        onToggle = { viewModel.toggleRuleEnabled(rule.id) },
                        onEdit = { viewModel.showEditDialog(rule) },
                        onDelete = { viewModel.deleteRule(rule.id) }
                    )
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RegexRuleCard(
    rule: RegexRule,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(rule.name, style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    buildString {
                        append(if (rule.isRegex) "Regex: " else "Text: ")
                        append(rule.pattern.take(40))
                        if (rule.pattern.length > 40) append("…")
                        append(" → ")
                        append(rule.replacement.take(20).ifBlank { "(remove)" })
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (rule.applyToOutput) FilterChip(
                        selected = true, onClick = {}, label = { Text("Output") },
                        modifier = Modifier.height(24.dp)
                    )
                    if (rule.applyToInput) FilterChip(
                        selected = true, onClick = {}, label = { Text("Input") },
                        modifier = Modifier.height(24.dp)
                    )
                }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(18.dp))
            }
            Switch(checked = rule.enabled, onCheckedChange = { onToggle() })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RegexRuleDialog(
    uiState: RegexSettingsUiState,
    viewModel: RegexSettingsViewModel
) {
    AlertDialog(
        onDismissRequest = viewModel::hideDialog,
        title = { Text(if (uiState.editingRuleId != null) "Edit Rule" else "New Rule") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = uiState.nameField,
                    onValueChange = viewModel::updateName,
                    label = { Text("Rule name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Regex pattern", modifier = Modifier.weight(1f))
                    Switch(checked = uiState.isRegexField, onCheckedChange = viewModel::toggleIsRegex)
                }
                OutlinedTextField(
                    value = uiState.patternField,
                    onValueChange = viewModel::updatePattern,
                    label = { Text(if (uiState.isRegexField) "Pattern (regex)" else "Find text") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = uiState.replacementField,
                    onValueChange = viewModel::updateReplacement,
                    label = { Text("Replace with") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Apply to toggles
                Text("Apply to:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = uiState.applyToOutputField, onCheckedChange = viewModel::toggleApplyToOutput)
                    Text("AI output", modifier = Modifier.padding(start = 4.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Checkbox(checked = uiState.applyToInputField, onCheckedChange = viewModel::toggleApplyToInput)
                    Text("My input", modifier = Modifier.padding(start = 4.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = uiState.caseInsensitiveField, onCheckedChange = viewModel::toggleCaseInsensitive)
                    Text("Case insensitive", modifier = Modifier.padding(start = 4.dp))
                }

                HorizontalDivider()

                // Live preview
                OutlinedTextField(
                    value = uiState.testInput,
                    onValueChange = viewModel::updateTestInput,
                    label = { Text("Test input") },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                if (uiState.testOutput.isNotBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = uiState.testOutput,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = viewModel::confirmRule) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = viewModel::hideDialog) { Text("Cancel") }
        }
    )
}
