package com.pockettavern.app.data.local

import android.content.Context
import com.pockettavern.app.domain.model.ChatInfo
import com.pockettavern.app.domain.model.Group
import com.pockettavern.app.domain.model.GroupChatMessage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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

    private fun chatsDir(groupId: String): File =
        File(groupsDir, "$groupId/chats").also { it.mkdirs() }

    private fun chatFile(groupId: String, chatFileName: String): File =
        File(chatsDir(groupId), "$chatFileName.jsonl")

    // ── Groups ────────────────────────────────────────────────────────────────

    suspend fun loadGroups(): List<Group> = withContext(Dispatchers.IO) {
        if (!groupsFile.exists()) return@withContext emptyList()
        try { json.decodeFromString<List<Group>>(groupsFile.readText()) }
        catch (_: Exception) { emptyList() }
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
        saveGroups(loadGroups().filter { it.id != groupId })
        File(groupsDir, groupId).deleteRecursively()
    }

    // ── Chat list ─────────────────────────────────────────────────────────────

    /** Returns chats sorted newest-first. Migrates legacy messages.jsonl if present. */
    suspend fun listChats(groupId: String): List<ChatInfo> = withContext(Dispatchers.IO) {
        migrateLegacyIfNeeded(groupId)
        chatsDir(groupId)
            .listFiles { f -> f.extension == "jsonl" }
            ?.sortedByDescending { it.lastModified() }
            ?.map { f ->
                val lines = f.readLines().filter { it.isNotBlank() }
                val lastMsg = lines.lastOrNull()?.let {
                    try { json.decodeFromString<GroupChatMessage>(it).content } catch (_: Exception) { null }
                }
                ChatInfo(
                    fileName = f.nameWithoutExtension,
                    lastMessage = lastMsg,
                    messageCount = lines.size,
                    lastModified = f.lastModified()
                )
            } ?: emptyList()
    }

    /** Creates a new empty chat file and returns its filename (without extension). */
    suspend fun createNewChat(groupId: String): String = withContext(Dispatchers.IO) {
        migrateLegacyIfNeeded(groupId)
        val fmt = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        val fileName = "chat_${fmt.format(Date())}"
        chatFile(groupId, fileName).also { it.parentFile?.mkdirs(); it.createNewFile() }
        fileName
    }

    suspend fun deleteChat(groupId: String, chatFileName: String) = withContext(Dispatchers.IO) {
        chatFile(groupId, chatFileName).delete()
    }

    // ── Messages ──────────────────────────────────────────────────────────────

    suspend fun loadMessages(groupId: String, chatFileName: String): List<GroupChatMessage> =
        withContext(Dispatchers.IO) {
            val file = chatFile(groupId, chatFileName)
            if (!file.exists()) return@withContext emptyList()
            file.readLines()
                .filter { it.isNotBlank() }
                .mapNotNull { line ->
                    try { json.decodeFromString<GroupChatMessage>(line) } catch (_: Exception) { null }
                }
        }

    suspend fun appendMessage(
        groupId: String,
        chatFileName: String,
        message: GroupChatMessage
    ) = withContext(Dispatchers.IO) {
        val file = chatFile(groupId, chatFileName)
        file.parentFile?.mkdirs()
        file.appendText(json.encodeToString(message) + "\n")
    }

    suspend fun saveMessages(
        groupId: String,
        chatFileName: String,
        messages: List<GroupChatMessage>
    ) = withContext(Dispatchers.IO) {
        val file = chatFile(groupId, chatFileName)
        file.parentFile?.mkdirs()
        file.writeText(
            messages.joinToString("\n") { json.encodeToString(it) } +
                if (messages.isNotEmpty()) "\n" else ""
        )
    }

    // ── Migration ─────────────────────────────────────────────────────────────

    private fun migrateLegacyIfNeeded(groupId: String) {
        val legacy = File(groupsDir, "$groupId/messages.jsonl")
        if (!legacy.exists()) return
        val dir = chatsDir(groupId)
        val existing = dir.listFiles { f -> f.extension == "jsonl" }
        if (!existing.isNullOrEmpty()) {
            legacy.delete()
            return
        }
        File(dir, "chat_imported.jsonl").also { legacy.copyTo(it, overwrite = true) }
        legacy.delete()
    }
}
