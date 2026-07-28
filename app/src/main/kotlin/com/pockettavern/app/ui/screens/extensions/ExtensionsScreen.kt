package com.pockettavern.app.ui.screens.extensions

import com.pockettavern.app.R
import androidx.compose.ui.res.stringResource
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material3.*
import com.pockettavern.app.ui.screens.extensions.CardExtensionUiItem
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.filled.SmartToy
import com.pockettavern.app.extensions.JsExtension
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionsScreen(
    onBack: () -> Unit,
    onNavigateToQuickReply: () -> Unit,
    onNavigateToRegex: () -> Unit,
    viewModel: ExtensionsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showInstallDialog by remember { mutableStateOf(false) }

    // File picker for importing .js or .zip files
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.installFromFile(uri)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.extensions)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Native Extensions ─────────────────────────────────────────────
            item {
                Text(text = stringResource(R.string.native_extensions),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            item {
                ExtensionCard(
                    icon = { Icon(Icons.Default.Chat, contentDescription = null) },
                    title = "Quick Reply",
                    description = "Preset message buttons shown above the chat input for quick responses.",
                    enabled = uiState.quickReplyEnabled,
                    onEnabledChange = viewModel::setQuickReplyEnabled,
                    onSettingsClick = onNavigateToQuickReply
                )
            }
            item {
                ExtensionCard(
                    icon = { Icon(Icons.Default.Code, contentDescription = null) },
                    title = "Regex",
                    description = "Find-and-replace rules applied to AI output or your messages before sending.",
                    enabled = uiState.regexEnabled,
                    onEnabledChange = viewModel::setRegexEnabled,
                    onSettingsClick = onNavigateToRegex
                )
            }
            item {
                ExtensionCard(
                    icon = { Icon(Icons.Default.Numbers, contentDescription = null) },
                    title = "Token Counter",
                    description = "Show an estimated token count while you type (approximately 4 chars per token).",
                    enabled = uiState.tokenCounterEnabled,
                    onEnabledChange = viewModel::setTokenCounterEnabled,
                    onSettingsClick = null
                )
            }

            // ── JavaScript Extensions ─────────────────────────────────────────
            item { Spacer(modifier = Modifier.height(8.dp)) }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = stringResource(R.string.javascript_extensions),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    FilledTonalIconButton(onClick = { showInstallDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.install_extension_2))
                    }
                }
            }

            if (uiState.jsExtensions.isEmpty()) {
                item {
                    Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No JavaScript extensions installed.\nTap + to install from a URL or file.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(uiState.jsExtensions, key = { it.id }) { ext ->
                    JsExtensionCard(
                        ext = ext,
                        settings = uiState.jsExtensionSettings[ext.id] ?: emptyMap(),
                        onEnabledChange = { viewModel.setJsExtensionEnabled(ext.id, it) },
                        onUninstall = { viewModel.uninstall(ext.id) },
                        onSettingChange = { key, value -> viewModel.updateJsSetting(ext.id, key, value) }
                    )
                }
            }

            // ── Card Extensions ───────────────────────────────────────────────
            item { Spacer(modifier = Modifier.height(8.dp)) }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = stringResource(R.string.card_extensions),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    if (uiState.cardExtensionsLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }
            }
            if (!uiState.cardExtensionsLoading && uiState.cardExtensions.isEmpty()) {
                item {
                    Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = stringResource(R.string.no_character_cards_with_embedded_scripts_foun),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(uiState.cardExtensions, key = { it.meta.characterFile }) { item ->
                    CardExtensionCard(
                        item = item,
                        onEnabledChange = { viewModel.setCardExtensionEnabled(item.meta.characterFile, it) }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    // Install dialog
    if (showInstallDialog) {
        InstallExtensionDialog(
            isInstalling = uiState.isInstalling,
            error = uiState.installError,
            onInstallUrl = { url ->
                viewModel.installFromUrl(url)
                showInstallDialog = false
            },
            onImportFile = {
                showInstallDialog = false
                filePickerLauncher.launch("*/*")
            },
            onDismiss = {
                showInstallDialog = false
                viewModel.clearInstallError()
            }
        )
    }
}

// ── Composables ───────────────────────────────────────────────────────────────

@Composable
private fun ExtensionCard(
    icon: @Composable () -> Unit,
    title: String,
    description: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onSettingsClick: (() -> Unit)?
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                icon()
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = enabled, onCheckedChange = onEnabledChange)
            }
            if (onSettingsClick != null) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = onSettingsClick,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(stringResource(R.string.settings))
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun JsExtensionCard(
    ext: JsExtension,
    settings: Map<String, JsonPrimitive>,
    onEnabledChange: (Boolean) -> Unit,
    onUninstall: () -> Unit,
    onSettingChange: (String, JsonPrimitive) -> Unit
) {
    var showUninstallDialog by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Extension, contentDescription = null)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(ext.name, style = MaterialTheme.typography.titleMedium)
                        if (ext.version.isNotBlank()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "v${ext.version}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (ext.description.isNotBlank()) {
                        Text(
                            ext.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (ext.author.isNotBlank()) {
                        Text(
                            "by ${ext.author}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(checked = ext.enabled, onCheckedChange = onEnabledChange)
            }

            // Settings panel
            if (settings.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(
                    onClick = { showSettings = !showSettings },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(if (showSettings) "Hide Settings" else "Settings")
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        if (showSettings) Icons.Default.Extension else Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
                if (showSettings) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        settings.entries.sortedBy { it.key }.forEach { (key, value) ->
                            val label = camelCaseToLabel(key)
                            when {
                                value.isString -> {
                                    // String setting
                                    var text by remember(key, value) { mutableStateOf(value.content) }
                                    OutlinedTextField(
                                        value = text,
                                        onValueChange = { newVal ->
                                            text = newVal
                                            onSettingChange(key, JsonPrimitive(newVal))
                                        },
                                        label = { Text(label) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        textStyle = MaterialTheme.typography.bodySmall
                                    )
                                }
                                value.booleanOrNull != null -> {
                                    // Boolean toggle
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            label,
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Switch(
                                            checked = value.booleanOrNull == true,
                                            onCheckedChange = { onSettingChange(key, JsonPrimitive(it)) }
                                        )
                                    }
                                }
                                value.intOrNull != null -> {
                                    // Integer setting
                                    var text by remember(key, value) { mutableStateOf(value.content) }
                                    OutlinedTextField(
                                        value = text,
                                        onValueChange = { newVal ->
                                            text = newVal
                                            newVal.toIntOrNull()?.let { onSettingChange(key, JsonPrimitive(it)) }
                                        },
                                        label = { Text(label) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        textStyle = MaterialTheme.typography.bodySmall,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                    )
                                }
                                value.doubleOrNull != null -> {
                                    // Float/double setting
                                    var text by remember(key, value) { mutableStateOf(value.content) }
                                    OutlinedTextField(
                                        value = text,
                                        onValueChange = { newVal ->
                                            text = newVal
                                            newVal.toDoubleOrNull()?.let { onSettingChange(key, JsonPrimitive(it)) }
                                        },
                                        label = { Text(label) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        textStyle = MaterialTheme.typography.bodySmall,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (!showSettings || settings.isEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (ext.sourceUrl.isNotBlank()) {
                    Text(
                        text = ext.sourceUrl.removePrefix("https://").removePrefix("http://").take(40),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
                TextButton(
                    onClick = { showUninstallDialog = true },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.uninstall))
                }
            }
        }
    }

    if (showUninstallDialog) {
        AlertDialog(
            onDismissRequest = { showUninstallDialog = false },
            title = { Text(stringResource(R.string.uninstall_named, ext.name)) },
            text = { Text(stringResource(R.string.this_will_permanently_delete_the_extension_fi)) },
            confirmButton = {
                TextButton(onClick = { showUninstallDialog = false; onUninstall() }) {
                    Text(stringResource(R.string.uninstall), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUninstallDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun CardExtensionCard(
    item: CardExtensionUiItem,
    onEnabledChange: (Boolean) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.SmartToy, contentDescription = null)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.meta.scriptName, style = MaterialTheme.typography.titleMedium)
                    if (item.meta.scriptVersion.isNotBlank()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "v${item.meta.scriptVersion}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (item.isActive) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(stringResource(R.string.active_2),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    item.meta.characterName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (item.meta.scriptDescription.isNotBlank()) {
                    Text(
                        item.meta.scriptDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Switch(checked = item.enabled, onCheckedChange = onEnabledChange)
        }
    }
}

/** Convert camelCase key to human-readable label: "showHeartMeter" → "Show Heart Meter" */
private fun camelCaseToLabel(key: String): String {
    return key.replace(Regex("([a-z])([A-Z])")) { "${it.groupValues[1]} ${it.groupValues[2]}" }
        .replaceFirstChar { it.uppercase() }
}

@Composable
private fun InstallExtensionDialog(
    isInstalling: Boolean,
    error: String?,
    onInstallUrl: (String) -> Unit,
    onImportFile: () -> Unit,
    onDismiss: () -> Unit
) {
    var url by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!isInstalling) onDismiss() },
        title = { Text(stringResource(R.string.install_extension)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // URL install
                Text(stringResource(R.string.from_url),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.url)) },
                    placeholder = { Text(stringResource(R.string.https_example_com_extension_index_js)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isInstalling
                )
                Button(
                    onClick = { onInstallUrl(url.trim()) },
                    enabled = url.isNotBlank() && !isInstalling,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.install_from_url))
                }

                HorizontalDivider()

                // File import
                Text(stringResource(R.string.from_device),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(stringResource(R.string.import_a_js_file_or_a_zip_containing_index_js),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = onImportFile,
                    enabled = !isInstalling,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.browse_files))
                }

                if (error != null) {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (isInstalling) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isInstalling) { Text(stringResource(R.string.cancel)) }
        }
    )
}
