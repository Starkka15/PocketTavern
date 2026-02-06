package com.stark.sillytavern.ui.screens.help

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stark.sillytavern.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupGuideScreen(
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Setup Guide") },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Introduction
            GuideSection(
                title = "Welcome to PocketTavern",
                content = "PocketTavern is a lightweight companion app for SillyTavern that lets you chat with your AI characters on the go. Note: Some advanced features (extensions, group chats, etc.) may not be available yet. We're working to add more over time, but no promises."
            )

            // Content disclaimer
            Surface(
                color = DarkSurfaceVariant,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "PocketTavern does not host or provide any content. All characters and data come from your own self-hosted servers.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(12.dp)
                )
            }

            HorizontalDivider(color = DarkSurfaceVariant)

            // Prerequisites
            GuideSection(
                title = "Prerequisites",
                content = "You need a running SillyTavern server accessible from your device."
            )

            GuideStep(
                number = 1,
                title = "Install SillyTavern",
                content = "Download and install SillyTavern from:\ngithub.com/SillyTavern/SillyTavern\n\nRun: npm install && ./start.sh"
            )

            GuideStep(
                number = 2,
                title = "Enable Network Access",
                content = "Edit config.yaml in SillyTavern folder:\n\n• Set listen: true\n• Set basicAuthMode: false\n• Note your computer's IP address"
            )

            GuideStep(
                number = 3,
                title = "Enable Multi-User Mode",
                content = "Multi-User Mode is required:\n\n• Set enableUserAccounts: true in config.yaml\n• Create a user account in SillyTavern\n• You'll use these credentials in the app"
            )

            GuideStep(
                number = 4,
                title = "Find Your Server URL",
                content = "Your URL will be:\nhttp://[YOUR-IP]:8000\n\nExample: http://192.168.1.100:8000"
            )

            HorizontalDivider(color = DarkSurfaceVariant)

            // Connect
            GuideSection(
                title = "Connect to SillyTavern",
                content = "Configure PocketTavern to connect to your server."
            )

            GuideStep(
                number = 1,
                title = "Open Settings",
                content = "Tap Settings on the main screen"
            )

            GuideStep(
                number = 2,
                title = "Enter Server URL",
                content = "Enter your SillyTavern server URL\n\nIf using Multi-User Mode, also enter your username and password"
            )

            GuideStep(
                number = 3,
                title = "Test & Save",
                content = "Tap 'Test Connection' to verify, then tap 'Save Settings'"
            )

            HorizontalDivider(color = DarkSurfaceVariant)

            // Features
            GuideSection(
                title = "Core Features",
                content = null
            )

            FeatureItem(
                title = "Characters",
                description = "Browse and chat with your characters. Long-press for more options like edit or delete."
            )

            FeatureItem(
                title = "Recent Chats",
                description = "Quickly continue your recent conversations."
            )

            FeatureItem(
                title = "Create Character",
                description = "Design new characters with name, description, personality, and first message."
            )

            HorizontalDivider(color = DarkSurfaceVariant)

            // Optional Features
            GuideSection(
                title = "Optional Features",
                content = "These appear when configured in Settings."
            )

            FeatureItem(
                title = "CardVault",
                description = "If you have a collection of character card PNGs, set up CardVault (github.com/Starkka15/cardvault) to browse and import them. Enter your CardVault server URL in Settings to enable."
            )

            FeatureItem(
                title = "Stable Diffusion Forge",
                description = "Generate character avatars using your local Forge server. Enter the Forge URL in Settings to enable image generation."
            )

            HorizontalDivider(color = DarkSurfaceVariant)

            // Troubleshooting
            GuideSection(
                title = "Troubleshooting",
                content = null
            )

            TroubleshootItem(
                problem = "Connection failed",
                solutions = listOf(
                    "Verify SillyTavern is running",
                    "Check listen: true in config.yaml",
                    "Ensure phone is on same network",
                    "Try IP address instead of hostname",
                    "Check firewall isn't blocking port 8000"
                )
            )

            TroubleshootItem(
                problem = "Characters not loading",
                solutions = listOf(
                    "Check connection status shows 'Connected'",
                    "Pull down to refresh",
                    "Check SillyTavern logs for errors"
                )
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun GuideSection(
    title: String,
    content: String?
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = AccentGreen
        )
        if (content != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun GuideStep(
    number: Int,
    title: String,
    content: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = AccentGreen.copy(alpha = 0.2f),
            modifier = Modifier.size(32.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Text(
                    text = number.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = AccentGreen
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun FeatureItem(
    title: String,
    description: String
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
    }
}

@Composable
private fun TroubleshootItem(
    problem: String,
    solutions: List<String>
) {
    Column {
        Text(
            text = problem,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = FireOrange
        )
        Spacer(modifier = Modifier.height(4.dp))
        solutions.forEach { solution ->
            Text(
                text = "• $solution",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}
