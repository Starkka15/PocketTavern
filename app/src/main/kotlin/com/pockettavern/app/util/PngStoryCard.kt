package com.pockettavern.app.util

import android.util.Base64
import com.pockettavern.app.domain.model.Story
import com.pockettavern.app.domain.model.StoryValidator
import kotlinx.serialization.json.Json
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * T7 — READ native Story cards. A Story card is a PNG carrying its JSON in a `tEXt` chunk with
 * keyword `ptensemble` (cover art = the PNG image), OR a raw `.json`. The app imports & runs only;
 * it never writes Story cards (V11) — authoring is external. Mirrors [PngCharacterCard] decode, but
 * coexists with ST's `chara` chunk untouched (V9).
 *
 * SPEC: ~/projects/pockettavern/ensemble-mode/SPEC.md
 */
object PngStoryCard {

    const val KEYWORD = "ptensemble"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /** Parse raw Story JSON text. Returns null on malformed/invalid (fails validation). */
    fun parseJson(text: String): Story? = try {
        val story = json.decodeFromString(Story.serializer(), text)
        if (StoryValidator.isValid(story)) story else null
    } catch (e: Exception) {
        android.util.Log.e("PngStoryCard", "Story JSON parse failed", e)
        null
    }

    /** True if the PNG carries a Story card (the `tEXt$KEYWORD` marker). Cheap pre-check. */
    fun isStoryCard(pngBytes: ByteArray): Boolean = findMarker(pngBytes) >= 0

    /**
     * Extract a [Story] from a Story-card PNG. Reads the `ptensemble` tEXt chunk (base64 JSON).
     * Returns null if not a Story card or the payload is invalid.
     */
    fun extractStory(pngBytes: ByteArray): Story? {
        return try {
            val markerPos = findMarker(pngBytes)
            if (markerPos < 0) return null

            // length is the 4 bytes before "tEXt"
            val lengthPos = markerPos - 4
            if (lengthPos < 0) return null
            val length = ByteBuffer.wrap(pngBytes, lengthPos, 4).order(ByteOrder.BIG_ENDIAN).int

            // base64 payload starts after "tEXt" + keyword + null  = 4 + KEYWORD.length + 1
            val headerLen = 4 + KEYWORD.length + 1
            val dataStart = markerPos + headerLen
            val dataLength = length - (KEYWORD.length + 1)  // chunk data = keyword + \0 + payload
            if (dataStart < 0 || dataLength <= 0 || dataStart + dataLength > pngBytes.size) return null

            val base64Data = String(pngBytes, dataStart, dataLength, Charsets.ISO_8859_1)
            val jsonStr = String(Base64.decode(base64Data, Base64.DEFAULT), Charsets.UTF_8)
            parseJson(jsonStr)
        } catch (e: Exception) {
            android.util.Log.e("PngStoryCard", "Error extracting Story", e)
            null
        }
    }

    /** Position of the `tEXt$KEYWORD` marker in [pngBytes], or -1. Verifies PNG signature first. */
    private fun findMarker(pngBytes: ByteArray): Int {
        if (pngBytes.size < 8) return -1
        val sig = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)
        if (!pngBytes.copyOfRange(0, 8).contentEquals(sig)) return -1
        val marker = ("tEXt$KEYWORD").toByteArray(Charsets.ISO_8859_1)
        outer@ for (i in 0..pngBytes.size - marker.size) {
            for (j in marker.indices) if (pngBytes[i + j] != marker[j]) continue@outer
            return i
        }
        return -1
    }
}
