package com.pockettavern.app.ui.screens.createcharacter

import com.pockettavern.app.R
import androidx.compose.ui.res.stringResource
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pockettavern.app.domain.model.GenerationState
import com.pockettavern.app.ui.components.ErrorDialog
import com.pockettavern.app.ui.theme.*

enum class CharacterTab(val title: String, val icon: @Composable () -> Unit) {
    BASIC("Basic", { Icon(Icons.Default.Person, null, Modifier.size(20.dp)) }),
    PERSONALITY("Personality", { Icon(Icons.Default.Psychology, null, Modifier.size(20.dp)) }),
    MESSAGES("Messages", { Icon(Icons.Default.Chat, null, Modifier.size(20.dp)) }),
    ADVANCED("Advanced", { Icon(Icons.Default.Settings, null, Modifier.size(20.dp)) }),
    META("Meta", { Icon(Icons.Default.Info, null, Modifier.size(20.dp)) })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateCharacterScreen(
    onBack: () -> Unit,
    onCreated: () -> Unit,
    editAvatarUrl: String? = null,
    viewModel: CreateCharacterViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(CharacterTab.BASIC) }
    var showAvatarOptionsDialog by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.use { inputStream ->
                    val bytes = inputStream.readBytes()
                    viewModel.setAvatarFromBytes(bytes)
                }
            } catch (e: Exception) { }
        }
    }

    LaunchedEffect(editAvatarUrl) {
        if (editAvatarUrl != null) {
            viewModel.loadCharacterForEdit(editAvatarUrl)
        }
    }

    LaunchedEffect(uiState.createSuccess) {
        if (uiState.createSuccess) {
            onCreated()
        }
    }

    val screenTitle = if (uiState.isEditMode) "Edit Character" else "Create Character"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(screenTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    // Save button in app bar
                    TextButton(
                        onClick = { viewModel.createCharacter() },
                        enabled = uiState.name.isNotBlank() && !uiState.isCreating
                    ) {
                        if (uiState.isCreating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Check, null)
                            Spacer(Modifier.width(4.dp))
                            Text(if (uiState.isEditMode) "Save" else "Create")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        if (uiState.isLoadingCharacter) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Tab row
                ScrollableTabRow(
                    selectedTabIndex = selectedTab.ordinal,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    edgePadding = 8.dp
                ) {
                    CharacterTab.entries.forEach { tab ->
                        Tab(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            text = { Text(tab.title) },
                            icon = tab.icon,
                            selectedContentColor = MaterialTheme.colorScheme.primary,
                            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Tab content
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    when (selectedTab) {
                        CharacterTab.BASIC -> BasicTab(
                            uiState = uiState,
                            viewModel = viewModel,
                            onAvatarClick = { showAvatarOptionsDialog = true }
                        )
                        CharacterTab.PERSONALITY -> PersonalityTab(uiState, viewModel)
                        CharacterTab.MESSAGES -> MessagesTab(uiState, viewModel)
                        CharacterTab.ADVANCED -> AdvancedTab(uiState, viewModel)
                        CharacterTab.META -> MetaTab(uiState, viewModel)
                    }

                    Spacer(Modifier.height(80.dp)) // Bottom padding for FAB
                }
            }
        }
    }

    // Avatar options dialog
    if (showAvatarOptionsDialog) {
        AvatarOptionsDialog(
            uiState = uiState,
            onDismiss = { showAvatarOptionsDialog = false },
            onPickImage = {
                showAvatarOptionsDialog = false
                imagePickerLauncher.launch("image/*")
            },
            onGenerate = {
                showAvatarOptionsDialog = false
                viewModel.generateAvatar()
            },
            onClear = {
                showAvatarOptionsDialog = false
                viewModel.clearAvatar()
            }
        )
    }

    uiState.error?.let { error ->
        ErrorDialog(message = error, onDismiss = { viewModel.clearError() })
    }
}

