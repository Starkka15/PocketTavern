package com.pockettavern.app.ui.screens.settings

import com.pockettavern.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pockettavern.app.data.remote.dto.st.MainApiTypes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiConfigScreen(
    onNavigateBack: () -> Unit,
    viewModel: ApiConfigViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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
                title = { Text(stringResource(R.string.api_configuration)) },
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
                        Text(text = stringResource(R.string.current_configuration),
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
                Text(text = stringResource(R.string.api_type),
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
                        customChatCompletionsUrl = uiState.config.customChatCompletionsUrl,
                        defaultChatCompletionsUrl = uiState.config.defaultChatCompletionsUrl,
                        currentModel = uiState.config.currentModel,
                        availableModels = uiState.availableModels,
                        isLoadingModels = uiState.isLoadingModels,
                        onSourceChange = { viewModel.setChatCompletionSource(it) },
                        onCustomUrlChange = { viewModel.setCustomUrl(it) },
                        onCustomChatCompletionsUrlChange = { viewModel.setCustomChatCompletionsUrl(it) },
                        onModelChange = { viewModel.setCurrentModel(it) },
                        onRefreshModels = { viewModel.fetchModels() },
                        sourceOptions = viewModel.chatCompletionSourceOptions,
                        catalog = viewModel.catalogFor(uiState.config.chatCompletionSource),
                        deviceSummary = viewModel.deviceSummary,
                        isDownloading = uiState.isDownloading,
                        downloadProgress = uiState.downloadProgress,
                        downloadStatus = uiState.downloadStatus,
                        onDownloadModel = { url, token -> viewModel.downloadOnDeviceModel(url, token) },
                        onDeleteModel = { viewModel.deleteOnDeviceModel(it) }
                    )
                }

                HorizontalDivider()

                // Show Thoughts (reasoning models: DeepSeek R1, QwQ, etc.)
                if (uiState.config.usesChatCompletions) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = stringResource(R.string.show_reasoning_tokens),
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(text = stringResource(R.string.display_thinking_from_reasoning_models_deepse),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = uiState.config.showThoughts,
                                onCheckedChange = { viewModel.setShowThoughts(it) }
                            )
                        }
                    }

                    HorizontalDivider()
                }

                // API Key
                ApiKeySection(
                    apiKey = uiState.apiKey,
                    onApiKeyChange = { viewModel.setApiKey(it) }
                )
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
            Text(text = stringResource(R.string.text_completion_backend),
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
                label = { Text(stringResource(R.string.server_url)) },
                placeholder = { Text(stringResource(R.string.http_127_0_0_1_5001)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Text(text = stringResource(R.string.the_model_is_auto_detected_from_the_backend_s),
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
    customChatCompletionsUrl: String?,
    defaultChatCompletionsUrl: String,
    currentModel: String,
    availableModels: List<com.pockettavern.app.domain.model.AvailableModel>,
    isLoadingModels: Boolean,
    onSourceChange: (String) -> Unit,
    onCustomUrlChange: (String) -> Unit,
    onCustomChatCompletionsUrlChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onRefreshModels: () -> Unit,
    sourceOptions: List<Pair<String, String>>,
    // On-device (LiteRT-LM)
    catalog: List<com.pockettavern.app.data.local.inference.CatalogModel> = emptyList(),
    deviceSummary: String = "",
    isDownloading: Boolean = false,
    downloadProgress: Float? = null,
    downloadStatus: String? = null,
    onDownloadModel: (String, String?) -> Unit = { _, _ -> },
    onDeleteModel: (String) -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = stringResource(R.string.chat_completion_provider),
                style = MaterialTheme.typography.titleSmall
            )

            DropdownSelector(
                label = "Provider",
                selectedValue = chatCompletionSource,
                options = sourceOptions,
                onValueChange = onSourceChange
            )

            if (chatCompletionSource == "ondevice" || chatCompletionSource == "ondevice-gguf") {
                OnDeviceModelSection(
                    currentModel = currentModel,
                    models = availableModels,
                    catalog = catalog,
                    deviceSummary = deviceSummary,
                    isDownloading = isDownloading,
                    downloadProgress = downloadProgress,
                    downloadStatus = downloadStatus,
                    onSelectModel = onModelChange,
                    onDownload = onDownloadModel,
                    onDelete = onDeleteModel
                )
            } else {
                // Show custom URL field for "custom" source
                if (chatCompletionSource == "custom") {
                    OutlinedTextField(
                        value = customUrl ?: "",
                        onValueChange = onCustomUrlChange,
                        label = { Text(stringResource(R.string.custom_api_url)) },
                        placeholder = { Text(stringResource(R.string.https_api_example_com_v1)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = customChatCompletionsUrl ?: defaultChatCompletionsUrl,
                        onValueChange = onCustomChatCompletionsUrlChange,
                        label = { Text(stringResource(R.string.chat_completions_url)) },
                        placeholder = { Text(stringResource(R.string.https_api_example_com_v1_chat_completions)) },
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
                            label = { Text(stringResource(R.string.model)) },
                            placeholder = { Text(stringResource(R.string.enter_model_name)) },
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
}

@Composable
private fun OnDeviceModelSection(
    currentModel: String,
    models: List<com.pockettavern.app.domain.model.AvailableModel>,
    catalog: List<com.pockettavern.app.data.local.inference.CatalogModel>,
    deviceSummary: String,
    isDownloading: Boolean,
    downloadProgress: Float?,
    downloadStatus: String?,
    onSelectModel: (String) -> Unit,
    onDownload: (String, String?) -> Unit,
    onDelete: (String) -> Unit
) {
    var url by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    val downloadedIds = remember(models) { models.map { it.id }.toSet() }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = stringResource(R.string.models_run_fully_on_your_device_nothing_is_se),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = stringResource(R.string.speed_depends_heavily_on_your_device_recent_f) +
                    "run smoothly; budget or older phones can be very slow or unusable, especially with " +
                    "larger models or long character prompts. Your device: $deviceSummary.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(10.dp)
            )
        }

        // Shared access token (for gated catalog models and gated manual URLs)
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text(stringResource(R.string.huggingface_token_for_gated_models)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !isDownloading
        )

        // Recommended catalog (mirrors Google AI Edge Gallery's list)
        Text(stringResource(R.string.recommended_models), style = MaterialTheme.typography.labelLarge)
        catalog.forEach { cm ->
            val isDownloaded = cm.modelId in downloadedIds
            val sizeMb = cm.sizeBytes / (1024 * 1024)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = cm.name + if (cm.gated) "  🔒" else "",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "$sizeMb MB · ${cm.description}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isDownloaded) {
                    Icon(Icons.Default.Check, contentDescription = stringResource(R.string.downloaded), tint = MaterialTheme.colorScheme.primary)
                } else {
                    TextButton(
                        onClick = { onDownload(cm.url, token) },
                        enabled = !isDownloading
                    ) { Text(stringResource(R.string.download)) }
                }
            }
        }

        HorizontalDivider()

        if (models.isEmpty()) {
            Text(text = stringResource(R.string.no_models_downloaded_yet),
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            Text(stringResource(R.string.downloaded_models), style = MaterialTheme.typography.labelLarge)
            models.forEach { model ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isDownloading) { onSelectModel(model.id) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = currentModel == model.id,
                        onClick = { onSelectModel(model.id) },
                        enabled = !isDownloading
                    )
                    Text(model.name, modifier = Modifier.weight(1f))
                    IconButton(onClick = { onDelete(model.id) }, enabled = !isDownloading) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete ${model.name}")
                    }
                }
            }
        }

        HorizontalDivider()

        Text(stringResource(R.string.or_download_by_url_task_litertlm), style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text(stringResource(R.string.model_url)) },
            placeholder = { Text(stringResource(R.string.https_huggingface_co_model_task)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !isDownloading
        )

        if (isDownloading) {
            if (downloadProgress != null) {
                LinearProgressIndicator(
                    progress = { downloadProgress },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }

        Button(
            onClick = { onDownload(url, token) },
            enabled = !isDownloading && url.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isDownloading) "Downloading…" else "Download")
        }

        downloadStatus?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ApiKeySection(
    apiKey: String,
    onApiKeyChange: (String) -> Unit
) {
    var showKey by remember { mutableStateOf(false) }

    Text(text = stringResource(R.string.api_key),
        style = MaterialTheme.typography.titleMedium
    )

    OutlinedTextField(
        value = apiKey,
        onValueChange = onApiKeyChange,
        label = { Text(stringResource(R.string.api_key)) },
        placeholder = { Text(stringResource(R.string.sk_leave_blank_for_local_backends)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { showKey = !showKey }) {
                Icon(
                    imageVector = if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (showKey) "Hide key" else "Show key"
                )
            }
        }
    )

    Text(text = stringResource(R.string.used_for_openai_claude_openrouter_tabbyapi_an),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
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
