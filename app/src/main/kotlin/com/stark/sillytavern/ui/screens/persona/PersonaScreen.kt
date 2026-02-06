package com.stark.sillytavern.ui.screens.persona

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.stark.sillytavern.domain.model.Persona
import com.stark.sillytavern.domain.model.PersonaPosition
import com.stark.sillytavern.domain.model.PersonaRole

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonaScreen(
    onBack: () -> Unit,
    viewModel: PersonaViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Handle errors
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    // Handle success messages
    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSuccess()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Personas") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadPersonas() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.showCreateDialog() }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create Persona")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (uiState.personas.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "No personas found",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Tap + to create a new persona",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Selected persona info
                    uiState.selectedPersona?.let { selected ->
                        item {
                            SelectedPersonaCard(
                                persona = selected,
                                serverUrl = uiState.serverUrl,
                                onEdit = { viewModel.showEditDialog(selected) }
                            )
                        }

                        item {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            Text(
                                "All Personas",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }

                    items(uiState.personas) { persona ->
                        PersonaListItem(
                            persona = persona,
                            serverUrl = uiState.serverUrl,
                            isSelected = persona.isSelected,
                            onSelect = { viewModel.selectPersona(persona) },
                            onEdit = { viewModel.showEditDialog(persona) },
                            isSaving = uiState.isSaving
                        )
                    }
                }
            }

            // Loading overlay
            if (uiState.isSaving) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }

        // Edit dialog
        if (uiState.showEditDialog && uiState.editingPersona != null) {
            EditPersonaDialog(
                persona = uiState.editingPersona!!,
                description = uiState.editDescription,
                position = uiState.editPosition,
                role = uiState.editRole,
                depth = uiState.editDepth,
                onDescriptionChange = { viewModel.updateEditDescription(it) },
                onPositionChange = { viewModel.updateEditPosition(it) },
                onRoleChange = { viewModel.updateEditRole(it) },
                onDepthChange = { viewModel.updateEditDepth(it) },
                onSave = { viewModel.savePersonaEdit() },
                onDelete = { viewModel.showDeleteConfirm() },
                onDismiss = { viewModel.hideEditDialog() },
                isSaving = uiState.isSaving
            )
        }

        // Delete confirmation
        if (uiState.showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { viewModel.hideDeleteConfirm() },
                title = { Text("Delete Persona?") },
                text = { Text("Are you sure you want to delete \"${uiState.editingPersona?.name}\"? This cannot be undone.") },
                confirmButton = {
                    TextButton(
                        onClick = { viewModel.deletePersona() },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.hideDeleteConfirm() }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Create persona dialog
        if (uiState.showCreateDialog) {
            CreatePersonaDialog(
                imageBytes = uiState.createImageBytes,
                name = uiState.createName,
                description = uiState.createDescription,
                forgeAvailable = uiState.forgeAvailable,
                generationPrompt = uiState.generationPrompt,
                isGenerating = uiState.isGenerating,
                generationProgress = uiState.generationProgress,
                onImageSelected = { bytes, mimeType ->
                    viewModel.setCreateImage(bytes, mimeType)
                },
                onNameChange = { viewModel.updateCreateName(it) },
                onDescriptionChange = { viewModel.updateCreateDescription(it) },
                onGenerationPromptChange = { viewModel.updateGenerationPrompt(it) },
                onGenerate = { viewModel.generateImage() },
                onCancelGeneration = { viewModel.cancelGeneration() },
                onCreate = { viewModel.createPersona() },
                onDismiss = { viewModel.hideCreateDialog() },
                isSaving = uiState.isSaving
            )
        }
    }
}

