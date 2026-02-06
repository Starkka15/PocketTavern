package com.stark.sillytavern.ui.screens.chub

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.stark.sillytavern.domain.model.ChubCharacter
import com.stark.sillytavern.domain.model.ChubSortOption
import com.stark.sillytavern.ui.components.ErrorDialog
import com.stark.sillytavern.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChubBrowserScreen(
    onBack: () -> Unit,
    onImportSuccess: () -> Unit,
    onLogin: () -> Unit,
    viewModel: ChubBrowserViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Browse Chub.ai")
                        if (uiState.isLoggedIn) {
                            Text(
                                text = uiState.chubUsername ?: "Signed in",
                                style = MaterialTheme.typography.labelSmall,
                                color = AccentGreen
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Close, "Close")
                    }
                },
                actions = {
                    if (uiState.isLoggedIn) {
                        IconButton(onClick = { viewModel.logout() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.Logout,
                                contentDescription = "Sign out",
                                tint = TextSecondary
                            )
                        }
                    } else {
                        TextButton(onClick = onLogin) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Sign in")
                        }
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
        ) {
            // Search bar
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search characters...") },
                leadingIcon = { Icon(Icons.Default.Search, "Search") },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            viewModel.updateSearchQuery("")
                            viewModel.search()
                        }) {
                            Icon(Icons.Default.Close, "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = DarkInputBackground,
                    unfocusedContainerColor = DarkInputBackground,
                    focusedBorderColor = AccentGreen,
                    unfocusedBorderColor = DarkInputBackground
                )
            )

            // Filters
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // NSFW toggle
                FilterChip(
                    selected = uiState.nsfw,
                    onClick = { viewModel.setNsfw(!uiState.nsfw) },
                    label = { Text("NSFW") }
                )

                // Sort dropdown
                var sortExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = sortExpanded,
                    onExpandedChange = { sortExpanded = it },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = uiState.sortBy.displayName,
                        onValueChange = { },
                        readOnly = true,
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sortExpanded) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = DarkInputBackground,
                            unfocusedContainerColor = DarkInputBackground
                        ),
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                    ExposedDropdownMenu(
                        expanded = sortExpanded,
                        onDismissRequest = { sortExpanded = false }
                    ) {
                        ChubSortOption.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.displayName) },
                                onClick = {
                                    viewModel.setSortBy(option)
                                    sortExpanded = false
                                }
                            )
                        }
                    }
                }

                // Search button
                FilledTonalButton(onClick = { viewModel.search() }) {
                    Text("Search")
                }
            }

            // Results
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Loading...", color = TextSecondary)
                    }
                }
            } else if (uiState.results.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No characters found", color = TextSecondary)
                }
            } else {
                val gridState = rememberLazyGridState()

                // Infinite scroll - load more when near bottom
                val lastVisibleIndex = remember {
                    derivedStateOf {
                        gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    }
                }

                // Trigger load when we're near the end (within 12 items or half the visible items)
                LaunchedEffect(lastVisibleIndex.value, uiState.results.size, uiState.isLoadingMore) {
                    val totalItems = uiState.results.size
                    val visibleCount = gridState.layoutInfo.visibleItemsInfo.size
                    val threshold = maxOf(totalItems - 12, totalItems - visibleCount)

                    if (lastVisibleIndex.value >= threshold &&
                        totalItems > 0 &&
                        !uiState.isLoadingMore &&
                        !uiState.isLoading &&
                        uiState.currentPage < uiState.totalPages
                    ) {
                        viewModel.loadMore()
                    }
                }

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f),
                    state = gridState
                ) {
                    items(uiState.results, key = { it.fullPath }) { character ->
                        ChubCharacterCard(
                            character = character,
                            onClick = { viewModel.selectCharacter(character) }
                        )
                    }

                    // Loading indicator at bottom
                    if (uiState.isLoadingMore) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }

            // Page indicator (simplified - no buttons needed with infinite scroll)
            if (uiState.totalPages > 1) {
                Text(
                    text = "Showing ${uiState.results.size} characters (Page ${uiState.currentPage} of ${uiState.totalPages})",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }

    // Character preview bottom sheet
    if (uiState.selectedCharacter != null) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.clearSelection() },
            sheetState = sheetState,
            containerColor = DarkBackground
        ) {
            ChubPreviewContent(
                character = uiState.selectedCharacter!!,
                isLoadingDetails = uiState.isLoadingDetails,
                firstMessage = uiState.selectedFirstMessage,
                isImporting = uiState.isImporting,
                onImport = { viewModel.importCharacter(onImportSuccess) },
                onCancel = { viewModel.clearSelection() }
            )
        }
    }

    // Error dialog
    uiState.error?.let { error ->
        ErrorDialog(
            message = error,
            onDismiss = { viewModel.clearError() }
        )
    }
}

@Composable
fun ChubCharacterCard(
    character: ChubCharacter,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, DarkSurfaceVariant)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // Avatar - direct access to Chub (no proxy needed on Android)
            AsyncImage(
                model = character.avatarUrl,
                contentDescription = character.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = character.name,
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = "by ${character.creatorName}",
                style = MaterialTheme.typography.labelSmall,
                color = AccentGreen,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = character.tagline.take(80) + if (character.tagline.length > 80) "..." else "",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 2,
                modifier = Modifier.height(32.dp)
            )

            Text(
                text = "${character.downloadCount} downloads | ${character.starCount} stars",
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary
            )
        }
    }
}

@Composable
fun ChubPreviewContent(
    character: ChubCharacter,
    isLoadingDetails: Boolean,
    firstMessage: String?,
    isImporting: Boolean,
    onImport: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Avatar - direct access to Chub (no proxy needed on Android)
            AsyncImage(
                model = character.avatarUrl,
                contentDescription = character.name,
                modifier = Modifier
                    .size(100.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = character.name,
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary
                )
                Text(
                    text = "by ${character.creatorName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AccentGreen
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${character.downloadCount} downloads | ${character.starCount} stars | ${character.ratingCount} ratings",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Tags
        if (character.topics.isNotEmpty()) {
            Text(
                text = "Tags",
                style = MaterialTheme.typography.titleSmall,
                color = AccentGreen
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = character.topics.joinToString(", "),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Description
        if (character.description.isNotBlank()) {
            Text(
                text = "Description",
                style = MaterialTheme.typography.titleSmall,
                color = AccentGreen
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = character.description.take(500) + if (character.description.length > 500) "..." else "",
                style = MaterialTheme.typography.bodySmall,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // First message
        if (isLoadingDetails) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        } else if (!firstMessage.isNullOrBlank()) {
            Text(
                text = "First Message",
                style = MaterialTheme.typography.titleSmall,
                color = AccentGreen
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = firstMessage.take(500) + if (firstMessage.length > 500) "..." else "",
                style = MaterialTheme.typography.bodySmall,
                color = TextPrimary
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f)
            ) {
                Text("Cancel")
            }

            Button(
                onClick = onImport,
                enabled = !isImporting,
                modifier = Modifier.weight(1f)
            ) {
                if (isImporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Import to SillyTavern")
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}
