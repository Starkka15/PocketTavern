package com.stark.sillytavern.util

import android.util.Base64
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

/**
 * Character Card V2 format for PNG embedding
 * Full spec: https://github.com/malfoyslastname/character-card-spec-v2
 */
@Serializable
data class CharacterCardV2(
    val spec: String = "chara_card_v2",
    @SerialName("spec_version")
    val specVersion: String = "2.0",
    val data: CharacterCardData
)

@Serializable
data class CharacterCardData(
    // Basic fields
    val name: String,
    val description: String = "",
    val personality: String = "",
    val scenario: String = "",
    @SerialName("first_mes")
    val firstMes: String = "",
    @SerialName("mes_example")
    val mesExample: String = "",

    // V2 extended fields
    @SerialName("creator_notes")
    val creatorNotes: String = "",
    @SerialName("system_prompt")
    val systemPrompt: String = "",
    @SerialName("post_history_instructions")
    val postHistoryInstructions: String = "",
    @SerialName("alternate_greetings")
    val alternateGreetings: List<String> = emptyList(),
    @SerialName("character_version")
    val characterVersion: String = "",
    val tags: List<String> = emptyList(),
    val creator: String = "",

    // Embedded character book (lorebook)
    @SerialName("character_book")
    val characterBook: CharacterBook? = null,

    // Extensions for custom data
    val extensions: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap()
)

/**
 * Embedded lorebook/world info in character card
 */
@Serializable
data class CharacterBook(
    val name: String = "",
    val description: String = "",
    val entries: List<CharacterBookEntry> = emptyList(),

    // Scan settings
    @SerialName("scan_depth")
    val scanDepth: Int? = null,
    @SerialName("token_budget")
    val tokenBudget: Int? = null,
    @SerialName("recursive_scanning")
    val recursiveScanning: Boolean = false,

    val extensions: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap()
)

/**
 * Individual lorebook entry
 */
@Serializable
data class CharacterBookEntry(
    val id: Int? = null,
    val keys: List<String> = emptyList(),
    @SerialName("secondary_keys")
    val secondaryKeys: List<String> = emptyList(),
    val content: String = "",
    val comment: String = "",
    val name: String = "",

    // Entry settings
    val enabled: Boolean = true,
    val constant: Boolean = false,
    val selective: Boolean = false,
    @SerialName("insertion_order")
    val insertionOrder: Int = 100,
    val priority: Int? = null,
    val position: String = "before_char",  // before_char, after_char

    // Match settings
    @SerialName("case_sensitive")
    val caseSensitive: Boolean = false,

    val extensions: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap()
)

/**
 * Embeds character data into a PNG file using the tEXt chunk format.
 * This creates a character card that SillyTavern can import.
 */
object PngCharacterCard {

    private val json = Json {
        encodeDefaults = true
        prettyPrint = false
        ignoreUnknownKeys = true  // V1 cards may have extra fields
    }

    /**
     * Embed character data into PNG bytes.
     *
     * @param pngBytes The original PNG image bytes
     * @param cardData The character card data to embed
     * @return PNG bytes with embedded character data
     */
    fun embedCharacterData(pngBytes: ByteArray, cardData: CharacterCardV2): ByteArray {
        // Convert character data to base64-encoded JSON
        val jsonStr = json.encodeToString(cardData)
        val base64Data = Base64.encodeToString(jsonStr.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

        // Create tEXt chunk content: "chara" + null byte + base64 data
        val keyword = "chara"
        val textContent = ByteArrayOutputStream().apply {
            write(keyword.toByteArray(Charsets.ISO_8859_1))
            write(0) // null separator
            write(base64Data.toByteArray(Charsets.ISO_8859_1))
        }.toByteArray()

        // Build complete tEXt chunk
        val chunkType = "tEXt".toByteArray(Charsets.ISO_8859_1)
        val textChunk = buildPngChunk(chunkType, textContent)

        // Find position after IHDR chunk (PNG signature is 8 bytes)
        // IHDR chunk: 4 bytes length + 4 bytes type + data + 4 bytes CRC
        val ihdrLength = ByteBuffer.wrap(pngBytes, 8, 4).order(ByteOrder.BIG_ENDIAN).int
        val insertPos = 8 + 4 + 4 + ihdrLength + 4 // after signature + IHDR chunk

        // Combine: original up to insertPos + tEXt chunk + rest of original
        return ByteArrayOutputStream().apply {
            write(pngBytes, 0, insertPos)
            write(textChunk)
            write(pngBytes, insertPos, pngBytes.size - insertPos)
        }.toByteArray()
    }

    /**
     * Build a PNG chunk with proper length and CRC.
     */
    private fun buildPngChunk(type: ByteArray, data: ByteArray): ByteArray {
        val length = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(data.size).array()

        // CRC is calculated over type + data
        val crc32 = CRC32()
        crc32.update(type)
        crc32.update(data)
        val crc = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(crc32.value.toInt()).array()

        return ByteArrayOutputStream().apply {
            write(length)
            write(type)
            write(data)
            write(crc)
        }.toByteArray()
    }

    /**
     * Create a character card from basic info.
     */
    fun createCard(
        name: String,
        description: String = "",
        personality: String = "",
        scenario: String = "",
        firstMessage: String = "",
        messageExample: String = ""
    ): CharacterCardV2 {
        return CharacterCardV2(
            data = CharacterCardData(
                name = name,
                description = description,
                personality = personality,
                scenario = scenario,
                firstMes = firstMessage,
                mesExample = messageExample
            )
        )
    }

    /**
     * Extract character data from a PNG file if it contains a character card.
     *
     * @param pngBytes The PNG image bytes
     * @return CharacterCardV2 if found, null otherwise
     */
    fun extractCharacterData(pngBytes: ByteArray): CharacterCardV2? {
        return try {
            // PNG signature is 8 bytes
            if (pngBytes.size < 8) return null

            // Verify PNG signature
            val pngSignature = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)
            if (!pngBytes.copyOfRange(0, 8).contentEquals(pngSignature)) {
                return null
            }

            // Search for "tEXtchara" marker directly - more reliable than chunk iteration
            val marker = "tEXtchara".toByteArray(Charsets.ISO_8859_1)
            var markerPos = -1

            for (i in 0 until pngBytes.size - marker.size) {
                if (pngBytes.copyOfRange(i, i + marker.size).contentEquals(marker)) {
                    markerPos = i
                    break
                }
            }

            if (markerPos < 0) return null

            // Get chunk length (4 bytes before "tEXt")
            val lengthPos = markerPos - 4
            if (lengthPos < 0) return null

            val length = ByteBuffer.wrap(pngBytes, lengthPos, 4).order(ByteOrder.BIG_ENDIAN).int

            // Base64 data starts after "tEXtchara\0" (10 bytes from marker)
            val dataStart = markerPos + 10
            val dataLength = length - 6  // subtract "chara\0"

            if (dataStart + dataLength > pngBytes.size) return null

            val base64Data = String(pngBytes, dataStart, dataLength, Charsets.ISO_8859_1)
            val jsonBytes = Base64.decode(base64Data, Base64.DEFAULT)
            val jsonStr = String(jsonBytes, Charsets.UTF_8)

            // Try to parse as V2 format first
            try {
                json.decodeFromString<CharacterCardV2>(jsonStr)
            } catch (e: Exception) {
                // Try V1 format (direct CharacterCardData without wrapper)
                try {
                    val v1Data = json.decodeFromString<CharacterCardData>(jsonStr)
                    CharacterCardV2(data = v1Data)
                } catch (e2: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("PngCharacterCard", "Error extracting character data", e)
            null
        }
    }
}
