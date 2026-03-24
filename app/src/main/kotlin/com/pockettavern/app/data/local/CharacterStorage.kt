package com.pockettavern.app.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.pockettavern.app.data.local.db.dao.CharacterDao
import com.pockettavern.app.data.local.db.entity.CharacterEntity
import com.pockettavern.app.domain.model.Character
import com.pockettavern.app.util.PngCharacterCard
import com.pockettavern.app.util.CharacterCardData
import com.pockettavern.app.util.CharacterCardV2
import com.pockettavern.app.util.DebugLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CharacterStorage @Inject constructor(
    @ApplicationContext private val context: Context,
    private val characterDao: CharacterDao
) {
    private val charactersDir: File
        get() = File(context.filesDir, "characters").also { it.mkdirs() }

    /** List all characters by scanning the directory and reading PNG metadata. */
    suspend fun listCharacters(): List<Character> = withContext(Dispatchers.IO) {
        val files = charactersDir.listFiles { f -> f.extension == "png" } ?: emptyArray()
        files.mapNotNull { file -> readCharacterFile(file) }
            .sortedBy { it.name }
    }

    /** Read a single character card from fileName (e.g. "seraphina.png"). */
    suspend fun getCharacter(fileName: String): Character? = withContext(Dispatchers.IO) {
        readCharacterFile(File(charactersDir, fileName))
    }

    /** Save a character card PNG with embedded metadata. Returns the saved file name. */
    suspend fun saveCharacter(
        character: Character,
        avatarBitmap: Bitmap?,
        fileName: String? = null
    ): String = withContext(Dispatchers.IO) {
        val safeFileName = fileName ?: sanitizeFileName(character.name) + ".png"
        val file = File(charactersDir, safeFileName)

        // Prepare PNG bytes (either from existing file, provided bitmap, or blank 100x100)
        val basePngBytes = when {
            file.exists() -> file.readBytes()
            avatarBitmap != null -> bitmapToPng(avatarBitmap)
            else -> defaultAvatarPng()
        }

        val cardData = CharacterCardV2(
            data = CharacterCardData(
                name = character.name,
                description = character.description,
                personality = character.personality,
                scenario = character.scenario,
                firstMes = character.firstMessage,
                mesExample = character.messageExample,
                creatorNotes = character.creatorNotes,
                systemPrompt = character.systemPrompt,
                postHistoryInstructions = character.postHistoryInstructions,
                alternateGreetings = character.alternateGreetings,
                tags = character.tags
            )
        )

        val pngBytes = PngCharacterCard.embedCharacterData(basePngBytes, cardData)
        file.writeBytes(pngBytes)

        // Update Room index
        characterDao.upsert(
            CharacterEntity(
                fileName = safeFileName,
                name = character.name,
                tags = character.tags.joinToString(","),
                isFavorite = character.isFavorite,
                hasCharacterBook = character.hasCharacterBook,
                useAvatarForImageGen = character.useAvatarForImageGen
            )
        )

        DebugLogger.log("CharacterStorage: saved $safeFileName")
        safeFileName
    }

    /** Import a character PNG from a content URI. Returns the saved file name. */
    suspend fun importCharacterCard(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.readBytes() ?: return@withContext null
            val card = PngCharacterCard.extractCharacterData(bytes) ?: return@withContext null
            val name = card.data.name.ifBlank { "imported_character" }
            val safeFileName = sanitizeFileName(name) + ".png"
            val file = File(charactersDir, safeFileName)
            file.writeBytes(bytes)

            characterDao.upsert(
                CharacterEntity(
                    fileName = safeFileName,
                    name = card.data.name,
                    tags = card.data.tags.joinToString(","),
                    hasCharacterBook = card.data.characterBook != null
                )
            )
            DebugLogger.log("CharacterStorage: imported $safeFileName")
            safeFileName
        } catch (e: Exception) {
            DebugLogger.logError("CharacterStorage", "importCharacterCard failed", e)
            null
        }
    }

    /** Delete a character card and remove from Room index. */
    suspend fun deleteCharacter(fileName: String) = withContext(Dispatchers.IO) {
        File(charactersDir, fileName).delete()
        characterDao.deleteByFileName(fileName)
    }

    /** Get the raw PNG bytes for a character (for export/sharing). */
    suspend fun getCharacterBytes(fileName: String): ByteArray? = withContext(Dispatchers.IO) {
        val file = File(charactersDir, fileName)
        if (file.exists()) file.readBytes() else null
    }

    /** Save raw PNG bytes to the characters directory (used by CharaVault/Chub importers). */
    suspend fun saveRawPng(bytes: ByteArray, fileName: String): String = withContext(Dispatchers.IO) {
        val safeFileName = if (fileName.endsWith(".png")) fileName else "$fileName.png"
        val file = File(charactersDir, safeFileName)
        file.writeBytes(bytes)
        // Try to read card metadata and update index
        val card = try { PngCharacterCard.extractCharacterData(bytes) } catch (e: Exception) { null }
        characterDao.upsert(
            CharacterEntity(
                fileName = safeFileName,
                name = card?.data?.name ?: file.nameWithoutExtension,
                tags = card?.data?.tags?.joinToString(",") ?: "",
                hasCharacterBook = card?.data?.characterBook != null
            )
        )
        safeFileName
    }

    /** Build local file:// URI for a character's avatar PNG. */
    fun getAvatarUri(fileName: String): Uri =
        Uri.fromFile(File(charactersDir, fileName))

    /** Rebuild the Room index from disk (call once on first launch or after manual edits). */
    suspend fun rebuildIndex() = withContext(Dispatchers.IO) {
        val files = charactersDir.listFiles { f -> f.extension == "png" } ?: emptyArray()
        val entities = files.mapNotNull { file ->
            val card = try { PngCharacterCard.extractCharacterData(file.readBytes()) } catch (e: Exception) { null }
            val name = card?.data?.name ?: file.nameWithoutExtension
            CharacterEntity(
                fileName = file.name,
                name = name,
                tags = card?.data?.tags?.joinToString(",") ?: "",
                hasCharacterBook = card?.data?.characterBook != null
            )
        }
        characterDao.deleteAll()
        characterDao.upsertAll(entities)
        DebugLogger.log("CharacterStorage: rebuilt index with ${entities.size} characters")
    }

    private fun readCharacterFile(file: File): Character? {
        if (!file.exists()) return null
        return try {
            val bytes = file.readBytes()
            val card = PngCharacterCard.extractCharacterData(bytes)
            val data = card?.data
            val extensions = data?.extensions
            Character(
                name = data?.name ?: file.nameWithoutExtension,
                avatar = file.name, // local file name used as avatar key
                description = data?.description ?: "",
                personality = data?.personality ?: "",
                scenario = data?.scenario ?: "",
                firstMessage = data?.firstMes ?: "",
                messageExample = data?.mesExample ?: "",
                creatorNotes = data?.creatorNotes ?: "",
                systemPrompt = data?.systemPrompt ?: "",
                postHistoryInstructions = data?.postHistoryInstructions ?: "",
                alternateGreetings = data?.alternateGreetings ?: emptyList(),
                tags = data?.tags ?: emptyList(),
                hasCharacterBook = data?.characterBook != null,
                characterBookEntryCount = data?.characterBook?.entries?.size ?: 0
            )
        } catch (e: Exception) {
            DebugLogger.logError("CharacterStorage", "Failed to read ${file.name}", e)
            null
        }
    }

    private fun bitmapToPng(bitmap: Bitmap): ByteArray {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        return out.toByteArray()
    }

    private fun defaultAvatarPng(): ByteArray {
        // 1x1 transparent PNG
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        return bitmapToPng(bitmap)
    }

    private fun sanitizeFileName(name: String): String =
        name.replace(Regex("[^a-zA-Z0-9_\\-. ]"), "_").trim().take(64)
}
