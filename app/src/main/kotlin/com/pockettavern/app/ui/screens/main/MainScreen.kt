package com.pockettavern.app.ui.screens.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Update
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.pockettavern.app.R
import java.io.File
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pockettavern.app.ui.audio.ThemeAudioManager
import com.pockettavern.app.ui.components.ConnectionStatusBar
import com.pockettavern.app.ui.components.ParticleBackground
import com.pockettavern.app.ui.theme.BackgroundScaleMode
import com.pockettavern.app.ui.theme.LocalThemeAssets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onNavigateToCharacters: () -> Unit,
    onNavigateToRecentChats: () -> Unit,
    onNavigateToCreateCharacter: () -> Unit,
    onNavigateToCharaVault: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToExtensionPanel: (String) -> Unit = {},
    themeAudioManager: ThemeAudioManager? = null,
    viewModel: MainViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val assets = LocalThemeAssets.current

    // Theme audio: start/stop based on audioPath
    LaunchedEffect(assets.audioPath) {
        if (assets.audioPath != null && themeAudioManager != null) {
            themeAudioManager.play(assets.audioPath, assets.audioLoop)
        } else {
            themeAudioManager?.stop()
        }
    }

    // Pause/resume on lifecycle events
    if (themeAudioManager != null && assets.audioPath != null) {
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_PAUSE -> themeAudioManager.pause()
                    Lifecycle.Event.ON_RESUME -> themeAudioManager.resume()
                    else -> {}
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                themeAudioManager.pause()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    // Theme background image (behind particles)
    assets.backgroundImagePath?.let { path ->
        val scaleMode = when (assets.backgroundScaleMode) {
            BackgroundScaleMode.FILL -> ContentScale.Crop
            BackgroundScaleMode.FIT -> ContentScale.Fit
            BackgroundScaleMode.STRETCH -> ContentScale.FillBounds
        }
        AsyncImage(
            model = File(path),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .alpha(assets.backgroundOpacity),
            contentScale = scaleMode
        )
    }

    ParticleBackground()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            // Taller top bar
            Surface(
                color = Color.Transparent,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Logo — custom from theme or default (optionally tinted)
                    if (assets.logoImagePath != null) {
                        AsyncImage(
                            model = File(assets.logoImagePath),
                            contentDescription = "PocketTavern",
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Image(
                            painter = painterResource(id = R.drawable.logo_pockettavern),
                            contentDescription = "PocketTavern",
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            contentScale = ContentScale.Fit,
                            colorFilter = assets.logoTint?.let { ColorFilter.tint(it) }
                        )
                    }

                }
            }
        },
        bottomBar = {
            ConnectionStatusBar(
                isConnected = uiState.isConnected,
                statusText = uiState.statusText
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Centered content - bigger cards with more spacing
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                NavigationCard(
                    icon = Icons.Default.People,
                    title = "Characters",
                    description = "Browse and chat with your characters",
                    iconColor = MaterialTheme.colorScheme.primary,
                    onClick = onNavigateToCharacters
                )

                Spacer(modifier = Modifier.height(20.dp))

                NavigationCard(
                    icon = Icons.Default.History,
                    title = "Recent Chats",
                    description = "Continue your recent conversations",
                    iconColor = MaterialTheme.colorScheme.primary,
                    onClick = onNavigateToRecentChats
                )

                Spacer(modifier = Modifier.height(20.dp))

                NavigationCard(
                    icon = Icons.Default.Add,
                    title = "Create Character",
                    description = "Design a new character from scratch",
                    iconColor = MaterialTheme.colorScheme.primary,
                    onClick = onNavigateToCreateCharacter
                )

                Spacer(modifier = Modifier.height(20.dp))

                // CharaVault - always available in standalone mode
                if (true) {
                    NavigationCard(
                        icon = Icons.Default.Storage,
                        title = "Card Search",
                        description = "Browse characters from CharaVault, Chub.ai, and more",
                        iconColor = MaterialTheme.colorScheme.primary,
                        onClick = onNavigateToCharaVault
                    )

                    Spacer(modifier = Modifier.height(20.dp))
                }

                NavigationCard(
                    icon = Icons.Default.Settings,
                    title = "Settings",
                    description = "Configure server and app preferences",
                    iconColor = MaterialTheme.colorScheme.primary,
                    onClick = onNavigateToSettings
                )

                // Extension panels registered via PT.registerPanel() or browser.html detection
                uiState.panelRegistrations.values.forEach { panel ->
                    Spacer(modifier = Modifier.height(20.dp))
                    NavigationCard(
                        icon = Icons.Default.Extension,
                        title = panel.title,
                        description = "Extension panel",
                        iconColor = MaterialTheme.colorScheme.tertiary,
                        onClick = { onNavigateToExtensionPanel(panel.extensionId) }
                    )
                }
            }
        }
    }

    // Update available dialog
    val updateInfo = uiState.updateAvailable
    if (uiState.showUpdateDialog && updateInfo != null) {
        val context = LocalContext.current

        AlertDialog(
            onDismissRequest = { viewModel.dismissUpdateDialog() },
            icon = {
                Icon(
                    Icons.Default.Update,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text("Update Available")
            },
            text = {
                Column {
                    Text(
                        "A new version of PocketTavern is available!",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Current: v${updateInfo.currentVersion}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Latest: v${updateInfo.latestVersion}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(updateInfo.releaseUrl))
                        context.startActivity(intent)
                        viewModel.dismissUpdateDialog()
                    }
                ) {
                    Text("Download", color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissUpdateDialog() }) {
                    Text("Later", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = Color.Black.copy(alpha = 0.95f),
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurface
        )
    }
    } // Box
}

@Composable
private fun NavigationCard(
    icon: ImageVector,
    title: String,
    description: String,
    iconColor: Color,
    onClick: () -> Unit
) {
    // Transparent card with subtle border - horizontal layout
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = 1.5.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        iconColor.copy(alpha = 0.6f),
                        iconColor.copy(alpha = 0.2f)
                    )
                ),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon with glow effect - bigger
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                iconColor.copy(alpha = 0.5f),
                                iconColor.copy(alpha = 0.15f),
                                Color.Transparent
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(34.dp)
                )
            }

            Spacer(modifier = Modifier.width(18.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}
