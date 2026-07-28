package com.pockettavern.app.ui.screens.settings

import com.pockettavern.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.material3.MenuAnchorType
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pockettavern.app.domain.model.ImageGenBackendType

data class ResolutionPreset(val label: String, val width: Int, val height: Int)

private val RESOLUTION_PRESETS = listOf(
    ResolutionPreset("Portrait (512x768)", 512, 768),
    ResolutionPreset("Landscape (768x512)", 768, 512),
    ResolutionPreset("Square (512x512)", 512, 512),
    ResolutionPreset("HD Portrait (768x1024)", 768, 1024),
    ResolutionPreset("HD Landscape (1024x768)", 1024, 768),
    ResolutionPreset("HD Square (1024x1024)", 1024, 1024)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageGenSettingsScreen(
    onBack: () -> Unit,
    viewModel: ImageGenSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val config = uiState.config
    val caps = uiState.capabilities

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.image_generation)) },
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
            // ── Backend Selector ─────────────────────────────────────
            item {
                ImageGenSectionCard {
                    Text(stringResource(R.string.backend),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it }
                    ) {
                        OutlinedTextField(
                            value = config.activeBackendType.displayName,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                            colors = imageGenTextFieldColors()
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            uiState.availableBackends.forEach { backend ->
                                DropdownMenuItem(
                                    text = { Text(backend.displayName) },
                                    onClick = {
                                        viewModel.updateBackend(backend)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // ── Connection (URL / API Key) ───────────────────────────
            if (caps.requiresUrl || caps.requiresApiKey) {
                item {
                    ImageGenSectionCard {
                        Text(stringResource(R.string.connection),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        when (config.activeBackendType) {
                            ImageGenBackendType.SD_WEBUI -> {
                                OutlinedTextField(
                                    value = config.sdWebuiUrl,
                                    onValueChange = { viewModel.updateSdWebuiUrl(it) },
                                    label = { Text(stringResource(R.string.sd_webui_forge_url)) },
                                    placeholder = { Text(stringResource(R.string.http_192_168_1_100_7860)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                                    colors = imageGenTextFieldColors()
                                )
                            }
                            ImageGenBackendType.COMFYUI -> {
                                OutlinedTextField(
                                    value = config.comfyuiUrl,
                                    onValueChange = { viewModel.updateComfyUiUrl(it) },
                                    label = { Text(stringResource(R.string.comfyui_url)) },
                                    placeholder = { Text(stringResource(R.string.http_192_168_1_100_8188)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                                    colors = imageGenTextFieldColors()
                                )
                            }
                            ImageGenBackendType.DALLE -> {
                                OutlinedTextField(
                                    value = config.dalleApiKey,
                                    onValueChange = { viewModel.updateDalleApiKey(it) },
                                    label = { Text(stringResource(R.string.openai_api_key)) },
                                    placeholder = { Text(stringResource(R.string.sk)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    colors = imageGenTextFieldColors()
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                var dalleExpanded by remember { mutableStateOf(false) }
                                val dalleModels = listOf("dall-e-3", "dall-e-2")
                                ExposedDropdownMenuBox(
                                    expanded = dalleExpanded,
                                    onExpandedChange = { dalleExpanded = it }
                                ) {
                                    OutlinedTextField(
                                        value = config.dalleModel,
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text(stringResource(R.string.model)) },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(dalleExpanded) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                                        colors = imageGenTextFieldColors()
                                    )
                                    ExposedDropdownMenu(
                                        expanded = dalleExpanded,
                                        onDismissRequest = { dalleExpanded = false }
                                    ) {
                                        dalleModels.forEach { model ->
                                            DropdownMenuItem(
                                                text = { Text(model) },
                                                onClick = {
                                                    viewModel.updateDalleModel(model)
                                                    dalleExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                            ImageGenBackendType.STABILITY -> {
                                OutlinedTextField(
                                    value = config.stabilityApiKey,
                                    onValueChange = { viewModel.updateStabilityApiKey(it) },
                                    label = { Text(stringResource(R.string.stability_ai_api_key)) },
                                    placeholder = { Text(stringResource(R.string.sk)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    colors = imageGenTextFieldColors()
                                )
                            }
                            ImageGenBackendType.HUGGINGFACE -> {
                                OutlinedTextField(
                                    value = config.huggingfaceApiKey,
                                    onValueChange = { viewModel.updateHuggingFaceApiKey(it) },
                                    label = { Text(stringResource(R.string.huggingface_api_key)) },
                                    placeholder = { Text(stringResource(R.string.hf)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    colors = imageGenTextFieldColors()
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = config.huggingfaceModel,
                                    onValueChange = { viewModel.updateHuggingFaceModel(it) },
                                    label = { Text(stringResource(R.string.model_id)) },
                                    placeholder = { Text(stringResource(R.string.stabilityai_stable_diffusion_xl_base_1_0)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    colors = imageGenTextFieldColors()
                                )
                            }
                            ImageGenBackendType.POLLINATIONS -> {
                                OutlinedTextField(
                                    value = config.pollinationsApiKey,
                                    onValueChange = { viewModel.updatePollinationsApiKey(it) },
                                    label = { Text(stringResource(R.string.pollinations_api_key)) },
                                    placeholder = { Text(stringResource(R.string.pollen)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    colors = imageGenTextFieldColors()
                                )
                                Text(stringResource(R.string.get_a_key_at_pollinations_ai_required_for_ima),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                var pollinationsExpanded by remember { mutableStateOf(false) }
                                val pollinationsModels = listOf("flux", "flux-realism", "flux-anime", "flux-3d", "flux-cablyai", "turbo")
                                ExposedDropdownMenuBox(
                                    expanded = pollinationsExpanded,
                                    onExpandedChange = { pollinationsExpanded = it }
                                ) {
                                    OutlinedTextField(
                                        value = config.pollinationsModel.ifBlank { "flux" },
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text(stringResource(R.string.model)) },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(pollinationsExpanded) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                                        colors = imageGenTextFieldColors()
                                    )
                                    ExposedDropdownMenu(
                                        expanded = pollinationsExpanded,
                                        onDismissRequest = { pollinationsExpanded = false }
                                    ) {
                                        pollinationsModels.forEach { model ->
                                            DropdownMenuItem(
                                                text = { Text(model) },
                                                onClick = {
                                                    viewModel.updatePollinationsModel(model)
                                                    pollinationsExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                            ImageGenBackendType.NANO_GPT -> {
                                OutlinedTextField(
                                    value = config.nanoGptApiKey,
                                    onValueChange = { viewModel.updateNanoGptApiKey(it) },
                                    label = { Text(stringResource(R.string.nano_gpt_api_key)) },
                                    placeholder = { Text(stringResource(R.string.sk_nano)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    colors = imageGenTextFieldColors()
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                val nanoModels = uiState.models
                                if (nanoModels.isNotEmpty()) {
                                    var nanoExpanded by remember { mutableStateOf(false) }
                                    ExposedDropdownMenuBox(
                                        expanded = nanoExpanded,
                                        onExpandedChange = { nanoExpanded = it }
                                    ) {
                                        OutlinedTextField(
                                            value = config.nanoGptModel.ifBlank { nanoModels.first() },
                                            onValueChange = {},
                                            readOnly = true,
                                            label = { Text(stringResource(R.string.model)) },
                                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(nanoExpanded) },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                                            colors = imageGenTextFieldColors()
                                        )
                                        ExposedDropdownMenu(
                                            expanded = nanoExpanded,
                                            onDismissRequest = { nanoExpanded = false }
                                        ) {
                                            nanoModels.forEach { model ->
                                                DropdownMenuItem(
                                                    text = { Text(model) },
                                                    onClick = {
                                                        viewModel.updateNanoGptModel(model)
                                                        nanoExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = { viewModel.fetchModels() },
                                        enabled = config.nanoGptApiKey.isNotBlank() && !uiState.isLoadingModels,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        if (uiState.isLoadingModels) {
                                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                            Spacer(modifier = Modifier.width(8.dp))
                                        } else {
                                            Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                        }
                                        Text(stringResource(R.string.load_models))
                                    }
                                }
                            }
                        }

                        // Test Connection button
                        if (caps.requiresUrl || caps.requiresApiKey) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.testConnection() },
                                    enabled = !uiState.isTesting,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    if (uiState.isTesting) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    Text(stringResource(R.string.test_connection))
                                }

                                OutlinedButton(
                                    onClick = {
                                        viewModel.fetchSamplers()
                                        viewModel.fetchModels()
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(stringResource(R.string.fetch_options))
                                }
                            }

                            uiState.testResult?.let { result ->
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = result,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (result.contains("successful"))
                                        MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            // ── Sampler / Model (if supported) ──────────────────────
            if (caps.supportsSamplers || caps.supportsModels) {
                item {
                    ImageGenSectionCard {
                        Text(stringResource(R.string.generation_options),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // Sampler dropdown
                        if (caps.supportsSamplers) {
                            val samplerList = if (uiState.samplers.isNotEmpty()) uiState.samplers
                            else listOf("Euler", "Euler a", "DPM++ 2M Karras", "DPM++ SDE Karras", "DDIM", "UniPC")
                            var samplerExpanded by remember { mutableStateOf(false) }
                            ExposedDropdownMenuBox(
                                expanded = samplerExpanded,
                                onExpandedChange = { samplerExpanded = it }
                            ) {
                                OutlinedTextField(
                                    value = config.sampler,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text(stringResource(R.string.sampler)) },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(samplerExpanded) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                                    colors = imageGenTextFieldColors()
                                )
                                ExposedDropdownMenu(
                                    expanded = samplerExpanded,
                                    onDismissRequest = { samplerExpanded = false }
                                ) {
                                    samplerList.forEach { sampler ->
                                        DropdownMenuItem(
                                            text = { Text(sampler) },
                                            onClick = {
                                                viewModel.updateSampler(sampler)
                                                samplerExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        // Model dropdown (only if fetched)
                        if (caps.supportsModels && uiState.models.isNotEmpty()) {
                            var modelExpanded by remember { mutableStateOf(false) }
                            ExposedDropdownMenuBox(
                                expanded = modelExpanded,
                                onExpandedChange = { modelExpanded = it }
                            ) {
                                OutlinedTextField(
                                    value = config.sdModel.ifBlank { uiState.models.firstOrNull() ?: "Default" },
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text(stringResource(R.string.model)) },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(modelExpanded) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                                    colors = imageGenTextFieldColors()
                                )
                                ExposedDropdownMenu(
                                    expanded = modelExpanded,
                                    onDismissRequest = { modelExpanded = false }
                                ) {
                                    uiState.models.forEach { model ->
                                        DropdownMenuItem(
                                            text = { Text(model) },
                                            onClick = {
                                                viewModel.updateSdModel(model)
                                                modelExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── Steps / CFG / Seed ──────────────────────────────────
            if (caps.supportsSteps || caps.supportsCfgScale || caps.supportsSeed) {
                item {
                    ImageGenSectionCard {
                        Text(stringResource(R.string.parameters),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // Steps
                        if (caps.supportsSteps) {
                            Text(
                                "Steps: ${config.steps}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Slider(
                                value = config.steps.toFloat(),
                                onValueChange = { viewModel.updateSteps(it.toInt()) },
                                valueRange = 1f..150f,
                                steps = 0,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        // CFG Scale
                        if (caps.supportsCfgScale) {
                            Text(
                                "CFG Scale: ${"%.1f".format(config.cfgScale)}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Slider(
                                value = config.cfgScale,
                                onValueChange = { viewModel.updateCfgScale(it) },
                                valueRange = 1f..30f,
                                steps = 0,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        // Seed
                        if (caps.supportsSeed) {
                            OutlinedTextField(
                                value = if (config.seed == -1) "" else config.seed.toString(),
                                onValueChange = {
                                    val seed = it.toIntOrNull() ?: -1
                                    viewModel.updateSeed(seed)
                                },
                                label = { Text(stringResource(R.string.seed_1_random)) },
                                placeholder = { Text("-1") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = imageGenTextFieldColors()
                            )
                        }
                    }
                }
            }

            // ── Resolution ──────────────────────────────────────────
            if (caps.supportsResolutionPresets) {
                item {
                    ImageGenSectionCard {
                        Text(stringResource(R.string.resolution),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            "Current: ${config.width} x ${config.height}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // Preset chips
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RESOLUTION_PRESETS.forEach { preset ->
                                val isSelected = config.width == preset.width && config.height == preset.height
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.updateResolution(preset.width, preset.height) },
                                    label = { Text(preset.label, style = MaterialTheme.typography.labelSmall) },
                                    leadingIcon = if (isSelected) {
                                        { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                                    } else null
                                )
                            }
                        }
                    }
                }
            }

            // ── Negative Prompt ──────────────────────────────────────
            if (caps.supportsNegativePrompt) {
                item {
                    ImageGenSectionCard {
                        Text(stringResource(R.string.negative_prompt),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = config.negativePrompt,
                            onValueChange = { viewModel.updateNegativePrompt(it) },
                            label = { Text(stringResource(R.string.negative_prompt_2)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 80.dp),
                            maxLines = 4,
                            colors = imageGenTextFieldColors()
                        )
                    }
                }
            }

            // ── CLIP Skip ───────────────────────────────────────────
            if (caps.supportsClipSkip) {
                item {
                    ImageGenSectionCard {
                        Text(stringResource(R.string.advanced),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            "CLIP Skip: ${config.clipSkip}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Slider(
                            value = config.clipSkip.toFloat(),
                            onValueChange = { viewModel.updateClipSkip(it.toInt()) },
                            valueRange = 1f..12f,
                            steps = 10,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Bottom spacer
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun ImageGenSectionCard(content: @Composable ColumnScope.() -> Unit) {
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

@Composable
private fun imageGenTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    focusedPlaceholderColor = MaterialTheme.colorScheme.outline,
    unfocusedPlaceholderColor = MaterialTheme.colorScheme.outline
)
