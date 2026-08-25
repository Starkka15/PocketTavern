package com.pockettavern.app.ui.screens.stories

import com.pockettavern.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pockettavern.app.domain.model.StoryMessage
import com.pockettavern.app.domain.model.StoryOpening
import com.pockettavern.app.ui.components.formatMessage

/**
 * T5 — renders a Story session. The player's beats render as their own bubbles; an assistant turn
 * is ONE multi-speaker scene that we split on inline "Name:" tags and render per-speaker (V7). The
 * `<think>` director reasoning shows in a collapsed panel (V6), never inline with the scene.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryChatScreen(
    storyId: String,
    chatFileName: String?,
    onBack: () -> Unit,
    viewModel: StoryChatViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(storyId, chatFileName) { viewModel.open(storyId, chatFileName) }

    val castNames = remember(state.story) { state.story?.cast?.map { it.name } ?: emptyList() }
    var input by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.story?.name ?: "Story", maxLines = 1) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
                actions = {
                    if (state.missions.isNotEmpty()) {
                        TextButton(onClick = { viewModel.toggleMissionPicker(true) }) { Text(stringResource(R.string.mission)) }
                    }
                    IconButton(onClick = { viewModel.newPlaythrough() }) { Icon(Icons.Default.Add, "New playthrough") }
                    IconButton(onClick = { viewModel.toggleChatPicker(true) }) { Icon(Icons.Default.List, "Playthroughs") }
                }
            )
        },
        bottomBar = {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.Bottom) {
                OutlinedTextField(
                    value = input, onValueChange = { input = it },
                    modifier = Modifier.weight(1f), placeholder = { Text(stringResource(R.string.your_move)) }, maxLines = 5
                )
                Spacer(Modifier.width(8.dp))
                if (state.isGenerating) {
                    FilledIconButton(onClick = { viewModel.stopGeneration() }) { Text("■") }
                } else {
                    FilledIconButton(onClick = { viewModel.sendMessage(input); input = "" }, enabled = input.isNotBlank()) {
                        Icon(Icons.Default.Send, "Send")
                    }
                }
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            // V5: fresh scene → let the player pick an opening seed (or just start typing).
            if (state.messages.isEmpty() && state.openings.size > 1) {
                OpeningPicker(state.openings, state.selectedOpening) { viewModel.selectOpening(it) }
            }
            // Active mission banner + roster chips (V4: leads highlighted, others may still appear).
            state.activeMission?.let { m ->
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                    Column(Modifier.padding(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("⊕ ${m.name}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            TextButton(onClick = { viewModel.clearMission() }) { Text(stringResource(R.string.end)) }
                        }
                        Text(m.briefing, style = MaterialTheme.typography.bodySmall, maxLines = 3)
                        Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp)) {
                            (state.story?.cast ?: emptyList()).forEach { c ->
                                FilterChip(
                                    selected = c.name in state.roster,
                                    onClick = { viewModel.toggleRoster(c.name) },
                                    label = { Text(c.name.substringBefore(' ')) },
                                    modifier = Modifier.padding(end = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
            LazyColumn(Modifier.weight(1f).padding(horizontal = 12.dp), reverseLayout = false) {
                items(state.messages, key = { it.id }) { msg -> MessageItem(msg, castNames) }
                if (state.isGenerating && (state.streamingContent.isNotEmpty() || state.streamingThinking.isNotEmpty())) {
                    item {
                        if (state.streamingThinking.isNotEmpty()) ReasoningPanel(state.streamingThinking, live = true)
                        SceneBlocks(parseScene(state.streamingContent, castNames))
                    }
                }
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp)) }
        }
    }

    if (state.showChatPicker) {
        PlaythroughPicker(
            chats = state.chats,
            current = state.chatFileName,
            onSelect = { viewModel.switchChat(it) },
            onNew = { viewModel.newPlaythrough() },
            onDelete = { viewModel.deleteChat(it) },
            onDismiss = { viewModel.toggleChatPicker(false) }
        )
    }

    if (state.showMissionPicker) {
        MissionPicker(
            missions = state.missions,
            onLaunch = { viewModel.launchMission(it) },
            onDismiss = { viewModel.toggleMissionPicker(false) }
        )
    }
}

/** Pick a card-authored mission to frame the current scene. */
@Composable
private fun MissionPicker(
    missions: List<com.pockettavern.app.domain.model.StoryMission>,
    onLaunch: (com.pockettavern.app.domain.model.StoryMission) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.missions)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                missions.forEach { m ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onLaunch(m) }
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Text(m.name, style = MaterialTheme.typography.titleSmall)
                            Text(m.briefing, style = MaterialTheme.typography.bodySmall, maxLines = 3)
                            val leads = if (m.cast.isEmpty()) stringResource(R.string.leads_whole_household) else m.cast.joinToString(", ") { it.substringBefore(' ') }
                            Text(stringResource(R.string.leads_count, leads), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } }
    )
}

