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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
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
        val context = LocalContext.current
        val density = LocalDensity.current
        // Tell Coil exactly what pixel size to decode at — enables BitmapFactory subsampling
        // so we never decode a full 800×1200 card PNG just to show a 48dp thumbnail.
        val px = with(density) { (size * 2).toPx().toInt() } // 2× for display density headroom
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(imageUrl)
                .size(px, px)
                .crossfade(true)
                .build(),
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
