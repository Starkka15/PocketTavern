package com.pockettavern.app.ui.screens.settings

import com.pockettavern.app.R
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
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
import android.app.Activity
import androidx.compose.ui.platform.LocalContext
import com.pockettavern.app.ui.theme.*
import com.pockettavern.app.util.LocaleHelper

/** Which API mode this settings item applies to. Null = always applicable. */
enum class SettingsMode { CHAT_COMPLETION, TEXT_GEN }

data class SettingsItem(
    @StringRes val title: Int,
    @StringRes val subtitle: Int,
    /** Optional format arg for [subtitle] (e.g. current persona name). */
    val subtitleArg: String? = null,
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
    onNavigateToBackup: () -> Unit = {},
    onNavigateToStorageBrowser: () -> Unit = {},
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

    var showLanguageDialog by remember { mutableStateOf(false) }
    if (showLanguageDialog) {
        LanguageDialog(onDismiss = { showLanguageDialog = false })
    }

    // ── Connection ────────────────────────────────────────────────
    val connectionItems = remember(onNavigateToApiConfig, onNavigateToConnectionProfiles, onNavigateToImageGen) {
        listOf(
            SettingsItem(
                title = R.string.api_configuration,
                subtitle = R.string.select_api_type_and_model,
                icon = Icons.Default.Cloud,
                onClick = onNavigateToApiConfig
            ),
            SettingsItem(
                title = R.string.connection_profiles,
                subtitle = R.string.save_switch_api_configs,
                icon = Icons.Default.SwitchAccount,
                onClick = onNavigateToConnectionProfiles,
                requiresConnection = false
            ),
            SettingsItem(
                title = R.string.image_generation,
                subtitle = R.string.configure_imagegen_backends,
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
                title = R.string.text_generation,
                subtitle = R.string.sampler_settings_presets,
                icon = Icons.Default.Tune,
                onClick = onNavigateToTextGen,
                requiresConnection = false,
                mode = SettingsMode.TEXT_GEN
            ),
            SettingsItem(
                title = R.string.chat_completion_presets,
                subtitle = R.string.sampling_presets_oai,
                icon = Icons.Default.AutoAwesome,
                onClick = onNavigateToOaiPresets,
                requiresConnection = false,
                mode = SettingsMode.CHAT_COMPLETION
            ),
            SettingsItem(
                title = R.string.formatting,
                subtitle = R.string.instruct_templates_prompts,
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
                title = R.string.world_info_lorebooks,
                subtitle = R.string.view_manage_lorebooks,
                icon = Icons.AutoMirrored.Filled.MenuBook,
                onClick = onNavigateToWorldInfo
            ),
            SettingsItem(
                title = R.string.context_settings,
                subtitle = R.string.authors_note_config,
                icon = Icons.AutoMirrored.Filled.StickyNote2,
                onClick = onNavigateToContextSettings
            ),
            SettingsItem(
                title = R.string.personas,
                subtitle = if (currentPersonaName != null) R.string.current_named else R.string.manage_personas_avatars,
                subtitleArg = currentPersonaName,
                icon = Icons.Default.Person,
                onClick = onNavigateToPersonas
            )
        )
    }

    // ── Appearance & Audio ─────────────────────────────────────────
    val appearanceItems = remember(onNavigateToTheme, onNavigateToTtsSettings) {
        listOf(
            SettingsItem(
                title = R.string.appearance,
                subtitle = R.string.import_apply_themes,
                icon = Icons.Default.Palette,
                onClick = onNavigateToTheme,
                requiresConnection = false
            ),
            SettingsItem(
                title = R.string.text_to_speech,
                subtitle = R.string.voice_synthesis_chat,
                icon = Icons.Default.RecordVoiceOver,
                onClick = onNavigateToTtsSettings,
                requiresConnection = false
            ),
            SettingsItem(
                title = R.string.language,
                subtitle = R.string.language_subtitle,
                icon = Icons.Default.Language,
                onClick = { showLanguageDialog = true },
                requiresConnection = false
            )
        )
    }

    // ── Utilities ──────────────────────────────────────────────────
    val utilityItems = remember(onNavigateToExtensions, onNavigateToStImport, onNavigateToSetupGuide, onNavigateToBackup, onNavigateToStorageBrowser) {
        listOf(
            SettingsItem(
                title = R.string.extensions,
                subtitle = R.string.extensions_subtitle,
                icon = Icons.Default.Extension,
                onClick = onNavigateToExtensions,
                requiresConnection = false
            ),
            SettingsItem(
                title = R.string.import_from_sillytavern,
                subtitle = R.string.migrate_chars_chats_lore,
                icon = Icons.Default.Download,
                onClick = onNavigateToStImport,
                requiresConnection = false
            ),
            SettingsItem(
                title = R.string.character_storage,
                subtitle = R.string.browse_delete_rescan,
                icon = Icons.Default.Storage,
                onClick = onNavigateToStorageBrowser,
                requiresConnection = false
            ),
            SettingsItem(
                title = R.string.backup_restore,
                subtitle = R.string.export_restore_zip,
                icon = Icons.Default.Save,
                onClick = onNavigateToBackup,
                requiresConnection = false
            ),
            SettingsItem(
                title = R.string.help,
                subtitle = R.string.guide_controls_troubleshooting,
                icon = Icons.AutoMirrored.Filled.Help,
                onClick = onNavigateToSetupGuide,
                requiresConnection = false
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
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
            settingsSection(R.string.connection, connectionItems, isConnected, usesChatCompletions, isFirst = true)
            settingsSection(R.string.generation, generationItems, isConnected, usesChatCompletions)
            settingsSection(R.string.world_characters, worldItems, isConnected, usesChatCompletions)
            settingsSection(R.string.appearance_audio, appearanceItems, isConnected, usesChatCompletions)
            settingsSection(R.string.utilities, utilityItems, isConnected, usesChatCompletions)

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
                        text = if (isConnected) stringResource(R.string.connected) else stringResource(R.string.not_connected),
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
                    text = stringResource(item.title),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (fullyActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                )
                val subtitleBase = item.subtitleArg?.let { stringResource(item.subtitle, it) }
                    ?: stringResource(item.subtitle)
                val subtitleText = if (!modeActive && item.mode != null) {
                    stringResource(R.string.subtitle_not_used_in_mode, subtitleBase)
                } else {
                    subtitleBase
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
    @StringRes title: Int,
    items: List<SettingsItem>,
    isConnected: Boolean,
    usesChatCompletions: Boolean,
    isFirst: Boolean = false
) {
    item(key = "header_$title") {
        SectionHeader(text = stringResource(title), isFirst = isFirst)
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
private fun LanguageDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val current = LocaleHelper.getLanguage(context)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.language)) },
        text = {
            Column {
                LocaleHelper.AVAILABLE.forEach { tag ->
                    val label = if (tag.isEmpty()) {
                        stringResource(R.string.system_default)
                    } else {
                        // language name shown in its own language, per i18n convention
                        val locale = java.util.Locale.forLanguageTag(tag)
                        locale.getDisplayName(locale).replaceFirstChar { it.uppercase(locale) }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                LocaleHelper.setLanguage(context, tag)
                                onDismiss()
                                (context as? Activity)?.recreate()
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = tag == current, onClick = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
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
