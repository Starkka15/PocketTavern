package com.pockettavern.app.data.local

import android.content.Context
import com.pockettavern.app.domain.model.ChatInfo
import com.pockettavern.app.domain.model.Story
import com.pockettavern.app.domain.model.StoryCardRef
import com.pockettavern.app.domain.model.StoryMessage
import com.pockettavern.app.util.PngStoryCard
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * T4 — persistence for imported Story cards (import + run only; never authors — V11). Mirrors
 * [GroupStorage]. Layout under filesDir:
 *   stories/index.json                 → List<StoryCardRef>
 *   stories/<id>/story.json            → the Story
 *   stories/<id>/cover.png             → cover art (when imported from PNG)
 *   stories/<id>/chats/<name>.jsonl    → StoryMessage per line
 */
@Singleton
class StoryStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private val root: File get() = File(context.filesDir, "stories").also { it.mkdirs() }
    private val indexFile: File get() = File(root, "index.json")
    private fun storyDir(id: String): File = File(root, id).also { it.mkdirs() }
    private fun storyFile(id: String): File = File(storyDir(id), "story.json")
    private fun chatsDir(id: String): File = File(storyDir(id), "chats").also { it.mkdirs() }
    private fun chatFile(id: String, name: String): File = File(chatsDir(id), "$name.jsonl")

    // ── catalog ────────────────────────────────────────────────────────────

    /** True iff at least one Story card is imported — gates the Stories tab (V12). */
    suspend fun hasAny(): Boolean = withContext(Dispatchers.IO) { loadIndex().isNotEmpty() }

    suspend fun loadIndex(): List<StoryCardRef> = withContext(Dispatchers.IO) {
        if (!indexFile.exists()) return@withContext emptyList()
        try { json.decodeFromString<List<StoryCardRef>>(indexFile.readText()) }
        catch (_: Exception) { emptyList() }
    }

    private suspend fun saveIndex(refs: List<StoryCardRef>) = withContext(Dispatchers.IO) {
        indexFile.writeText(json.encodeToString(refs))
    }

    suspend fun loadStory(id: String): Story? = withContext(Dispatchers.IO) {
        val f = storyFile(id)
        if (!f.exists()) return@withContext null
        try { json.decodeFromString(Story.serializer(), f.readText()) } catch (_: Exception) { null }
    }

    fun coverFileFor(id: String): File? = File(storyDir(id), "cover.png").takeIf { it.exists() }

    // ── import (V11: read pre-made cards only) ──────────────────────────────

    /** Import from a Story-card PNG (reads the `ptensemble` chunk + keeps the image as cover). */
    suspend fun importFromPng(pngBytes: ByteArray): StoryCardRef? = withContext(Dispatchers.IO) {
        val story = PngStoryCard.extractStory(pngBytes) ?: return@withContext null
        val ref = persist(story)
        File(storyDir(ref.id), "cover.png").writeBytes(pngBytes)   // whole PNG = the cover
        ref.copy(cover = "cover.png").also { upsertRef(it) }
    }

    /** Import from raw Story JSON (no cover). */
    suspend fun importFromJson(text: String): StoryCardRef? = withContext(Dispatchers.IO) {
        val story = PngStoryCard.parseJson(text) ?: return@withContext null
        persist(story)
    }

    private suspend fun persist(story: Story): StoryCardRef {
        val id = UUID.randomUUID().toString()
        storyFile(id).writeText(json.encodeToString(Story.serializer(), story))
        val ref = StoryCardRef(
            id = id, name = story.name, creator = story.creator, tags = story.tags,
            cover = null, castCount = story.cast.size, importedAt = System.currentTimeMillis()
        )
        upsertRef(ref)
        return ref
    }

    private suspend fun upsertRef(ref: StoryCardRef) {
        val refs = loadIndex().toMutableList()
        val i = refs.indexOfFirst { it.id == ref.id }
        if (i >= 0) refs[i] = ref else refs.add(ref)
        saveIndex(refs)
    }

    suspend fun deleteStory(id: String) = withContext(Dispatchers.IO) {
        saveIndex(loadIndex().filter { it.id != id })
        storyDir(id).deleteRecursively()
    }

    // ── chats ───────────────────────────────────────────────────────────────

    suspend fun listChats(id: String): List<ChatInfo> = withContext(Dispatchers.IO) {
        chatsDir(id).listFiles { f -> f.extension == "jsonl" }
            ?.sortedByDescending { it.lastModified() }
            ?.map { f ->
                val lines = f.readLines().filter { it.isNotBlank() }
                val last = lines.lastOrNull()?.let {
                    try { json.decodeFromString<StoryMessage>(it).content } catch (_: Exception) { null }
                }
                ChatInfo(f.nameWithoutExtension, last, lines.size, f.lastModified())
            } ?: emptyList()
    }

    suspend fun createChat(id: String): String = withContext(Dispatchers.IO) {
        val name = "chat_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}"
        chatFile(id, name).also { it.parentFile?.mkdirs(); it.createNewFile() }
        name
    }

    suspend fun loadMessages(id: String, chat: String): List<StoryMessage> = withContext(Dispatchers.IO) {
        val f = chatFile(id, chat)
        if (!f.exists()) return@withContext emptyList()
        f.readLines().filter { it.isNotBlank() }
            .mapNotNull { try { json.decodeFromString<StoryMessage>(it) } catch (_: Exception) { null } }
    }

    suspend fun appendMessage(id: String, chat: String, m: StoryMessage) = withContext(Dispatchers.IO) {
        chatFile(id, chat).apply { parentFile?.mkdirs() }.appendText(json.encodeToString(m) + "\n")
    }

    suspend fun saveMessages(id: String, chat: String, msgs: List<StoryMessage>) = withContext(Dispatchers.IO) {
        chatFile(id, chat).apply { parentFile?.mkdirs() }
            .writeText(msgs.joinToString("") { json.encodeToString(it) + "\n" })
    }

    suspend fun deleteChat(id: String, chat: String) = withContext(Dispatchers.IO) {
        chatFile(id, chat).delete()
    }
}
