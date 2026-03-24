package com.pockettavern.app.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf

enum class ParticleShape {
    CIRCLE, SQUARE, DIAMOND, STAR, SNOWFLAKE, RAINDROP, CLOUD
}

enum class ParticleDirection {
    UP, DOWN, LEFT, RIGHT, RANDOM
}

/**
 * Configuration for a single particle layer.
 * Multiple layers compose into complex effects (e.g. fireAndIce = embers + snow).
 */
data class ParticleLayerConfig(
    val count: Int = 35,
    val shape: ParticleShape = ParticleShape.CIRCLE,
    val direction: ParticleDirection = ParticleDirection.UP,
    val sizeMin: Float = 2f,
    val sizeMax: Float = 7f,
    val speedMin: Float = 0.3f,
    val speedMax: Float = 0.7f,
    val wobbleAmplitude: Float = 0.5f,
    val wobbleFrequency: Float = 1.0f,
    val opacityMin: Float = 0.25f,
    val opacityMax: Float = 0.7f,
    val glow: Boolean = true,
    val glowRadius: Float = 2.8f,
    val glowOpacity: Float = 0.25f,
    val rotation: Boolean = false,
    val colors: List<String> = emptyList() // hex strings; empty = derive from accent
)

/**
 * Top-level particle effect configuration for a theme.
 * Contains one or more layers plus global settings.
 */
data class ParticleEffectConfig(
    val preset: String? = null,
    val layers: List<ParticleLayerConfig> = emptyList(),
    val animationDuration: Int = 10_000,
    val fadeEdgePercent: Float = 0.20f,
    val backgroundGlow: Boolean = true,
    val backgroundGlowOpacity: Float = 0.07f,
    val enabled: Boolean = true
) {
    companion object {
        val None = ParticleEffectConfig(preset = "none", enabled = false)
        val Default = ParticleEffectConfig(preset = "fireAndIce")
    }
}

val LocalParticleEffect = staticCompositionLocalOf { ParticlePresets.fireAndIce() }
