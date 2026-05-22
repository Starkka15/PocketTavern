package com.pockettavern.app.data.local

import android.content.Context
import com.pockettavern.app.data.local.db.dao.ChatDao
import com.pockettavern.app.data.local.db.entity.ChatEntity
import com.pockettavern.app.domain.model.Chat
import com.pockettavern.app.domain.model.ChatInfo
import com.pockettavern.app.domain.model.ChatMessage
import com.pockettavern.app.domain.model.ChatMessageMetadata
import com.pockettavern.app.domain.model.MessageHeaderEntry
import com.pockettavern.app.util.DebugLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.contentOrNull
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ST-compatible JSONL chat file format.
 *
 * Line 0: ChatHeader (metadata)
 * Line 1+: ChatLine (individual messages)
 */
@Serializable
private data class ChatHeader(
    @SerialName("user_name") val userName: String = "User",
    @SerialName("character_name") val characterName: String = "",
    @SerialName("create_date") val createDate: String = "",
    val chat: List<JsonElement> = emptyList() // unused in file, kept for compat
)

@Serializable
private data class ChatLine(
    val name: String = "",
    @SerialName("is_user") val isUser: Boolean = false,
    @SerialName("is_system") val isSystem: Boolean = false,
    val send_date: String = "",
    val mes: String = "",
    val extra: JsonObject = JsonObject(emptyMap())
)

