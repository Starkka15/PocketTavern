package com.pockettavern.app.data.local

import android.content.Context
import com.pockettavern.app.util.DebugLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Per-chat key-value store backed by a JSON sidecar file.
 *
 * File: {filesDir}/chats/{sanitizedCharacterName}/{chatFileName}.vars.json
 * Values stored as raw JSON strings internally so all JSON types round-trip cleanly.
 * Debounced 300 ms disk write to avoid excessive I/O.
 */
@Singleton
class VarsStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val store = mutableMapOf<String, String>()   // key → JSON-encoded value
    private var currentVarsFile: File? = null
    private var saveJob: Job? = null

    private val chatsDir get() = File(context.filesDir, "chats")

    /** Load vars for a chat. Call on chat switch (runs on caller's thread — must be IO). */
    @Synchronized
    fun loadForChat(characterName: String, chatFileName: String) {
        val dir = File(chatsDir, sanitize(characterName))
        val varsFile = File(dir, chatFileName.removeSuffix(".jsonl") + ".vars.json")
        currentVarsFile = varsFile
        store.clear()
        if (varsFile.exists()) {
            try {
                val json = JSONObject(varsFile.readText())
                for (key in json.keys()) {
                    store[key] = encodeValue(json.get(key))
                }
                DebugLogger.log("[VarsStorage] Loaded ${store.size} var(s) for $chatFileName")
            } catch (e: Exception) {
                DebugLogger.log("[VarsStorage] Load error: ${e.message}")
            }
        }
    }

    /** Delete the vars sidecar when a chat is deleted. */
    fun deleteForChat(characterName: String, chatFileName: String) {
        val dir = File(chatsDir, sanitize(characterName))
        val varsFile = File(dir, chatFileName.removeSuffix(".jsonl") + ".vars.json")
        varsFile.delete()
        synchronized(this) {
            if (currentVarsFile?.absolutePath == varsFile.absolutePath) {
                store.clear()
                currentVarsFile = null
            }
        }
        DebugLogger.log("[VarsStorage] Deleted vars for $chatFileName")
    }

    /** Returns JSON-encoded value for key, or "null" if missing. */
    @Synchronized fun get(key: String): String = store[key] ?: "null"

    /** Sets key to a JSON-encoded value and schedules a save. */
    @Synchronized fun set(key: String, valueJson: String) {
        store[key] = valueJson
        scheduleSave()
    }

    /** Removes a key and schedules a save. */
    @Synchronized fun delete(key: String) {
        store.remove(key)
        scheduleSave()
    }

    /** Returns all current vars as a JSON object string. */
    @Synchronized fun getAll(): String {
        if (store.isEmpty()) return "{}"
        val sb = StringBuilder("{")
        store.entries.forEachIndexed { i, (k, v) ->
            if (i > 0) sb.append(',')
            sb.append('"').append(k.replace("\\", "\\\\").replace("\"", "\\\"")).append('"')
            sb.append(':').append(v)
        }
        sb.append('}')
        return sb.toString()
    }

    /** Clears all vars for the current chat and schedules a save. */
    @Synchronized fun clear() {
        store.clear()
        scheduleSave()
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(300)
            doSave()
        }
    }

    private fun doSave() {
        val file = synchronized(this) { currentVarsFile } ?: return
        val json = synchronized(this) { getAll() }
        try {
            file.parentFile?.mkdirs()
            file.writeText(json)
        } catch (e: Exception) {
            DebugLogger.log("[VarsStorage] Save error: ${e.message}")
        }
    }

    private fun encodeValue(v: Any): String = when (v) {
        is String  -> JSONObject.quote(v)
        is Boolean -> v.toString()
        is Int, is Long -> v.toString()
        is Double, is Float -> v.toString()
        is org.json.JSONArray -> v.toString()
        is org.json.JSONObject -> v.toString()
        org.json.JSONObject.NULL -> "null"
        else -> JSONObject.quote(v.toString())
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
}
