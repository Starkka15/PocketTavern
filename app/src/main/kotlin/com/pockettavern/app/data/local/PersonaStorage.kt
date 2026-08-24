package com.pockettavern.app.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One saved user persona. The ACTIVE persona is still mirrored into
 * SettingsDataStore's UserPersona fields on selection, so the entire prompt
 * path (loadChatContext, group chats, {{user}} macros) is untouched — this
 * store only owns the roster and which entry is selected.
 */
@Serializable
data class StoredPersona(
    val id: String,
    val name: String,
    val description: String = "",
    val position: Int = 0,
    val depth: Int = 2,
    val role: Int = 0,
    val avatarPath: String? = null
)

@Serializable
private data class PersonaRoster(
    val personas: List<StoredPersona> = emptyList(),
    val selectedId: String? = null
)

@Singleton
class PersonaStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val file: File get() = File(context.filesDir, "personas.json")

    suspend fun load(): Pair<List<StoredPersona>, String?> = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext emptyList<StoredPersona>() to null
        try {
            val roster = json.decodeFromString<PersonaRoster>(file.readText())
            roster.personas to roster.selectedId
        } catch (_: Exception) {
            emptyList<StoredPersona>() to null
        }
    }

    suspend fun save(personas: List<StoredPersona>, selectedId: String?) = withContext(Dispatchers.IO) {
        file.writeText(json.encodeToString(PersonaRoster(personas, selectedId)))
    }

    /** Per-persona avatar file (the old single-slot code shared one file for all). */
    fun avatarFile(id: String): File = File(context.filesDir, "persona_avatar_$id.png")
}
