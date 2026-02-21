package com.pockettavern.app.data.local

import android.content.Context
import com.pockettavern.app.extensions.JsExtension
import com.pockettavern.app.util.DebugLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.File
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JsExtensionStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val jsExtDir: File
        get() = File(context.filesDir, "js_extensions").also { it.mkdirs() }

    private val settingsFile: File get() = File(jsExtDir, "_settings.json")
    private val enabledFile: File  get() = File(jsExtDir, "_enabled.json")

    // ── Listing ───────────────────────────────────────────────────────────────

    fun listExtensions(): List<JsExtension> {
        val enabledMap = loadEnabledMap()
        return jsExtDir.listFiles { f -> f.isDirectory }
            ?.mapNotNull { dir ->
                val script = File(dir, "index.js")
                if (!script.exists()) return@mapNotNull null
                val manifest = loadManifest(dir)
                JsExtension(
                    id          = dir.name,
                    name        = manifest["name"] ?: dir.name,
                    version     = manifest["version"] ?: "1.0.0",
                    description = manifest["description"] ?: "",
                    author      = manifest["author"] ?: "",
                    sourceUrl   = manifest["sourceUrl"] ?: "",
                    enabled     = enabledMap[dir.name] ?: true,
                    scriptFile  = script
                )
            }
            ?.sortedBy { it.name }
            ?: emptyList()
    }

    // ── Install / Uninstall ───────────────────────────────────────────────────

    /**
     * Download and install an extension from a URL.
     * The URL can point to an index.js directly, or a folder — we append /index.js if needed.
     * Optionally fetches manifest.json from the same directory.
     */
    suspend fun installFromUrl(url: String): JsExtension = withContext(Dispatchers.IO) {
        val scriptUrl = if (url.trimEnd('/').endsWith(".js")) url.trim()
                        else url.trimEnd('/') + "/index.js"
        val baseUrl = scriptUrl.removeSuffix("/index.js")

        // Try manifest.json — don't fail if missing
        val manifest = try {
            json.parseToJsonElement(URL("$baseUrl/manifest.json").readText()).jsonObject
        } catch (_: Exception) { null }

        val id = (manifest?.get("id")?.jsonPrimitive?.content
            ?: baseUrl.substringAfterLast('/')
                .replace(Regex("[^a-z0-9_-]"), "_").lowercase())
            .ifBlank { "ext_${System.currentTimeMillis()}" }

        val scriptText = URL(scriptUrl).readText()

        val extDir = File(jsExtDir, id).also { it.mkdirs() }
        File(extDir, "index.js").writeText(scriptText)
        File(extDir, "manifest.json").writeText(buildJsonObject {
            put("id", id)
            put("name",        manifest?.get("name")?.jsonPrimitive?.content ?: id)
            put("version",     manifest?.get("version")?.jsonPrimitive?.content ?: "1.0.0")
            put("description", manifest?.get("description")?.jsonPrimitive?.content ?: "")
            put("author",      manifest?.get("author")?.jsonPrimitive?.content ?: "")
            put("sourceUrl",   baseUrl)
        }.toString())

        val name = manifest?.get("name")?.jsonPrimitive?.content ?: id
        DebugLogger.log("[JsExtensionStorage] Installed '$name' ($id)")
        JsExtension(
            id          = id,
            name        = name,
            version     = manifest?.get("version")?.jsonPrimitive?.content ?: "1.0.0",
            description = manifest?.get("description")?.jsonPrimitive?.content ?: "",
            author      = manifest?.get("author")?.jsonPrimitive?.content ?: "",
            sourceUrl   = baseUrl,
            enabled     = true,
            scriptFile  = File(extDir, "index.js")
        )
    }

    fun uninstall(id: String) {
        File(jsExtDir, id).deleteRecursively()
        val map = loadEnabledMap().toMutableMap().also { it.remove(id) }
        saveEnabledMap(map)
        DebugLogger.log("[JsExtensionStorage] Uninstalled '$id'")
    }

    fun setEnabled(id: String, enabled: Boolean) {
        saveEnabledMap(loadEnabledMap().toMutableMap().also { it[id] = enabled })
    }

    // ── Settings ──────────────────────────────────────────────────────────────

    fun loadAllSettings(): String =
        if (settingsFile.exists()) settingsFile.readText() else "{}"

    fun saveAllSettings(settingsJson: String) {
        settingsFile.writeText(settingsJson)
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun loadEnabledMap(): Map<String, Boolean> {
        if (!enabledFile.exists()) return emptyMap()
        return try {
            json.parseToJsonElement(enabledFile.readText()).jsonObject
                .entries.associate { (k, v) -> k to v.jsonPrimitive.boolean }
        } catch (_: Exception) { emptyMap() }
    }

    private fun saveEnabledMap(map: Map<String, Boolean>) {
        enabledFile.writeText(buildJsonObject { map.forEach { (k, v) -> put(k, v) } }.toString())
    }

    private fun loadManifest(extDir: File): Map<String, String> {
        val f = File(extDir, "manifest.json")
        if (!f.exists()) return emptyMap()
        return try {
            json.parseToJsonElement(f.readText()).jsonObject
                .entries.associate { (k, v) -> k to (v as? JsonPrimitive)?.content.orEmpty() }
        } catch (_: Exception) { emptyMap() }
    }
}
