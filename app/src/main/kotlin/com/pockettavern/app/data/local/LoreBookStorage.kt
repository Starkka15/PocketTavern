package com.pockettavern.app.data.local

import android.content.Context
import com.pockettavern.app.domain.model.WorldInfoEntry
import com.pockettavern.app.util.DebugLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ST-compatible world info JSON format.
 */
@Serializable
private data class WorldInfoFile(
    val name: String = "",
    val description: String = "",
    val entries: Map<String, WorldInfoFileEntry> = emptyMap()
)

/**
 * V2 character-book style lorebook entry — used when a lorebook file's `entries`
 * is a JSON ARRAY (chub/agnai/card-book exports) instead of ST's object-map.
 */
@Serializable
private data class BookArrayEntry(
    val id: Int? = null,
    val keys: List<String> = emptyList(),
    @SerialName("secondary_keys") val secondaryKeys: List<String> = emptyList(),
    val comment: String = "",
    val name: String = "",
    val content: String = "",
    val constant: Boolean = false,
    val selective: Boolean = false,
    @SerialName("insertion_order") val insertionOrder: Int = 100,
    val enabled: Boolean = true,
    val position: String = "",
    val probability: Int = 100,
    @SerialName("scan_depth") val scanDepth: Int? = null,
    @SerialName("case_sensitive") val caseSensitive: Boolean = false
)

@Serializable
private data class WorldInfoFileEntry(
    val uid: Int = 0,
    val key: List<String> = emptyList(),
    @SerialName("keysecondary") val keySecondary: List<String> = emptyList(),
    val comment: String = "",
    val content: String = "",
    val constant: Boolean = false,
    val selective: Boolean = false,
    val order: Int = 100,
    val position: Int = 0,
    val depth: Int = 4,
    val probability: Int = 100,
    val enabled: Boolean = true,
    val group: String = "",
    @SerialName("scan_depth") val scanDepth: Int? = null,
    @SerialName("case_sensitive") val caseSensitive: Boolean = false,
    @SerialName("match_whole_words") val matchWholeWords: Boolean = false
)

@Singleton
class LoreBookStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
    }

    private val worldsDir: File
        get() = File(context.filesDir, "worlds").also { it.mkdirs() }

    /** List all lorebook names. */
    suspend fun listLorebooks(): List<String> = withContext(Dispatchers.IO) {
        worldsDir.listFiles { f -> f.extension == "json" }
            ?.map { it.nameWithoutExtension }
            ?.sorted()
            ?: emptyList()
    }

    /**
     * Load a lorebook and return its entries. Accepts both wild formats:
     * - ST world info: `entries` is an OBJECT keyed by uid
     * - V2 character-book export: `entries` is an ARRAY (chub/agnai/card tools)
     */
    suspend fun loadLorebook(name: String): List<WorldInfoEntry> = withContext(Dispatchers.IO) {
        val file = File(worldsDir, "$name.json")
        if (!file.exists()) return@withContext emptyList()
        try {
            val root = json.parseToJsonElement(file.readText()).jsonObject
            when (val entriesEl = root["entries"]) {
                is JsonObject -> json.decodeFromJsonElement<Map<String, WorldInfoFileEntry>>(entriesEl)
                    .values.map { entry ->
                        WorldInfoEntry(
                            uid = entry.uid.toString(),
                            key = entry.key,
                            keysecondary = entry.keySecondary,
                            content = entry.content,
                            comment = entry.comment,
                            constant = entry.constant,
                            selective = entry.selective,
                            order = entry.order,
                            position = entry.position,
                            depth = entry.depth,
                            probability = entry.probability,
                            enabled = entry.enabled,
                            group = entry.group,
                            scanDepth = entry.scanDepth,
                            caseSensitive = entry.caseSensitive,
                            matchWholeWords = entry.matchWholeWords
                        )
                    }
                is JsonArray -> json.decodeFromJsonElement<List<BookArrayEntry>>(entriesEl)
                    .mapIndexed { idx, entry ->
                        WorldInfoEntry(
                            uid = (entry.id ?: idx).toString(),
                            key = entry.keys,
                            keysecondary = entry.secondaryKeys,
                            content = entry.content,
                            comment = entry.comment.ifBlank { entry.name },
                            constant = entry.constant,
                            selective = entry.selective,
                            order = entry.insertionOrder,
                            position = if (entry.position == "after_char") 1 else 0,
                            depth = 4,
                            probability = entry.probability,
                            enabled = entry.enabled,
                            scanDepth = entry.scanDepth,
                            caseSensitive = entry.caseSensitive
                        )
                    }
                else -> {
                    DebugLogger.log("LoreBookStorage: $name has no usable entries field")
                    emptyList()
                }
            }
        } catch (e: Exception) {
            DebugLogger.logError("LoreBookStorage", "Failed to load $name", e)
            emptyList()
        }
    }

    /** Save a lorebook. */
    suspend fun saveLorebook(name: String, entries: List<WorldInfoEntry>) = withContext(Dispatchers.IO) {
        val fileEntries = entries.mapIndexed { idx, entry ->
            idx.toString() to WorldInfoFileEntry(
                uid = entry.uid.toIntOrNull() ?: idx,
                key = entry.key,
                keySecondary = entry.keysecondary,
                comment = entry.comment,
                content = entry.content,
                constant = entry.constant,
                selective = entry.selective,
                order = entry.order,
                position = entry.position,
                depth = entry.depth,
                probability = entry.probability,
                enabled = entry.enabled,
                group = entry.group,
                scanDepth = entry.scanDepth,
                caseSensitive = entry.caseSensitive,
                matchWholeWords = entry.matchWholeWords
            )
        }.toMap()

        val wif = WorldInfoFile(name = name, entries = fileEntries)
        File(worldsDir, "$name.json").writeText(json.encodeToString(wif))
    }

    /** Delete a lorebook. */
    suspend fun deleteLorebook(name: String) = withContext(Dispatchers.IO) {
        File(worldsDir, "$name.json").delete()
    }

    /** Save raw JSON bytes as a lorebook file (used by CharaVault importer). */
    suspend fun saveRawLorebook(name: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        File(worldsDir, "$name.json").writeBytes(bytes)
    }
}
