package com.pockettavern.app.ui.screens.charavault

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.pockettavern.app.domain.model.CardSource
import com.pockettavern.app.domain.model.CharaVaultCharacter
import com.pockettavern.app.domain.model.CharaVaultLorebook
import com.pockettavern.app.domain.model.CharaVaultNsfwFilter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharaVaultScreen(
    onNavigateBack: () -> Unit,
    viewModel: CharaVaultViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showFilterMenu by remember { mutableStateOf(false) }
    var showTagSelector by remember { mutableStateOf(false) }
    var showContentTypeMenu by remember { mutableStateOf(false) }
    var tagSearchQuery by remember { mutableStateOf("") }

    // Load more when near bottom
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisibleIndex = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = gridState.layoutInfo.totalItemsCount
            lastVisibleIndex >= totalItems - 10 && !uiState.isLoadingMore && uiState.currentPage < uiState.totalPages
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            viewModel.loadMore()
        }
    }

    // Show error snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    // Content type dropdown (lorebooks only available for CharaVault)
                    val isCharaVault = uiState.selectedSource == CardSource.CHARAVAULT
                    Box {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = if (isCharaVault) Modifier.clickable { showContentTypeMenu = true } else Modifier
                        ) {
                            Column {
                                Text(if (isCharaVault) uiState.contentType.displayName else "Card Search")
                                val countText = when {
                                    isCharaVault && uiState.contentType == CharaVaultContentType.CHARACTERS ->
                                        uiState.stats?.totalCards?.let { "$it cards" }
                                    isCharaVault && uiState.contentType == CharaVaultContentType.LOREBOOKS ->
                                        uiState.lorebookStats?.totalLorebooks?.let { "$it lorebooks" }
                                    else -> null
                                }
                                countText?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (isCharaVault) {
                                Icon(
                                    Icons.Default.ArrowDropDown,
                                    contentDescription = "Switch content type",
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            }
                        }

                        // Content type dropdown menu (CharaVault only)
                        if (isCharaVault) {
                            DropdownMenu(
                                expanded = showContentTypeMenu,
                                onDismissRequest = { showContentTypeMenu = false }
                            ) {
                                CharaVaultContentType.entries.forEach { type ->
                                    DropdownMenuItem(
                                        text = { Text(type.displayName) },
                                        onClick = {
                                            viewModel.setContentType(type)
                                            showContentTypeMenu = false
                                        },
                                        leadingIcon = {
                                            if (uiState.contentType == type) {
                                                Icon(Icons.Default.Check, contentDescription = null)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Login/account button when in CharaVault.net mode
                    if (uiState.selectedSource == CardSource.CHARAVAULT && uiState.charavaultMode == "charavault") {
                        if (uiState.isLoggedIn) {
                            IconButton(onClick = { showSettingsDialog = true }) {
                                Icon(Icons.Default.AccountCircle, contentDescription = "Account", tint = MaterialTheme.colorScheme.primary)
                            }
                        } else {
                            TextButton(onClick = { viewModel.showLogin() }) {
                                Text("Login", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    IconButton(onClick = { showFilterMenu = true }) {
                        Icon(Icons.Default.FilterList, contentDescription = "Filter")
                    }
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }

                    // Filter dropdown
                    DropdownMenu(
                        expanded = showFilterMenu,
                        onDismissRequest = { showFilterMenu = false }
                    ) {
                        Text(
                            "Content Filter",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        CharaVaultNsfwFilter.entries.forEach { filter ->
                            DropdownMenuItem(
                                text = { Text(filter.displayName) },
                                onClick = {
                                    viewModel.setNsfwFilter(filter)
                                    showFilterMenu = false
                                },
                                leadingIcon = {
                                    if (uiState.nsfwFilter == filter) {
                                        Icon(Icons.Default.Check, contentDescription = null)
                                    }
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { paddingValues ->
        if (!uiState.isServerConfigured && uiState.selectedSource == CardSource.CHARAVAULT) {
            // Server not configured
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        Icons.Default.Cloud,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "CharaVault Server Not Configured",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "Enter your CharaVault server URL to browse cards",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = { showSettingsDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Configure Server")
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Source selector row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CardSource.entries.forEach { source ->
                        FilterChip(
                            selected = uiState.selectedSource == source,
                            onClick = { viewModel.setSource(source) },
                            label = { Text(source.displayName) },
                            leadingIcon = if (uiState.selectedSource == source) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null
                        )
                    }
                }

                // Search bar and tag selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    SearchBar(
                        query = uiState.searchQuery,
                        onQueryChange = { viewModel.search(it) },
                        totalCount = uiState.totalCount,
                        modifier = Modifier.weight(1f)
                    )

                    // Tag selector button (CharaVault only — tags are loaded from server)
                    if (uiState.selectedSource == CardSource.CHARAVAULT) {
                        FilledTonalButton(
                            onClick = { showTagSelector = true },
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Icon(Icons.Default.Label, contentDescription = null)
                            if (uiState.selectedTags.isNotEmpty()) {
                                Spacer(Modifier.width(4.dp))
                                Text("${uiState.selectedTags.size}")
                            }
                        }
                    }
                }

                // Selected tags
                if (uiState.selectedTags.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        uiState.selectedTags.forEach { tag ->
                            FilterChip(
                                selected = true,
                                onClick = { viewModel.toggleTag(tag) },
                                label = { Text(tag) },
                                trailingIcon = {
                                    Icon(Icons.Default.Close, contentDescription = "Remove")
                                }
                            )
                        }
                        TextButton(onClick = { viewModel.clearTags() }) {
                            Text("Clear all")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                // Results grid
                val isEmpty = when (uiState.contentType) {
                    CharaVaultContentType.CHARACTERS -> uiState.characterResults.isEmpty()
                    CharaVaultContentType.LOREBOOKS -> uiState.lorebookResults.isEmpty()
                }
                val emptyText = when (uiState.contentType) {
                    CharaVaultContentType.CHARACTERS -> "No cards found"
                    CharaVaultContentType.LOREBOOKS -> "No lorebooks found"
                }

                if (uiState.isLoading && isEmpty) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (isEmpty) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            emptyText,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Results grid
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 160.dp),
                            state = gridState,
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            when (uiState.contentType) {
                                CharaVaultContentType.CHARACTERS -> {
                                    items(
                                        count = uiState.characterResults.size,
                                        key = { index -> "char_${index}_${uiState.characterResults[index].id}" }
                                    ) { index ->
                                        val character = uiState.characterResults[index]
                                        CharaVaultCharacterCard(
                                            character = character,
                                            imageUrl = viewModel.buildImageUrl(character),
                                            onClick = { viewModel.selectCharacter(character) }
                                        )
                                    }
                                }
                                CharaVaultContentType.LOREBOOKS -> {
                                    items(
                                        count = uiState.lorebookResults.size,
                                        key = { index -> "lb_${index}_${uiState.lorebookResults[index].id}" }
                                    ) { index ->
                                        val lorebook = uiState.lorebookResults[index]
                                        CharaVaultLorebookCard(
                                            lorebook = lorebook,
                                            onClick = { viewModel.selectLorebook(lorebook) }
                                        )
                                    }
                                }
                            }

                            // Loading indicator
                            if (uiState.isLoading) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator()
                                    }
                                }
                            }
                        }

                        // Pagination controls
                        if (uiState.totalPages > 1) {
                            PaginationBar(
                                currentPage = uiState.currentPage,
                                totalPages = uiState.totalPages,
                                totalCount = uiState.totalCount,
                                isLoading = uiState.isLoading,
                                onPreviousPage = { viewModel.previousPage() },
                                onNextPage = { viewModel.nextPage() },
                                onGoToPage = { page -> viewModel.goToPage(page) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Character preview bottom sheet
    if (uiState.selectedCharacter != null) {
        CharacterPreviewSheet(
            character = uiState.selectedCharacter!!,
            imageUrl = viewModel.buildImageUrl(uiState.selectedCharacter!!),
            isLoadingDetails = uiState.isLoadingDetails,
            isImporting = uiState.isImporting,
            importSuccess = uiState.importSuccess,
            onDismiss = { viewModel.clearSelection() },
            onImport = { viewModel.importCharacter() },
            onTagClick = { tag -> viewModel.toggleTag(tag) }
        )
    }

    // Lorebook preview bottom sheet
    if (uiState.selectedLorebook != null) {
        LorebookPreviewSheet(
            lorebook = uiState.selectedLorebook!!,
            isLoadingDetails = uiState.isLoadingDetails,
            isImporting = uiState.isImporting,
            importSuccess = uiState.importSuccess,
            onDismiss = { viewModel.clearLorebookSelection() },
            onImport = { viewModel.importLorebook() },
            onTopicClick = { topic -> viewModel.toggleTag(topic) }
        )
    }

    // Settings dialog
    if (showSettingsDialog) {
        ServerSettingsDialog(
            currentUrl = uiState.serverUrl,
            currentMode = uiState.charavaultMode,
            isLoggedIn = uiState.isLoggedIn,
            charavaultEmail = uiState.charavaultEmail,
            nsfwVerified = uiState.nsfwVerified,
            onDismiss = { showSettingsDialog = false },
            onSaveUrl = { url ->
                viewModel.setServerUrl(url)
                showSettingsDialog = false
            },
            onSetMode = { mode ->
                viewModel.setMode(mode)
                if (mode == "charavault" && !uiState.isLoggedIn) {
                    showSettingsDialog = false
                    viewModel.showLogin()
                } else {
                    showSettingsDialog = false
                }
            },
            onLogout = {
                viewModel.logout()
                showSettingsDialog = false
            },
            onLogin = {
                viewModel.setMode("charavault")
                showSettingsDialog = false
                viewModel.showLogin()
            },
            onVerifyAge = { viewModel.verifyAge() }
        )
    }

    // Login dialog
    if (uiState.showLoginDialog) {
        CharaVaultLoginDialog(
            isLoggingIn = uiState.isLoggingIn,
            loginError = uiState.loginError,
            requires2fa = uiState.requires2fa,
            onDismiss = { viewModel.hideLogin() },
            onLogin = { email, password -> viewModel.login(email, password) },
            onVerify2fa = { code -> viewModel.verify2fa(code) }
        )
    }

    // Tag selector dialog
    if (showTagSelector) {
        TagSelectorDialog(
            availableTags = uiState.availableTags,
            selectedTags = uiState.selectedTags,
            isLoading = uiState.isLoadingTags,
            onTagToggle = { tag -> viewModel.toggleTag(tag) },
            onDismiss = { showTagSelector = false }
        )
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    totalCount: Int,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    var text by remember { mutableStateOf(query) }

    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        modifier = modifier,
        placeholder = { Text("Search cards...") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (text.isNotEmpty()) {
                IconButton(onClick = {
                    text = ""
                    onQueryChange("")
                }) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(
            onSearch = {
                onQueryChange(text)
                focusManager.clearFocus()
            }
        ),
        supportingText = {
            if (totalCount > 0) {
                Text("$totalCount results")
            }
        }
    )
}

@Composable
private fun PaginationBar(
    currentPage: Int,
    totalPages: Int,
    totalCount: Int,
    isLoading: Boolean,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    onGoToPage: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var showPageJumpDialog by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Previous button
            IconButton(
                onClick = onPreviousPage,
                enabled = currentPage > 1 && !isLoading
            ) {
                Icon(
                    Icons.Default.ChevronLeft,
                    contentDescription = "Previous page"
                )
            }

            // Page info (clickable to jump to page)
            TextButton(
                onClick = { showPageJumpDialog = true },
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    "Page $currentPage of $totalPages",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            // Next button
            IconButton(
                onClick = onNextPage,
                enabled = currentPage < totalPages && !isLoading
            ) {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = "Next page"
                )
            }
        }
    }

    // Page jump dialog
    if (showPageJumpDialog) {
        PageJumpDialog(
            currentPage = currentPage,
            totalPages = totalPages,
            onDismiss = { showPageJumpDialog = false },
            onGoToPage = { page ->
                onGoToPage(page)
                showPageJumpDialog = false
            }
        )
    }
}

@Composable
private fun PageJumpDialog(
    currentPage: Int,
    totalPages: Int,
    onDismiss: () -> Unit,
    onGoToPage: (Int) -> Unit
) {
    var pageText by remember { mutableStateOf(currentPage.toString()) }
    val isValid = pageText.toIntOrNull()?.let { it in 1..totalPages } ?: false

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Go to Page") },
        text = {
            Column {
                OutlinedTextField(
                    value = pageText,
                    onValueChange = { pageText = it.filter { c -> c.isDigit() } },
                    label = { Text("Page number (1-$totalPages)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(
                        onGo = {
                            if (isValid) {
                                onGoToPage(pageText.toInt())
                            }
                        }
                    ),
                    isError = pageText.isNotEmpty() && !isValid
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onGoToPage(pageText.toInt()) },
                enabled = isValid
            ) {
                Text("Go")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun CharaVaultCharacterCard(
    character: CharaVaultCharacter,
    imageUrl: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column {
            // Character image
            AsyncImage(
                model = imageUrl,
                contentDescription = character.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                contentScale = ContentScale.Crop
            )

            // Character info
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                // NSFW badge
                if (character.nsfw) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        Text(
                            "NSFW",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Text(
                    text = character.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = character.creator,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary, // Green accent like Chub
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (character.descriptionPreview.isNotBlank()) {
                    Text(
                        text = character.descriptionPreview,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CharacterPreviewSheet(
    character: CharaVaultCharacter,
    imageUrl: String,
    isLoadingDetails: Boolean,
    isImporting: Boolean,
    importSuccess: Boolean,
    onDismiss: () -> Unit,
    onImport: () -> Unit,
    onTagClick: (String) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        ) {
            // Scrollable content area
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header with image and basic info
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = character.name,
                        modifier = Modifier
                            .size(120.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        if (character.nsfw) {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.padding(bottom = 4.dp)
                            ) {
                                Text(
                                    "NSFW",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Text(
                            text = character.name,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "by ${character.creator}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Text(
                            text = character.folder,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Tags
                if (character.tags.isNotEmpty()) {
                    Text(
                        "Tags",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        character.tags.take(15).forEach { tag ->
                            SuggestionChip(
                                onClick = { onTagClick(tag) },
                                label = { Text(tag) }
                            )
                        }
                        if (character.tags.size > 15) {
                            Text(
                                "+${character.tags.size - 15} more",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                // Description
                val description = character.fullDescription ?: character.descriptionPreview
                if (description.isNotBlank()) {
                    Text(
                        "Description",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = description.take(500) + if (description.length > 500) "..." else "",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(16.dp))
                }

                // First message
                val firstMes = character.fullFirstMes ?: character.firstMesPreview
                if (firstMes.isNotBlank()) {
                    Text(
                        "First Message",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = firstMes.take(500) + if (firstMes.length > 500) "..." else "",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }

                // Loading indicator
                if (isLoadingDetails) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(16.dp))
                }
            } // End scrollable content

            // Action buttons - always visible at bottom
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }

                Button(
                    onClick = onImport,
                    modifier = Modifier.weight(1f),
                    enabled = !isImporting && !importSuccess
                ) {
                    if (isImporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Importing...")
                    } else if (importSuccess) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Imported!")
                    } else {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Import to PocketTavern")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ServerSettingsDialog(
    currentUrl: String,
    currentMode: String,
    isLoggedIn: Boolean,
    charavaultEmail: String?,
    nsfwVerified: Boolean,
    onDismiss: () -> Unit,
    onSaveUrl: (String) -> Unit,
    onSetMode: (String) -> Unit,
    onLogout: () -> Unit,
    onLogin: () -> Unit,
    onVerifyAge: () -> Unit
) {
    var url by remember { mutableStateOf(currentUrl) }
    var selectedMode by remember { mutableStateOf(currentMode) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Card Server") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Mode selector
                Text("Source", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Local CharaVault button
                    FilterChip(
                        selected = selectedMode == "local",
                        onClick = { selectedMode = "local" },
                        label = { Text("CharaVault (Local)") },
                        leadingIcon = {
                            if (selectedMode == "local") Icon(Icons.Default.Check, null, Modifier.size(16.dp))
                            else Icon(Icons.Default.Storage, null, Modifier.size(16.dp))
                        }
                    )

                    // CharaVault.net button
                    FilterChip(
                        selected = selectedMode == "charavault",
                        onClick = { selectedMode = "charavault" },
                        label = { Text("CharaVault.net") },
                        leadingIcon = {
                            if (selectedMode == "charavault") Icon(Icons.Default.Check, null, Modifier.size(16.dp))
                            else Icon(Icons.Default.Cloud, null, Modifier.size(16.dp))
                        }
                    )
                }

                HorizontalDivider()

                if (selectedMode == "local") {
                    // Local server URL input
                    Text(
                        "Enter your local CharaVault server URL",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("Server URL") },
                        placeholder = { Text("http://192.168.1.100:8787") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    // CharaVault.net status
                    if (isLoggedIn) {
                        // Logged in state
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.AccountCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    charavaultEmail ?: "Logged in",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    if (nsfwVerified) "NSFW Enabled" else "SFW Only",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (nsfwVerified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (!nsfwVerified) {
                            OutlinedButton(
                                onClick = onVerifyAge,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Verify Age (18+) for NSFW")
                            }
                        }

                        OutlinedButton(
                            onClick = onLogout,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Logout, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Logout")
                        }
                    } else {
                        // Not logged in
                        Text(
                            "Login to CharaVault.net to access the full card library including NSFW content.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = onLogin,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Login, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Login to CharaVault.net")
                        }
                        Text(
                            "Browsing without login shows SFW cards only.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedMode == "local") {
                        onSaveUrl(url.trim())
                        if (currentMode != "local") onSetMode("local")
                    } else {
                        onSetMode("charavault")
                    }
                },
                enabled = selectedMode == "charavault" || url.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun CharaVaultLoginDialog(
    isLoggingIn: Boolean,
    loginError: String?,
    requires2fa: Boolean,
    onDismiss: () -> Unit,
    onLogin: (String, String) -> Unit,
    onVerify2fa: (String) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var tfaCode by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!isLoggingIn) onDismiss() },
        title = { Text(if (requires2fa) "Two-Factor Authentication" else "Login to CharaVault.net") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (requires2fa) {
                    Text(
                        "Enter the 6-digit code from your authenticator app.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = tfaCode,
                        onValueChange = { tfaCode = it.filter { c -> c.isDigit() }.take(6) },
                        label = { Text("2FA Code") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        keyboardActions = KeyboardActions(
                            onDone = { if (tfaCode.length == 6) onVerify2fa(tfaCode) }
                        )
                    )
                } else {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        keyboardActions = KeyboardActions(
                            onDone = { if (email.isNotBlank() && password.isNotBlank()) onLogin(email, password) }
                        )
                    )
                }

                if (loginError != null) {
                    Text(
                        loginError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                if (isLoggingIn) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (requires2fa) {
                        onVerify2fa(tfaCode)
                    } else {
                        onLogin(email.trim(), password)
                    }
                },
                enabled = !isLoggingIn && if (requires2fa) tfaCode.length == 6 else (email.isNotBlank() && password.isNotBlank())
            ) {
                Text(if (requires2fa) "Verify" else "Login")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoggingIn) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable () -> Unit
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = verticalArrangement
    ) {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TagSelectorDialog(
    availableTags: List<Pair<String, Int>>,
    selectedTags: List<String>,
    isLoading: Boolean,
    onTagToggle: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    // Filter tags based on search
    val filteredTags = remember(availableTags, searchQuery) {
        if (searchQuery.isBlank()) {
            availableTags.take(100) // Show top 100 by default
        } else {
            availableTags.filter { (tag, _) ->
                tag.contains(searchQuery, ignoreCase = true)
            }.take(100)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Tags") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
            ) {
                // Search field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search tags") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))

                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (filteredTags.isEmpty()) {
                    Text(
                        "No tags found",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
                    // Tag list with checkboxes
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(filteredTags.size) { index ->
                            val (tag, count) = filteredTags[index]
                            val isSelected = tag in selectedTags

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onTagToggle(tag) }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { onTagToggle(tag) }
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = tag,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = count.toString(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Done")
            }
        },
        dismissButton = {
            if (selectedTags.isNotEmpty()) {
                TextButton(onClick = {
                    selectedTags.forEach { onTagToggle(it) }
                }) {
                    Text("Clear All")
                }
            }
        }
    )
}

@Composable
private fun CharaVaultLorebookCard(
    lorebook: CharaVaultLorebook,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // NSFW badge
            if (lorebook.nsfw) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    Text(
                        "NSFW",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Lorebook icon placeholder
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Book,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = lorebook.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = lorebook.creator,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(4.dp))

            // Stats row
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Entry count
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.List,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(
                        text = "${lorebook.entryCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Star count
                if (lorebook.starCount > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = Color(0xFFFFD700)
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            text = "${lorebook.starCount}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Topics
            if (lorebook.topics.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = lorebook.topics.take(3).joinToString(", "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LorebookPreviewSheet(
    lorebook: CharaVaultLorebook,
    isLoadingDetails: Boolean,
    isImporting: Boolean,
    importSuccess: Boolean,
    onDismiss: () -> Unit,
    onImport: () -> Unit,
    onTopicClick: (String) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        ) {
            // Scrollable content
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
            ) {
            // Header with icon and basic info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Lorebook icon
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Book,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    if (lorebook.nsfw) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.padding(bottom = 4.dp)
                        ) {
                            Text(
                                "NSFW",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Text(
                        text = lorebook.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "by ${lorebook.creator}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(Modifier.height(8.dp))

                    // Stats
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.List,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "${lorebook.entryCount} entries",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (lorebook.tokenCount > 0) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Numbers,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "${lorebook.tokenCount} tokens",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (lorebook.starCount > 0) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Star,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = Color(0xFFFFD700)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "${lorebook.starCount}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Description
            if (lorebook.description.isNotBlank()) {
                Text(
                    "Description",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = lorebook.description,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(16.dp))
            }

            // Topics
            if (lorebook.topics.isNotEmpty()) {
                Text(
                    "Topics",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    lorebook.topics.forEach { topic ->
                        SuggestionChip(
                            onClick = { onTopicClick(topic) },
                            label = { Text(topic) }
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // Keywords
            if (lorebook.keywords.isNotBlank()) {
                Text(
                    "Keywords",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = lorebook.keywords,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
            }

            // Entries preview
            if (lorebook.entries != null && lorebook.entries.isNotEmpty()) {
                Text(
                    "Entries Preview (${lorebook.entries.size} total)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))

                lorebook.entries.take(3).forEach { entry ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = entry.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            if (entry.keys.isNotEmpty()) {
                                Text(
                                    text = "Keys: ${entry.keys.take(5).joinToString(", ")}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            if (entry.content.isNotBlank()) {
                                Text(
                                    text = entry.content.take(100) + if (entry.content.length > 100) "..." else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                if (lorebook.entries.size > 3) {
                    Text(
                        text = "+${lorebook.entries.size - 3} more entries",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
            } // End scrollable content

            // Loading indicator
            if (isLoadingDetails) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
            }

            // Action buttons - always visible at bottom
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }

                Button(
                    onClick = onImport,
                    modifier = Modifier.weight(1f),
                    enabled = !isImporting && !importSuccess
                ) {
                    if (isImporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Importing...")
                    } else if (importSuccess) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Imported!")
                    } else {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Import to PocketTavern")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
