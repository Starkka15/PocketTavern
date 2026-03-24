package com.pockettavern.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.animation.AnimatedVisibility
import com.pockettavern.app.domain.model.ChatMessage
import com.pockettavern.app.domain.model.MessageHeaderEntry
import com.pockettavern.app.extensions.JsExtensionHost
import com.pockettavern.app.ui.theme.*

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ChatBubble(
    message: ChatMessage,
    characterName: String,
    modifier: Modifier = Modifier,
    headers: List<MessageHeaderEntry> = emptyList(),
    headerButtons: Map<String, List<JsExtensionHost.HeaderAction>> = emptyMap(),
    visibleButtonExtensions: Set<String> = emptySet(),
    headerMenus: Map<String, List<JsExtensionHost.HeaderAction>> = emptyMap(),
    onHeaderLongPress: ((String) -> Unit)? = null,
    onHeaderActionClick: ((String, String) -> Unit)? = null,
    onBubbleLongPress: (() -> Unit)? = null,
    onImageAction: (() -> Unit)? = null
) {
    // Voice note messages: render as audio player bubble
    if (message.audioPath != null) {
        VoiceNoteBubble(
            audioPath = message.audioPath,
            characterName = characterName,
            modifier = modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
        return
    }

    // Narrator/system messages: images align left (like character sent it), text stays centered
    if (message.isNarrator) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalAlignment = if (message.imagePath != null) Alignment.Start else Alignment.CenterHorizontally
        ) {
            // Image message: render as a small thumbnail, tap to view fullscreen
            if (message.imagePath != null) {
                val context = LocalContext.current
                val imageFile = remember(message.imagePath) {
                    java.io.File(context.filesDir, message.imagePath)
                }
                if (imageFile.exists()) {
                    val bitmap = remember(imageFile.absolutePath) {
                        android.graphics.BitmapFactory.decodeFile(imageFile.absolutePath)
                    }
                    if (bitmap != null) {
                        var showFullscreen by remember { mutableStateOf(false) }

                        // Thumbnail in chat — sized like a received photo
                        Box(
                            modifier = Modifier
                                .widthIn(max = 200.dp)
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { showFullscreen = true }
                        ) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = message.content.ifBlank { "Generated image" },
                                modifier = Modifier.fillMaxWidth(),
                                contentScale = ContentScale.FillWidth
                            )
                            // Action button overlay
                            if (onImageAction != null) {
                                IconButton(
                                    onClick = onImageAction,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(4.dp)
                                        .size(28.dp)
                                        .background(
                                            MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                            CircleShape
                                        )
                                ) {
                                    Icon(
                                        Icons.Default.MoreVert,
                                        contentDescription = "Image actions",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        // Fullscreen overlay — tap anywhere to dismiss
                        if (showFullscreen) {
                            Dialog(
                                onDismissRequest = { showFullscreen = false },
                                properties = DialogProperties(
                                    usePlatformDefaultWidth = false,
                                    decorFitsSystemWindows = false
                                )
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black)
                                        .clickable { showFullscreen = false },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = message.content.ifBlank { "Generated image" },
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Fit
                                    )
                                }
                            }
                        }
                    }
                }
                // Show text caption below the image if present
                if (message.content.isNotBlank()) {
                    Text(
                        text = formatMessage(message.content),
                        style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                    )
                }
            } else {
                // Standard narrator text
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.surfaceVariant)
                    Text(
                        text = formatMessage(message.content),
                        style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.surfaceVariant)
                }
            }
        }
        return
    }

    val ptColors = LocalPocketTavernColors.current
    val bubbleColor = if (message.isUser) ptColors.userBubble else ptColors.assistantBubble
    val textColor = if (message.isUser) ptColors.userBubbleText else ptColors.assistantBubbleText
    val senderColor = if (message.isUser) ptColors.userBubbleText else ptColors.accentPrimary
    val senderName = if (message.isUser) "You" else characterName

    val alignment = if (message.isUser) Alignment.End else Alignment.Start
    val bubbleShape = RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 16.dp,
        bottomStart = if (message.isUser) 16.dp else 4.dp,
        bottomEnd = if (message.isUser) 4.dp else 16.dp
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        // Header boxes set by JS extensions via PT.setMessageHeader()
        if (!message.isUser && headers.isNotEmpty()) {
            headers.forEach { entry ->
                val extId = entry.extensionId
                val inlineButtons = headerButtons[extId]
                val buttonsVisible = extId in visibleButtonExtensions
                val menuItems = headerMenus[extId]
                var menuExpanded by remember { mutableStateOf(false) }
                var collapsibleExpanded by remember { mutableStateOf(false) }
                val hasCollapsible = entry.collapsibleText.isNotBlank()

                Box {
                    Surface(
                        modifier = Modifier
                            .widthIn(max = 320.dp)
                            .padding(bottom = 4.dp)
                            .then(
                                if (onHeaderLongPress != null) {
                                    Modifier.combinedClickable(
                                        onClick = {
                                            if (hasCollapsible) collapsibleExpanded = !collapsibleExpanded
                                        },
                                        onLongClick = {
                                            // If menu registered (and no inline buttons), show popup
                                            if (inlineButtons.isNullOrEmpty() && !menuItems.isNullOrEmpty()) {
                                                menuExpanded = true
                                            }
                                            onHeaderLongPress(extId)
                                        }
                                    )
                                } else if (hasCollapsible) {
                                    Modifier.combinedClickable(
                                        onClick = { collapsibleExpanded = !collapsibleExpanded },
                                        onLongClick = { }
                                    )
                                } else Modifier
                            ),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                            Text(
                                text = entry.text,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            // Collapsible section (tap header to toggle)
                            // First line of collapsibleText = chevron label, rest = expandable body
                            if (hasCollapsible) {
                                val newlineIdx = entry.collapsibleText.indexOf('\n')
                                val chevronLabel = if (newlineIdx > 0) entry.collapsibleText.substring(0, newlineIdx).trim() else entry.collapsibleText.trim()
                                val expandableBody = if (newlineIdx > 0) entry.collapsibleText.substring(newlineIdx + 1) else ""
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (collapsibleExpanded) "▾" else "▸",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = chevronLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                if (expandableBody.isNotBlank()) {
                                    AnimatedVisibility(visible = collapsibleExpanded) {
                                        Column {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = expandableBody,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                            // Inline buttons (toggled by long-press)
                            if (buttonsVisible && !inlineButtons.isNullOrEmpty()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    inlineButtons.forEach { btn ->
                                        AssistChip(
                                            onClick = { onHeaderActionClick?.invoke(btn.action, btn.label) },
                                            label = {
                                                Text(
                                                    text = btn.label,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    maxLines = 1
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    // Context menu (shown on long-press if no inline buttons)
                    if (!menuItems.isNullOrEmpty()) {
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            menuItems.forEach { item ->
                                DropdownMenuItem(
                                    text = { Text(item.label) },
                                    onClick = {
                                        menuExpanded = false
                                        onHeaderActionClick?.invoke(item.action, item.label)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
        Surface(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .then(
                    if (onBubbleLongPress != null) {
                        Modifier.combinedClickable(
                            onClick = { },
                            onLongClick = onBubbleLongPress
                        )
                    } else Modifier
                ),
            shape = bubbleShape,
            color = bubbleColor
        ) {
            Column(modifier = Modifier.padding(12.dp, 8.dp)) {
                Text(
                    text = senderName,
                    style = MaterialTheme.typography.labelSmall,
                    color = senderColor,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatMessage(message.content),
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor
                )
            }
        }
    }
}

@Composable
fun StreamingChatBubble(
    content: String,
    characterName: String,
    modifier: Modifier = Modifier
) {
    val bubbleShape = RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 16.dp,
        bottomStart = 4.dp,
        bottomEnd = 16.dp
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        val ptColors = LocalPocketTavernColors.current
        Surface(
            modifier = Modifier.widthIn(max = 320.dp),
            shape = bubbleShape,
            color = ptColors.assistantBubble
        ) {
            Column(modifier = Modifier.padding(12.dp, 8.dp)) {
                Text(
                    text = characterName,
                    style = MaterialTheme.typography.labelSmall,
                    color = ptColors.accentPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatMessage(content + "▌"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = ptColors.assistantBubbleText
                )
            }
        }
    }
}

// Represents a parsed markdown segment
private data class MarkdownSegment(
    val text: String,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isCode: Boolean = false,
    val isQuote: Boolean = false
)

@Composable
private fun formatMessage(text: String): AnnotatedString {
    val segments = parseMarkdown(text)
    val ptColors = LocalPocketTavernColors.current

    return buildAnnotatedString {
        segments.forEach { segment ->
            val color = when {
                segment.isQuote -> ptColors.quoteTextColor
                segment.isItalic && ptColors.italicTextColor != Color.Unspecified -> ptColors.italicTextColor
                else -> Color.Unspecified
            }
            val style = SpanStyle(
                fontWeight = if (segment.isBold) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (segment.isItalic) FontStyle.Italic else FontStyle.Normal,
                fontFamily = if (segment.isCode) FontFamily.Monospace else null,
                background = if (segment.isCode) ptColors.codeBackgroundColor else Color.Unspecified,
                color = color
            )
            withStyle(style) {
                append(segment.text)
            }
        }
    }
}

private fun parseMarkdown(text: String): List<MarkdownSegment> {
    val segments = mutableListOf<MarkdownSegment>()
    var i = 0
    val sb = StringBuilder()

    fun flushPlainText() {
        if (sb.isNotEmpty()) {
            segments.add(MarkdownSegment(sb.toString()))
            sb.clear()
        }
    }

    while (i < text.length) {
        when {
            // Inline code: `code`
            text[i] == '`' -> {
                val endIndex = text.indexOf('`', i + 1)
                if (endIndex > i) {
                    flushPlainText()
                    segments.add(MarkdownSegment(
                        text = text.substring(i + 1, endIndex),
                        isCode = true
                    ))
                    i = endIndex + 1
                } else {
                    sb.append(text[i])
                    i++
                }
            }

            // Check for asterisk patterns
            text[i] == '*' -> {
                // Count consecutive asterisks
                var asteriskCount = 0
                var j = i
                while (j < text.length && text[j] == '*') {
                    asteriskCount++
                    j++
                }

                when {
                    // Bold+Italic: ***text***
                    asteriskCount >= 3 -> {
                        val closePattern = "***"
                        val closeIndex = text.indexOf(closePattern, j)
                        if (closeIndex > j) {
                            flushPlainText()
                            segments.add(MarkdownSegment(
                                text = text.substring(i + 3, closeIndex),
                                isBold = true,
                                isItalic = true
                            ))
                            i = closeIndex + 3
                        } else {
                            sb.append("*".repeat(asteriskCount))
                            i = j
                        }
                    }
                    // Bold: **text**
                    asteriskCount == 2 -> {
                        val closePattern = "**"
                        val closeIndex = findClosingPattern(text, j, closePattern)
                        if (closeIndex > j) {
                            flushPlainText()
                            segments.add(MarkdownSegment(
                                text = text.substring(i + 2, closeIndex),
                                isBold = true
                            ))
                            i = closeIndex + 2
                        } else {
                            sb.append("**")
                            i = j
                        }
                    }
                    // Italic: *text*
                    asteriskCount == 1 -> {
                        val closeIndex = findClosingPattern(text, j, "*")
                        if (closeIndex > j) {
                            flushPlainText()
                            segments.add(MarkdownSegment(
                                text = text.substring(i + 1, closeIndex),
                                isItalic = true
                            ))
                            i = closeIndex + 1
                        } else {
                            sb.append("*")
                            i = j
                        }
                    }
                    else -> {
                        sb.append(text[i])
                        i++
                    }
                }
            }

            // Underscore italic: _text_
            text[i] == '_' -> {
                val closeIndex = findClosingPattern(text, i + 1, "_")
                if (closeIndex > i + 1) {
                    flushPlainText()
                    segments.add(MarkdownSegment(
                        text = text.substring(i + 1, closeIndex),
                        isItalic = true
                    ))
                    i = closeIndex + 1
                } else {
                    sb.append(text[i])
                    i++
                }
            }

            // Quoted dialogue: "text"
            text[i] == '"' -> {
                val closeIndex = text.indexOf('"', i + 1)
                if (closeIndex > i) {
                    flushPlainText()
                    // Include the quote marks in the displayed text
                    segments.add(MarkdownSegment(
                        text = text.substring(i, closeIndex + 1),
                        isQuote = true
                    ))
                    i = closeIndex + 1
                } else {
                    sb.append(text[i])
                    i++
                }
            }

            else -> {
                sb.append(text[i])
                i++
            }
        }
    }

    flushPlainText()
    return segments
}

// Find closing pattern, but not if it's part of a longer asterisk sequence
private fun findClosingPattern(text: String, startIndex: Int, pattern: String): Int {
    var idx = startIndex
    while (idx < text.length) {
        val foundIdx = text.indexOf(pattern, idx)
        if (foundIdx < 0) return -1

        // For single asterisk, make sure it's not part of ** or ***
        if (pattern == "*") {
            val before = if (foundIdx > 0) text[foundIdx - 1] else ' '
            val after = if (foundIdx + 1 < text.length) text[foundIdx + 1] else ' '
            if (before != '*' && after != '*') {
                return foundIdx
            }
            idx = foundIdx + 1
        } else if (pattern == "**") {
            val after = if (foundIdx + 2 < text.length) text[foundIdx + 2] else ' '
            if (after != '*') {
                return foundIdx
            }
            idx = foundIdx + 2
        } else {
            return foundIdx
        }
    }
    return -1
}
