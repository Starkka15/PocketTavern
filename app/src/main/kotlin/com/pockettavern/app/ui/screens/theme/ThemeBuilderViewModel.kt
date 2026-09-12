package com.pockettavern.app.ui.screens.theme

import android.content.Context
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pockettavern.app.ui.theme.AvatarShape
import com.pockettavern.app.ui.theme.BackgroundScaleMode
import com.pockettavern.app.ui.theme.PocketTavernColors
import com.pockettavern.app.ui.theme.StThemeParser
import com.pockettavern.app.ui.theme.ThemeManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt
import javax.inject.Inject

data class ThemeBuilderUiState(
    val colors: PocketTavernColors = PocketTavernColors.Default,
    val backgroundUri: Uri? = null,
    val backgroundOpacity: Float = 0.3f,
    val backgroundScaleMode: BackgroundScaleMode = BackgroundScaleMode.FILL,
    /**
     * Whether the user picked the bubble text colours by hand. Until they do, the colours
     * track the bubble/background automatically — so saving a theme doesn't freeze a text
     * colour that should still be adapting. Only an explicit pick is written to the theme.
     */
    val userBubbleTextOverridden: Boolean = false,
    val assistantBubbleTextOverridden: Boolean = false
)

@HiltViewModel
class ThemeBuilderViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    val themeManager: ThemeManager
) : ViewModel() {

    private val _state = MutableStateFlow(
        themeManager.colors.value.let { c ->
            ThemeBuilderUiState(
                colors = c,
                // Treat the active theme's text colours as hand-picked only where they
                // differ from what derivation would produce — that is exactly the case
                // where the theme must have set them explicitly, and it stops a
                // round-trip through the builder from dropping them.
                userBubbleTextOverridden =
                    c.userBubbleText != StThemeParser.autoBubbleTextColor(c.userBubble, c.background),
                assistantBubbleTextOverridden = c.assistantBubbleText != c.textPrimary
            )
        }
    )
    val state: StateFlow<ThemeBuilderUiState> = _state.asStateFlow()

    private val _savedEvent = MutableSharedFlow<Boolean>(replay = 0)
    val savedEvent: SharedFlow<Boolean> = _savedEvent.asSharedFlow()

    fun updateColors(transform: (PocketTavernColors) -> PocketTavernColors) {
        _state.update { st ->
            var colors = transform(st.colors)
            // Keep un-overridden text colours in step with the colours they follow, so the
            // live preview shows what the parser will actually produce after a save.
            if (!st.userBubbleTextOverridden) {
                colors = colors.copy(
                    userBubbleText = StThemeParser.autoBubbleTextColor(colors.userBubble, colors.background)
                )
            }
            if (!st.assistantBubbleTextOverridden) {
                colors = colors.copy(assistantBubbleText = colors.textPrimary)
            }
            st.copy(colors = colors)
        }
    }

    /** Explicit bubble text picks — these are persisted with the theme. */
    fun setUserBubbleText(color: Color) = _state.update {
        it.copy(colors = it.colors.copy(userBubbleText = color), userBubbleTextOverridden = true)
    }

    fun setAssistantBubbleText(color: Color) = _state.update {
        it.copy(colors = it.colors.copy(assistantBubbleText = color), assistantBubbleTextOverridden = true)
    }

    fun setBackgroundUri(uri: Uri?) = _state.update { it.copy(backgroundUri = uri) }
    fun setBackgroundOpacity(v: Float) = _state.update { it.copy(backgroundOpacity = v) }
    fun setBackgroundScaleMode(m: BackgroundScaleMode) = _state.update { it.copy(backgroundScaleMode = m) }

    fun saveTheme(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val s = _state.value
            val cleanName = name.trim().ifEmpty { "Custom Theme" }

            var backgroundBytes: ByteArray? = null
            var backgroundExt: String? = null
            s.backgroundUri?.let { uri ->
                val mime = context.contentResolver.getType(uri) ?: ""
                backgroundExt = when {
                    mime.contains("gif")  -> "gif"
                    mime.contains("webp") -> "webp"
                    mime.contains("png")  -> "png"
                    else                  -> "jpg"
                }
                backgroundBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }

            val json = buildThemeJson(cleanName, s)
            val id = if (backgroundBytes != null) {
                themeManager.importThemeWithBackground(json, backgroundBytes, backgroundExt)
            } else {
                themeManager.importTheme(json)
            }
            if (id != null) themeManager.applyTheme(id)
            _savedEvent.emit(id != null)
        }
    }

    private fun buildThemeJson(name: String, s: ThemeBuilderUiState): String {
        val c = s.colors
        // Locale.US throughout: these are JSON number literals, and a comma decimal
        // separator (de/fr/…) would emit rgba(30,30,40,0,5) and corrupt the theme file.
        fun rgba(color: Color): String {
            val r = (color.red * 255).roundToInt()
            val g = (color.green * 255).roundToInt()
            val b = (color.blue * 255).roundToInt()
            // Alpha must survive the round trip — writing a literal 1 here is what
            // made every saved theme's bubbles opaque no matter what was picked.
            val a = "%.3f".format(Locale.US, color.alpha).trimEnd('0').trimEnd('.')
            return "rgba($r,$g,$b,$a)"
        }
        val escaped = name.replace("\\", "\\\\").replace("\"", "\\\"")
        val bgFields = if (s.backgroundUri != null) {
            val modeStr = when (s.backgroundScaleMode) {
                BackgroundScaleMode.FIT     -> "fit"
                BackgroundScaleMode.STRETCH -> "stretch"
                BackgroundScaleMode.FILL    -> "fill"
            }
            ""","background_image":true,"background_image_mode":"$modeStr","background_opacity":${"%.2f".format(Locale.US, s.backgroundOpacity)}"""
        } else ""
        val avatarStyle = when (c.avatarShape) {
            AvatarShape.CIRCLE         -> 0
            AvatarShape.ROUNDED_SQUARE -> 1
            AvatarShape.SQUARE         -> 2
        }
        // Color.Unspecified means "inherit the bubble text colour". Writing it out
        // numerically yields rgba(0,0,0,0), which reads back as a genuine fully
        // transparent colour and makes every italic span invisible — so omit the
        // field entirely and let the parser fall back to the inherit default.
        val italicField = if (c.italicTextColor == Color.Unspecified) "" else
            ""","italic_text_color":"${rgba(c.italicTextColor)}""""
        // Only hand-picked bubble text colours are written. Omitting them keeps the
        // derived-from-contrast behaviour alive for themes that never set one.
        val textFields = buildString {
            if (s.userBubbleTextOverridden) append(""","user_mes_text_color":"${rgba(c.userBubbleText)}"""")
            if (s.assistantBubbleTextOverridden) append(""","bot_mes_text_color":"${rgba(c.assistantBubbleText)}"""")
        }
        return """{"name":"$escaped","shadow_color":"${rgba(c.background)}","blur_tint_color":"${rgba(c.surface)}","underline_text_color":"${rgba(c.accentPrimary)}","main_text_color":"${rgba(c.textPrimary)}","quote_text_color":"${rgba(c.quoteTextColor)}","border_color":"${rgba(c.borderColor)}","user_mes_blur_tint_color":"${rgba(c.userBubble)}","bot_mes_blur_tint_color":"${rgba(c.assistantBubble)}","code_background_color":"${rgba(c.codeBackgroundColor)}","avatar_style":$avatarStyle$italicField$textFields$bgFields}"""
    }
}
