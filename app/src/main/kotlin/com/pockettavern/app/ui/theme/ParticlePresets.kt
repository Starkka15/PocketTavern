package com.pockettavern.app.ui.theme

object ParticlePresets {

    fun resolve(name: String): ParticleEffectConfig = when (name.lowercase()) {
        "none"       -> ParticleEffectConfig.None
        "embers"     -> embers()
        "snow"       -> snow()
        "bubbles"    -> bubbles()
        "rain"       -> rain()
        "sparkles"   -> sparkles()
        "fireandice" -> fireAndIce()
        else         -> ParticleEffectConfig.None
    }

    fun embers() = ParticleEffectConfig(
        preset = "embers",
        layers = listOf(
            ParticleLayerConfig(
                count = 35,
                shape = ParticleShape.CIRCLE,
                direction = ParticleDirection.UP,
                sizeMin = 2f, sizeMax = 7f,
                speedMin = 0.3f, speedMax = 0.7f,
                wobbleAmplitude = 0.5f, wobbleFrequency = 1.0f,
                opacityMin = 0.25f, opacityMax = 0.7f,
                glow = true, glowRadius = 2.8f, glowOpacity = 0.25f,
                colors = listOf("#FF6B00", "#FFB347", "#E84A1B")
            )
        ),
        backgroundGlow = true
    )

    fun snow() = ParticleEffectConfig(
        preset = "snow",
        layers = listOf(
            ParticleLayerConfig(
                count = 40,
                shape = ParticleShape.SNOWFLAKE,
                direction = ParticleDirection.DOWN,
                sizeMin = 3f, sizeMax = 9f,
                speedMin = 0.15f, speedMax = 0.4f,
                wobbleAmplitude = 0.6f, wobbleFrequency = 0.8f,
                opacityMin = 0.3f, opacityMax = 0.8f,
                glow = false,
                rotation = true,
                colors = listOf("#FFFFFF", "#E0F0FF", "#B0D8F0")
            )
        ),
        backgroundGlow = false
    )

    fun bubbles() = ParticleEffectConfig(
        preset = "bubbles",
        layers = listOf(
            ParticleLayerConfig(
                count = 20,
                shape = ParticleShape.CIRCLE,
                direction = ParticleDirection.UP,
                sizeMin = 6f, sizeMax = 16f,
                speedMin = 0.1f, speedMax = 0.3f,
                wobbleAmplitude = 0.3f, wobbleFrequency = 0.6f,
                opacityMin = 0.08f, opacityMax = 0.25f,
                glow = true, glowRadius = 1.5f, glowOpacity = 0.15f,
                colors = listOf("#80D0FF", "#60E0C0", "#FFFFFF")
            )
        ),
        backgroundGlow = false,
        animationDuration = 14000
    )

    fun rain() = ParticleEffectConfig(
        preset = "rain",
        layers = listOf(
            ParticleLayerConfig(
                count = 60,
                shape = ParticleShape.RAINDROP,
                direction = ParticleDirection.DOWN,
                sizeMin = 2f, sizeMax = 4f,
                speedMin = 0.8f, speedMax = 1.5f,
                wobbleAmplitude = 0.05f, wobbleFrequency = 0.3f,
                opacityMin = 0.15f, opacityMax = 0.5f,
                glow = false,
                colors = listOf("#AACCEE", "#DDEEFF", "#88AADD")
            )
        ),
        backgroundGlow = false,
        animationDuration = 6000
    )

    fun sparkles() = ParticleEffectConfig(
        preset = "sparkles",
        layers = listOf(
            ParticleLayerConfig(
                count = 50,
                shape = ParticleShape.STAR,
                direction = ParticleDirection.RANDOM,
                sizeMin = 1.5f, sizeMax = 4f,
                speedMin = 0.1f, speedMax = 0.5f,
                wobbleAmplitude = 0.2f, wobbleFrequency = 2.0f,
                opacityMin = 0.1f, opacityMax = 0.9f,
                glow = true, glowRadius = 3.0f, glowOpacity = 0.3f,
                rotation = true,
                colors = listOf("#FFFFFF", "#FFD700", "#FFFACD")
            )
        ),
        backgroundGlow = false,
        animationDuration = 8000
    )

    fun fireAndIce() = ParticleEffectConfig(
        preset = "fireAndIce",
        layers = listOf(
            // Fire embers rising
            ParticleLayerConfig(
                count = 25,
                shape = ParticleShape.CIRCLE,
                direction = ParticleDirection.UP,
                sizeMin = 2f, sizeMax = 6f,
                speedMin = 0.3f, speedMax = 0.7f,
                wobbleAmplitude = 0.5f, wobbleFrequency = 1.0f,
                opacityMin = 0.25f, opacityMax = 0.6f,
                glow = true, glowRadius = 2.8f, glowOpacity = 0.25f,
                colors = listOf("#FF6B00", "#FFB347", "#E84A1B")
            ),
            // Ice snowflakes falling
            ParticleLayerConfig(
                count = 15,
                shape = ParticleShape.SNOWFLAKE,
                direction = ParticleDirection.DOWN,
                sizeMin = 3f, sizeMax = 7f,
                speedMin = 0.15f, speedMax = 0.35f,
                wobbleAmplitude = 0.6f, wobbleFrequency = 0.8f,
                opacityMin = 0.2f, opacityMax = 0.5f,
                glow = false,
                rotation = true,
                colors = listOf("#00BFFF", "#4DD0E1", "#E0F0FF")
            )
        ),
        backgroundGlow = true,
        backgroundGlowOpacity = 0.10f
    )
}