@Composable
private fun BasicTab(
    uiState: CreateCharacterUiState,
    viewModel: CreateCharacterViewModel,
    onAvatarClick: () -> Unit
) {
    // Avatar section
    if (!uiState.isEditMode) {
        SectionHeader(stringResource(R.string.avatar))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Avatar preview
            AvatarPreview(
                avatarBase64 = uiState.avatarBase64,
                generationState = uiState.generationState,
                onClick = onAvatarClick
            )

            // Avatar prompt
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (uiState.forgeAvailable) {
                    CharacterTextField(
                        value = uiState.avatarPrompt,
                        onValueChange = { viewModel.updateAvatarPrompt(it) },
                        label = "Avatar Prompt",
                        placeholder = "Describe appearance for AI generation...",
                        minLines = 2,
                        maxLines = 3
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val isGenerating = uiState.generationState is GenerationState.InProgress ||
                                uiState.generationState is GenerationState.Starting

                        if (isGenerating) {
                            OutlinedButton(onClick = { viewModel.cancelGeneration() }) {
                                Icon(Icons.Default.Close, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.cancel))
                            }
                        } else {
                            FilledTonalButton(onClick = { viewModel.generateAvatar() }) {
                                Icon(Icons.Default.AutoAwesome, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.generate))
                            }
                        }
                    }
                } else {
                    Text(stringResource(R.string.configure_stable_diffusion_forge_in_settings),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
    }

    // Name field
    SectionHeader(stringResource(R.string.character_name))
    CharacterTextField(
        value = uiState.name,
        onValueChange = { viewModel.updateName(it) },
        label = "Name *",
        placeholder = "Enter character name",
        singleLine = true,
        isError = uiState.name.isBlank() && uiState.error != null
    )

    // Card import notice
    if (uiState.isCardImport) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                Column {
                    Text(stringResource(R.string.character_card_detected), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "All data will be imported including ${if (uiState.hasCharacterBook) "${uiState.characterBookEntryCount} lorebook entries" else "metadata"}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun PersonalityTab(uiState: CreateCharacterUiState, viewModel: CreateCharacterViewModel) {
    SectionHeader(stringResource(R.string.description))
    Text(stringResource(R.string.physical_appearance_background_and_general_in),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    CharacterTextField(
        value = uiState.description,
        onValueChange = { viewModel.updateDescription(it) },
        label = "Description",
        placeholder = "{{char}} is a tall woman with long black hair...",
        minLines = 4,
        maxLines = 8
    )

    SectionHeader(stringResource(R.string.personality))
    Text(stringResource(R.string.character_traits_demeanor_and_how_they_behave),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    CharacterTextField(
        value = uiState.personality,
        onValueChange = { viewModel.updatePersonality(it) },
        label = "Personality",
        placeholder = "Witty, sarcastic, secretly kind-hearted...",
        minLines = 3,
        maxLines = 6
    )

    SectionHeader(stringResource(R.string.scenario))
    Text(stringResource(R.string.the_setting_or_situation_for_the_conversation),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    CharacterTextField(
        value = uiState.scenario,
        onValueChange = { viewModel.updateScenario(it) },
        label = "Scenario",
        placeholder = "{{user}} meets {{char}} at a coffee shop...",
        minLines = 2,
        maxLines = 4
    )
}

@Composable
private fun MessagesTab(uiState: CreateCharacterUiState, viewModel: CreateCharacterViewModel) {
    SectionHeader(stringResource(R.string.first_message))
    Text(stringResource(R.string.the_opening_message_when_starting_a_new_chat),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    CharacterTextField(
        value = uiState.firstMessage,
        onValueChange = { viewModel.updateFirstMessage(it) },
        label = "First Message",
        placeholder = "*{{char}} looks up as you approach* \"Oh, hello there!\"",
        minLines = 4,
        maxLines = 8
    )

    // Alternate Greetings
    SectionHeader(stringResource(R.string.alternate_greetings))
    Text(stringResource(R.string.additional_first_messages_that_can_be_swiped),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    uiState.alternateGreetings.forEachIndexed { index, greeting ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top
        ) {
            CharacterTextField(
                value = greeting,
                onValueChange = { viewModel.updateAlternateGreeting(index, it) },
                label = "Greeting ${index + 2}",
                placeholder = "Alternative opening...",
                modifier = Modifier.weight(1f),
                minLines = 2,
                maxLines = 4
            )
            IconButton(
                onClick = { viewModel.removeAlternateGreeting(index) },
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Icon(Icons.Default.Delete, "Remove", tint = MaterialTheme.colorScheme.error)
            }
        }
    }

    OutlinedButton(
        onClick = { viewModel.addAlternateGreeting() },
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.Add, null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.add_alternate_greeting))
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(vertical = 8.dp))

    SectionHeader(stringResource(R.string.example_dialogue))
    Text(stringResource(R.string.example_conversations_to_guide_the_ai_s_writi),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    CharacterTextField(
        value = uiState.messageExample,
        onValueChange = { viewModel.updateMessageExample(it) },
        label = "Example Dialogue",
        placeholder = "<START>\n{{user}}: Hi!\n{{char}}: *waves* Hey there!\n<START>\n{{user}}: How are you?\n{{char}}: I'm doing great, thanks for asking!",
        minLines = 6,
        maxLines = 12
    )
}

@Composable
private fun AdvancedTab(uiState: CreateCharacterUiState, viewModel: CreateCharacterViewModel) {
    SectionHeader(stringResource(R.string.system_prompt_2))
    Text(stringResource(R.string.overrides_the_default_system_prompt_use_origi),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    CharacterTextField(
        value = uiState.systemPrompt,
        onValueChange = { viewModel.updateSystemPrompt(it) },
        label = "System Prompt",
        placeholder = "{{original}}\n\nAdditional instructions for this character...",
        minLines = 4,
        maxLines = 10
    )

    SectionHeader(stringResource(R.string.post_history_instructions))
    Text(stringResource(R.string.injected_after_the_chat_history_like_a_jailbr),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    CharacterTextField(
        value = uiState.postHistoryInstructions,
        onValueChange = { viewModel.updatePostHistoryInstructions(it) },
        label = "Post-History Instructions",
        placeholder = "Remember to stay in character...",
        minLines = 3,
        maxLines = 6
    )

    if (uiState.hasCharacterBook) {
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(vertical = 8.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Book, null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.character_lorebook), style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${uiState.characterBookEntryCount} entries will be imported",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun MetaTab(uiState: CreateCharacterUiState, viewModel: CreateCharacterViewModel) {
    SectionHeader(stringResource(R.string.creator))
    CharacterTextField(
        value = uiState.creator,
        onValueChange = { viewModel.updateCreator(it) },
        label = "Creator",
        placeholder = "Your name or username",
        singleLine = true
    )

    SectionHeader(stringResource(R.string.tags))
    Text(stringResource(R.string.keywords_for_categorizing_the_character),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    // Tag input
    var tagInput by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CharacterTextField(
            value = tagInput,
            onValueChange = { tagInput = it },
            label = "Add Tag",
            placeholder = "fantasy, female, elf...",
            modifier = Modifier.weight(1f),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                if (tagInput.isNotBlank()) {
                    viewModel.addTag(tagInput)
                    tagInput = ""
                }
                focusManager.clearFocus()
            })
        )
        FilledTonalButton(
            onClick = {
                if (tagInput.isNotBlank()) {
                    viewModel.addTag(tagInput)
                    tagInput = ""
                }
            },
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Icon(Icons.Default.Add, "Add tag")
        }
    }

    // Display tags
    if (uiState.tags.isNotEmpty()) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp)
        ) {
            itemsIndexed(uiState.tags) { _, tag ->
                InputChip(
                    selected = false,
                    onClick = { viewModel.removeTag(tag) },
                    label = { Text(tag) },
                    trailingIcon = {
                        Icon(Icons.Default.Close, "Remove", Modifier.size(16.dp))
                    }
                )
            }
        }
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(vertical = 8.dp))

    SectionHeader(stringResource(R.string.creator_notes))
    Text(stringResource(R.string.notes_for_users_about_the_character_not_used),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    CharacterTextField(
        value = uiState.creatorNotes,
        onValueChange = { viewModel.updateCreatorNotes(it) },
        label = "Creator Notes",
        placeholder = "Tips for getting the best experience with this character...",
        minLines = 3,
        maxLines = 6
    )
    CharacterTextField(
        value = uiState.loreHints,
        onValueChange = { viewModel.updateLoreHints(it) },
        label = "Lore Tracking (PocketTavern)",
        placeholder = "What /scanlore should watch for — e.g. Track: weight changes after digestion, villain captures (name + outcome), solo missions without partner...",
        minLines = 3,
        maxLines = 8
    )
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun CharacterTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = Int.MAX_VALUE,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder, color = MaterialTheme.colorScheme.outline) },
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine,
        minLines = if (singleLine) 1 else minLines,
        maxLines = if (singleLine) 1 else maxLines,
        isError = isError,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            cursorColor = MaterialTheme.colorScheme.primary
        )
    )
}