@Composable
private fun SelectedPersonaCard(
    persona: Persona,
    serverUrl: String,
    onEdit: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = "$serverUrl/User Avatars/${persona.avatarId}",
                contentDescription = "Avatar",
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape),
                contentScale = ContentScale.Crop
            )

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Current Persona",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
                Text(
                    persona.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                if (persona.description.isNotBlank()) {
                    Text(
                        persona.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(onClick = onEdit) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Edit",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun PersonaListItem(
    persona: Persona,
    serverUrl: String,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    isSaving: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isSaving) { onSelect() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = "$serverUrl/User Avatars/${persona.avatarId}",
                contentDescription = "Avatar",
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .then(
                        if (isSelected) {
                            Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        } else {
                            Modifier
                        }
                    ),
                contentScale = ContentScale.Crop
            )

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        persona.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                    if (isSelected) {
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Selected",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                if (persona.description.isNotBlank()) {
                    Text(
                        persona.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(onClick = onEdit) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Edit",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditPersonaDialog(
    persona: Persona,
    description: String,
    position: PersonaPosition,
    role: PersonaRole,
    depth: Int,
    onDescriptionChange: (String) -> Unit,
    onPositionChange: (PersonaPosition) -> Unit,
    onRoleChange: (PersonaRole) -> Unit,
    onDepthChange: (Int) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    isSaving: Boolean
) {
    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text("Edit ${persona.name}") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = description,
                    onValueChange = onDescriptionChange,
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5
                )

                // Position dropdown
                var positionExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = positionExpanded,
                    onExpandedChange = { positionExpanded = it }
                ) {
                    OutlinedTextField(
                        value = when (position) {
                            PersonaPosition.IN_PROMPT -> "In System Prompt"
                            PersonaPosition.IN_CHAT -> "In Chat at Depth"
                            PersonaPosition.TOP_OF_AN -> "Top of Author's Note"
                            PersonaPosition.BOTTOM_OF_AN -> "Bottom of Author's Note"
                        },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Position") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = positionExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = positionExpanded,
                        onDismissRequest = { positionExpanded = false }
                    ) {
                        PersonaPosition.entries.forEach { pos ->
                            DropdownMenuItem(
                                text = {
                                    Text(when (pos) {
                                        PersonaPosition.IN_PROMPT -> "In System Prompt"
                                        PersonaPosition.IN_CHAT -> "In Chat at Depth"
                                        PersonaPosition.TOP_OF_AN -> "Top of Author's Note"
                                        PersonaPosition.BOTTOM_OF_AN -> "Bottom of Author's Note"
                                    })
                                },
                                onClick = {
                                    onPositionChange(pos)
                                    positionExpanded = false
                                }
                            )
                        }
                    }
                }

                // Depth (only shown for IN_CHAT position)
                if (position == PersonaPosition.IN_CHAT) {
                    OutlinedTextField(
                        value = depth.toString(),
                        onValueChange = { it.toIntOrNull()?.let(onDepthChange) },
                        label = { Text("Depth") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }

                // Role dropdown
                var roleExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = roleExpanded,
                    onExpandedChange = { roleExpanded = it }
                ) {
                    OutlinedTextField(
                        value = when (role) {
                            PersonaRole.SYSTEM -> "System"
                            PersonaRole.USER -> "User"
                            PersonaRole.ASSISTANT -> "Assistant"
                        },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Role") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = roleExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = roleExpanded,
                        onDismissRequest = { roleExpanded = false }
                    ) {
                        PersonaRole.entries.forEach { r ->
                            DropdownMenuItem(
                                text = {
                                    Text(when (r) {
                                        PersonaRole.SYSTEM -> "System"
                                        PersonaRole.USER -> "User"
                                        PersonaRole.ASSISTANT -> "Assistant"
                                    })
                                },
                                onClick = {
                                    onRoleChange(r)
                                    roleExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(
                    onClick = onDelete,
                    enabled = !isSaving,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = onSave,
                    enabled = !isSaving
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Save")
                    }
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isSaving
            ) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreatePersonaDialog(
    imageBytes: ByteArray?,
    name: String,
    description: String,
    forgeAvailable: Boolean,
    generationPrompt: String,
    isGenerating: Boolean,
    generationProgress: Float,
    onImageSelected: (ByteArray, String) -> Unit,
    onNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onGenerationPromptChange: (String) -> Unit,
    onGenerate: () -> Unit,
    onCancelGeneration: () -> Unit,
    onCreate: () -> Unit,
    onDismiss: () -> Unit,
    isSaving: Boolean
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val bytes = inputStream?.readBytes()
                inputStream?.close()

                if (bytes != null) {
                    val mimeType = context.contentResolver.getType(it) ?: "image/png"
                    onImageSelected(bytes, mimeType)
                }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isSaving && !isGenerating) onDismiss() },
        title = { Text("Create Persona") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Tab selector if Forge is available
                if (forgeAvailable) {
                    TabRow(selectedTabIndex = selectedTab) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("Select") }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("Generate") }
                        )
                    }
                }

                // Image preview
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .then(
                            if (selectedTab == 0 && !isGenerating) {
                                Modifier.clickable { imagePicker.launch("image/*") }
                            } else {
                                Modifier
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isGenerating -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(
                                    progress = { generationProgress },
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "${(generationProgress * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        imageBytes != null -> {
                            val bitmap = remember(imageBytes) {
                                BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                            }
                            bitmap?.let {
                                Image(
                                    bitmap = it.asImageBitmap(),
                                    contentDescription = "Avatar",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                        selectedTab == 0 -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.AddAPhoto,
                                    contentDescription = "Select image",
                                    modifier = Modifier.size(32.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "Tap to select",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        else -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = "Generate",
                                    modifier = Modifier.size(32.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "Enter prompt below",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Generation prompt (only on Generate tab)
                if (selectedTab == 1 && forgeAvailable) {
                    OutlinedTextField(
                        value = generationPrompt,
                        onValueChange = onGenerationPromptChange,
                        label = { Text("Generation Prompt") },
                        placeholder = { Text("portrait of a person, detailed, high quality") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 3,
                        enabled = !isGenerating
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isGenerating) {
                            OutlinedButton(
                                onClick = onCancelGeneration,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Cancel")
                            }
                        } else {
                            Button(
                                onClick = onGenerate,
                                modifier = Modifier.weight(1f),
                                enabled = generationPrompt.isNotBlank()
                            ) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Generate")
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isGenerating
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = onDescriptionChange,
                    label = { Text("Description (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 3,
                    enabled = !isGenerating
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onCreate,
                enabled = !isSaving && !isGenerating && imageBytes != null && name.isNotBlank()
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Create")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isSaving && !isGenerating
            ) {
                Text("Cancel")
            }
        }
    )
}
