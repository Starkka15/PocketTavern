package com.pockettavern.app.ui.screens.theme

import android.content.Context
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pockettavern.app.ui.theme.AvatarShape
import com.pockettavern.app.ui.theme.BackgroundScaleMode
import com.pockettavern.app.ui.theme.PocketTavernColors
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
import kotlin.math.roundToInt
import javax.inject.Inject

data class ThemeBuilderUiState(
    val colors: PocketTavernColors = PocketTavernColors.Default,
    val backgroundUri: Uri? = null,
    val backgroundOpacity: Float = 0.3f,
    val backgroundScaleMode: BackgroundScaleMode = BackgroundScaleMode.FILL
)

@HiltViewModel
class ThemeBuilderViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    val themeManager: ThemeManager
) : ViewModel() {

    private val _state = MutableStateFlow(ThemeBuilderUiState(colors = themeManager.colors.value))
    val state: StateFlow<ThemeBuilderUiState> = _state.asStateFlow()

    private val _savedEvent = MutableSharedFlow<Boolean>(replay = 0)
    val savedEvent: SharedFlow<Boolean> = _savedEvent.asSharedFlow()

    fun updateColors(transform: (PocketTavernColors) -> PocketTavernColors) {
        _state.update { it.copy(colors = transform(it.colors)) }
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
        fun rgba(color: Color): String {
            val r = (color.red * 255).roundToInt()
            val g = (color.green * 255).roundToInt()
            val b = (color.blue * 255).roundToInt()
            return "rgba($r,$g,$b,1)"
        }
        val escaped = name.replace("\\", "\\\\").replace("\"", "\\\"")
        val bgFields = if (s.backgroundUri != null) {
            val modeStr = when (s.backgroundScaleMode) {
                BackgroundScaleMode.FIT     -> "fit"
                BackgroundScaleMode.STRETCH -> "stretch"
                BackgroundScaleMode.FILL    -> "fill"
            }
            ""","background_image":true,"background_image_mode":"$modeStr","background_opacity":${"%.2f".format(s.backgroundOpacity)}"""
        } else ""
        val avatarStyle = when (c.avatarShape) {
            AvatarShape.CIRCLE         -> 0
            AvatarShape.ROUNDED_SQUARE -> 1
            AvatarShape.SQUARE         -> 2
        }
        return """{"name":"$escaped","shadow_color":"${rgba(c.background)}","blur_tint_color":"${rgba(c.surface)}","underline_text_color":"${rgba(c.accentPrimary)}","main_text_color":"${rgba(c.textPrimary)}","quote_text_color":"${rgba(c.quoteTextColor)}","border_color":"${rgba(c.borderColor)}","user_mes_blur_tint_color":"${rgba(c.userBubble)}","bot_mes_blur_tint_color":"${rgba(c.assistantBubble)}","italic_text_color":"${rgba(c.italicTextColor)}","code_background_color":"${rgba(c.codeBackgroundColor)}","avatar_style":$avatarStyle$bgFields}"""
    }
}
