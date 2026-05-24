package com.pockettavern.app.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

enum class AvatarShape {
    CIRCLE,
    ROUNDED_SQUARE,
    SQUARE;

    fun toShape(): Shape = when (this) {
        CIRCLE         -> CircleShape
        ROUNDED_SQUARE -> RoundedCornerShape(6.dp)
        SQUARE         -> RoundedCornerShape(0.dp)
    }
}

/**
 * All app-specific colors in one place.
 * Defaults to the built-in Fire & Ice theme.
 * Distributed through [LocalPocketTavernColors] so any composable can read them.
 */
data class PocketTavernColors(
    val background: Color      = DarkBackground,
    val surface: Color         = DarkSurface,
    val surfaceVariant: Color  = DarkSurfaceVariant,
    val inputBackground: Color = DarkInputBackground,
    val accentPrimary: Color   = FireOrange,
    val avatarShape: AvatarShape = AvatarShape.CIRCLE,
    val borderColor: Color     = DarkSurfaceVariant,
    val textPrimary: Color     = TextPrimary,
    val textSecondary: Color   = TextSecondary,
    val textTertiary: Color    = TextTertiary,
    val userBubble: Color      = UserBubble,
    val userBubbleText: Color  = UserBubbleText,
    val assistantBubble: Color     = AssistantBubble,
    val assistantBubbleText: Color = AssistantBubbleText,
    val quoteTextColor: Color      = QuoteTextColor,
    val italicTextColor: Color     = ItalicTextColor,
    val codeBackgroundColor: Color = CodeBackgroundColor
) {
    companion object {
        val Default = PocketTavernColors()
    }
}

val LocalPocketTavernColors = staticCompositionLocalOf { PocketTavernColors.Default }
