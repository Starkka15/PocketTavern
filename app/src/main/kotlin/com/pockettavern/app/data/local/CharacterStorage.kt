package com.pockettavern.app.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.pockettavern.app.data.local.db.dao.CharacterDao
import com.pockettavern.app.data.local.db.entity.CharacterEntity
import com.pockettavern.app.domain.model.Character
import com.pockettavern.app.util.CharxParser
import com.pockettavern.app.util.PngCharacterCard
import com.pockettavern.app.util.CharacterCardData
import com.pockettavern.app.util.CharacterCardV2
import com.pockettavern.app.util.DebugLogger
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

data class CardExtensionMeta(
    val characterFile: String,
    val characterName: String,
    val scriptName: String,
    val scriptVersion: String,
    val scriptDescription: String
)

@Singleton
class CharacterStorage @Inject constructor(
    @ApplicationContext private val context: Context,
    private val characterDao: CharacterDao,
    private val spriteStorage: SpriteStorage
) {
    private val charactersDir: File
        get() = File(context.filesDir, "characters").also { it.mkdirs() }

    /** List all characters from Room cache. Auto-rebuilds on first run if DB is empty. */
    suspend fun listCharacters(): List<Character> = withContext(Dispatchers.IO) {
        var entities = characterDao.getAll()
        if (entities.isEmpty()) {
            val files = charactersDir.listFiles { f -> f.extension == "png" }
            if (!files.isNullOrEmpty()) {
                DebugLogger.log("CharacterStorage: DB empty, rebuilding index from ${files.size} PNG files")
                rebuildIndex()
                entities = characterDao.getAll()
            }
        }
        entities.map { entityToCharacter(it) }
    }

    /** Read a single character from Room cache. Falls back to PNG if not indexed. */
    suspend fun getCharacter(fileName: String): Character? = withContext(Dispatchers.IO) {
        val entity = characterDao.getByFileName(fileName)
        if (entity != null) {
            entityToCharacter(entity)
        } else {
            val character = readCharacterFile(File(charactersDir, fileName)) ?: return@withContext null
            characterDao.upsert(characterToEntity(character))
            character
        }
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

        val extensions = buildMap<String, kotlinx.serialization.json.JsonElement> {
            if (character.loreHints.isNotBlank())
                put("pockettavern_lore_hints", JsonPrimitive(character.loreHints))
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
                tags = character.tags,
                extensions = extensions
            )
        )

        val pngBytes = PngCharacterCard.embedCharacterData(basePngBytes, cardData)
        file.writeBytes(pngBytes)

        // Update Room cache
        characterDao.upsert(characterToEntity(character.copy(avatar = safeFileName), safeFileName))

        DebugLogger.log("CharacterStorage: saved $safeFileName")
        safeFileName
    }

    /** Import a character card from a content URI. Handles both PNG and .charx formats. Throws on failure. */
    suspend fun importCharacterCard(uri: Uri): String = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.readBytes()
            ?: throw Exception("Cannot read file — URI not accessible")
        DebugLogger.log("CharacterStorage: read ${bytes.size} bytes, isPng=${isPng(bytes)}")
        val isCharx = !isPng(bytes) && CharxParser.findZipOffset(bytes) != null
        DebugLogger.log("CharacterStorage: isCharx=$isCharx")
        if (isCharx) importCharx(bytes) else importPng(bytes)
            ?: throw Exception("PNG has no embedded character data")
    }

    /** Import a character card from raw bytes (e.g. fetched from a URL). Same logic as importCharacterCard. */
    suspend fun importCharacterCardBytes(bytes: ByteArray): String = withContext(Dispatchers.IO) {
        DebugLogger.log("CharacterStorage: importBytes ${bytes.size} bytes, isPng=${isPng(bytes)}")
        val isCharx = !isPng(bytes) && CharxParser.findZipOffset(bytes) != null
        DebugLogger.log("CharacterStorage: isCharx=$isCharx")
        if (isCharx) importCharx(bytes) else importPng(bytes)
            ?: throw Exception("PNG has no embedded character data")
    }

    /** Import a character card from a File. Uses streaming for charx to avoid OOM on large files. */
    suspend fun importCharacterCardFile(file: File): String = withContext(Dispatchers.IO) {
        DebugLogger.log("CharacterStorage: importFile ${file.name} (${file.length()} bytes)")
        val header = ByteArray(8).also { buf ->
            file.inputStream().use { it.read(buf) }
        }
        if (isPng(header)) {
            importPng(file.readBytes()) ?: throw Exception("PNG has no embedded character data")
        } else {
            importCharxFile(file)
        }
    }

    private suspend fun importCharxFile(file: File): String {
        DebugLogger.log("CharacterStorage: streaming charx from file (${file.length()} bytes)")
        val tempSpriteDir = File(context.cacheDir, "charx_sprites_${System.currentTimeMillis()}")
        val result = CharxParser.parseFile(file, tempSpriteDir)
        val name = result.cardData.data.name.ifBlank { "imported_character" }
        val safeFileName = uniqueCharacterFileName(sanitizeFileName(name))
        DebugLogger.log("CharacterStorage: charx name='$name' iconPng=${result.iconPng?.size} spriteDir=${tempSpriteDir.listFiles()?.size ?: 0} files")

        // Move streamed sprites to final sprite storage location
        if (tempSpriteDir.exists()) {
            val finalDir = spriteStorage.dir(name)
            finalDir.mkdirs()
            tempSpriteDir.listFiles()?.forEach { f -> f.renameTo(File(finalDir, f.name)) }
            tempSpriteDir.delete()
        }

        val avatarPng = normalizeIconToPng(result.iconPng)
        val embeddedPng = PngCharacterCard.embedCharacterData(avatarPng, result.cardData)
        val file = File(charactersDir, safeFileName)
        file.writeBytes(embeddedPng)
        characterDao.upsert(cardDataToEntity(safeFileName, result.cardData.data))
        DebugLogger.log("CharacterStorage: imported charx (streaming) -> $safeFileName")
        return safeFileName
    }

    private suspend fun importCharx(bytes: ByteArray): String {
        DebugLogger.log("CharacterStorage: parsing charx (${bytes.size} bytes)")
        val result = CharxParser.parse(bytes)  // throws with real message on failure
        val name = result.cardData.data.name.ifBlank { "imported_character" }
        val safeFileName = uniqueCharacterFileName(sanitizeFileName(name))
        DebugLogger.log("CharacterStorage: charx name='$name' iconPng=${result.iconPng?.size} sprites=${result.sprites.size}")

        val avatarPng = normalizeIconToPng(result.iconPng)
        val embeddedPng = PngCharacterCard.embedCharacterData(avatarPng, result.cardData)
        val saved = saveRawPng(embeddedPng, safeFileName)

        if (result.sprites.isNotEmpty()) {
            spriteStorage.save(name, result.sprites)
            DebugLogger.log("CharacterStorage: saved ${result.sprites.size} sprites for $name")
        }

        DebugLogger.log("CharacterStorage: imported charx -> $saved")
        return saved
    }

    private suspend fun importPng(bytes: ByteArray): String? {
        val card = PngCharacterCard.extractCharacterData(bytes)
            ?: return null
        val name = card.data.name.ifBlank { "imported_character" }
        val safeFileName = uniqueCharacterFileName(sanitizeFileName(name))
        File(charactersDir, safeFileName).writeBytes(bytes)
        characterDao.upsert(cardDataToEntity(safeFileName, card.data))
        DebugLogger.log("CharacterStorage: imported PNG $safeFileName")
        return safeFileName
    }

    private fun isPng(bytes: ByteArray): Boolean {
        if (bytes.size < 8) return false
        val sig = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)
        return bytes.copyOfRange(0, 8).contentEquals(sig)
    }

    // Decode any image format (WebP, JPEG, PNG) → PNG bytes for tEXt embedding
    private fun normalizeIconToPng(raw: ByteArray?): ByteArray {
        if (raw != null) {
            val bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.size)
            if (bitmap != null) return bitmapToPng(bitmap)
        }
        return defaultAvatarPng()
    }

    /** Delete a character card and remove from Room index. Chat cleanup is handled by LocalRepository. */
    suspend fun deleteCharacter(fileName: String) = withContext(Dispatchers.IO) {
        File(charactersDir, fileName).delete()
        characterDao.deleteByFileName(fileName)
    }

    data class CharacterFileInfo(
        val fileName: String,
        val displayName: String,
        val sizeBytes: Long,
        val existsOnDisk: Boolean,
        val inDatabase: Boolean
    )

    /** List all character entries from DB, cross-referenced with disk. */
    suspend fun listCharacterFileInfo(): List<CharacterFileInfo> = withContext(Dispatchers.IO) {
        val dbEntries = characterDao.getAll().associateBy { it.fileName }
        val diskFiles = (charactersDir.listFiles { f -> f.extension == "png" } ?: emptyArray())
            .associateBy { it.name }
        val allKeys = (dbEntries.keys + diskFiles.keys).toSet()
        allKeys.map { key ->
            val entity = dbEntries[key]
            val file = diskFiles[key]
            CharacterFileInfo(
                fileName = key,
                displayName = entity?.name ?: key.removeSuffix(".png"),
                sizeBytes = file?.length() ?: 0L,
                existsOnDisk = file != null,
                inDatabase = entity != null
            )
        }.sortedBy { it.displayName.lowercase() }
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
        val card = try { PngCharacterCard.extractCharacterData(bytes) } catch (e: Exception) { null }
        characterDao.upsert(cardDataToEntity(safeFileName, card?.data))
        safeFileName
    }

    /** Build local file:// URI for a character's avatar PNG. */
    fun getAvatarUri(fileName: String): Uri =
        Uri.fromFile(File(charactersDir, fileName))

    /** Rebuild the Room index from disk. Preserves existing isFavorite/useAvatarForImageGen/notes. */
    suspend fun rebuildIndex() = withContext(Dispatchers.IO) {
        val files = charactersDir.listFiles { f -> f.extension == "png" } ?: emptyArray()
        val existing = characterDao.getAll().associateBy { it.fileName }
        val entities = files.mapNotNull { file ->
            val card = try { PngCharacterCard.extractCharacterData(file.readBytes()) } catch (e: Exception) { null }
            val prev = existing[file.name]
            cardDataToEntity(file.name, card?.data).copy(
                isFavorite = prev?.isFavorite ?: false,
                useAvatarForImageGen = prev?.useAvatarForImageGen ?: true,
                notes = prev?.notes ?: "",
                lastChatDate = prev?.lastChatDate ?: 0
            )
        }
        characterDao.deleteAll()
        characterDao.upsertAll(entities)
        DebugLogger.log("CharacterStorage: rebuilt index with ${entities.size} characters")
    }

    // ── Converters ────────────────────────────────────────────────────────────

    private fun cardDataToEntity(fileName: String, data: CharacterCardData?): CharacterEntity {
        val name = data?.name ?: fileName.removeSuffix(".png")
        val greetingsJson = if (data?.alternateGreetings.isNullOrEmpty()) "[]" else {
            JSONArray(data!!.alternateGreetings).toString()
        }
        val loreHints = try {
            data?.extensions?.get("pockettavern_lore_hints")?.jsonPrimitive?.content ?: ""
        } catch (_: Exception) { "" }
        return CharacterEntity(
            fileName = fileName,
            avatar = fileName,
            name = name,
            description = data?.description ?: "",
            personality = data?.personality ?: "",
            scenario = data?.scenario ?: "",
            firstMessage = data?.firstMes ?: "",
            messageExample = data?.mesExample ?: "",
            creatorNotes = data?.creatorNotes ?: "",
            systemPrompt = data?.systemPrompt ?: "",
            postHistoryInstructions = data?.postHistoryInstructions ?: "",
            alternateGreetings = greetingsJson,
            tags = data?.tags?.joinToString(",") ?: "",
            hasCharacterBook = data?.characterBook != null,
            characterBookEntryCount = data?.characterBook?.entries?.size ?: 0,
            loreHints = loreHints
        )
    }

    private fun characterToEntity(character: Character, fileName: String? = null): CharacterEntity {
        val fn = fileName ?: character.avatar ?: sanitizeFileName(character.name) + ".png"
        val greetingsJson = if (character.alternateGreetings.isEmpty()) "[]" else
            JSONArray(character.alternateGreetings).toString()
        return CharacterEntity(
            fileName = fn,
            avatar = fn,
            name = character.name,
            description = character.description,
            personality = character.personality,
            scenario = character.scenario,
            firstMessage = character.firstMessage,
            messageExample = character.messageExample,
            creatorNotes = character.creatorNotes,
            systemPrompt = character.systemPrompt,
            postHistoryInstructions = character.postHistoryInstructions,
            alternateGreetings = greetingsJson,
            tags = character.tags.joinToString(","),
            hasCharacterBook = character.hasCharacterBook,
            characterBookEntryCount = character.characterBookEntryCount,
            isFavorite = character.isFavorite,
            useAvatarForImageGen = character.useAvatarForImageGen,
            notes = character.notes,
            loreHints = character.loreHints
        )
    }

    private fun entityToCharacter(entity: CharacterEntity): Character {
        val greetings = try {
            val arr = JSONArray(entity.alternateGreetings)
            List(arr.length()) { arr.getString(it) }
        } catch (_: Exception) { emptyList() }
        return Character(
            name = entity.name,
            avatar = entity.avatar.ifBlank { entity.fileName },
            description = entity.description,
            personality = entity.personality,
            scenario = entity.scenario,
            firstMessage = entity.firstMessage,
            messageExample = entity.messageExample,
            creatorNotes = entity.creatorNotes,
            systemPrompt = entity.systemPrompt,
            postHistoryInstructions = entity.postHistoryInstructions,
            alternateGreetings = greetings,
            tags = if (entity.tags.isBlank()) emptyList() else entity.tags.split(","),
            hasCharacterBook = entity.hasCharacterBook,
            characterBookEntryCount = entity.characterBookEntryCount,
            isFavorite = entity.isFavorite,
            useAvatarForImageGen = entity.useAvatarForImageGen,
            notes = entity.notes,
            loreHints = entity.loreHints
        )
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
                characterBookEntryCount = data?.characterBook?.entries?.size ?: 0,
                loreHints = try {
                    extensions?.get("pockettavern_lore_hints")?.jsonPrimitive?.content ?: ""
                } catch (_: Exception) { "" }
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

    /** Scan all character PNGs and return metadata for those with embedded PT scripts. */
    suspend fun listCardExtensions(): List<CardExtensionMeta> = withContext(Dispatchers.IO) {
        val entities = characterDao.getAll()
        entities.mapNotNull { entity ->
            try {
                val file = File(charactersDir, entity.fileName)
                if (!file.exists()) return@mapNotNull null
                val card = PngCharacterCard.extractCharacterData(file.readBytes()) ?: return@mapNotNull null
                val ptExtJson = card.data.extensions["pockettavern"] ?: return@mapNotNull null
                val ptExt = JSONObject(ptExtJson.toString())
                if (ptExt.optString("script", "").isBlank()) return@mapNotNull null
                CardExtensionMeta(
                    characterFile   = entity.fileName,
                    characterName   = entity.name,
                    scriptName      = ptExt.optString("script_name", entity.name),
                    scriptVersion   = ptExt.optString("script_version", ""),
                    scriptDescription = ptExt.optString("script_description", "")
                )
            } catch (_: Exception) { null }
        }
    }

    private fun sanitizeFileName(name: String): String =
        name.replace(Regex("[^a-zA-Z0-9_\\-. ]"), "_").trim().take(64)

    /** Returns a filename that doesn't already exist in charactersDir, appending _2, _3, etc. */
    private fun uniqueCharacterFileName(base: String): String {
        val candidate = File(charactersDir, "$base.png")
        if (!candidate.exists()) return "$base.png"
        var n = 2
        while (File(charactersDir, "${base}_$n.png").exists()) n++
        return "${base}_$n.png"
    }
}
