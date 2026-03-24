package com.pockettavern.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import com.pockettavern.app.ui.theme.*
import kotlin.math.*
import kotlin.random.Random

private data class Particle(
    val layerIndex: Int,
    val xRatio: Float,
    val startOffset: Float,
    val size: Float,
    val color: Color,
    val speed: Float,
    val wobbleAmp: Float,
    val wobbleFreq: Float,
    val alpha: Float,
    val rotationSpeed: Float
)

/**
 * Generic config-driven particle background that reads from [LocalParticleEffect].
 * Supports multiple layers, custom shapes, directions, colors, and glow effects.
 */
@Composable
fun ParticleBackground(modifier: Modifier = Modifier) {
    val config = LocalParticleEffect.current
    if (!config.enabled || config.layers.isEmpty()) return

    val accent = MaterialTheme.colorScheme.primary
    val bg = MaterialTheme.colorScheme.background

    val resolvedLayers = remember(config, accent) {
        config.layers.map { layer ->
            if (layer.colors.isEmpty()) {
                val lighter = Color(
                    red   = accent.red   + (1f - accent.red)   * 0.5f,
                    green = accent.green + (1f - accent.green) * 0.5f,
                    blue  = accent.blue  + (1f - accent.blue)  * 0.5f
                )
                layer to listOf(accent, lighter)
            } else {
                layer to layer.colors.mapNotNull { parseHexColor(it) }
            }
        }
    }

    val particles = remember(resolvedLayers) {
        resolvedLayers.flatMapIndexed { layerIdx, (layer, colors) ->
            if (colors.isEmpty()) return@flatMapIndexed emptyList()
            List(layer.count) {
                Particle(
                    layerIndex = layerIdx,
                    xRatio = Random.nextFloat(),
                    startOffset = Random.nextFloat(),
                    size = Random.nextFloat() * (layer.sizeMax - layer.sizeMin) + layer.sizeMin,
                    color = colors.random(),
                    speed = Random.nextFloat() * (layer.speedMax - layer.speedMin) + layer.speedMin,
                    wobbleAmp = Random.nextFloat() * layer.wobbleAmplitude * 0.8f + layer.wobbleAmplitude * 0.2f,
                    wobbleFreq = Random.nextFloat() * layer.wobbleFrequency * 0.5f + layer.wobbleFrequency * 0.5f,
                    alpha = Random.nextFloat() * (layer.opacityMax - layer.opacityMin) + layer.opacityMin,
                    rotationSpeed = if (layer.rotation) Random.nextFloat() * 360f else 0f
                )
            }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "particleBg")
    val animProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(config.animationDuration, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "progress"
    )

    val hasBackgroundImage = LocalThemeAssets.current.backgroundImagePath != null

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        if (!hasBackgroundImage) {
            drawRect(color = bg)
        }

        // Per-layer directional glow — each layer glows at its origin edge
        if (config.backgroundGlow) {
            for ((layer, colors) in resolvedLayers) {
                val glowColor = colors.firstOrNull() ?: accent
                val opacity = config.backgroundGlowOpacity
                when (layer.direction) {
                    ParticleDirection.UP -> {
                        // Particles rise from bottom → glow at bottom
                        drawRect(brush = Brush.verticalGradient(
                            listOf(Color.Transparent, glowColor.copy(alpha = opacity * 0.3f), glowColor.copy(alpha = opacity)),
                            startY = h * 0.65f, endY = h
                        ))
                    }
                    ParticleDirection.DOWN -> {
                        // Particles fall from top → glow at top
                        drawRect(brush = Brush.verticalGradient(
                            listOf(glowColor.copy(alpha = opacity), glowColor.copy(alpha = opacity * 0.3f), Color.Transparent),
                            startY = 0f, endY = h * 0.35f
                        ))
                    }
                    else -> {
                        // Fallback: top glow
                        drawRect(brush = Brush.verticalGradient(
                            listOf(glowColor.copy(alpha = opacity), glowColor.copy(alpha = opacity * 0.3f), Color.Transparent),
                            startY = 0f, endY = h * 0.35f
                        ))
                    }
                }
            }
        }

        particles.forEach { p ->
            val layer = config.layers[p.layerIndex]
            val cycle = (animProgress * p.speed + p.startOffset) % 1f

            val (x, y) = computePosition(layer.direction, cycle, p, w, h)

            val fade = minOf(cycle / config.fadeEdgePercent, (1f - cycle) / config.fadeEdgePercent, 1f)
            val a = p.alpha * fade
            if (a <= 0.001f) return@forEach

            val center = Offset(x, y)
            val rot = p.rotationSpeed * cycle

            if (layer.glow) {
                drawCircle(
                    color = p.color.copy(alpha = a * layer.glowOpacity),
                    radius = p.size * layer.glowRadius,
                    center = center
                )
            }

            when (layer.shape) {
                ParticleShape.CIRCLE -> {
                    drawCircle(color = p.color.copy(alpha = a), radius = p.size, center = center)
                }
                ParticleShape.SQUARE -> {
                    rotate(rot, pivot = center) {
                        drawRect(
                            color = p.color.copy(alpha = a),
                            topLeft = Offset(x - p.size, y - p.size),
                            size = Size(p.size * 2, p.size * 2)
                        )
                    }
                }
                ParticleShape.DIAMOND -> {
                    rotate(45f + rot, pivot = center) {
                        drawRect(
                            color = p.color.copy(alpha = a),
                            topLeft = Offset(x - p.size, y - p.size),
                            size = Size(p.size * 2, p.size * 2)
                        )
                    }
                }
                ParticleShape.STAR -> {
                    rotate(rot, pivot = center) {
                        drawPath(
                            path = starPath(center, p.size, 5),
                            color = p.color.copy(alpha = a)
                        )
                    }
                }
                ParticleShape.SNOWFLAKE -> {
                    rotate(rot, pivot = center) {
                        drawPath(
                            path = snowflakePath(center, p.size),
                            color = p.color.copy(alpha = a),
                            style = Stroke(width = maxOf(p.size * 0.25f, 0.8f))
                        )
                    }
                }
                ParticleShape.RAINDROP -> {
                    drawLine(
                        color = p.color.copy(alpha = a),
                        start = Offset(x, y - p.size * 2f),
                        end = Offset(x, y + p.size * 2f),
                        strokeWidth = p.size * 0.6f,
                        cap = StrokeCap.Round
                    )
                }
                ParticleShape.CLOUD -> {
                    drawPath(
                        path = cloudPath(center, p.size),
                        color = p.color.copy(alpha = a)
                    )
                }
            }
        }
    }
}

