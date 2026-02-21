package com.pockettavern.app.ui.theme

import android.content.Context
import com.pockettavern.app.util.DebugLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class ThemeEntry(
    val id: String,
    val name: String,
    val isDefault: Boolean
)

@Singleton
class ThemeManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
    private val themesDir get() = File(context.filesDir, "themes").also { it.mkdirs() }

    private val _colors  = MutableStateFlow(PocketTavernColors.Default)
    val colors: StateFlow<PocketTavernColors> = _colors.asStateFlow()

    private val _activeId = MutableStateFlow(prefs.getString(KEY_ACTIVE, THEME_DEFAULT) ?: THEME_DEFAULT)
    val activeId: StateFlow<String> = _activeId.asStateFlow()

    init {
        val savedId = _activeId.value
        if (savedId != THEME_DEFAULT) loadFromDisk(savedId)
    }

    // ── Public API ────────────────────────────────────────────────────────────

    fun listThemes(): List<ThemeEntry> {
        val list = mutableListOf(ThemeEntry(THEME_DEFAULT, "PocketTavern", isDefault = true))
        themesDir.listFiles { f -> f.extension == "json" }
            ?.sortedBy { it.nameWithoutExtension }
            ?.forEach { f ->
                val name = extractName(f.readText()) ?: f.nameWithoutExtension
                list += ThemeEntry(f.nameWithoutExtension, name, isDefault = false)
            }
        return list
    }

    fun applyTheme(id: String) {
        if (id == THEME_DEFAULT) {
            _colors.value  = PocketTavernColors.Default
            _activeId.value = THEME_DEFAULT
            prefs.edit().putString(KEY_ACTIVE, THEME_DEFAULT).apply()
        } else {
            loadFromDisk(id)
        }
    }

    /** Import raw JSON, save to disk, return the generated id or null on error. */
    fun importTheme(json: String): String? = try {
        val name = extractName(json) ?: "imported"
        val id = name.lowercase().replace(Regex("[^a-z0-9_]"), "_").take(40)
            .ifBlank { "theme_${System.currentTimeMillis()}" }
        File(themesDir, "$id.json").writeText(json)
        DebugLogger.log("[ThemeManager] Imported '$name' as '$id'")
        id
    } catch (e: Exception) {
        DebugLogger.log("[ThemeManager] Import failed: ${e.message}")
        null
    }

    fun deleteTheme(id: String) {
        if (id == THEME_DEFAULT) return
        File(themesDir, "$id.json").delete()
        if (_activeId.value == id) applyTheme(THEME_DEFAULT)
        DebugLogger.log("[ThemeManager] Deleted '$id'")
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private fun loadFromDisk(id: String) {
        val f = File(themesDir, "$id.json")
        if (!f.exists()) { applyTheme(THEME_DEFAULT); return }
        try {
            _colors.value   = StThemeParser.parse(f.readText())
            _activeId.value = id
            prefs.edit().putString(KEY_ACTIVE, id).apply()
        } catch (e: Exception) {
            DebugLogger.log("[ThemeManager] Failed to load '$id': ${e.message}")
            applyTheme(THEME_DEFAULT)
        }
    }

    private fun extractName(json: String): String? =
        Regex(""""name"\s*:\s*"([^"\\]*)"""").find(json)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }

    companion object {
        const val THEME_DEFAULT = "pockettavern"
        private const val KEY_ACTIVE = "active_theme_id"
    }
}
