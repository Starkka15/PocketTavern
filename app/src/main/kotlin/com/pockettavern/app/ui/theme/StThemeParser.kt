package com.pockettavern.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import java.io.File
import com.pockettavern.app.util.DebugLogger
import kotlinx.serialization.json.*

/**
 * Parses a SillyTavern theme JSON string into [PocketTavernColors].
 *
 * Fields consumed (CSS / web-only fields are intentionally ignored):
 *   underline_text_color     → accentPrimary
 *   main_text_color          → textPrimary / assistantBubbleText
 *   quote_text_color         → textSecondary
 *   blur_tint_color          → surface
 *   shadow_color             → background
 *   border_color             → borderColor  (falls back to derived surface+6% when transparent)
 *   user_mes_blur_tint_color → userBubble  (falls back to accentPrimary when absent)
 *   bot_mes_blur_tint_color  → assistantBubble (falls back to chat_tint_color, then default)
 *   chat_tint_color          → assistantBubble fallback
 *   user_mes_text_color      → userBubbleText (PocketTavern extension; derived when absent)
 *   bot_mes_text_color       → assistantBubbleText (PocketTavern extension; main_text_color when absent)
 *
 * Colour values may be given as `rgb()`/`rgba()` or as CSS hex (`#RGB`, `#RGBA`,
 * `#RRGGBB`, `#RRGGBBAA`). Bubble colours keep their alpha so a theme can let the
 * chat background image show through; see the alpha note on the bubble parsing below.
 *
 * Intentionally ignored (web/CSS-only, no Android equivalent):
 *   italics_text_color, font_scale, blur_strength, chat_display, avatar_style,
 *   noShadows, chat_width, hideChatAvatars, timestamp_*, mesIDDisplay,
 *   messageTimer_enabled, scrollLock, hotswap_enabled, custom_css
 */
object StThemeParser {

    fun parse(json: String): PocketTavernColors {
        val fields = extractStringFields(json)
        fun color(key: String) = parseColor(fields[key])
        fun intField(key: String) = extractIntField(json, key)

        val accentPrimary = color("underline_text_color") ?: FireOrange
        val textPrimary   = color("main_text_color")      ?: TextPrimary
        val textSecondary = color("quote_text_color")     ?: TextSecondary

        // ST blur_tint_color is meant for CSS backdrop-filter; strip alpha for solid Android bg
        val surface    = color("blur_tint_color")?.opaque() ?: DarkSurface
        val background = color("shadow_color")?.opaque()    ?: DarkBackground

        // Derive slightly lighter surface variants
        val surfaceVariant  = lerp(surface, Color.White, 0.06f)
        val inputBackground = lerp(surface, Color.White, 0.10f)

        // Avatar shape: 0 = circle (default), 1 = rounded square
        val avatarShape = when (intField("avatar_style")) {
            1    -> AvatarShape.ROUNDED_SQUARE
            2    -> AvatarShape.SQUARE
            else -> AvatarShape.CIRCLE
        }

        // Border color: use explicit ST field if visible; otherwise derive a subtle separator
        val borderRaw = color("border_color")
        val borderColor = if (borderRaw != null && borderRaw.alpha > 0.05f)
            borderRaw
        else
            lerp(surface, Color.White, 0.12f)

        // Bubble colours keep their alpha: a partly transparent bubble is a deliberate
        // choice that lets the chat background image show through, and forcing it opaque
        // was what made bubbles solid regardless of the theme.
        //
        // Alpha 0 exactly is still treated as "unset" rather than "invisible" — that is
        // SillyTavern's idiom for "no tint, use default styling", and a fully invisible
        // bubble is never a useful result. Anything above 0 is honoured as written.
        val userBubble = color("user_mes_blur_tint_color")?.takeIf { it.alpha > 0f }
            ?: accentPrimary

        // Assistant bubble: bot_mes → chat_tint → default
        val assistantBubble = color("bot_mes_blur_tint_color")?.takeIf { it.alpha > 0f }
            ?: color("chat_tint_color")?.takeIf { it.alpha > 0f }
            ?: AssistantBubble

        // Bubble text colours are explicit if the theme sets them, otherwise derived.
        val userBubbleText = color("user_mes_text_color")?.takeIf { it.alpha > 0f }
            ?: autoBubbleTextColor(userBubble, background)

        return PocketTavernColors(
            background          = background,
            surface             = surface,
            surfaceVariant      = surfaceVariant,
            inputBackground     = inputBackground,
            accentPrimary       = accentPrimary,
            avatarShape         = avatarShape,
            borderColor         = borderColor,
            textPrimary         = textPrimary,
            textSecondary       = textSecondary,
            textTertiary        = textSecondary.copy(alpha = 0.65f),
            userBubble          = userBubble,
            userBubbleText      = userBubbleText,
            assistantBubble     = assistantBubble,
            assistantBubbleText = color("bot_mes_text_color")?.takeIf { it.alpha > 0f } ?: textPrimary,
            quoteTextColor      = color("quote_text_color") ?: QuoteTextColor,
            // A fully transparent italic colour is never a deliberate choice — it would
            // render italics invisible. Treat it as "inherit", which also heals themes
            // already saved with the zeroed-out Unspecified value.
            italicTextColor     = color("italic_text_color")?.takeIf { it.alpha > 0f } ?: ItalicTextColor,
            codeBackgroundColor = color("code_background_color") ?: CodeBackgroundColor
        )
    }

