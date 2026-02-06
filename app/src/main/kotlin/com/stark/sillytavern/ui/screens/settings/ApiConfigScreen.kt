package com.stark.sillytavern.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.stark.sillytavern.data.remote.dto.st.MainApiTypes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiConfigScreen(
    onNavigateBack: () -> Unit,
    viewModel: ApiConfigViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Show snackbar on save success
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            snackbarHostState.showSnackbar("Settings saved")
            viewModel.clearSaveSuccess()
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("API Configuration") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(onClick = { viewModel.saveConfiguration() }) {
                            Icon(Icons.Default.Check, "Save")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
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
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Current Status Card
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Current Configuration",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "API: ${uiState.config.displayName}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (uiState.config.currentModel.isNotBlank()) {
                            Text(
                                text = "Model: ${uiState.config.currentModel}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Text(
                            text = if (uiState.config.usesChatCompletions) "Mode: Chat Completions" else "Mode: Text Completions",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider()

                // Main API Type Selection
                Text(
                    text = "API Type",
                    style = MaterialTheme.typography.titleMedium
                )

                DropdownSelector(
                    label = "Main API",
                    selectedValue = uiState.config.mainApi,
                    options = viewModel.mainApiOptions,
                    onValueChange = { viewModel.setMainApi(it) }
                )

                // Show different options based on main API type
                if (uiState.config.mainApi.lowercase() in MainApiTypes.textCompletionApis) {
                    // Text Completion Settings
                    TextCompletionSettings(
                        textGenType = uiState.config.textGenType,
                        apiServer = uiState.config.apiServer,
                        onTextGenTypeChange = { viewModel.setTextGenType(it) },
                        onApiServerChange = { viewModel.setApiServer(it) },
                        options = viewModel.textGenTypeOptions
                    )
                } else {
                    // Chat Completion Settings
                    ChatCompletionSettings(
                        chatCompletionSource = uiState.config.chatCompletionSource,
                        customUrl = uiState.config.customUrl,
                        currentModel = uiState.config.currentModel,
                        availableModels = uiState.availableModels,
                        isLoadingModels = uiState.isLoadingModels,
                        onSourceChange = { viewModel.setChatCompletionSource(it) },
                        onCustomUrlChange = { viewModel.setCustomUrl(it) },
                        onModelChange = { viewModel.setCurrentModel(it) },
                        onRefreshModels = { viewModel.fetchModels() },
                        sourceOptions = viewModel.chatCompletionSourceOptions
                    )
                }
            }
        }
    }
}

@Composable
private fun TextCompletionSettings(
    textGenType: String,
    apiServer: String,
    onTextGenTypeChange: (String) -> Unit,
    onApiServerChange: (String) -> Unit,
    options: List<Pair<String, String>>
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Text Completion Backend",
                style = MaterialTheme.typography.titleSmall
            )

            DropdownSelector(
                label = "Backend Type",
                selectedValue = textGenType,
                options = options,
                onValueChange = onTextGenTypeChange
            )

            OutlinedTextField(
                value = apiServer,
                onValueChange = onApiServerChange,
                label = { Text("Server URL") },
                placeholder = { Text("http://127.0.0.1:5001") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Text(
                text = "The model is auto-detected from the backend server.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ChatCompletionSettings(
    chatCompletionSource: String,
    customUrl: String?,
    currentModel: String,
    availableModels: List<com.stark.sillytavern.domain.model.AvailableModel>,
    isLoadingModels: Boolean,
    onSourceChange: (String) -> Unit,
    onCustomUrlChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onRefreshModels: () -> Unit,
    sourceOptions: List<Pair<String, String>>
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Chat Completion Provider",
                style = MaterialTheme.typography.titleSmall
            )

            DropdownSelector(
                label = "Provider",
                selectedValue = chatCompletionSource,
                options = sourceOptions,
                onValueChange = onSourceChange
            )

            // Show custom URL field for "custom" source
            if (chatCompletionSource == "custom") {
                OutlinedTextField(
                    value = customUrl ?: "",
                    onValueChange = onCustomUrlChange,
                    label = { Text("Custom API URL") },
                    placeholder = { Text("https://api.example.com/v1") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            // Model Selection
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (availableModels.isNotEmpty()) {
                    DropdownSelector(
                        label = "Model",
                        selectedValue = currentModel,
                        options = availableModels.map { it.id to it.name },
                        onValueChange = onModelChange,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    OutlinedTextField(
                        value = currentModel,
                        onValueChange = onModelChange,
                        label = { Text("Model") },
                        placeholder = { Text("Enter model name") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                IconButton(
                    onClick = onRefreshModels,
                    enabled = !isLoadingModels
                ) {
                    if (isLoadingModels) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Refresh, "Refresh models")
                    }
                }
            }

            if (availableModels.isNotEmpty()) {
                Text(
                    text = "${availableModels.size} models available",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownSelector(
    label: String,
    selectedValue: String,
    options: List<Pair<String, String>>,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.find { it.first == selectedValue }?.second ?: selectedValue

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { (value, displayName) ->
                DropdownMenuItem(
                    text = { Text(displayName) },
                    onClick = {
                        onValueChange(value)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}
