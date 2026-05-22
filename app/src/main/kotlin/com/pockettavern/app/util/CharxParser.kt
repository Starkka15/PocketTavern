package com.pockettavern.app.util

import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

data class CharxResult(
    val cardData: CharacterCardV2,
    val iconPng: ByteArray?,
    val sprites: Map<String, ByteArray>  // normalized key (lowercase, no .png) -> PNG bytes
)

object CharxParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

    // ZIP local file header magic: PK\x03\x04
    private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

    fun parse(bytes: ByteArray): CharxResult {
        val zipOffset = findZipOffset(bytes)
            ?: throw IllegalArgumentException("No ZIP data in charx file")
        val zipBytes = bytes.copyOfRange(zipOffset, bytes.size)

        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(zipBytes.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val name = entry.name
                // Skip x_meta/ (per-sprite duplicates of card.json, ~1MB each) and module.risum
                val skip = entry.isDirectory
                    || name.startsWith("x_meta/")
                    || name == "module.risum"
                if (skip) {
                    zis.skip(Long.MAX_VALUE)
                } else {
                    entries[name] = zis.readBytes()
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        val cardJsonBytes = entries["card.json"]
            ?: throw IllegalArgumentException("card.json not found in charx")
        val cardJson = String(cardJsonBytes, Charsets.UTF_8)
        val cardData = parseCardJson(cardJson)

        val iconPng = entries["assets/icon/image/iconx.png"]

        // sprites: assets/other/image/*.png — charx stores them with doubled extension (angry.png.png)
        val sprites = entries
            .filterKeys { it.startsWith("assets/other/image/") && it.endsWith(".png") }
            .mapKeys { (path, _) ->
                val filename = path.substringAfterLast("/")
                // strip double .png suffix: "angry.png.png" -> "angry"
                filename.removeSuffix(".png").removeSuffix(".png").lowercase()
            }

        return CharxResult(cardData = cardData, iconPng = iconPng, sprites = sprites)
    }

    /**
     * Stream-based parse for large charx files. Sprites are written directly to
     * spriteOutputDir (no in-memory accumulation). CharxResult.sprites is always empty —
     * sprites are already on disk when this returns.
     */
    fun parseFile(file: File, spriteOutputDir: File): CharxResult {
        val zipOffset = findZipOffsetInFile(file)
            ?: throw IllegalArgumentException("No ZIP data in charx file")

        var cardJson: String? = null
        var iconPng: ByteArray? = null

        FileInputStream(file).use { fis ->
            fis.skip(zipOffset.toLong())
            ZipInputStream(fis).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val name = entry.name
                    val skip = entry.isDirectory
                        || name.startsWith("x_meta/")
                        || name == "module.risum"
                    if (!skip) {
                        when {
                            name == "card.json" ->
                                cardJson = zis.readBytes().toString(Charsets.UTF_8)
                            name == "assets/icon/image/iconx.png" ->
                                iconPng = zis.readBytes()
                            name.startsWith("assets/other/image/") && name.endsWith(".png") -> {
                                val filename = name.substringAfterLast("/")
                                val key = filename.removeSuffix(".png").removeSuffix(".png").lowercase()
                                spriteOutputDir.mkdirs()
                                FileOutputStream(File(spriteOutputDir, "$key.png")).use { out ->
                                    zis.copyTo(out)
                                }
                            }
                            else -> zis.skip(Long.MAX_VALUE)
                        }
                    } else {
                        zis.skip(Long.MAX_VALUE)
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }

        val json = cardJson ?: throw IllegalArgumentException("card.json not found in charx")
        val cardData = parseCardJson(json)
        return CharxResult(cardData = cardData, iconPng = iconPng, sprites = emptyMap())
    }

    private fun findZipOffsetInFile(file: File): Int? {
        val scanSize = minOf(file.length(), 1_048_576L).toInt()
        val buf = ByteArray(scanSize)
        var total = 0
        FileInputStream(file).use { fis ->
            while (total < scanSize) {
                val n = fis.read(buf, total, scanSize - total)
                if (n == -1) break
                total += n
            }
        }
        for (i in 0..total - 4) {
            if (buf[i] == 0x50.toByte() && buf[i + 1] == 0x4B.toByte() &&
                buf[i + 2] == 0x03.toByte() && buf[i + 3] == 0x04.toByte()
            ) return i
        }
        return null
    }

    // V1: scan for first PK\x03\x04 — never assume offset 0
    fun findZipOffset(bytes: ByteArray): Int? {
        for (i in 0..bytes.size - 4) {
            if (bytes[i] == ZIP_MAGIC[0] && bytes[i + 1] == ZIP_MAGIC[1] &&
                bytes[i + 2] == ZIP_MAGIC[2] && bytes[i + 3] == ZIP_MAGIC[3]
            ) return i
        }
        return null
    }

    private fun parseCardJson(json: String): CharacterCardV2 {
        return try {
            CharxParser.json.decodeFromString<CharacterCardV2>(json)
        } catch (_: Exception) {
            val data = CharxParser.json.decodeFromString<CharacterCardData>(json)
            CharacterCardV2(data = data)
        }
    }
}