    /**
     * Legible text colour for [bubble] once it is composited over [background]:
     * black on light, white on dark.
     *
     * A translucent bubble shows the background through it, so contrast has to be judged
     * against what actually reaches the screen rather than the bubble colour alone. Shared
     * with the theme builder so its live preview matches what the parser will produce.
     */
    fun autoBubbleTextColor(bubble: Color, background: Color): Color =
        if (bubble.compositeOver(background).perceivedLuminance() > 0.4f) Color.Black
        else Color.White

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun Color.opaque() = copy(alpha = 1f)

    /** Perceived luminance in 0..1 using standard sRGB weights. */
    private fun Color.perceivedLuminance() = 0.2126f * red + 0.7152f * green + 0.0722f * blue

    private fun extractIntField(json: String, key: String): Int? {
        val m = Regex(""""$key"\s*:\s*(\d+)""").find(json) ?: return null
        return m.groupValues[1].toIntOrNull()
    }

    /** Accepts either `rgb()`/`rgba()` or CSS hex. Returns null if neither form matches. */
    private fun parseColor(s: String?): Color? {
        val raw = s?.trim() ?: return null
        return if (raw.startsWith("#")) parseHex(raw) else parseRgba(raw)
    }

    /**
     * Parses `#RGB`, `#RGBA`, `#RRGGBB` and `#RRGGBBAA`.
     *
     * Alpha is the *trailing* component, following CSS Color 4 — which is what a theme
     * author writing hex will expect. This is deliberately not Android's leading-alpha
     * `#AARRGGBB`; an 8-digit value here means RGBA, not ARGB.
     */
    private fun parseHex(s: String): Color? {
        val hex = s.removePrefix("#")
        if (hex.isEmpty() || hex.any { it !in "0123456789abcdefABCDEF" }) return null
        // Expand shorthand: #RGB → #RRGGBB, #RGBA → #RRGGBBAA
        val full = when (hex.length) {
            3, 4 -> hex.map { "$it$it" }.joinToString("")
            6, 8 -> hex
            else -> return null
        }
        fun byte(i: Int) = full.substring(i, i + 2).toInt(16) / 255f
        return Color(byte(0), byte(2), byte(4), if (full.length == 8) byte(6) else 1f)
    }

    private fun parseRgba(s: String): Color? {
        val m = Regex("""rgba?\(\s*([\d.]+)\s*,\s*([\d.]+)\s*,\s*([\d.]+)(?:\s*,\s*([\d.]+))?\s*\)""")
            .find(s) ?: return null
        val r = m.groupValues[1].toFloatOrNull() ?: return null
        val g = m.groupValues[2].toFloatOrNull() ?: return null
        val b = m.groupValues[3].toFloatOrNull() ?: return null
        val a = m.groupValues[4].toFloatOrNull() ?: 1f
        return Color(r / 255f, g / 255f, b / 255f, a)
    }

