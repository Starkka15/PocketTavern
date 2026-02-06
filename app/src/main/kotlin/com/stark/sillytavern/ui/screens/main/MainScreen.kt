package com.stark.sillytavern.ui.screens.main

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
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import com.stark.sillytavern.R
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stark.sillytavern.BuildConfig
import com.stark.sillytavern.ui.components.ConnectionStatusBar
import com.stark.sillytavern.ui.components.FireIceBackground
import com.stark.sillytavern.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onNavigateToCharacters: () -> Unit,
    onNavigateToRecentChats: () -> Unit,
    onNavigateToCreateCharacter: () -> Unit,
    onNavigateToChub: () -> Unit,
    onNavigateToCardVault: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToProfile: () -> Unit,
    viewModel: MainViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            // Taller top bar
            Surface(
                color = Color.Black,
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
                    // Logo takes most of the space
                    Image(
                        painter = painterResource(id = R.drawable.logo_pockettavern),
                        contentDescription = "PocketTavern",
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        contentScale = ContentScale.Fit
                    )

                    // Larger profile icon with glow
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        IceBlue.copy(alpha = 0.3f),
                                        Color.Transparent
                                    )
                                )
                            )
                            .clickable(onClick = onNavigateToProfile),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = "Profile",
                            tint = IceBlue,
                            modifier = Modifier.size(36.dp)
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
            // Animated fire & ice background
            FireIceBackground(
                modifier = Modifier.fillMaxSize()
            )

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
                    iconColor = FireOrange,
                    onClick = onNavigateToCharacters
                )

                Spacer(modifier = Modifier.height(20.dp))

                NavigationCard(
                    icon = Icons.Default.History,
                    title = "Recent Chats",
                    description = "Continue your recent conversations",
                    iconColor = IceBlue,
                    onClick = onNavigateToRecentChats
                )

                Spacer(modifier = Modifier.height(20.dp))

                NavigationCard(
                    icon = Icons.Default.Add,
                    title = "Create Character",
                    description = "Design a new character from scratch",
                    iconColor = FireGold,
                    onClick = onNavigateToCreateCharacter
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Chub.ai - only show in full build and if enabled in settings
                if (BuildConfig.CHUB_ENABLED && uiState.settings.chubEnabled) {
                    NavigationCard(
                        icon = Icons.Default.Public,
                        title = "Chub.ai",
                        description = "Browse and import characters",
                        iconColor = IceCyan,
                        onClick = onNavigateToChub
                    )

                    Spacer(modifier = Modifier.height(20.dp))
                }

                // CardVault - only show if URL is configured
                if (uiState.settings.isCardVaultEnabled) {
                    NavigationCard(
                        icon = Icons.Default.Storage,
                        title = "CardVault",
                        description = "Your local character card library",
                        iconColor = Color(0xFF9C27B0),  // Purple
                        onClick = onNavigateToCardVault
                    )

                    Spacer(modifier = Modifier.height(20.dp))
                }

                NavigationCard(
                    icon = Icons.Default.Settings,
                    title = "Settings",
                    description = "Configure server and app preferences",
                    iconColor = FireRed,
                    onClick = onNavigateToSettings
                )
            }
        }
    }
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
        color = Color.Black.copy(alpha = 0.35f),
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
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    maxLines = 1
                )
            }
        }
    }
}
