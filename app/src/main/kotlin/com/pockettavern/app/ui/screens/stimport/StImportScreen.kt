package com.pockettavern.app.ui.screens.stimport

import com.pockettavern.app.R
import androidx.compose.ui.res.stringResource
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
                title = { Text(stringResource(R.string.import_from_sillytavern)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
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
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = title,
                                color = if (selectedTab == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
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
        Text(text = stringResource(R.string.select_your_sillytavern_data_folder_the_app_w) +
                    "lorebooks (world info), and chat histories.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Folder structure hint
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                .padding(12.dp)
        ) {
            Text(
                text = stringResource(R.string.expected_folder_structure),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }

        if (!uiState.isImporting && !uiState.isComplete) {
            Button(
                onClick = onPickFolder,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.choose_sillytavern_folder), color = Color.Black)
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
        Text(text = stringResource(R.string.connect_to_a_running_sillytavern_server_to_pu) +
                    "This is a one-time migration — no server needed afterward.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = uiState.serverUrl,
            onValueChange = onUrlChange,
            label = { Text(stringResource(R.string.server_url)) },
            placeholder = { Text(stringResource(R.string.http_192_168_1_x_8000)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            enabled = !uiState.isImporting,
            colors = outlinedTextFieldColors()
        )

        OutlinedTextField(
            value = uiState.username,
            onValueChange = onUsernameChange,
            label = { Text(stringResource(R.string.handle_optional)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !uiState.isImporting,
            colors = outlinedTextFieldColors()
        )

        OutlinedTextField(
            value = uiState.password,
            onValueChange = onPasswordChange,
            label = { Text(stringResource(R.string.password_optional)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !uiState.isImporting,
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (showPassword) "Hide password" else "Show password",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
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
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Download, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.import_from_server), color = Color.Black)
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${progress.current}/${progress.total}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { progress.current.toFloat() / progress.total },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            } else {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.import_complete), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)

            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                StatItem(label = stringResource(R.string.characters), value = uiState.charactersImported, color = MaterialTheme.colorScheme.primary)
                StatItem(label = stringResource(R.string.lorebooks), value = uiState.lorebooksImported, color = MaterialTheme.colorScheme.primary)
                StatItem(label = stringResource(R.string.chats), value = uiState.chatsImported, color = MaterialTheme.colorScheme.primary)
                if (uiState.errors > 0) {
                    StatItem(label = stringResource(R.string.errors), value = uiState.errors, color = MaterialTheme.colorScheme.error)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            OutlinedButton(
                onClick = onReset,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
            ) {
                Text(stringResource(R.string.import_again))
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
            color = MaterialTheme.colorScheme.onSurfaceVariant
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

    Text(text = stringResource(R.string.log),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .background(MaterialTheme.colorScheme.background, RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        LazyColumn(state = listState) {
            items(log) { line ->
                val color = when {
                    line.startsWith("ERROR") -> MaterialTheme.colorScheme.error
                    line.startsWith("Done.") -> MaterialTheme.colorScheme.primary
                    line.startsWith("Imported") -> MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
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
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    cursorColor = MaterialTheme.colorScheme.primary
)
