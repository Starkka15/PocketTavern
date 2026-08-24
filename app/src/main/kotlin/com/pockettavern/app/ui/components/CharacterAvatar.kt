package com.pockettavern.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.pockettavern.app.ui.theme.LocalPocketTavernColors

@Composable
fun CharacterAvatar(
    imageUrl: String?,
    characterName: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    onClick: (() -> Unit)? = null
) {
    val ptColors = LocalPocketTavernColors.current
    val avatarShape = ptColors.avatarShape.toShape()
    val clickModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier

    if (imageUrl != null) {
        val context = LocalContext.current
        val density = LocalDensity.current
        val px = with(density) { (size * 2).toPx().toInt() }
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(imageUrl)
                .size(px, px)
                .crossfade(true)
                .build(),
            contentDescription = characterName,
            modifier = modifier
                .size(size)
                .clip(avatarShape)
                .then(clickModifier),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(avatarShape)
                .background(MaterialTheme.colorScheme.primary)
                .then(clickModifier),
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
