package com.pockettavern.app.ui.screens.extensions

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
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pockettavern.app.extensions.JsExtension
import com.pockettavern.app.ui.theme.AccentGreen
import com.pockettavern.app.ui.theme.DarkSurface

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Extensions") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                Text(
                    text = "Native Extensions",
                    style = MaterialTheme.typography.titleSmall,
                    color = AccentGreen,
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
                    Text(
                        text = "JavaScript Extensions",
                        style = MaterialTheme.typography.titleSmall,
                        color = AccentGreen,
                        modifier = Modifier.weight(1f)
                    )
                    FilledTonalIconButton(onClick = { showInstallDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Install extension")
                    }
                }
            }

            if (uiState.jsExtensions.isEmpty()) {
                item {
                    Surface(color = DarkSurface, shape = MaterialTheme.shapes.medium) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No JavaScript extensions installed.\nTap + to install from a URL.",
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
                        onEnabledChange = { viewModel.setJsExtensionEnabled(ext.id, it) },
                        onUninstall = { viewModel.uninstall(ext.id) }
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
            onInstall = { url ->
                viewModel.installFromUrl(url)
                showInstallDialog = false
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
        color = DarkSurface,
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
                    Text("Settings")
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
    onEnabledChange: (Boolean) -> Unit,
    onUninstall: () -> Unit
) {
    var showUninstallDialog by remember { mutableStateOf(false) }

    Surface(
        color = DarkSurface,
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
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
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
                    Text("Uninstall")
                }
            }
        }
    }

    if (showUninstallDialog) {
        AlertDialog(
            onDismissRequest = { showUninstallDialog = false },
            title = { Text("Uninstall ${ext.name}?") },
            text = { Text("This will permanently delete the extension files.") },
            confirmButton = {
                TextButton(onClick = { showUninstallDialog = false; onUninstall() }) {
                    Text("Uninstall", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUninstallDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun InstallExtensionDialog(
    isInstalling: Boolean,
    error: String?,
    onInstall: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var url by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!isInstalling) onDismiss() },
        title = { Text("Install Extension") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Enter the URL of the extension's index.js or its parent folder.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL") },
                    placeholder = { Text("https://example.com/my-extension/index.js") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isInstalling
                )
                if (error != null) {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (isInstalling) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onInstall(url.trim()) },
                enabled = url.isNotBlank() && !isInstalling
            ) { Text("Install") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isInstalling) { Text("Cancel") }
        }
    )
}
