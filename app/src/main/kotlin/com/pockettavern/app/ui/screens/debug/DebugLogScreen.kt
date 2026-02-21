package com.pockettavern.app.ui.screens.debug

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pockettavern.app.ui.theme.*
import com.pockettavern.app.util.DebugLogger

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugLogScreen(onBack: () -> Unit) {
    var logContent by remember { mutableStateOf(DebugLogger.getLogContents()) }
    val clipboard = LocalClipboardManager.current
    val verticalScroll = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Auto-scroll to bottom when content updates
    LaunchedEffect(logContent) {
        verticalScroll.animateScrollTo(verticalScroll.maxValue)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Debug Log") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    // Copy to clipboard
                    IconButton(onClick = {
                        clipboard.setText(AnnotatedString(logContent))
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy log")
                    }
                    // Clear log
                    IconButton(onClick = {
                        DebugLogger.clearLog()
                        logContent = DebugLogger.getLogContents()
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear log", tint = ErrorRed)
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
            // Refresh button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Tap Refresh after generating to see the latest prompt.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                TextButton(onClick = { logContent = DebugLogger.getLogContents() }) {
                    Text("Refresh")
                }
            }

            HorizontalDivider()

            // Log content — selectable, monospace, scrollable both ways
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .verticalScroll(verticalScroll)
                    .horizontalScroll(rememberScrollState())
            ) {
                SelectionContainer {
                    Text(
                        text = logContent.ifBlank { "(log is empty)" },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = TextPrimary,
                        softWrap = false  // horizontal scroll shows long lines unbroken
                    )
                }
            }
        }
    }
}
