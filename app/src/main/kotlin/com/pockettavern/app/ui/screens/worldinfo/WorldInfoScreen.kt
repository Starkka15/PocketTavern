package com.pockettavern.app.ui.screens.worldinfo

import com.pockettavern.app.R
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pockettavern.app.domain.model.WorldInfoEntry
import com.pockettavern.app.domain.model.WorldInfoListItem
import com.pockettavern.app.ui.components.ErrorDialog
import com.pockettavern.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WorldInfoScreen(
    onBack: () -> Unit,
    viewModel: WorldInfoViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val bytes = context.contentResolver.openInputStream(uri)?.readBytes() ?: return@rememberLauncherForActivityResult
        val nameFromUri = uri.lastPathSegment
            ?.substringAfterLast('/')
            ?.substringAfterLast('%')
            ?.removeSuffix(".json")
            ?.ifBlank { null }
        // If the URI segment is just a number (document ID, not filename), fall back to the JSON name field
        val name = if (nameFromUri != null && nameFromUri.all { it.isDigit() }) {
            try {
                val json = org.json.JSONObject(String(bytes))
                json.optString("name").ifBlank { null }
            } catch (_: Exception) { null }
        } else nameFromUri ?: try {
            val json = org.json.JSONObject(String(bytes))
            json.optString("name").ifBlank { null }
        } catch (_: Exception) { null } ?: "Imported Lorebook"
        viewModel.importJson(name ?: "Imported Lorebook", bytes)
    }

    Scaffold(
        floatingActionButton = {
            if (uiState.selectedLorebook == null) {
                FloatingActionButton(onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) }) {
                    Icon(Icons.Default.FileOpen, contentDescription = stringResource(R.string.import_lorebook))
                }
            } else {
                FloatingActionButton(onClick = { viewModel.addNewEntry() }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_entry))
                }
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (uiState.selectedLorebook != null)
                            uiState.selectedLorebook!!
                        else
                            "World Info / Lorebooks"
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (uiState.selectedLorebook != null) {
                                viewModel.clearSelection()
                            } else {
                                onBack()
                            }
                        }
                    ) {
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
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.selectedLorebook != null) {
            WorldInfoEntriesList(
                entries = uiState.entries,
                isLoading = uiState.isLoadingEntries,
                expandedEntryId = uiState.expandedEntryId,
                onToggleExpand = { viewModel.toggleEntryExpanded(it) },
                onEditEntry = { viewModel.startEditEntry(it) },
                modifier = Modifier.padding(padding)
            )
        } else {
            LorebookList(
                lorebooks = uiState.lorebooks,
                onSelect = { viewModel.selectLorebook(it.name) },
                onDelete = { viewModel.requestDeleteLorebook(it.name) },
                modifier = Modifier.padding(padding)
            )
        }
    }

    // Delete lorebook confirm dialog
    uiState.deleteLorebookConfirm?.let { name ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteLorebook() },
            title = { Text(stringResource(R.string.delete_lorebook)) },
            text = { Text(stringResource(R.string.delete_named_confirm, name)) },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmDeleteLorebook() }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDeleteLorebook() }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    // Entry edit dialog
    uiState.editingEntry?.let { entry ->
        WorldInfoEntryEditDialog(
            entry = entry,
            onSave = { viewModel.saveEntry(it) },
            onDelete = { viewModel.deleteEntry(entry.uid) },
            onDismiss = { viewModel.dismissEditEntry() }
        )
    }

    uiState.error?.let { error ->
        ErrorDialog(message = error, onDismiss = { viewModel.clearError() })
    }
}