private fun computePosition(
    direction: ParticleDirection,
    cycle: Float,
    p: Particle,
    w: Float, h: Float
): Pair<Float, Float> {
    val wobbleX = sin(cycle * p.wobbleFreq * 6.28f) * p.wobbleAmp * w * 0.07f
    val wobbleY = sin(cycle * p.wobbleFreq * 6.28f) * p.wobbleAmp * h * 0.07f

    return when (direction) {
        ParticleDirection.UP -> {
            (p.xRatio * w + wobbleX) to (h * (1f - cycle))
        }
        ParticleDirection.DOWN -> {
            (p.xRatio * w + wobbleX) to (h * cycle)
        }
        ParticleDirection.LEFT -> {
            (w * (1f - cycle)) to (p.xRatio * h + wobbleY)
        }
        ParticleDirection.RIGHT -> {
            (w * cycle) to (p.xRatio * h + wobbleY)
        }
        ParticleDirection.RANDOM -> {
            val angle = p.startOffset * 6.28f
            val baseX = p.xRatio * w + cos(angle) * cycle * w * 0.3f + wobbleX
            val baseY = 0.5f * h + sin(angle) * cycle * h * 0.3f + wobbleY
            baseX to baseY
        }
    }
}

private fun starPath(center: Offset, radius: Float, points: Int): Path {
    val path = Path()
    val inner = radius * 0.4f
    for (i in 0 until points * 2) {
        val r = if (i % 2 == 0) radius else inner
        val angle = Math.PI / points * i - Math.PI / 2
        val px = center.x + (r * cos(angle)).toFloat()
        val py = center.y + (r * sin(angle)).toFloat()
        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
    }
    path.close()
    return path
}

private fun snowflakePath(center: Offset, radius: Float): Path {
    val path = Path()
    for (i in 0 until 6) {
        val angle = Math.PI / 3 * i
        val ex = center.x + (radius * cos(angle)).toFloat()
        val ey = center.y + (radius * sin(angle)).toFloat()
        path.moveTo(center.x, center.y)
        path.lineTo(ex, ey)
        // Small branches at 2/3 out
        val bx = center.x + (radius * 0.6f * cos(angle)).toFloat()
        val by = center.y + (radius * 0.6f * sin(angle)).toFloat()
        val branchLen = radius * 0.3f
        for (side in listOf(-1, 1)) {
            val ba = angle + side * Math.PI / 4
            path.moveTo(bx, by)
            path.lineTo(
                bx + (branchLen * cos(ba)).toFloat(),
                by + (branchLen * sin(ba)).toFloat()
            )
        }
    }
    return path
}

/**
 * Draws a cumulus-style cloud shape: a flat base with 3 bumpy lobes on top.
 * Total width ≈ 3×radius, height ≈ 1.8×radius.
 */
private fun cloudPath(center: Offset, radius: Float): Path {
    val path = Path()
    val r = radius
    // Base rectangle bottom-center at `center`
    val baseY = center.y + r * 0.3f
    val baseTop = center.y - r * 0.1f
    val baseLeft = center.x - r * 1.5f
    val baseRight = center.x + r * 1.5f

    // Start at bottom-left, go across the flat base
    path.moveTo(baseLeft, baseY)
    path.lineTo(baseRight, baseY)

    // Right lobe — arc up from right edge
    path.arcTo(
        rect = androidx.compose.ui.geometry.Rect(
            center.x + r * 0.2f, baseTop - r * 0.6f,
            center.x + r * 1.5f, baseY
        ),
        startAngleDegrees = 0f, sweepAngleDegrees = -180f, forceMoveTo = false
    )

    // Center lobe — tallest bump
    path.arcTo(
        rect = androidx.compose.ui.geometry.Rect(
            center.x - r * 0.6f, baseTop - r * 1.0f,
            center.x + r * 0.6f, baseTop + r * 0.2f
        ),
        startAngleDegrees = -20f, sweepAngleDegrees = -140f, forceMoveTo = false
    )

    // Left lobe — arc up from left side
    path.arcTo(
        rect = androidx.compose.ui.geometry.Rect(
            center.x - r * 1.5f, baseTop - r * 0.5f,
            center.x - r * 0.2f, baseY
        ),
        startAngleDegrees = -20f, sweepAngleDegrees = -160f, forceMoveTo = false
    )

    path.close()
    return path
}

private fun parseHexColor(hex: String): Color? {
    return try {
        val clean = hex.removePrefix("#")
        val value = clean.toLong(16)
        when (clean.length) {
            6 -> Color(0xFF000000 or value)
            8 -> Color(value)
            else -> null
        }
    } catch (_: Exception) { null }
}
