package com.pockettavern.app.ui.components

import android.media.MediaPlayer
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pockettavern.app.ui.theme.LocalPocketTavernColors
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun VoiceNoteBubble(
    audioPath: String,
    characterName: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val ptColors = LocalPocketTavernColors.current
    val bubbleColor = ptColors.assistantBubble
    val textColor = ptColors.assistantBubbleText
    val accentColor = ptColors.accentPrimary

    val bubbleShape = RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 16.dp,
        bottomStart = 4.dp,
        bottomEnd = 16.dp
    )

    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var durationMs by remember { mutableIntStateOf(0) }
    var currentMs by remember { mutableIntStateOf(0) }

    val audioFile = remember(audioPath) { File(context.filesDir, audioPath) }

    val mediaPlayer = remember(audioPath) {
        if (audioFile.exists()) {
            try {
                MediaPlayer().apply {
                    setDataSource(audioFile.absolutePath)
                    prepare()
                    durationMs = duration
                }
            } catch (e: Exception) {
                null
            }
        } else null
    }

    // Clean up MediaPlayer on dispose
    DisposableEffect(audioPath) {
        onDispose {
            mediaPlayer?.release()
        }
    }

    // Completion listener
    LaunchedEffect(mediaPlayer) {
        mediaPlayer?.setOnCompletionListener {
            isPlaying = false
            progress = 0f
            currentMs = 0
            mediaPlayer.seekTo(0)
        }
    }

    // Progress polling while playing
    LaunchedEffect(isPlaying) {
        while (isPlaying && mediaPlayer != null) {
            try {
                currentMs = mediaPlayer.currentPosition
                progress = if (durationMs > 0) currentMs.toFloat() / durationMs else 0f
            } catch (_: Exception) {
                break
            }
            delay(100)
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 280.dp),
            shape = bubbleShape,
            color = bubbleColor
        ) {
            Column(modifier = Modifier.padding(12.dp, 8.dp)) {
                Text(
                    text = characterName,
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(
                        onClick = {
                            mediaPlayer?.let { mp ->
                                if (isPlaying) {
                                    mp.pause()
                                    isPlaying = false
                                } else {
                                    mp.start()
                                    isPlaying = true
                                }
                            }
                        },
                        modifier = Modifier.size(36.dp),
                        enabled = mediaPlayer != null
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = accentColor
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp),
                        color = accentColor,
                        trackColor = textColor.copy(alpha = 0.2f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatDuration(if (isPlaying) currentMs else durationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

private fun formatDuration(ms: Int): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
