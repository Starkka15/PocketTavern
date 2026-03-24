package com.pockettavern.app.ui.theme

import android.content.Context
import com.pockettavern.app.util.DebugLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

data class ThemeEntry(
    val id: String,
    val name: String,
    val isDefault: Boolean,
    val effectName: String = "None",
    val hasBackground: Boolean = false,
    val hasAudio: Boolean = false
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

    private val _themeAssets = MutableStateFlow(ThemeAssets.Default)
    val themeAssets: StateFlow<ThemeAssets> = _themeAssets.asStateFlow()

    private val _activeId = MutableStateFlow(prefs.getString(KEY_ACTIVE, THEME_DEFAULT) ?: THEME_DEFAULT)
    val activeId: StateFlow<String> = _activeId.asStateFlow()

    init {
        migrateIfNeeded()
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
        // User-imported themes from disk (directory-per-theme)
        themesDir.listFiles { f -> f.isDirectory }
            ?.filter { File(it, "theme.json").exists() }
            ?.sortedBy { it.name }
            ?.forEach { dir ->
                val json = File(dir, "theme.json").readText()
                val name = extractName(json) ?: dir.name
                val effectName = try {
                    effectDisplayName(StThemeParser.parseParticleEffect(json, isDefault = false))
                } catch (_: Exception) { "None" }
                val hasBackground = listOf("background.gif", "background.png", "background.jpg", "background.webp")
                    .any { File(dir, it).exists() }
                val hasAudio = listOf("music.mp3", "music.ogg", "music.wav")
                    .any { File(dir, it).exists() }
                list += ThemeEntry(dir.name, name, isDefault = false, effectName = effectName, hasBackground = hasBackground, hasAudio = hasAudio)
            }
        return list
    }

    fun applyTheme(id: String) {
        if (id == THEME_DEFAULT) {
            _colors.value  = PocketTavernColors.Default
            _particleEffect.value = ParticlePresets.fireAndIce()
            _themeAssets.value = ThemeAssets.Default
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
        val id = generateId(name)
        val themeDir = File(themesDir, id).also { it.mkdirs() }
        File(themeDir, "theme.json").writeText(json)
        DebugLogger.log("[ThemeManager] Imported '$name' as '$id'")
        id
    } catch (e: Exception) {
        DebugLogger.log("[ThemeManager] Import failed: ${e.message}")
        null
    }

    /** Import a ZIP bundle containing theme.json + optional images. */
    fun importThemeZip(inputStream: InputStream): String? = try {
        val tempDir = File(context.cacheDir, "theme_import_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        var totalBytes = 0L
        val maxBytes = 50L * 1024 * 1024 // 50 MB cap

        ZipInputStream(inputStream).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val safeName = File(entry.name).name // ZipSlip protection
                    val outFile = File(tempDir, safeName)
                    outFile.outputStream().use { out ->
                        val buf = ByteArray(8192)
                        var len: Int
                        while (zis.read(buf).also { len = it } > 0) {
                            totalBytes += len
                            if (totalBytes > maxBytes) throw Exception("ZIP exceeds 50 MB limit")
                            out.write(buf, 0, len)
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        val themeJsonFile = File(tempDir, "theme.json")
        if (!themeJsonFile.exists()) {
            tempDir.deleteRecursively()
            throw Exception("ZIP must contain theme.json")
        }

        val json = themeJsonFile.readText()
        val name = extractName(json) ?: "imported"
        val id = generateId(name)

        val themeDir = File(themesDir, id)
        if (themeDir.exists()) themeDir.deleteRecursively()
        tempDir.renameTo(themeDir)

        DebugLogger.log("[ThemeManager] Imported ZIP theme '$name' as '$id'")
        id
    } catch (e: Exception) {
        DebugLogger.log("[ThemeManager] ZIP import failed: ${e.message}")
        null
    }

    fun deleteTheme(id: String) {
        if (id == THEME_DEFAULT || id in BUNDLED_THEMES) return
        File(themesDir, id).deleteRecursively()
        if (_activeId.value == id) applyTheme(THEME_DEFAULT)
        DebugLogger.log("[ThemeManager] Deleted '$id'")
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private fun migrateIfNeeded() {
        val marker = File(themesDir, ".migrated_v2")
        if (marker.exists()) return
        themesDir.listFiles { f -> f.isFile && f.extension == "json" }?.forEach { jsonFile ->
            val id = jsonFile.nameWithoutExtension
            val dir = File(themesDir, id).also { it.mkdirs() }
            jsonFile.renameTo(File(dir, "theme.json"))
        }
        try { marker.createNewFile() } catch (_: Exception) {}
        DebugLogger.log("[ThemeManager] Migrated theme storage to directory layout")
    }

    private fun generateId(name: String): String =
        name.lowercase().replace(Regex("[^a-z0-9_]"), "_").take(40)
            .ifBlank { "theme_${System.currentTimeMillis()}" }

    private fun loadBundled(id: String) {
        val json = readBundledJson(id)
        if (json == null) { applyTheme(THEME_DEFAULT); return }
        try {
            _colors.value = StThemeParser.parse(json)
            _particleEffect.value = StThemeParser.parseParticleEffect(json, isDefault = false)
            _themeAssets.value = StThemeParser.parseThemeAssets(json, bundledAssetDir(id))
            _activeId.value = id
            prefs.edit().putString(KEY_ACTIVE, id).apply()
        } catch (e: Exception) {
            DebugLogger.log("[ThemeManager] Failed to load bundled '$id': ${e.message}")
            applyTheme(THEME_DEFAULT)
        }
    }

    private fun readBundledJson(id: String): String? = try {
        // Try directory-style first, then flat file
        context.assets.open("themes/$id/theme.json").bufferedReader().readText()
    } catch (_: Exception) {
        try {
            context.assets.open("themes/$id.json").bufferedReader().readText()
        } catch (_: Exception) { null }
    }

    /** Returns a File pointing to the bundled theme's asset cache directory (copies assets on first access). */
    private fun bundledAssetDir(id: String): File? {
        val cacheDir = File(context.cacheDir, "bundled_themes/$id")
        if (cacheDir.exists()) return cacheDir
        // Check if asset directory has files by trying to open known filenames
        val assetNames = listOf(
            "background.gif", "background.png", "background.jpg", "background.webp",
            "logo.gif", "logo.png",
            "music.mp3", "music.ogg", "music.wav"
        )
        var hasAssets = false
        for (name in assetNames) {
            try {
                context.assets.open("themes/$id/$name").use { src ->
                    cacheDir.mkdirs()
                    File(cacheDir, name).outputStream().use { dst -> src.copyTo(dst) }
                    hasAssets = true
                }
            } catch (_: Exception) { /* file doesn't exist in assets */ }
        }
        return if (hasAssets) cacheDir else null
    }

    private fun loadFromDisk(id: String) {
        val themeDir = File(themesDir, id)
        val f = File(themeDir, "theme.json")
        if (!f.exists()) { applyTheme(THEME_DEFAULT); return }
        try {
            val json = f.readText()
            _colors.value   = StThemeParser.parse(json)
            _particleEffect.value = StThemeParser.parseParticleEffect(json, isDefault = false)
            _themeAssets.value = StThemeParser.parseThemeAssets(json, themeDir)
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
        private val BUNDLED_THEMES = setOf("fire_and_ice", "midnight_plum", "ember", "sand_and_sea")
    }
}
