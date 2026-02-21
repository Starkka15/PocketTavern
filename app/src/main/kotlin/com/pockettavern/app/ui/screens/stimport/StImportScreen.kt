package com.pockettavern.app.ui.screens.stimport

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pockettavern.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StImportScreen(
    onBack: () -> Unit,
    viewModel: StImportViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // SAF folder picker
    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.resetState()
            viewModel.importFromFolder(uri)
        }
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("From Folder", "From Server")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import from SillyTavern") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Tab row
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = DarkSurface,
                contentColor = AccentGreen
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = title,
                                color = if (selectedTab == index) AccentGreen else TextSecondary
                            )
                        }
                    )
                }
            }

            when (selectedTab) {
                0 -> FolderImportTab(
                    uiState = uiState,
                    onPickFolder = { folderLauncher.launch(null) },
                    onReset = { viewModel.resetState() }
                )
                1 -> ServerImportTab(
                    uiState = uiState,
                    onUrlChange = viewModel::updateServerUrl,
                    onUsernameChange = viewModel::updateUsername,
                    onPasswordChange = viewModel::updatePassword,
                    onImport = { viewModel.importFromServer() },
                    onReset = { viewModel.resetState() }
                )
            }
        }
    }
}

@Composable
private fun FolderImportTab(
    uiState: StImportUiState,
    onPickFolder: () -> Unit,
    onReset: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Description
        Text(
            text = "Select your SillyTavern data folder. The app will import characters (PNG cards), " +
                    "lorebooks (world info), and chat histories.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )

        // Folder structure hint
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, DarkSurfaceVariant, RoundedCornerShape(8.dp))
                .padding(12.dp)
        ) {
            Text(
                text = "Expected folder structure:\n" +
                        "  data/\n" +
                        "  ├── characters/*.png\n" +
                        "  ├── worlds/*.json\n" +
                        "  └── chats/{character}/*.jsonl",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = TextSecondary,
                fontSize = 12.sp
            )
        }

        if (!uiState.isImporting && !uiState.isComplete) {
            Button(
                onClick = onPickFolder,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Choose SillyTavern Folder", color = Color.Black)
            }
        }

        // Progress + log
        ImportProgressSection(uiState = uiState, onReset = onReset)
    }
}

@Composable
private fun ServerImportTab(
    uiState: StImportUiState,
    onUrlChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onImport: () -> Unit,
    onReset: () -> Unit
) {
    var showPassword by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Connect to a running SillyTavern server to pull all characters and lorebooks. " +
                    "This is a one-time migration — no server needed afterward.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )

        OutlinedTextField(
            value = uiState.serverUrl,
            onValueChange = onUrlChange,
            label = { Text("Server URL") },
            placeholder = { Text("http://192.168.1.x:8000") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            enabled = !uiState.isImporting,
            colors = outlinedTextFieldColors()
        )

        OutlinedTextField(
            value = uiState.username,
            onValueChange = onUsernameChange,
            label = { Text("Handle (optional)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !uiState.isImporting,
            colors = outlinedTextFieldColors()
        )

        OutlinedTextField(
            value = uiState.password,
            onValueChange = onPasswordChange,
            label = { Text("Password (optional)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !uiState.isImporting,
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (showPassword) "Hide password" else "Show password",
                        tint = TextSecondary
                    )
                }
            },
            colors = outlinedTextFieldColors()
        )

        if (!uiState.isImporting && !uiState.isComplete) {
            Button(
                onClick = onImport,
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState.serverUrl.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
            ) {
                Icon(Icons.Default.Download, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Import from Server", color = Color.Black)
            }
        }

        // Progress + log
        ImportProgressSection(uiState = uiState, onReset = onReset)
    }
}

@Composable
private fun ImportProgressSection(
    uiState: StImportUiState,
    onReset: () -> Unit
) {
    // Active import progress
    if (uiState.isImporting) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val progress = uiState.progress
            if (progress.total > 0) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = progress.currentItem.take(40),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Text(
                            text = "${progress.current}/${progress.total}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { progress.current.toFloat() / progress.total },
                        modifier = Modifier.fillMaxWidth(),
                        color = AccentGreen,
                        trackColor = DarkSurfaceVariant
                    )
                }
            } else {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = AccentGreen,
                    trackColor = DarkSurfaceVariant
                )
            }
        }
    }

    // Results summary
    if (uiState.isComplete) {
        ResultsSummaryCard(uiState = uiState, onReset = onReset)
    }

    // Log output
    if (uiState.log.isNotEmpty()) {
        LogSection(log = uiState.log)
    }
}

@Composable
private fun ResultsSummaryCard(
    uiState: StImportUiState,
    onReset: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Import Complete", style = MaterialTheme.typography.titleMedium, color = AccentGreen, fontWeight = FontWeight.Bold)

            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                StatItem(label = "Characters", value = uiState.charactersImported, color = AccentGreen)
                StatItem(label = "Lorebooks", value = uiState.lorebooksImported, color = AccentBlue)
                StatItem(label = "Chats", value = uiState.chatsImported, color = AccentPurple)
                if (uiState.errors > 0) {
                    StatItem(label = "Errors", value = uiState.errors, color = ErrorRed)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            OutlinedButton(
                onClick = onReset,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
            ) {
                Text("Import Again")
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
    }
}

@Composable
private fun LogSection(log: List<String>) {
    val listState = rememberLazyListState()

    // Auto-scroll to bottom as new lines arrive
    LaunchedEffect(log.size) {
        if (log.isNotEmpty()) {
            listState.animateScrollToItem(log.size - 1)
        }
    }

    Text(
        text = "Log",
        style = MaterialTheme.typography.labelMedium,
        color = TextSecondary
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .background(DarkBackground, RoundedCornerShape(8.dp))
            .border(1.dp, DarkSurfaceVariant, RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        LazyColumn(state = listState) {
            items(log) { line ->
                val color = when {
                    line.startsWith("ERROR") -> ErrorRed
                    line.startsWith("Done.") -> AccentGreen
                    line.startsWith("Imported") -> AccentGreen.copy(alpha = 0.85f)
                    else -> TextSecondary
                }
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = color,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(vertical = 1.dp)
                )
            }
        }
    }
}

@Composable
private fun outlinedTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AccentGreen,
    unfocusedBorderColor = DarkSurfaceVariant,
    focusedLabelColor = AccentGreen,
    unfocusedLabelColor = TextSecondary,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    cursorColor = AccentGreen
)
