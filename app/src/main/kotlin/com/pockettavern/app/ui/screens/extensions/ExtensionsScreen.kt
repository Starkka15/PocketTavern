package com.pockettavern.app.ui.screens.extensions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
                    onSettingsClick = null   // No separate settings screen needed
                )
            }
        }
    }
}

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
