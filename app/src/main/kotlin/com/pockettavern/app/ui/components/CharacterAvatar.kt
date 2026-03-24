package com.pockettavern.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.pockettavern.app.ui.theme.LocalPocketTavernColors

@Composable
fun CharacterAvatar(
    imageUrl: String?,
    characterName: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp
) {
    val avatarShape = LocalPocketTavernColors.current.avatarShape.toShape()

    if (imageUrl != null) {
        AsyncImage(
            model = imageUrl,
            contentDescription = characterName,
            modifier = modifier
                .size(size)
                .clip(avatarShape),
            contentScale = ContentScale.Crop
        )
    } else {
        // Fallback to initial letter
        Box(
            modifier = modifier
                .size(size)
                .clip(avatarShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = characterName.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.background
            )
        }
    }
}
