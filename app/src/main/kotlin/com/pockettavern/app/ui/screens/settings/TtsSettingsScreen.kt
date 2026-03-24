package com.pockettavern.app.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import com.pockettavern.app.ui.audio.TtsVoice
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TtsSettingsScreen(
    onBack: () -> Unit,
    viewModel: TtsSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val config = uiState.config

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Text-to-Speech") },
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
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Enable TTS ──────────────────────────────────────────────
            item {
                SectionCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Enable TTS", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Speak chat messages aloud",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = config.enabled,
                            onCheckedChange = { viewModel.updateEnabled(it) }
                        )
                    }
                }
            }

            if (config.enabled) {
                // ── Provider ────────────────────────────────────────────
                item {
                    SectionCard {
                        Text(
                            "Provider",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = config.provider == "system",
                                onClick = { viewModel.updateProvider("system") },
                                label = { Text("System TTS") },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = config.provider == "openai",
                                onClick = { viewModel.updateProvider("openai") },
                                label = { Text("OpenAI-Compatible") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // ── OpenAI Settings ─────────────────────────────────────
                if (config.provider == "openai") {
                    item {
                        SectionCard {
                            Text(
                                "OpenAI-Compatible API",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Works with OpenAI, AllTalk, XTTS, Kokoro, and other compatible endpoints",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = config.openAiUrl,
                                onValueChange = { viewModel.updateOpenAiUrl(it) },
                                label = { Text("API URL") },
                                placeholder = { Text("http://192.168.1.100:8000") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = config.openAiKey,
                                onValueChange = { viewModel.updateOpenAiKey(it) },
                                label = { Text("API Key (optional)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation()
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = config.openAiModel,
                                onValueChange = { viewModel.updateOpenAiModel(it) },
                                label = { Text("Model") },
                                placeholder = { Text("tts-1") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            // Voice selector — dynamically fetched from server
                            VoiceSelector(
                                label = "Default Voice",
                                selectedVoice = config.openAiVoice,
                                voices = uiState.voices,
                                onVoiceSelected = { viewModel.updateOpenAiVoice(it) },
                                onRefresh = { viewModel.refreshVoices() }
                            )
                        }
                    }
                }

                // ── Playback Settings ───────────────────────────────────
                item {
                    SectionCard {
                        Text(
                            "Playback",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Auto-play toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Auto-play", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "Automatically speak new AI messages",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = config.autoPlay,
                                onCheckedChange = { viewModel.updateAutoPlay(it) }
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Speed slider
                        Text("Speed: ${"%.1f".format(config.speed)}x", style = MaterialTheme.typography.bodyMedium)
                        Slider(
                            value = config.speed,
                            onValueChange = { viewModel.updateSpeed(it) },
                            valueRange = 0.5f..2.0f,
                            steps = 5
                        )
                    }
                }

                // ── Text Filter ─────────────────────────────────────────
                item {
                    SectionCard {
                        Text(
                            "Text Filter",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Choose what text to speak",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        val filters = listOf(
                            "all" to "All text",
                            "quotes_only" to "Quotes only (\"dialogue\")",
                            "no_asterisks" to "No action text (*actions*)"
                        )
                        filters.forEach { (mode, label) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = config.filterMode == mode,
                                    onClick = { viewModel.updateFilterMode(mode) }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(label, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                // ── Test ────────────────────────────────────────────────
                item {
                    SectionCard {
                        Text(
                            "Test",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.testVoice() },
                                enabled = !uiState.isTesting
                            ) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Test Voice")
                            }
                            if (uiState.isTesting) {
                                OutlinedButton(
                                    onClick = { viewModel.stopTest() }
                                ) {
                                    Icon(
                                        Icons.Default.Stop,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Stop")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoiceSelector(
    label: String,
    selectedVoice: String,
    voices: List<TtsVoice>,
    onVoiceSelected: (String) -> Unit,
    onRefresh: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Text(label, style = MaterialTheme.typography.bodyMedium)
    Spacer(modifier = Modifier.height(4.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.weight(1f)
        ) {
            OutlinedTextField(
                value = voices.find { it.id == selectedVoice }?.name ?: selectedVoice,
                onValueChange = { },
                readOnly = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                label = { Text("Voice") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                singleLine = true
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                if (voices.isEmpty()) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                "No voices available — check API URL",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        onClick = { expanded = false }
                    )
                }
                voices.forEach { voice ->
                    DropdownMenuItem(
                        text = {
                            if (voice.name != voice.id) {
                                Column {
                                    Text(voice.name)
                                    Text(
                                        voice.id,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                Text(voice.name)
                            }
                        },
                        onClick = {
                            onVoiceSelected(voice.id)
                            expanded = false
                        }
                    )
                }
            }
        }

        // Refresh button to re-fetch voices from server
        IconButton(onClick = onRefresh) {
            Icon(
                Icons.Default.Refresh,
                contentDescription = "Refresh voices",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}
