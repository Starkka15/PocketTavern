package com.pockettavern.app.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpriteStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun dir(characterName: String): File =
        File(context.filesDir, "characters/sprites/${sanitize(characterName)}").also { it.mkdirs() }

    suspend fun save(characterName: String, sprites: Map<String, ByteArray>) = withContext(Dispatchers.IO) {
        val d = dir(characterName)
        sprites.forEach { (name, bytes) ->
            File(d, "${normalize(name)}.png").writeBytes(bytes)
        }
    }

    // Exact match first, then fuzzy: handles model hallucinating names like
    // "pouting indignantly" when the stored sprite is just "pouting"
    fun getFile(characterName: String, spriteName: String): File? {
        val key = normalize(spriteName)
        val d = dir(characterName)
        val exact = File(d, "$key.png")
        if (exact.exists()) return exact

        val available = d.listFiles { f -> f.extension == "png" }
            ?.map { it.nameWithoutExtension } ?: return null
        val queryNorm = key.replace('_', ' ')
        val queryWords = queryNorm.split(' ').filter { it.length >= 4 }

        val match = available.firstOrNull { stored ->
            val storedNorm = stored.replace('_', ' ')
            // "pouting indignantly" contains "pouting"
            queryNorm.contains(storedNorm) ||
            // "angrily_shrugging" starts with "angry"
            queryWords.any { word -> word.startsWith(storedNorm) || storedNorm.startsWith(word) }
        }
        return if (match != null) File(d, "$match.png") else null
    }

    fun list(characterName: String): List<String> =
        dir(characterName).listFiles { f -> f.extension == "png" }
            ?.map { it.nameWithoutExtension }
            ?: emptyList()

    fun hasSprites(characterName: String): Boolean =
        dir(characterName).listFiles { f -> f.extension == "png" }?.isNotEmpty() == true

    private fun normalize(key: String): String = key.lowercase().removeSuffix(".png")

    private fun sanitize(name: String): String =
        name.replace(Regex("[^a-zA-Z0-9_\\-. ]"), "_").trim().take(64)
}
