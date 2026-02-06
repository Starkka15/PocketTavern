package com.stark.sillytavern.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.stark.sillytavern.domain.model.ChatMessage
import com.stark.sillytavern.ui.theme.*

@Composable
fun ChatBubble(
    message: ChatMessage,
    characterName: String,
    modifier: Modifier = Modifier
) {
    val bubbleColor = if (message.isUser) UserBubble else AssistantBubble
    val textColor = if (message.isUser) UserBubbleText else AssistantBubbleText
    val senderColor = if (message.isUser) UserBubbleText else AccentGreen
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
        Surface(
            modifier = Modifier.widthIn(max = 320.dp),
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
        Surface(
            modifier = Modifier.widthIn(max = 320.dp),
            shape = bubbleShape,
            color = AssistantBubble
        ) {
            Column(modifier = Modifier.padding(12.dp, 8.dp)) {
                Text(
                    text = characterName,
                    style = MaterialTheme.typography.labelSmall,
                    color = AccentGreen,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatMessage(content + "▌"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AssistantBubbleText
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
    val isCode: Boolean = false
)

@Composable
private fun formatMessage(text: String): AnnotatedString {
    val segments = parseMarkdown(text)

    return buildAnnotatedString {
        segments.forEach { segment ->
            val style = SpanStyle(
                fontWeight = if (segment.isBold) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (segment.isItalic) FontStyle.Italic else FontStyle.Normal,
                fontFamily = if (segment.isCode) FontFamily.Monospace else null,
                background = if (segment.isCode) Color(0xFF2D2D2D) else Color.Unspecified
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
