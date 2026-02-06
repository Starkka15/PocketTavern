package com.stark.sillytavern.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stark.sillytavern.domain.model.Character
import com.stark.sillytavern.ui.theme.AccentGreen
import com.stark.sillytavern.ui.theme.TextSecondary
import com.stark.sillytavern.ui.theme.TextTertiary

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CharacterListItem(
    character: Character,
    avatarUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    onLongClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CharacterAvatar(
            imageUrl = avatarUrl,
            characterName = character.name,
            size = 48.dp
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = character.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                // World Info indicators
                if (character.attachedWorldInfo != null || character.hasCharacterBook) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = buildString {
                            if (character.attachedWorldInfo != null) append("World Info: ${character.attachedWorldInfo}")
                            if (character.hasCharacterBook) {
                                if (isNotEmpty()) append(", ")
                                append("Character Book (${character.characterBookEntryCount} entries)")
                            }
                        },
                        modifier = Modifier.size(16.dp),
                        tint = AccentGreen
                    )
                }
            }

            if (character.description.isNotBlank()) {
                Text(
                    text = character.description.take(100),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Show World Info name if attached
            if (character.attachedWorldInfo != null) {
                Text(
                    text = "Lorebook: ${character.attachedWorldInfo}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