@Composable
private fun LorebookList(
    lorebooks: List<WorldInfoListItem>,
    onSelect: (WorldInfoListItem) -> Unit,
    onDelete: (WorldInfoListItem) -> Unit,
    modifier: Modifier = Modifier
) {
    if (lorebooks.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
                Text(stringResource(R.string.no_lorebooks_found),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(stringResource(R.string.import_a_json_lorebook_or_create_one_in_silly),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(lorebooks) { lorebook ->
                LorebookCard(
                    lorebook = lorebook,
                    onClick = { onSelect(lorebook) },
                    onDelete = { onDelete(lorebook) }
                )
            }
        }
    }
}

@Composable
private fun LorebookCard(
    lorebook: WorldInfoListItem,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.MenuBook,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = lorebook.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(text = stringResource(R.string.tap_to_view_entries),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete_lorebook_2),
                    tint = MaterialTheme.colorScheme.error
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun WorldInfoEntriesList(
    entries: List<WorldInfoEntry>,
    isLoading: Boolean,
    expandedEntryId: String?,
    onToggleExpand: (String) -> Unit,
    onEditEntry: (WorldInfoEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    if (isLoading) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (entries.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Notes,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
                Text(stringResource(R.string.no_entries_yet),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(stringResource(R.string.tap_to_add_an_entry),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatItem("Total", entries.size.toString())
                        StatItem("Enabled", entries.count { it.enabled }.toString())
                        StatItem("Constant", entries.count { it.constant }.toString())
                    }
                }
            }
            items(entries, key = { it.uid }) { entry ->
                WorldInfoEntryCard(
                    entry = entry,
                    isExpanded = expandedEntryId == entry.uid,
                    onToggleExpand = { onToggleExpand(entry.uid) },
                    onEdit = { onEditEntry(entry) }
                )
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WorldInfoEntryCard(
    entry: WorldInfoEntry,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onEdit: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (entry.enabled)
                MaterialTheme.colorScheme.surfaceContainerLow
            else
                MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.clickable(onClick = onToggleExpand)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (entry.constant) StatusBadge("C", MaterialTheme.colorScheme.primary, "Constant")
                    if (entry.selective) StatusBadge("S", MaterialTheme.colorScheme.primary, "Selective")
                    if (!entry.enabled) StatusBadge("D", MaterialTheme.colorScheme.error, "Disabled")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.comment.ifBlank { "Entry ${entry.uid}" },
                        style = MaterialTheme.typography.titleSmall,
                        color = if (entry.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (entry.key.isEmpty()) "No keys (constant)" else "Keys: ${entry.key.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "#${entry.order}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (entry.key.isNotEmpty()) {
                        DetailSection("Primary Keys") {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                entry.key.forEach { key -> KeyChip(key, MaterialTheme.colorScheme.primary) }
                            }
                        }
                    }
                    if (entry.selective && entry.keysecondary.isNotEmpty()) {
                        DetailSection("Secondary Keys (AND)") {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                entry.keysecondary.forEach { key -> KeyChip(key, MaterialTheme.colorScheme.primary) }
                            }
                        }
                    }
                    DetailSection("Content") {
                        Text(
                            text = entry.content.ifBlank { "(empty)" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (entry.content.isNotBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        SettingItem("Position", getPositionName(entry.position))
                        SettingItem("Depth", entry.depth.toString())
                        SettingItem("Probability", "${entry.probability}%")
                    }
                    if (entry.group.isNotBlank()) {
                        Text(
                            text = "Group: ${entry.group}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    // Edit button
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        OutlinedButton(
                            onClick = onEdit,
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.edit), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorldInfoEntryEditDialog(
    entry: WorldInfoEntry,
    onSave: (WorldInfoEntry) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var comment by remember(entry.uid) { mutableStateOf(entry.comment) }
    var primaryKeys by remember(entry.uid) { mutableStateOf(entry.key.joinToString(", ")) }
    var secondaryKeys by remember(entry.uid) { mutableStateOf(entry.keysecondary.joinToString(", ")) }
    var content by remember(entry.uid) { mutableStateOf(entry.content) }
    var constant by remember(entry.uid) { mutableStateOf(entry.constant) }
    var enabled by remember(entry.uid) { mutableStateOf(entry.enabled) }
    var selective by remember(entry.uid) { mutableStateOf(entry.selective) }
    var orderText by remember(entry.uid) { mutableStateOf(entry.order.toString()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    fun parseKeys(raw: String) = raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (entry.content.isEmpty() && entry.comment == "New Entry") "New Entry" else "Edit Entry") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text(stringResource(R.string.name_comment)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = primaryKeys,
                    onValueChange = { primaryKeys = it },
                    label = { Text(stringResource(R.string.primary_keys_comma_separated)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = selective, onCheckedChange = { selective = it })
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.selective_and_with_secondary_keys), style = MaterialTheme.typography.bodyMedium)
                }
                if (selective) {
                    OutlinedTextField(
                        value = secondaryKeys,
                        onValueChange = { secondaryKeys = it },
                        label = { Text(stringResource(R.string.secondary_keys_comma_separated)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text(stringResource(R.string.content)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                    minLines = 4
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = constant, onCheckedChange = { constant = it })
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.constant_always_inject), style = MaterialTheme.typography.bodyMedium)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.enabled), style = MaterialTheme.typography.bodyMedium)
                }
                OutlinedTextField(
                    value = orderText,
                    onValueChange = { orderText = it.filter { c -> c.isDigit() } },
                    label = { Text(stringResource(R.string.order)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                // Delete button
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.delete_entry))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    entry.copy(
                        comment = comment,
                        key = parseKeys(primaryKeys),
                        keysecondary = parseKeys(secondaryKeys),
                        content = content,
                        constant = constant,
                        enabled = enabled,
                        selective = selective,
                        order = orderText.toIntOrNull() ?: entry.order
                    )
                )
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.delete_entry)) },
            text = { Text(stringResource(R.string.delete_named_confirm, entry.comment.ifBlank { stringResource(R.string.this_entry) })) },
            confirmButton = {
                TextButton(onClick = { onDelete() }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun StatusBadge(
    text: String,
    color: androidx.compose.ui.graphics.Color,
    contentDescription: String
) {
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun KeyChip(key: String, color: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text = key, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
private fun DetailSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        content()
    }
}

@Composable
private fun SettingItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

private fun getPositionName(position: Int): String {
    return when (position) {
        0 -> "Before Char"
        1 -> "After Char"
        2 -> "Top of AN"
        3 -> "Bottom of AN"
        4 -> "@ Depth"
        else -> "Unknown"
    }
}
