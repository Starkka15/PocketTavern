package com.pockettavern.app.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pockettavern.app.ui.theme.*

/** Which API mode this settings item applies to. Null = always applicable. */
enum class SettingsMode { CHAT_COMPLETION, TEXT_GEN }

data class SettingsItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val requiresConnection: Boolean = true,
    /** If set, item is dimmed when the current API mode differs. */
    val mode: SettingsMode? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsHubScreen(
    onBack: () -> Unit,
    onNavigateToConnection: () -> Unit,
    onNavigateToApiConfig: () -> Unit,
    onNavigateToTextGen: () -> Unit,
    onNavigateToFormatting: () -> Unit,
    onNavigateToWorldInfo: () -> Unit,
    onNavigateToContextSettings: () -> Unit,
    onNavigateToPersonas: () -> Unit,
    onNavigateToSetupGuide: () -> Unit = {},
    onNavigateToStImport: () -> Unit = {},
    onNavigateToOaiPresets: () -> Unit = {},
    onNavigateToExtensions: () -> Unit = {},
    onNavigateToConnectionProfiles: () -> Unit = {},
    onNavigateToTheme: () -> Unit = {},
    onNavigateToTtsSettings: () -> Unit = {},
    onNavigateToImageGen: () -> Unit = {},
    onNavigateToPtExport: () -> Unit = {},
    viewModel: SettingsHubViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isConnected = uiState.isConnected
    val currentPersonaName = uiState.currentPersonaName
    val usesChatCompletions = uiState.usesChatCompletions

    // Refresh when screen appears (for when returning from sub-screens)
    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    // ── Connection ────────────────────────────────────────────────
    val connectionItems = remember(onNavigateToApiConfig, onNavigateToConnectionProfiles, onNavigateToImageGen) {
        listOf(
            SettingsItem(
                title = "API Configuration",
                subtitle = "Select API type and model",
                icon = Icons.Default.Cloud,
                onClick = onNavigateToApiConfig
            ),
            SettingsItem(
                title = "Connection Profiles",
                subtitle = "Save and switch between API configurations",
                icon = Icons.Default.SwitchAccount,
                onClick = onNavigateToConnectionProfiles,
                requiresConnection = false
            ),
            SettingsItem(
                title = "Image Generation",
                subtitle = "Configure image generation backends",
                icon = Icons.Default.Image,
                onClick = onNavigateToImageGen,
                requiresConnection = false
            )
        )
    }

    // ── Generation ─────────────────────────────────────────────────
    val generationItems = remember(onNavigateToTextGen, onNavigateToOaiPresets, onNavigateToFormatting) {
        listOf(
            SettingsItem(
                title = "Text Generation",
                subtitle = "Sampler settings and presets (KoboldCpp / local)",
                icon = Icons.Default.Tune,
                onClick = onNavigateToTextGen,
                requiresConnection = false,
                mode = SettingsMode.TEXT_GEN
            ),
            SettingsItem(
                title = "Chat Completion Presets",
                subtitle = "Sampling presets for OpenAI-compatible APIs",
                icon = Icons.Default.AutoAwesome,
                onClick = onNavigateToOaiPresets,
                requiresConnection = false,
                mode = SettingsMode.CHAT_COMPLETION
            ),
            SettingsItem(
                title = "Formatting",
                subtitle = "Instruct templates and system prompts",
                icon = Icons.Default.TextFormat,
                onClick = onNavigateToFormatting,
                requiresConnection = false,
                mode = SettingsMode.TEXT_GEN
            )
        )
    }

    // ── World & Characters ─────────────────────────────────────────
    val worldItems = remember(currentPersonaName, onNavigateToWorldInfo, onNavigateToContextSettings, onNavigateToPersonas) {
        listOf(
            SettingsItem(
                title = "World Info / Lorebooks",
                subtitle = "View and manage lorebook entries",
                icon = Icons.AutoMirrored.Filled.MenuBook,
                onClick = onNavigateToWorldInfo
            ),
            SettingsItem(
                title = "Context Settings",
                subtitle = "Author's note configuration",
                icon = Icons.AutoMirrored.Filled.StickyNote2,
                onClick = onNavigateToContextSettings
            ),
            SettingsItem(
                title = "Personas",
                subtitle = currentPersonaName?.let { "Current: $it" } ?: "Manage user personas and avatars",
                icon = Icons.Default.Person,
                onClick = onNavigateToPersonas
            )
        )
    }

    // ── Appearance & Audio ─────────────────────────────────────────
    val appearanceItems = remember(onNavigateToTheme, onNavigateToTtsSettings) {
        listOf(
            SettingsItem(
                title = "Appearance",
                subtitle = "Import and apply SillyTavern themes",
                icon = Icons.Default.Palette,
                onClick = onNavigateToTheme,
                requiresConnection = false
            ),
            SettingsItem(
                title = "Text-to-Speech",
                subtitle = "Voice synthesis for chat messages",
                icon = Icons.Default.RecordVoiceOver,
                onClick = onNavigateToTtsSettings,
                requiresConnection = false
            )
        )
    }

    // ── Utilities ──────────────────────────────────────────────────
    val utilityItems = remember(onNavigateToExtensions, onNavigateToStImport, onNavigateToPtExport, onNavigateToSetupGuide) {
        listOf(
            SettingsItem(
                title = "Extensions",
                subtitle = "Quick reply, regex rules, token counter and more",
                icon = Icons.Default.Extension,
                onClick = onNavigateToExtensions,
                requiresConnection = false
            ),
            SettingsItem(
                title = "Import from SillyTavern",
                subtitle = "Migrate characters, chats, and lorebooks",
                icon = Icons.Default.Download,
                onClick = onNavigateToStImport,
                requiresConnection = false
            ),
            SettingsItem(
                title = "Export Data",
                subtitle = "Export characters, chats, and lorebooks to a folder",
                icon = Icons.Default.Upload,
                onClick = onNavigateToPtExport,
                requiresConnection = false
            ),
            SettingsItem(
                title = "Help",
                subtitle = "Guide, controls, and troubleshooting",
                icon = Icons.AutoMirrored.Filled.Help,
                onClick = onNavigateToSetupGuide,
                requiresConnection = false
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            settingsSection("Connection", connectionItems, isConnected, usesChatCompletions, isFirst = true)
            settingsSection("Generation", generationItems, isConnected, usesChatCompletions)
            settingsSection("World & Characters", worldItems, isConnected, usesChatCompletions)
            settingsSection("Appearance & Audio", appearanceItems, isConnected, usesChatCompletions)
            settingsSection("Utilities", utilityItems, isConnected, usesChatCompletions)

            // Connection status footer
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (isConnected) Icons.Default.CheckCircle else Icons.Default.Error,
                        contentDescription = null,
                        tint = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isConnected) "Connected" else "Not connected",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsListItem(
    item: SettingsItem,
    enabled: Boolean,
    modeActive: Boolean = true
) {
    val fullyActive = enabled && modeActive
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = fullyActive, onClick = item.onClick),
        color = androidx.compose.ui.graphics.Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = if (fullyActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Text content
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (fullyActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                )
                val subtitleText = if (!modeActive && item.mode != null) {
                    val modeLabel = if (item.mode == SettingsMode.CHAT_COMPLETION) "chat completion" else "text generation"
                    "${item.subtitle} · not used in current mode"
                } else {
                    item.subtitle
                }
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (fullyActive) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outline
                )
            }

            // Chevron
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = if (fullyActive) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outline
            )
        }
    }

    HorizontalDivider(
        modifier = Modifier.padding(start = 72.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    )
}

private fun LazyListScope.settingsSection(
    title: String,
    items: List<SettingsItem>,
    isConnected: Boolean,
    usesChatCompletions: Boolean,
    isFirst: Boolean = false
) {
    item(key = "header_$title") {
        SectionHeader(text = title, isFirst = isFirst)
    }
    items(items, key = { it.title }) { item ->
        val enabled = !item.requiresConnection || isConnected
        val modeActive = when (item.mode) {
            SettingsMode.CHAT_COMPLETION -> usesChatCompletions
            SettingsMode.TEXT_GEN -> !usesChatCompletions
            null -> true
        }
        SettingsListItem(item = item, enabled = enabled, modeActive = modeActive)
    }
}

@Composable
private fun SectionHeader(text: String, isFirst: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = if (isFirst) 8.dp else 24.dp, bottom = 8.dp)
    )
}
