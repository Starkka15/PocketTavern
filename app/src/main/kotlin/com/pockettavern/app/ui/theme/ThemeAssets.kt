package com.pockettavern.app.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

enum class BackgroundScaleMode {
    FILL,    // ContentScale.Crop — fills screen, may crop edges
    FIT,     // ContentScale.Fit — entire image visible, may letterbox
    STRETCH  // ContentScale.FillBounds — stretches to fill exactly
}

data class ThemeAssets(
    val backgroundImagePath: String? = null,
    val backgroundScaleMode: BackgroundScaleMode = BackgroundScaleMode.FILL,
    val backgroundOpacity: Float = 0.3f,
    val logoImagePath: String? = null,
    val logoTint: Color? = null
) {
    companion object {
        val Default = ThemeAssets()
    }
}

val LocalThemeAssets = staticCompositionLocalOf { ThemeAssets.Default }