/** Switch between / start / delete playthroughs of one Story card. */
@Composable
private fun PlaythroughPicker(
    chats: List<com.pockettavern.app.domain.model.ChatInfo>,
    current: String?,
    onSelect: (String) -> Unit,
    onNew: () -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.playthroughs)) },
        text = {
            Column {
                if (chats.isEmpty()) Text(stringResource(R.string.no_playthroughs_yet))
                chats.forEach { c ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).clickable { onSelect(c.fileName) }) {
                            Text(
                                if (c.fileName == current) "● ${c.fileName}" else c.fileName,
                                style = MaterialTheme.typography.bodyMedium, maxLines = 1
                            )
                            Text(
                                (c.lastMessage?.take(50) ?: "empty") + "  ·  ${c.messageCount} msgs",
                                style = MaterialTheme.typography.bodySmall, color = androidx.compose.ui.graphics.Color.Gray, maxLines = 1
                            )
                        }
                        IconButton(onClick = { onDelete(c.fileName) }) { Icon(Icons.Default.Delete, "Delete") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onNew) { Text(stringResource(R.string.new_playthrough)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } }
    )
}

@Composable
private fun OpeningPicker(openings: List<StoryOpening>, selected: StoryOpening?, onPick: (StoryOpening) -> Unit) {
    Column(Modifier.padding(12.dp)) {
        Text(stringResource(R.string.opening), style = MaterialTheme.typography.labelLarge)
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            openings.forEach { o ->
                FilterChip(
                    selected = o == selected, onClick = { onPick(o) },
                    label = { Text(o.label.ifBlank { "Seed" }) }, modifier = Modifier.padding(end = 6.dp)
                )
            }
        }
        selected?.let { Text(it.seed, style = MaterialTheme.typography.bodySmall, color = Color.Gray) }
    }
}

@Composable
private fun MessageItem(msg: StoryMessage, castNames: List<String>) {
    if (msg.isUser) {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.End) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(12.dp)) {
                SelectionContainer { Text(formatMessage(msg.content), Modifier.padding(10.dp)) }
            }
        }
    } else {
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            msg.reasoning?.let { ReasoningPanel(it, live = false) }
            SceneBlocks(parseScene(msg.content, castNames))
        }
    }
}

/** Collapsible R1 reasoning — the invisible director, available but folded (V6). */
@Composable
private fun ReasoningPanel(text: String, live: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Column(Modifier.padding(8.dp)) {
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (live) "thinking…" else if (expanded) "hide reasoning" else "reasoning")
            }
            if (expanded || live) Text(text, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    }
}

@Composable
private fun SceneBlocks(blocks: List<SceneBlock>) {
    SelectionContainer {
        Column {
            blocks.forEach { b ->
                if (b.speaker != null) {
                    Row(Modifier.padding(vertical = 2.dp)) {
                        Text("${b.speaker}: ", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text(formatMessage(b.text))   // markdown: *actions*, **bold**, "quotes"
                    }
                } else {
                    Text(formatMessage(b.text), modifier = Modifier.padding(vertical = 2.dp)) // narration
                }
            }
        }
    }
}

data class SceneBlock(val speaker: String?, val text: String)

/**
 * Split an assistant scene into per-speaker blocks. A line starting "Name:" (Name ∈ cast) starts a
 * new speaker block; untagged lines are narration. Lenient — unknown tags fall through to narration.
 */
fun parseScene(content: String, castNames: List<String>): List<SceneBlock> {
    if (content.isBlank()) return emptyList()
    val nameSet = castNames.map { it.lowercase() }.toSet()
    val blocks = mutableListOf<SceneBlock>()
    var curSpeaker: String? = null
    val buf = StringBuilder()
    fun flush() { if (buf.isNotBlank()) blocks.add(SceneBlock(curSpeaker, buf.toString().trim())); buf.clear() }
    content.lines().forEach { line ->
        val m = Regex("^\\s*([A-Z][\\w '\\-]{0,40}):\\s*(.*)$").find(line)
        val tag = m?.groupValues?.getOrNull(1)?.trim()
        if (tag != null && tag.lowercase() in nameSet) {
            flush(); curSpeaker = tag; buf.append(m.groupValues[2])
        } else {
            if (buf.isNotEmpty()) buf.append('\n'); buf.append(line)
        }
    }
    flush()
    return blocks
}
