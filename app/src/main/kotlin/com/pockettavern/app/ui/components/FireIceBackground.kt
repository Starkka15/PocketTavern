package com.pockettavern.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.sin
import kotlin.random.Random

private data class ParticleData(
    val xRatio: Float,
    val startOffset: Float,
    val size: Float,
    val color: Color,
    val speed: Float,
    val wobbleAmp: Float,
    val wobbleFreq: Float,
    val alpha: Float
)

/**
 * A subtle ambient particle background that adapts to the active theme's primary color.
 * Particles drift upward slowly with a gentle horizontal wobble.
 */
@Composable
fun FireIceBackground(modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val bg     = MaterialTheme.colorScheme.background

    // Lighter tint of the accent for particle variety
    val lighter = Color(
        red   = accent.red   + (1f - accent.red)   * 0.5f,
        green = accent.green + (1f - accent.green) * 0.5f,
        blue  = accent.blue  + (1f - accent.blue)  * 0.5f
    )

    val colors    = remember(accent) { listOf(accent, lighter, accent, lighter, accent) }
    val particles = remember(accent) { List(35) { createParticle(colors) } }

    val infiniteTransition = rememberInfiniteTransition(label = "bg")
    val animProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue  = 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "progress"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Solid themed background
        drawRect(color = bg)

        // Subtle accent glow at top edge only
        drawRect(brush = Brush.verticalGradient(
            listOf(
                accent.copy(alpha = 0.07f),
                accent.copy(alpha = 0.02f),
                Color.Transparent
            ),
            startY = 0f,
            endY   = h * 0.35f
        ))

        // Ambient particles — all drift upward with gentle wobble
        particles.forEach { p ->
            val cycle = (animProgress * p.speed + p.startOffset) % 1f
            val y     = h * (1f - cycle)
            val x     = p.xRatio * w + sin(cycle * p.wobbleFreq * 6.28f) * p.wobbleAmp * w * 0.07f

            // Smooth fade: in for first 20%, out for last 20%
            val fade = minOf(cycle * 5f, (1f - cycle) * 5f, 1f)
            val a    = p.alpha * fade

            // Soft glow ring + solid core
            drawCircle(color = p.color.copy(alpha = a * 0.25f), radius = p.size * 2.8f, center = Offset(x, y))
            drawCircle(color = p.color.copy(alpha = a),          radius = p.size,         center = Offset(x, y))
        }
    }
}

private fun createParticle(colors: List<Color>): ParticleData = ParticleData(
    xRatio      = Random.nextFloat(),
    startOffset = Random.nextFloat(),
    size        = Random.nextFloat() * 5f + 2f,
    color       = colors.random(),
    speed       = Random.nextFloat() * 0.4f + 0.3f,
    wobbleAmp   = Random.nextFloat() * 0.6f + 0.2f,
    wobbleFreq  = Random.nextFloat() * 1.5f + 0.8f,
    alpha       = Random.nextFloat() * 0.45f + 0.25f
)
