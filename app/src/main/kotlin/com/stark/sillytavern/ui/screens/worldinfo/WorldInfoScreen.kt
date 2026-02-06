package com.stark.sillytavern.ui.screens.worldinfo

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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stark.sillytavern.domain.model.WorldInfoEntry
import com.stark.sillytavern.domain.model.WorldInfoListItem
import com.stark.sillytavern.ui.components.ErrorDialog
import com.stark.sillytavern.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WorldInfoScreen(
    onBack: () -> Unit,
    viewModel: WorldInfoViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
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
        } else if (uiState.selectedLorebook != null) {
            // Show entries for selected lorebook
            WorldInfoEntriesList(
                entries = uiState.entries,
                isLoading = uiState.isLoadingEntries,
                expandedEntryId = uiState.expandedEntryId,
                onToggleExpand = { viewModel.toggleEntryExpanded(it) },
                modifier = Modifier.padding(padding)
            )
        } else {
            // Show lorebook list
            LorebookList(
                lorebooks = uiState.lorebooks,
                onSelect = { viewModel.selectLorebook(it.name) },
                modifier = Modifier.padding(padding)
            )
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

@Composable
private fun LorebookList(
    lorebooks: List<WorldInfoListItem>,
    onSelect: (WorldInfoListItem) -> Unit,
    modifier: Modifier = Modifier
) {
    if (lorebooks.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = TextTertiary
                )
                Text(
                    "No lorebooks found",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextSecondary
                )
                Text(
                    "Create lorebooks in SillyTavern to see them here",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextTertiary
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
                    onClick = { onSelect(lorebook) }
                )
            }
        }
    }
}

@Composable
private fun LorebookCard(
    lorebook: WorldInfoListItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = DarkCard
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.MenuBook,
                contentDescription = null,
                tint = AccentGreen,
                modifier = Modifier.size(32.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = lorebook.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Text(
                    text = "Tap to view entries",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = TextSecondary
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
    modifier: Modifier = Modifier
) {
    if (isLoading) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else if (entries.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Notes,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = TextTertiary
                )
                Text(
                    "No entries in this lorebook",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextSecondary
                )
            }
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Summary header
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = DarkSurfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
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
                    onToggleExpand = { onToggleExpand(entry.uid) }
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
            color = AccentGreen
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WorldInfoEntryCard(
    entry: WorldInfoEntry,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (entry.enabled) DarkCard else DarkCard.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.clickable(onClick = onToggleExpand)
        ) {
            // Header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Status indicators
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (entry.constant) {
                        StatusBadge("C", AccentGreen, "Constant")
                    }
                    if (entry.selective) {
                        StatusBadge("S", AccentBlue, "Selective")
                    }
                    if (!entry.enabled) {
                        StatusBadge("D", ErrorRed, "Disabled")
                    }
                }

                // Entry info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.comment.ifBlank { "Entry ${entry.uid}" },
                        style = MaterialTheme.typography.titleSmall,
                        color = if (entry.enabled) TextPrimary else TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Keys: ${entry.key.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Order badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(DarkSurfaceVariant)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "#${entry.order}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }

                // Expand icon
                Icon(
                    if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = TextSecondary
                )
            }

            // Expanded content
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkInputBackground)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Keys
                    if (entry.key.isNotEmpty()) {
                        DetailSection("Primary Keys") {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                entry.key.forEach { key ->
                                    KeyChip(key, AccentGreen)
                                }
                            }
                        }
                    }

                    // Secondary keys (if selective)
                    if (entry.selective && entry.keysecondary.isNotEmpty()) {
                        DetailSection("Secondary Keys (AND)") {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                entry.keysecondary.forEach { key ->
                                    KeyChip(key, AccentBlue)
                                }
                            }
                        }
                    }

                    // Content
                    DetailSection("Content") {
                        Text(
                            text = entry.content.ifBlank { "(empty)" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (entry.content.isNotBlank()) TextPrimary else TextTertiary
                        )
                    }

                    // Settings
                    HorizontalDivider(color = DarkSurfaceVariant)
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
                            color = TextTertiary
                        )
                    }
                }
            }
        }
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
        Text(
            text = key,
            style = MaterialTheme.typography.labelMedium,
            color = color
        )
    }
}

@Composable
private fun DetailSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = TextSecondary
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
            color = TextPrimary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary
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
