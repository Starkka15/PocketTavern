package com.stark.sillytavern.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing per-character chat backgrounds.
 * Backgrounds are stored locally in the app's internal storage.
 */
@Singleton
class BackgroundRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val backgroundsDir: File
        get() = File(context.filesDir, "backgrounds").also { it.mkdirs() }

    /**
     * Get the background file for a character
     */
    private fun getBackgroundFile(characterId: String): File {
        val safeId = characterId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return File(backgroundsDir, "${safeId}.png")
    }

    /**
     * Check if a character has a custom background
     */
    suspend fun hasBackground(characterId: String): Boolean = withContext(Dispatchers.IO) {
        getBackgroundFile(characterId).exists()
    }

    /**
     * Get background as a File path for Coil to load
     */
    suspend fun getBackgroundPath(characterId: String): String? = withContext(Dispatchers.IO) {
        val file = getBackgroundFile(characterId)
        if (file.exists()) file.absolutePath else null
    }

    /**
     * Save a background from base64 string (from image generation)
     */
    suspend fun saveBackgroundFromBase64(characterId: String, base64: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val imageBytes = Base64.decode(base64, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            saveBitmap(characterId, bitmap)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Save a background from URI (from image picker)
     */
    suspend fun saveBackgroundFromUri(characterId: String, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val bitmap = BitmapFactory.decodeStream(inputStream)
                saveBitmap(characterId, bitmap)
            } ?: false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Save bitmap to file
     */
    private fun saveBitmap(characterId: String, bitmap: Bitmap): Boolean {
        return try {
            val file = getBackgroundFile(characterId)
            file.outputStream().use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, outputStream)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Delete a character's background
     */
    suspend fun deleteBackground(characterId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            getBackgroundFile(characterId).delete()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get background as base64 (for potential sync/export)
     */
    suspend fun getBackgroundAsBase64(characterId: String): String? = withContext(Dispatchers.IO) {
        try {
            val file = getBackgroundFile(characterId)
            if (!file.exists()) return@withContext null

            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, outputStream)
            Base64.encodeToString(outputStream.toByteArray(), Base64.DEFAULT)
        } catch (e: Exception) {
            null
        }
    }
}
