package com.pockettavern.app.ui.screens.help

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
import com.pockettavern.app.ui.theme.*

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
                    containerColor = MaterialTheme.colorScheme.surface
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
                content = "PocketTavern is a standalone AI character chat app — no SillyTavern server required. Connect directly to any LLM backend (local or cloud), manage your character cards locally, and chat anywhere."
            )

            // Content disclaimer
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "PocketTavern does not host or provide any AI models or content. You connect to your own LLM backends. Character cards are stored on your device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp)
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            // Step 1 — Characters
            GuideSection(
                title = "Step 1 — Get Some Characters",
                content = "PocketTavern uses standard PNG character cards (SillyTavern-compatible format). You can import your own or browse CharaVault."
            )

            GuideStep(
                number = 1,
                title = "Import a PNG card",
                content = "Go to Characters → tap the import button → select a PNG character card from your device. The card's data is stored locally."
            )

            GuideStep(
                number = 2,
                title = "Create a character",
                content = "Tap Create Character on the home screen to build a new character from scratch — name, description, personality, scenario, and first message."
            )

            GuideStep(
                number = 3,
                title = "Browse CharaVault (optional)",
                content = "If you run a CharaVault server, enter its URL in Settings → CharaVault to browse and import cards directly."
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            // Step 2 — LLM Backend
            GuideSection(
                title = "Step 2 — Connect an LLM Backend",
                content = "PocketTavern talks directly to your AI backend. Choose what works for you."
            )

            GuideStep(
                number = 1,
                title = "Open API Configuration",
                content = "Go to Settings → API Configuration. Select your API type from the dropdown."
            )

            GuideStep(
                number = 2,
                title = "Local backends (KoboldCpp / Ollama / LM Studio)",
                content = "Run KoboldCpp, Ollama, or LM Studio on your PC. Make sure it's listening on your network:\n\n• KoboldCpp: http://[YOUR-IP]:5001\n• Ollama: http://[YOUR-IP]:11434\n• LM Studio / TabbyAPI / TextGenWebUI: use their OpenAI-compatible endpoint\n\nYour phone must be on the same Wi-Fi network as your PC."
            )

            GuideStep(
                number = 3,
                title = "Cloud APIs (OpenAI, Mistral, Groq, etc.)",
                content = "Select Chat Completion as the API type. Enter the API URL and your API key:\n\n• OpenAI: api.openai.com/v1\n• OpenRouter: openrouter.ai/api/v1\n• Groq: api.groq.com/openai/v1\n• Any OpenAI-compatible endpoint works\n\nThen tap the refresh button to load available models."
            )

            GuideStep(
                number = 4,
                title = "Test the connection",
                content = "Tap Test Connection to verify. A green 'Connected' indicator appears at the bottom of the Settings screen when successful."
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            // Step 3 — Start Chatting
            GuideSection(
                title = "Step 3 — Start Chatting",
                content = "Once you have a character and a connected backend, you're ready."
            )

            GuideStep(
                number = 1,
                title = "Open a character",
                content = "Go to Characters → tap a character card to open a new chat."
            )

            GuideStep(
                number = 2,
                title = "Send a message",
                content = "Type in the message box and tap Send. The app streams the AI response in real-time."
            )

            GuideStep(
                number = 3,
                title = "Continue later",
                content = "Your chat is saved automatically. Find it again under Recent Chats or by tapping the character."
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            // Features
            GuideSection(
                title = "Key Settings",
                content = null
            )

            FeatureItem(
                title = "API Configuration",
                description = "Select API type (Text Generation or Chat Completion), enter backend URL, API key, and choose a model. Tap the refresh icon to load models from the server."
            )

            FeatureItem(
                title = "Chat Completion Presets",
                description = "For cloud APIs — control temperature, top-p, and other sampling parameters. Also lets you set a custom Main Prompt that overrides the default system prompt."
            )

            FeatureItem(
                title = "Text Generation",
                description = "For local backends (KoboldCpp, etc.) — configure samplers, presets, instruct templates, and context templates."
            )

            FeatureItem(
                title = "Formatting",
                description = "Choose instruct and context templates that match your model (e.g. ChatML for most models, Llama 3 for Meta models, Mistral for Mistral models)."
            )

            FeatureItem(
                title = "World Info / Lorebooks",
                description = "Attach lorebooks to your chats for extra context about settings, characters, and lore. Entries are injected automatically when their keywords are mentioned."
            )

            FeatureItem(
                title = "Context Settings",
                description = "Add an Author's Note that gets injected into every conversation at a configurable depth — useful for steering tone, style, or scenario."
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            // Optional Features
            GuideSection(
                title = "Optional Features",
                content = null
            )

            FeatureItem(
                title = "Stable Diffusion Forge",
                description = "Generate character avatars and scene images using a local Stable Diffusion Forge server. Enter the Forge URL in Settings → Stable Diffusion Forge."
            )

            FeatureItem(
                title = "CharaVault",
                description = "Browse a hosted library of character cards. Enter your CharaVault server URL in Settings to enable. Cards can be downloaded directly to your local library."
            )

            FeatureItem(
                title = "Extensions",
                description = "Enable quick reply buttons, regex output filters, and token counting from Settings → Extensions."
            )

            FeatureItem(
                title = "Connection Profiles",
                description = "Save multiple API configurations and switch between them instantly — useful if you use both a local model and a cloud API."
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            // Troubleshooting
            GuideSection(
                title = "Troubleshooting",
                content = null
            )

            TroubleshootItem(
                problem = "Can't connect to local backend",
                solutions = listOf(
                    "Make sure the backend is running and listening on the network",
                    "Use your PC's local IP address (e.g. 192.168.1.x), not localhost",
                    "Phone and PC must be on the same Wi-Fi network",
                    "Check that your firewall allows the port (5001, 11434, etc.)",
                    "For KoboldCpp: start with --host 0.0.0.0 flag",
                    "For Ollama: set OLLAMA_HOST=0.0.0.0 before starting"
                )
            )

            TroubleshootItem(
                problem = "Cloud API not working",
                solutions = listOf(
                    "Double-check your API key is correct and has credits",
                    "Make sure the API URL does not have a trailing slash",
                    "Tap the model refresh button after entering the URL and key",
                    "Check Debug Log (Settings → Extensions → Debug Log) for error details"
                )
            )

            TroubleshootItem(
                problem = "Model ignores instructions / Author's Note",
                solutions = listOf(
                    "Some models (especially Claude) anchor heavily to their initial system prompt",
                    "Try putting key instructions in the character's system prompt instead",
                    "Reduce Author's Note depth (lower = closer to the end = more influential)",
                    "For chat completion: edit the Main Prompt in Chat Completion Presets"
                )
            )

            TroubleshootItem(
                problem = "Characters not loading",
                solutions = listOf(
                    "Check that the PNG file is a valid character card (exported from SillyTavern or similar)",
                    "Try re-importing the PNG file",
                    "Check the Debug Log for parse errors"
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
            color = MaterialTheme.colorScheme.primary
        )
        if (content != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
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
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
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
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        solutions.forEach { solution ->
            Text(
                text = "• $solution",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}
