package com.stark.sillytavern.ui.theme

import androidx.compose.ui.graphics.Color

// PocketTavern Fire & Ice Theme
val DarkBackground = Color(0xFF0A0A0F)  // Near black
val DarkSurface = Color(0xFF12121A)     // Dark surface
val DarkSurfaceVariant = Color(0xFF1A1A25)  // Slightly lighter
val DarkInputBackground = Color(0xFF1E1E2A)
val DarkCard = Color(0xFF151520)

// Fire colors (orange/gold)
val FireOrange = Color(0xFFFF6B00)
val FireGold = Color(0xFFFFB347)
val FireRed = Color(0xFFE84A1B)

// Ice colors (blue)
val IceBlue = Color(0xFF00BFFF)
val IceCyan = Color(0xFF4DD0E1)
val IceDeep = Color(0xFF1E90FF)

// Primary accent - Fire orange
val AccentPrimary = FireOrange
val AccentPrimaryDark = Color(0xFFCC5500)

// Secondary accent - Ice blue
val AccentSecondary = IceBlue
val AccentSecondaryDark = Color(0xFF0099CC)

// Legacy aliases for compatibility
val AccentGreen = FireOrange  // Now uses fire orange as primary
val AccentGreenDark = AccentPrimaryDark
val AccentBlue = IceBlue
val AccentPurple = Color(0xFFBB86FC)
val ErrorRed = FireRed

val TextPrimary = Color(0xFFEEEEEE)
val TextSecondary = Color(0xFF888888)
val TextTertiary = Color(0xFF666666)

// Message bubble colors - Fire for user, Ice for assistant
val UserBubble = FireOrange
val UserBubbleText = Color(0xFF000000)
val AssistantBubble = Color(0xFF1A2A3A)  // Dark blue-tinted
val AssistantBubbleText = TextPrimary
