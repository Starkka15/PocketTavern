package com.pockettavern.app.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Photo orientation handling. Camera JPEGs store rotation as EXIF metadata that
 * BitmapFactory ignores — saving raw bytes produces sideways avatars. Normalize
 * bakes the EXIF rotation into the pixels; rotate is for a manual rotate button.
 */
object ImageOrientation {

    /** Apply EXIF orientation; returns input unchanged when no rotation is needed. */
    fun normalize(bytes: ByteArray): ByteArray {
        return try {
            val exif = ExifInterface(ByteArrayInputStream(bytes))
            val degrees = when (exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (degrees == 0f) bytes else rotate(bytes, degrees)
        } catch (_: Exception) {
            bytes
        }
    }

    /** Rotate clockwise by [degrees], re-encoded as PNG (EXIF-free). */
    fun rotate(bytes: ByteArray, degrees: Float): ByteArray {
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bytes
        val matrix = Matrix().apply { postRotate(degrees) }
        val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
        val out = ByteArrayOutputStream()
        rotated.compress(Bitmap.CompressFormat.PNG, 100, out)
        return out.toByteArray()
    }
}
