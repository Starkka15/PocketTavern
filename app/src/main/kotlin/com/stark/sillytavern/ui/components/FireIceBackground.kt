package com.stark.sillytavern.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.sin
import kotlin.random.Random

// Fire colors
private val FireOrange = Color(0xFFFF6B00)
private val FireGold = Color(0xFFFFB347)
private val FireRed = Color(0xFFE84A1B)

// Ice colors
private val IceBlue = Color(0xFF00BFFF)
private val IceCyan = Color(0xFF4DD0E1)
private val IceWhite = Color(0xFFE0F7FA)

private data class ParticleData(
    val xRatio: Float,      // 0-1 position across width
    val startOffset: Float, // 0-1 offset in animation cycle
    val size: Float,
    val color: Color,
    val speed: Float,       // How fast it moves through cycle
    val wobbleAmp: Float,   // Horizontal wobble amplitude
    val wobbleFreq: Float,  // Horizontal wobble frequency
    val alpha: Float
)

@Composable
fun FireIceBackground(
    modifier: Modifier = Modifier
) {
    // Pre-generate particle data once
    val fireParticles = remember {
        List(30) { createFireParticleData() }
    }
    val iceParticles = remember {
        List(25) { createIceParticleData() }
    }

    // Single animated value drives everything
    val infiniteTransition = rememberInfiniteTransition(label = "bg")
    val animProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "progress"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        // Draw subtle gradient background
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF0A0A14),
                    Color(0xFF0A0A0F),
                    Color(0xFF100A0A)
                )
            )
        )

        // Draw fire glow at bottom
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.Transparent,
                    FireOrange.copy(alpha = 0.06f),
                    FireOrange.copy(alpha = 0.12f)
                )
            )
        )

        // Draw ice glow at top
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    IceBlue.copy(alpha = 0.1f),
                    IceBlue.copy(alpha = 0.04f),
                    Color.Transparent,
                    Color.Transparent
                )
            )
        )

        // Draw fire particles (rising from bottom)
        fireParticles.forEach { particle ->
            // Calculate Y position - particle rises from bottom to top
            val cyclePos = (animProgress * particle.speed + particle.startOffset) % 1f
            val y = height * (1f - cyclePos)  // Bottom to top

            // Calculate X with wobble
            val wobble = sin(cyclePos * particle.wobbleFreq * 6.28f) * particle.wobbleAmp * width * 0.1f
            val x = particle.xRatio * width + wobble

            // Fade based on height (fade out near top)
            val fadeAlpha = (cyclePos.coerceIn(0.1f, 0.9f) - 0.1f) / 0.8f
            val alpha = particle.alpha * (1f - fadeAlpha * 0.7f)

            // Draw glow
            drawCircle(
                color = particle.color.copy(alpha = alpha * 0.4f),
                radius = particle.size * 2.5f,
                center = Offset(x, y)
            )
            // Draw core
            drawCircle(
                color = particle.color.copy(alpha = alpha),
                radius = particle.size,
                center = Offset(x, y)
            )
        }

        // Draw ice particles (falling from top)
        iceParticles.forEach { particle ->
            // Calculate Y position - particle falls from top to bottom
            val cyclePos = (animProgress * particle.speed + particle.startOffset) % 1f
            val y = height * cyclePos  // Top to bottom

            // Calculate X with wobble
            val wobble = sin(cyclePos * particle.wobbleFreq * 6.28f) * particle.wobbleAmp * width * 0.08f
            val x = particle.xRatio * width + wobble

            // Fade based on height (fade out near bottom)
            val fadeAlpha = cyclePos.coerceIn(0.1f, 0.9f)
            val alpha = particle.alpha * (1f - fadeAlpha * 0.6f)

            // Draw glow
            drawCircle(
                color = particle.color.copy(alpha = alpha * 0.5f),
                radius = particle.size * 2f,
                center = Offset(x, y)
            )
            // Draw core
            drawCircle(
                color = particle.color.copy(alpha = alpha),
                radius = particle.size,
                center = Offset(x, y)
            )
        }
    }
}

private fun createFireParticleData(): ParticleData {
    val colors = listOf(FireOrange, FireGold, FireRed, FireGold)
    return ParticleData(
        xRatio = Random.nextFloat(),
        startOffset = Random.nextFloat(),
        size = Random.nextFloat() * 6f + 4f,
        color = colors.random(),
        speed = Random.nextFloat() * 0.5f + 0.8f,
        wobbleAmp = Random.nextFloat() * 0.8f + 0.3f,
        wobbleFreq = Random.nextFloat() * 2f + 1f,
        alpha = Random.nextFloat() * 0.4f + 0.5f
    )
}

private fun createIceParticleData(): ParticleData {
    val colors = listOf(IceBlue, IceCyan, IceWhite, IceCyan)
    return ParticleData(
        xRatio = Random.nextFloat(),
        startOffset = Random.nextFloat(),
        size = Random.nextFloat() * 5f + 3f,
        color = colors.random(),
        speed = Random.nextFloat() * 0.4f + 0.6f,
        wobbleAmp = Random.nextFloat() * 0.6f + 0.2f,
        wobbleFreq = Random.nextFloat() * 1.5f + 0.8f,
        alpha = Random.nextFloat() * 0.5f + 0.4f
    )
}
