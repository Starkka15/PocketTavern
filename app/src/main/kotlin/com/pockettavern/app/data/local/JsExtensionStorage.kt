package com.pockettavern.app.data.local

import android.content.Context
import android.net.Uri
import com.pockettavern.app.R
import com.pockettavern.app.extensions.JsExtension
import com.pockettavern.app.util.DebugLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.net.URL
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

@Singleton
class JsExtensionStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val jsExtDir: File
        get() = File(context.filesDir, "js_extensions").also { it.mkdirs() }

    private val settingsFile: File get() = File(jsExtDir, "_settings.json")
    private val enabledFile: File  get() = File(jsExtDir, "_enabled.json")
    private val charOverridesFile: File get() = File(jsExtDir, "_char_overrides.json")

    // ── Listing ───────────────────────────────────────────────────────────────

    fun listExtensions(): List<JsExtension> {
        val enabledMap = loadEnabledMap()
        val result = mutableListOf<JsExtension>()
        val seenIds = mutableSetOf<String>()

        // Bundled extensions from assets
        try {
            val extDirs = context.assets.list("extensions") ?: emptyArray()
            for (dirName in extDirs) {
                if (dirName == "pt_api.js") continue
                try {
                    // Verify index.js exists by opening it
                    context.assets.open("extensions/$dirName/index.js").close()
                    val manifest = loadBundledManifest(dirName)
                    seenIds.add(dirName)
                    result.add(JsExtension(
                        id          = dirName,
                        name        = manifest["name"] ?: dirName,
                        version     = manifest["version"] ?: "1.0.0",
                        description = manifest["description"] ?: "",
                        author      = manifest["author"] ?: "",
                        sourceUrl   = "",
                        enabled     = enabledMap[dirName] ?: true,
                        scriptFile  = File(jsExtDir, "$dirName/index.js"), // placeholder
                        bundled     = true
                    ))
                } catch (_: Exception) { /* not a valid extension dir */ }
            }
        } catch (_: Exception) { /* assets listing failed */ }

        // User-installed extensions from filesystem
        jsExtDir.listFiles { f -> f.isDirectory }?.forEach { dir ->
            if (dir.name in seenIds) return@forEach // skip if bundled version exists
            val script = File(dir, "index.js")
            if (!script.exists()) return@forEach
            val manifest = loadManifest(dir)
            result.add(JsExtension(
                id          = dir.name,
                name        = manifest["name"] ?: dir.name,
                version     = manifest["version"] ?: "1.0.0",
                description = manifest["description"] ?: "",
                author      = manifest["author"] ?: "",
                sourceUrl   = manifest["sourceUrl"] ?: "",
                enabled     = enabledMap[dir.name] ?: true,
                scriptFile  = script
            ))
        }

        return result.sortedBy { it.name }
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
        val displayName = manifest?.get("display_name")?.jsonPrimitive?.content?.ifBlank { null }
            ?: manifest?.get("displayName")?.jsonPrimitive?.content?.ifBlank { null }
        val name = displayName ?: manifest?.get("name")?.jsonPrimitive?.content ?: id
        File(extDir, "manifest.json").writeText(buildJsonObject {
            put("id", id)
            put("name",        name)
            if (displayName != null) put("display_name", displayName)
            put("version",     manifest?.get("version")?.jsonPrimitive?.content ?: "1.0.0")
            put("description", manifest?.get("description")?.jsonPrimitive?.content ?: "")
            put("author",      manifest?.get("author")?.jsonPrimitive?.content ?: "")
            put("sourceUrl",   baseUrl)
        }.toString())
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

    /**
     * Install an extension from a file URI (picked via Android file picker).
     * Supports:
     *   - A single .js file → treated as index.js, filename becomes the extension id
     *   - A .zip file → must contain index.js at root or inside a single top-level folder,
     *     optionally with manifest.json
     */
    suspend fun installFromFile(uri: Uri): JsExtension = withContext(Dispatchers.IO) {
        val cr = context.contentResolver
        val mimeType = cr.getType(uri) ?: ""
        val fileName = getDisplayName(uri) ?: "extension"

        if (mimeType == "application/zip" || mimeType == "application/x-zip-compressed"
            || fileName.endsWith(".zip", ignoreCase = true)
        ) {
            installFromZip(uri)
        } else {
            installFromJsFile(uri, fileName)
        }
    }

    private suspend fun installFromJsFile(uri: Uri, fileName: String): JsExtension =
        withContext(Dispatchers.IO) {
            val scriptText = context.contentResolver.openInputStream(uri)
                ?.bufferedReader()?.readText()
                ?: throw IllegalStateException("Could not read file")

            val baseName = fileName.removeSuffix(".js")
                .replace(Regex("[^a-zA-Z0-9_-]"), "_").lowercase()
                .ifBlank { "ext_${System.currentTimeMillis()}" }

            val extDir = File(jsExtDir, baseName).also { it.mkdirs() }
            File(extDir, "index.js").writeText(scriptText)
            File(extDir, "manifest.json").writeText(buildJsonObject {
                put("id", baseName)
                put("name", baseName)
                put("version", "1.0.0")
                put("description", context.getString(R.string.imported_from_file))
                put("author", "")
                put("sourceUrl", "")
            }.toString())

            DebugLogger.log("[JsExtensionStorage] Installed '$baseName' from file")
            JsExtension(
                id = baseName, name = baseName, version = "1.0.0",
                description = context.getString(R.string.imported_from_file), author = "", sourceUrl = "",
                enabled = true, scriptFile = File(extDir, "index.js")
            )
        }

    private suspend fun installFromZip(uri: Uri): JsExtension = withContext(Dispatchers.IO) {
        val files = mutableMapOf<String, ByteArray>()
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            ZipInputStream(inputStream).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        files[entry.name] = zis.readBytes()
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        } ?: throw IllegalStateException("Could not read zip file")

        // Find index.js — could be at root or inside a single top-level folder
        val indexPath = files.keys.firstOrNull { it == "index.js" }
            ?: files.keys.firstOrNull { it.endsWith("/index.js") && it.count { c -> c == '/' } == 1 }
            ?: throw IllegalStateException("Zip must contain index.js")

        val prefix = if (indexPath == "index.js") "" else indexPath.removeSuffix("index.js")

        // Check for manifest.json
        val manifestPath = "${prefix}manifest.json"
        val manifest = files[manifestPath]?.let { bytes ->
            try {
                json.parseToJsonElement(String(bytes)).jsonObject
            } catch (_: Exception) { null }
        }

        val id = (manifest?.get("id")?.jsonPrimitive?.content
            ?: prefix.trimEnd('/').ifBlank { getDisplayName(uri)?.removeSuffix(".zip") ?: "ext" }
                .replace(Regex("[^a-zA-Z0-9_-]"), "_").lowercase())
            .ifBlank { "ext_${System.currentTimeMillis()}" }

        val extDir = File(jsExtDir, id).also { it.mkdirs() }

        // Write all files under the prefix into the extension directory
        files.forEach { (path, bytes) ->
            if (path.startsWith(prefix)) {
                val relPath = path.removePrefix(prefix)
                if (relPath.isNotBlank()) {
                    val outFile = File(extDir, relPath)
                    outFile.parentFile?.mkdirs()
                    outFile.writeBytes(bytes)
                }
            }
        }

        val displayName = manifest?.get("display_name")?.jsonPrimitive?.content?.ifBlank { null }
            ?: manifest?.get("displayName")?.jsonPrimitive?.content?.ifBlank { null }
        val name = displayName ?: manifest?.get("name")?.jsonPrimitive?.content ?: id
        val version = manifest?.get("version")?.jsonPrimitive?.content ?: "1.0.0"
        val description = manifest?.get("description")?.jsonPrimitive?.content ?: ""
        val author = manifest?.get("author")?.jsonPrimitive?.content ?: ""

        // Write/overwrite manifest with standardised fields
        File(extDir, "manifest.json").writeText(buildJsonObject {
            put("id", id)
            put("name", name)
            if (displayName != null) put("display_name", displayName)
            put("version", version)
            put("description", description)
            put("author", author)
            put("sourceUrl", "")
        }.toString())

        DebugLogger.log("[JsExtensionStorage] Installed '$name' ($id) from zip")
        JsExtension(
            id = id, name = name, version = version,
            description = description, author = author, sourceUrl = "",
            enabled = true, scriptFile = File(extDir, "index.js")
        )
    }

    private fun getDisplayName(uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0) cursor.getString(nameIndex) else null
            }
        } catch (_: Exception) { null }
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

    // ── Per-character overrides ──────────────────────────────────────────────

    /**
     * Get per-character extension overrides.
     * Returns a map of extensionId → enabled for the given character file.
     * Only contains entries that differ from the global enabled state.
     */
    fun getCharacterOverrides(characterFile: String): Map<String, Boolean> {
        val allOverrides = loadCharOverrides()
        val charObj = allOverrides[characterFile]?.jsonObject ?: return emptyMap()
        return charObj.entries.associate { (k, v) -> k to v.jsonPrimitive.boolean }
    }

    /**
     * Set whether a specific extension is enabled/disabled for a specific character.
     * Pass null to remove the override (revert to global).
     */
    fun setCharacterExtensionEnabled(characterFile: String, extensionId: String, enabled: Boolean?) {
        val allOverrides = loadCharOverrides().toMutableMap()
        val charObj = (allOverrides[characterFile]?.jsonObject?.toMutableMap() ?: mutableMapOf())

        if (enabled != null) {
            charObj[extensionId] = JsonPrimitive(enabled)
        } else {
            charObj.remove(extensionId)
        }

        if (charObj.isEmpty()) {
            allOverrides.remove(characterFile)
        } else {
            allOverrides[characterFile] = JsonObject(charObj)
        }

        saveCharOverrides(allOverrides)
    }

    /**
     * Get the list of extension IDs that are disabled for a specific character.
     * Combines global enabled state with per-character overrides.
     */
    fun getDisabledExtensionsForCharacter(characterFile: String): List<String> {
        val globalEnabled = loadEnabledMap()
        val charOverrides = getCharacterOverrides(characterFile)
        val extensions = listExtensions()

        return extensions.filter { ext ->
            val globallyEnabled = globalEnabled[ext.id] ?: true
            val charEnabled = charOverrides[ext.id] ?: globallyEnabled
            !charEnabled
        }.map { it.id }
    }

    private fun loadCharOverrides(): Map<String, JsonElement> {
        if (!charOverridesFile.exists()) return emptyMap()
        return try {
            json.parseToJsonElement(charOverridesFile.readText()).jsonObject.toMap()
        } catch (_: Exception) { emptyMap() }
    }

    private fun saveCharOverrides(map: Map<String, JsonElement>) {
        charOverridesFile.writeText(JsonObject(map).toString())
    }

    // ── Settings ──────────────────────────────────────────────────────────────

    fun loadAllSettings(): String =
        if (settingsFile.exists()) settingsFile.readText() else "{}"

    fun saveAllSettings(settingsJson: String) {
        settingsFile.writeText(settingsJson)
    }

    /** Get settings for a specific extension as a flat key→JsonPrimitive map. */
    fun getExtensionSettings(extensionId: String): Map<String, JsonPrimitive> {
        val allJson = loadAllSettings()
        return try {
            val root = json.parseToJsonElement(allJson).jsonObject
            val extObj = root[extensionId]?.jsonObject ?: return emptyMap()
            extObj.entries
                .filter { it.value is JsonPrimitive }
                .associate { (k, v) -> k to v.jsonPrimitive }
        } catch (_: Exception) { emptyMap() }
    }

    /** Update a single setting for an extension and persist. */
    fun updateExtensionSetting(extensionId: String, key: String, value: JsonElement) {
        val allJson = loadAllSettings()
        val root = try {
            json.parseToJsonElement(allJson).jsonObject.toMutableMap()
        } catch (_: Exception) { mutableMapOf() }

        val extObj = try {
            (root[extensionId] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
        } catch (_: Exception) { mutableMapOf() }

        extObj[key] = value
        root[extensionId] = JsonObject(extObj)

        val newJson = JsonObject(root).toString()
        settingsFile.writeText(newJson)
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

    private fun loadBundledManifest(dirName: String): Map<String, String> {
        return try {
            val text = context.assets.open("extensions/$dirName/manifest.json").bufferedReader().readText()
            json.parseToJsonElement(text).jsonObject
                .entries.associate { (k, v) -> k to (v as? JsonPrimitive)?.content.orEmpty() }
        } catch (_: Exception) { emptyMap() }
    }
}