@Singleton
class ChatStorage @Inject constructor(
    @ApplicationContext private val context: Context,
    private val chatDao: ChatDao
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
    }

    private val chatsDir: File
        get() = File(context.filesDir, "chats").also { it.mkdirs() }

    private fun characterDir(characterName: String): File =
        File(chatsDir, sanitizeFileName(characterName)).also { it.mkdirs() }

    /** List all chat files for a character. Returns lightweight ChatInfo sorted by last-modified. */
    suspend fun listChats(characterName: String): List<ChatInfo> = withContext(Dispatchers.IO) {
        val dir = characterDir(characterName)
        val files = dir.listFiles { f -> f.extension == "jsonl" } ?: emptyArray()
        files.sortedByDescending { it.lastModified() }.map { file ->
            val (count, lastMsg) = readChatSummary(file)
            ChatInfo(
                fileName = file.name,
                lastMessage = lastMsg,
                messageCount = count,
                lastModified = file.lastModified()
            )
        }
    }

    /** Read message count and last message text in one pass without loading the full chat. */
    private fun readChatSummary(file: File): Pair<Int, String?> {
        return try {
            val lines = file.readLines().filter { it.isNotBlank() }
            val messageLines = lines.drop(1) // drop header
            val lastMsg = messageLines.lastOrNull()?.let { line ->
                try { json.decodeFromString<ChatLine>(line).mes.trim().ifBlank { null } }
                catch (e: Exception) { null }
            }
            Pair(messageLines.size, lastMsg)
        } catch (e: Exception) {
            Pair(0, null)
        }
    }

    /** Load a full chat from a JSONL file. Memory fields loaded from Room. */
    suspend fun loadChat(characterName: String, fileName: String): Chat? = withContext(Dispatchers.IO) {
        val file = File(characterDir(characterName), fileName)
        if (!file.exists()) return@withContext null
        val chat = parseChat(file, characterName) ?: return@withContext null
        val entity = chatDao.getByFileName(fileName)
        chat.copy(
            memoryBlock = entity?.memoryBlock ?: "",
            summarizedTurnCount = entity?.summarizedTurnCount ?: 0
        )
    }

    /** Persist the memory summary for a chat without rewriting the JSONL file. */
    suspend fun updateMemoryBlock(characterName: String, fileName: String, block: String, count: Int) =
        withContext(Dispatchers.IO) {
            chatDao.updateMemoryBlock(fileName, block, count)
            DebugLogger.log("ChatStorage: memoryBlock updated for $fileName (${block.length} chars, $count turns summarized)")
        }

    /** Save a chat to JSONL format. Creates file if it doesn't exist. */
    suspend fun saveChat(chat: Chat, userName: String = "User"): String = withContext(Dispatchers.IO) {
        val fileName = chat.fileName.ifBlank { generateFileName(chat.characterName) }
        val file = File(characterDir(chat.characterName), fileName)

        val lines = buildList {
            // Line 0: header
            val header = ChatHeader(
                userName = userName,
                characterName = chat.characterName,
                createDate = formatDate(chat.createDate)
            )
            add(json.encodeToString(header))

            // Lines 1+: messages
            for (message in chat.messages) {
                val line = ChatLine(
                    name = when {
                        message.isUser -> userName
                        message.isNarrator -> "narrator"
                        else -> chat.characterName
                    },
                    isUser = message.isUser,
                    isSystem = message.isNarrator,
                    send_date = formatDate(message.timestamp),
                    mes = message.content,
                    extra = buildExtra(message)
                )
                add(json.encodeToString(line))
            }
        }

        file.writeText(lines.joinToString("\n"))

        // Update Room index
        chatDao.upsert(
            ChatEntity(
                fileName = fileName,
                characterName = chat.characterName,
                createDate = chat.createDate.toEpochMilli(),
                modifyDate = System.currentTimeMillis(),
                messageCount = chat.messages.size
            )
        )

        fileName
    }

    /** Delete a chat file and remove from Room index. */
    suspend fun deleteChat(characterName: String, fileName: String) = withContext(Dispatchers.IO) {
        File(characterDir(characterName), fileName).delete()
        chatDao.deleteByFileName(fileName)
    }

    /** Append a single message to an existing chat (efficient for real-time saving). */
    suspend fun appendMessage(
        characterName: String,
        fileName: String,
        message: ChatMessage,
        userName: String = "User"
    ) = withContext(Dispatchers.IO) {
        val file = File(characterDir(characterName), fileName)
        if (!file.exists()) return@withContext

        val line = ChatLine(
            name = when {
                message.isUser -> userName
                message.isNarrator -> "narrator"
                else -> characterName
            },
            isUser = message.isUser,
            isSystem = message.isNarrator,
            send_date = formatDate(message.timestamp),
            mes = message.content,
            extra = buildExtra(message)
        )
        file.appendText("\n" + json.encodeToString(line))

        chatDao.updateStats(
            fileName = fileName,
            count = readChatSummary(file).first,
            modifyDate = System.currentTimeMillis()
        )
    }

    /** Generate a new chat file name in ST format. */
    fun generateFileName(characterName: String): String {
        val now = LocalDateTime.now()
        val formatted = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd @HH'h'mm'm'ss's'"))
        return "${sanitizeFileName(characterName)} - $formatted.jsonl"
    }

    private fun parseChat(file: File, characterName: String): Chat? {
        return try {
            val lines = file.readLines().filter { it.isNotBlank() }
            if (lines.isEmpty()) return null

            // First line is header
            val header = try { json.decodeFromString<ChatHeader>(lines[0]) } catch (e: Exception) { ChatHeader() }
            val createDate = parseDate(header.createDate) ?: Instant.ofEpochMilli(file.lastModified())

            // Remaining lines are messages
            val messages = lines.drop(1).mapNotNull { line ->
                try {
                    val chatLine = json.decodeFromString<ChatLine>(line)
                    val extra = chatLine.extra
                    ChatMessage(
                        id = UUID.randomUUID().toString(),
                        content = chatLine.mes,
                        isUser = chatLine.isUser,
                        isNarrator = chatLine.isSystem,
                        timestamp = parseDate(chatLine.send_date) ?: Instant.now(),
                        senderName = if (!chatLine.isUser && !chatLine.isSystem) chatLine.name else null,
                        rawContent = extra["raw_content"]?.jsonPrimitive?.contentOrNull,
                        extensionHeaders = parseExtensionHeaders(extra),
                        imagePath = extra["image_path"]?.jsonPrimitive?.contentOrNull
                    )
                } catch (e: Exception) { null }
            }

            Chat(
                fileName = file.name,
                characterName = characterName,
                messages = messages,
                createDate = createDate
            )
        } catch (e: Exception) {
            DebugLogger.logError("ChatStorage", "Failed to parse ${file.name}", e)
            null
        }
    }


    private fun buildExtra(message: ChatMessage): JsonObject {
        val map = mutableMapOf<String, JsonElement>()
        if (message.integritySlug != null) {
            map["slug"] = JsonPrimitive(message.integritySlug)
        }
        if (message.rawContent != null) {
            map["raw_content"] = JsonPrimitive(message.rawContent)
        }
        if (message.imagePath != null) {
            map["image_path"] = JsonPrimitive(message.imagePath)
        }
        if (message.extensionHeaders.isNotEmpty()) {
            val headersArray = message.extensionHeaders.map { entry ->
                val fields = mutableMapOf<String, JsonElement>(
                    "text" to JsonPrimitive(entry.text),
                    "ext" to JsonPrimitive(entry.extensionId)
                )
                if (entry.collapsibleText.isNotBlank()) {
                    fields["collapsible"] = JsonPrimitive(entry.collapsibleText)
                }
                JsonObject(fields)
            }
            map["extension_headers"] = kotlinx.serialization.json.JsonArray(headersArray)
        }
        return JsonObject(map)
    }

    private fun formatDate(instant: Instant): String {
        val ldt = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
        return ldt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
    }

    private fun parseDate(dateStr: String): Instant? {
        if (dateStr.isBlank()) return null
        return try {
            val ldt = LocalDateTime.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            ldt.atZone(ZoneId.systemDefault()).toInstant()
        } catch (e: Exception) { null }
    }

    private fun sanitizeFileName(name: String): String =
        name.replace(Regex("[^a-zA-Z0-9_\\-. ]"), "_").trim().take(64)

    /** Parse extension headers from extra JSON, with backwards compat for old single-header format. */
    private fun parseExtensionHeaders(extra: JsonObject): List<MessageHeaderEntry> {
        // New format: "extension_headers" is a JSON array of { text, ext }
        val arr = extra["extension_headers"]
        if (arr is kotlinx.serialization.json.JsonArray) {
            return arr.mapNotNull { elem ->
                try {
                    val obj = elem.jsonObject
                    val text = obj["text"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val ext = obj["ext"]?.jsonPrimitive?.contentOrNull ?: ""
                    val collapsible = obj["collapsible"]?.jsonPrimitive?.contentOrNull ?: ""
                    MessageHeaderEntry(text = text, extensionId = ext, collapsibleText = collapsible)
                } catch (e: Exception) { null }
            }
        }
        // Backwards compat: old format had "extension_header" + "extension_header_owner"
        val oldHeader = extra["extension_header"]?.jsonPrimitive?.contentOrNull
        if (!oldHeader.isNullOrBlank()) {
            val oldOwner = extra["extension_header_owner"]?.jsonPrimitive?.contentOrNull ?: ""
            return listOf(MessageHeaderEntry(text = oldHeader, extensionId = oldOwner))
        }
        return emptyList()
    }
}
