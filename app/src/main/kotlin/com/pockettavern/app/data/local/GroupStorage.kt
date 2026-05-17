package com.pockettavern.app.data.local

import android.content.Context
import com.pockettavern.app.domain.model.Group
import com.pockettavern.app.domain.model.GroupChatMessage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GroupStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private val groupsDir: File
        get() = File(context.filesDir, "groups").also { it.mkdirs() }

    private val groupsFile: File
        get() = File(groupsDir, "groups.json")

    suspend fun loadGroups(): List<Group> = withContext(Dispatchers.IO) {
        if (!groupsFile.exists()) return@withContext emptyList()
        try {
            json.decodeFromString<List<Group>>(groupsFile.readText())
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun saveGroups(groups: List<Group>) = withContext(Dispatchers.IO) {
        groupsFile.writeText(json.encodeToString(groups))
    }

    suspend fun saveGroup(group: Group) = withContext(Dispatchers.IO) {
        val groups = loadGroups().toMutableList()
        val idx = groups.indexOfFirst { it.id == group.id }
        if (idx >= 0) groups[idx] = group else groups.add(group)
        saveGroups(groups)
    }

    suspend fun deleteGroup(groupId: String) = withContext(Dispatchers.IO) {
        val groups = loadGroups().filter { it.id != groupId }
        saveGroups(groups)
        // Delete chat files
        val chatDir = File(groupsDir, groupId)
        chatDir.deleteRecursively()
    }

    suspend fun loadMessages(groupId: String): List<GroupChatMessage> = withContext(Dispatchers.IO) {
        val file = File(groupsDir, "$groupId/messages.jsonl")
        if (!file.exists()) return@withContext emptyList()
        file.readLines()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                try { json.decodeFromString<GroupChatMessage>(line) }
                catch (_: Exception) { null }
            }
    }

    suspend fun appendMessage(groupId: String, message: GroupChatMessage) = withContext(Dispatchers.IO) {
        val dir = File(groupsDir, groupId).also { it.mkdirs() }
        val file = File(dir, "messages.jsonl")
        file.appendText(json.encodeToString(message) + "\n")
    }

    suspend fun saveMessages(groupId: String, messages: List<GroupChatMessage>) = withContext(Dispatchers.IO) {
        val dir = File(groupsDir, groupId).also { it.mkdirs() }
        val file = File(dir, "messages.jsonl")
        file.writeText(messages.joinToString("\n") { json.encodeToString(it) } +
            if (messages.isNotEmpty()) "\n" else "")
    }
}
