package com.stark.sillytavern.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stark.sillytavern.ui.theme.*

data class SettingsItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val iconTint: androidx.compose.ui.graphics.Color = AccentGreen,
    val onClick: () -> Unit,
    val requiresConnection: Boolean = true
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
    viewModel: SettingsHubViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isConnected = uiState.isConnected
    val currentPersonaName = uiState.currentPersonaName

    // Refresh when screen appears (for when returning from sub-screens)
    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    val settingsItems = remember(isConnected, currentPersonaName) {
        listOf(
            SettingsItem(
                title = "Connection",
                subtitle = "SillyTavern server URL and credentials",
                icon = Icons.Default.Wifi,
                onClick = onNavigateToConnection,
                requiresConnection = false
            ),
            SettingsItem(
                title = "API Configuration",
                subtitle = "Select API type and model",
                icon = Icons.Default.Cloud,
                onClick = onNavigateToApiConfig
            ),
            SettingsItem(
                title = "Text Generation",
                subtitle = "Sampler settings and presets",
                icon = Icons.Default.Tune,
                onClick = onNavigateToTextGen
            ),
            SettingsItem(
                title = "Formatting",
                subtitle = "Instruct templates and system prompts",
                icon = Icons.Default.TextFormat,
                onClick = onNavigateToFormatting
            ),
            SettingsItem(
                title = "World Info / Lorebooks",
                subtitle = "View and manage lorebook entries",
                icon = Icons.AutoMirrored.Filled.MenuBook,
                iconTint = AccentBlue,
                onClick = onNavigateToWorldInfo
            ),
            SettingsItem(
                title = "Context Settings",
                subtitle = "Author's note configuration",
                icon = Icons.AutoMirrored.Filled.StickyNote2,
                iconTint = AccentBlue,
                onClick = onNavigateToContextSettings
            ),
            SettingsItem(
                title = "Personas",
                subtitle = currentPersonaName?.let { "Current: $it" } ?: "Manage user personas and avatars",
                icon = Icons.Default.Person,
                iconTint = AccentPurple,
                onClick = onNavigateToPersonas
            ),
            SettingsItem(
                title = "Setup Guide",
                subtitle = "Help with setup and troubleshooting",
                icon = Icons.Default.Help,
                iconTint = IceCyan,
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
                    containerColor = DarkSurface
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
            items(settingsItems) { item ->
                val enabled = !item.requiresConnection || isConnected
                SettingsListItem(
                    item = item,
                    enabled = enabled
                )
            }

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
                        tint = if (isConnected) AccentGreen else ErrorRed,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isConnected) "Connected to SillyTavern" else "Not connected",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isConnected) AccentGreen else ErrorRed
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsListItem(
    item: SettingsItem,
    enabled: Boolean
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = item.onClick),
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
                modifier = Modifier
                    .size(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = if (enabled) item.iconTint else TextTertiary,
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
                    color = if (enabled) TextPrimary else TextTertiary
                )
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) TextSecondary else TextTertiary
                )
            }

            // Chevron
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = if (enabled) TextSecondary else TextTertiary
            )
        }
    }

    HorizontalDivider(
        modifier = Modifier.padding(start = 72.dp),
        color = DarkSurfaceVariant
    )
}
