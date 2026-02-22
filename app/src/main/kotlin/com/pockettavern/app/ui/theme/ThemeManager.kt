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
    val isDefault: Boolean,
    val effectName: String = "None"
)

@Singleton
class ThemeManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
    private val themesDir get() = File(context.filesDir, "themes").also { it.mkdirs() }

    private val _colors  = MutableStateFlow(PocketTavernColors.Default)
    val colors: StateFlow<PocketTavernColors> = _colors.asStateFlow()

    private val _particleEffect = MutableStateFlow(ParticlePresets.fireAndIce())
    val particleEffect: StateFlow<ParticleEffectConfig> = _particleEffect.asStateFlow()

    private val _activeId = MutableStateFlow(prefs.getString(KEY_ACTIVE, THEME_DEFAULT) ?: THEME_DEFAULT)
    val activeId: StateFlow<String> = _activeId.asStateFlow()

    init {
        val savedId = _activeId.value
        if (savedId != THEME_DEFAULT) {
            if (savedId in BUNDLED_THEMES) loadBundled(savedId) else loadFromDisk(savedId)
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    fun listThemes(): List<ThemeEntry> {
        val list = mutableListOf(
            ThemeEntry(THEME_DEFAULT, "PocketTavern", isDefault = true, effectName = "Fire & Ice")
        )
        // Bundled themes from assets
        for (id in BUNDLED_THEMES) {
            val json = readBundledJson(id) ?: continue
            val name = extractName(json) ?: id
            val effectName = try {
                effectDisplayName(StThemeParser.parseParticleEffect(json, isDefault = false))
            } catch (_: Exception) { "None" }
            list += ThemeEntry(id, name, isDefault = true, effectName = effectName)
        }
        // User-imported themes from disk
        themesDir.listFiles { f -> f.extension == "json" }
            ?.sortedBy { it.nameWithoutExtension }
            ?.forEach { f ->
                val json = f.readText()
                val name = extractName(json) ?: f.nameWithoutExtension
                val effectName = try {
                    effectDisplayName(StThemeParser.parseParticleEffect(json, isDefault = false))
                } catch (_: Exception) { "None" }
                list += ThemeEntry(f.nameWithoutExtension, name, isDefault = false, effectName = effectName)
            }
        return list
    }

    fun applyTheme(id: String) {
        if (id == THEME_DEFAULT) {
            _colors.value  = PocketTavernColors.Default
            _particleEffect.value = ParticlePresets.fireAndIce()
            _activeId.value = THEME_DEFAULT
            prefs.edit().putString(KEY_ACTIVE, THEME_DEFAULT).apply()
        } else if (id in BUNDLED_THEMES) {
            loadBundled(id)
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
        if (id == THEME_DEFAULT || id in BUNDLED_THEMES) return
        File(themesDir, "$id.json").delete()
        if (_activeId.value == id) applyTheme(THEME_DEFAULT)
        DebugLogger.log("[ThemeManager] Deleted '$id'")
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private fun loadBundled(id: String) {
        val json = readBundledJson(id)
        if (json == null) { applyTheme(THEME_DEFAULT); return }
        try {
            _colors.value = StThemeParser.parse(json)
            _particleEffect.value = StThemeParser.parseParticleEffect(json, isDefault = false)
            _activeId.value = id
            prefs.edit().putString(KEY_ACTIVE, id).apply()
        } catch (e: Exception) {
            DebugLogger.log("[ThemeManager] Failed to load bundled '$id': ${e.message}")
            applyTheme(THEME_DEFAULT)
        }
    }

    private fun readBundledJson(id: String): String? = try {
        context.assets.open("themes/$id.json").bufferedReader().readText()
    } catch (_: Exception) { null }

    private fun loadFromDisk(id: String) {
        val f = File(themesDir, "$id.json")
        if (!f.exists()) { applyTheme(THEME_DEFAULT); return }
        try {
            val json = f.readText()
            _colors.value   = StThemeParser.parse(json)
            _particleEffect.value = StThemeParser.parseParticleEffect(json, isDefault = false)
            _activeId.value = id
            prefs.edit().putString(KEY_ACTIVE, id).apply()
        } catch (e: Exception) {
            DebugLogger.log("[ThemeManager] Failed to load '$id': ${e.message}")
            applyTheme(THEME_DEFAULT)
        }
    }

    private fun effectDisplayName(effect: ParticleEffectConfig): String {
        if (!effect.enabled) return "None"
        return when (effect.preset?.lowercase()) {
            "fireandice" -> "Fire & Ice"
            null -> if (effect.layers.isNotEmpty()) "Custom" else "None"
            else -> effect.preset.replaceFirstChar { it.uppercase() }
        }
    }

    private fun extractName(json: String): String? =
        Regex(""""name"\s*:\s*"([^"\\]*)"""").find(json)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }

    companion object {
        const val THEME_DEFAULT = "pockettavern"
        private const val KEY_ACTIVE = "active_theme_id"
        private val BUNDLED_THEMES = setOf("fire_and_ice", "midnight_plum", "ember")
    }
}
