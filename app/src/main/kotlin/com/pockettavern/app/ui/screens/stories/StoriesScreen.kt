package com.pockettavern.app.ui.screens.stories

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import android.graphics.BitmapFactory
import com.pockettavern.app.domain.model.StoryCardRef

/**
 * T8 — the Stories tab. Grid of imported Story cards (cover art). Import (PNG `ptensemble` / JSON)
 * is the only authoring path (V11). The tab itself is gated on having ≥1 Story (V12) by the host;
 * this screen also shows an empty-state with an import prompt for first run.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoriesScreen(
    onOpenStory: (storyId: String) -> Unit,
    onBack: () -> Unit,
    viewModel: StoriesViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.importUri(it) }
    }
    LaunchedEffect(state.importMessage) {
        state.importMessage?.let { snackbar.showSnackbar(it); viewModel.clearMessage() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { TopAppBar(title = { Text("Stories") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { picker.launch("*/*") },
                icon = { Icon(Icons.Default.Add, null) }, text = { Text("Import") }
            )
        }
    ) { pad ->
        if (state.stories.isEmpty()) {
            Column(
                Modifier.padding(pad).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("No Stories yet", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text("Import a Story card (.png or .json) to begin.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                modifier = Modifier.padding(pad).fillMaxSize().padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.stories, key = { it.id }) { ref ->
                    StoryCardTile(ref, viewModel, onClick = { onOpenStory(ref.id) }, onDelete = { viewModel.deleteStory(ref.id) })
                }
            }
        }
    }
}

@Composable
private fun StoryCardTile(ref: StoryCardRef, viewModel: StoriesViewModel, onClick: () -> Unit, onDelete: () -> Unit) {
    val cover = remember(ref.id) { viewModel.coverFile(ref) }
    val bitmap = remember(cover) {
        cover?.let { runCatching { BitmapFactory.decodeFile(it.absolutePath) }.getOrNull() }
    }
    Card(Modifier.fillMaxWidth().clickable { onClick() }) {
        Column {
            if (bitmap != null) {
                Image(bitmap.asImageBitmap(), ref.name, Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(4.dp)), contentScale = ContentScale.Crop)
            } else {
                Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) { Text(ref.name.take(2)) }
            }
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(ref.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${ref.castCount} characters", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete") }
            }
        }
    }
}