@Composable
private fun AvatarPreview(
    avatarBase64: String?,
    generationState: GenerationState,
    onClick: () -> Unit
) {
    val isGenerating = generationState is GenerationState.InProgress ||
            generationState is GenerationState.Starting

    Box(
        modifier = Modifier
            .size(120.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(enabled = !isGenerating, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        when {
            avatarBase64 != null -> {
                val bitmap = remember(avatarBase64) {
                    val bytes = Base64.decode(avatarBase64, Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
                bitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = stringResource(R.string.avatar),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
            generationState is GenerationState.InProgress -> {
                val progress = generationState.progress
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.size(48.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            generationState is GenerationState.Starting -> {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            else -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.AddPhotoAlternate, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.add_avatar), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun AvatarOptionsDialog(
    uiState: CreateCharacterUiState,
    onDismiss: () -> Unit,
    onPickImage: () -> Unit,
    onGenerate: () -> Unit,
    onClear: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.select_avatar)) },
        text = {
            Column {
                TextButton(onClick = onPickImage, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Image, null, Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.choose_from_gallery))
                }

                if (uiState.forgeAvailable) {
                    TextButton(onClick = onGenerate, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.AutoAwesome, null, Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.generate_with_ai))
                    }
                }

                if (uiState.avatarBase64 != null) {
                    TextButton(
                        onClick = onClear,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Delete, null, Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.remove_avatar))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