    /**
     * Extracts top-level string fields from a JSON string.
     * Handles simple "key": "value" pairs — sufficient for ST theme color fields.
     * Stops at the first backslash inside a value (skips custom_css safely).
     */
    private fun extractStringFields(json: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        Regex(""""(\w+)"\s*:\s*"([^"\\]*)"""").findAll(json).forEach { m ->
            result[m.groupValues[1]] = m.groupValues[2]
        }
        return result
    }

    // ── Particle effect parsing ─────────────────────────────────────────────

    private val jsonParser = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parseParticleEffect(json: String, isDefault: Boolean = false): ParticleEffectConfig {
        return try {
            val root = jsonParser.parseToJsonElement(json).jsonObject
            val effectElement = root["particle_effect"]
                ?: return if (isDefault) ParticleEffectConfig.Default else ParticleEffectConfig.None

            when (effectElement) {
                is JsonPrimitive -> ParticlePresets.resolve(effectElement.content)
                is JsonObject -> parseEffectObject(effectElement)
                else -> ParticleEffectConfig.None
            }
        } catch (e: Exception) {
            DebugLogger.log("[StThemeParser] Failed to parse particle_effect: ${e.message}")
            if (isDefault) ParticleEffectConfig.Default else ParticleEffectConfig.None
        }
    }

    fun parseThemeAssets(json: String, themeDir: File?): ThemeAssets {
        return try {
            val fields = extractStringFields(json)
            val root = jsonParser.parseToJsonElement(json).jsonObject

            // Background image
            val hasBackground = root["background_image"]?.jsonPrimitive?.booleanOrNull == true
            val backgroundPath = if (hasBackground && themeDir != null) {
                listOf("background.gif", "background.png", "background.jpg", "background.webp")
                    .map { File(themeDir, it) }
                    .firstOrNull { it.exists() }
                    ?.absolutePath
            } else null

            val scaleMode = when (fields["background_image_mode"]?.lowercase()) {
                "fit" -> BackgroundScaleMode.FIT
                "stretch" -> BackgroundScaleMode.STRETCH
                else -> BackgroundScaleMode.FILL
            }

            val bgOpacity = root["background_opacity"]?.jsonPrimitive?.floatOrNull ?: 0.3f

            // Logo
            val hasLogo = root["logo_image"]?.jsonPrimitive?.booleanOrNull == true
            val logoPath = if (hasLogo && themeDir != null) {
                listOf("logo.gif", "logo.png")
                    .map { File(themeDir, it) }
                    .firstOrNull { it.exists() }
                    ?.absolutePath
            } else null

            val logoTint = if (logoPath != null) null else parseColor(fields["logo_tint"])

            // Audio
            val hasAudio = root["theme_audio"]?.jsonPrimitive?.booleanOrNull == true
            val audioPath = if (hasAudio && themeDir != null) {
                listOf("music.mp3", "music.ogg", "music.wav")
                    .map { File(themeDir, it) }
                    .firstOrNull { it.exists() }
                    ?.absolutePath
            } else null
            val audioLoop = root["theme_audio_loop"]?.jsonPrimitive?.booleanOrNull ?: true

            ThemeAssets(
                backgroundImagePath = backgroundPath,
                backgroundScaleMode = scaleMode,
                backgroundOpacity = bgOpacity.coerceIn(0f, 1f),
                logoImagePath = logoPath,
                logoTint = logoTint,
                audioPath = audioPath,
                audioLoop = audioLoop
            )
        } catch (e: Exception) {
            DebugLogger.log("[StThemeParser] Failed to parse theme assets: ${e.message}")
            ThemeAssets.Default
        }
    }

    private fun parseEffectObject(obj: JsonObject): ParticleEffectConfig {
        val presetName = obj["preset"]?.jsonPrimitive?.contentOrNull
        val base = if (presetName != null) ParticlePresets.resolve(presetName) else ParticleEffectConfig()

        val layers = obj["layers"]?.jsonArray?.mapIndexed { i, elem ->
            val baseLayer = base.layers.getOrElse(i) { ParticleLayerConfig() }
            parseLayerObject(elem.jsonObject, baseLayer)
        } ?: base.layers

        return base.copy(
            layers = layers.ifEmpty { base.layers },
            animationDuration = obj["animation_duration"]?.jsonPrimitive?.intOrNull
                ?: base.animationDuration,
            fadeEdgePercent = obj["fade_edge_percent"]?.jsonPrimitive?.floatOrNull
                ?: base.fadeEdgePercent,
            backgroundGlow = obj["background_glow"]?.jsonPrimitive?.booleanOrNull
                ?: base.backgroundGlow,
            backgroundGlowOpacity = obj["background_glow_opacity"]?.jsonPrimitive?.floatOrNull
                ?: base.backgroundGlowOpacity,
            enabled = obj["enabled"]?.jsonPrimitive?.booleanOrNull ?: base.enabled
        )
    }

    private fun parseLayerObject(obj: JsonObject, base: ParticleLayerConfig): ParticleLayerConfig {
        return base.copy(
            count = obj["count"]?.jsonPrimitive?.intOrNull ?: base.count,
            shape = obj["shape"]?.jsonPrimitive?.contentOrNull?.let { parseShape(it) } ?: base.shape,
            direction = obj["direction"]?.jsonPrimitive?.contentOrNull?.let { parseDirection(it) } ?: base.direction,
            sizeMin = obj["size_min"]?.jsonPrimitive?.floatOrNull ?: base.sizeMin,
            sizeMax = obj["size_max"]?.jsonPrimitive?.floatOrNull ?: base.sizeMax,
            speedMin = obj["speed_min"]?.jsonPrimitive?.floatOrNull ?: base.speedMin,
            speedMax = obj["speed_max"]?.jsonPrimitive?.floatOrNull ?: base.speedMax,
            wobbleAmplitude = obj["wobble_amplitude"]?.jsonPrimitive?.floatOrNull ?: base.wobbleAmplitude,
            wobbleFrequency = obj["wobble_frequency"]?.jsonPrimitive?.floatOrNull ?: base.wobbleFrequency,
            opacityMin = obj["opacity_min"]?.jsonPrimitive?.floatOrNull ?: base.opacityMin,
            opacityMax = obj["opacity_max"]?.jsonPrimitive?.floatOrNull ?: base.opacityMax,
            glow = obj["glow"]?.jsonPrimitive?.booleanOrNull ?: base.glow,
            glowRadius = obj["glow_radius"]?.jsonPrimitive?.floatOrNull ?: base.glowRadius,
            glowOpacity = obj["glow_opacity"]?.jsonPrimitive?.floatOrNull ?: base.glowOpacity,
            rotation = obj["rotation"]?.jsonPrimitive?.booleanOrNull ?: base.rotation,
            colors = obj["colors"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: base.colors
        )
    }

    private fun parseShape(s: String): ParticleShape? = when (s.lowercase()) {
        "circle"    -> ParticleShape.CIRCLE
        "square"    -> ParticleShape.SQUARE
        "diamond"   -> ParticleShape.DIAMOND
        "star"      -> ParticleShape.STAR
        "snowflake" -> ParticleShape.SNOWFLAKE
        "raindrop"  -> ParticleShape.RAINDROP
        "cloud"     -> ParticleShape.CLOUD
        else        -> null
    }

    private fun parseDirection(s: String): ParticleDirection? = when (s.lowercase()) {
        "up"     -> ParticleDirection.UP
        "down"   -> ParticleDirection.DOWN
        "left"   -> ParticleDirection.LEFT
        "right"  -> ParticleDirection.RIGHT
        "random" -> ParticleDirection.RANDOM
        else     -> null
    }
}
