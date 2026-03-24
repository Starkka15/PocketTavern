package com.pockettavern.app.util

import android.graphics.Bitmap
import android.graphics.Color
import androidx.palette.graphics.Palette
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.label.ImageLabel
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Analyzes an avatar bitmap using on-device ML (no API key needed).
 * Combines Palette color extraction, ML Kit image labeling, and face detection
 * to produce a text description of the character's appearance.
 */
object AvatarAnalyzer {

    /** Bridge Google Task to coroutines without needing kotlinx-coroutines-play-services. */
    private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { result -> cont.resume(result) }
        addOnFailureListener { e -> cont.resumeWithException(e) }
    }

    /**
     * Analyze [bitmap] and return a text description of the character's appearance.
     * Runs entirely on-device — no network calls.
     */
    suspend fun analyze(bitmap: Bitmap): String = withContext(Dispatchers.Default) {
        val parts = mutableListOf<String>()

        // 1. Palette — dominant colors
        val colorDesc = analyzeColors(bitmap)
        if (colorDesc.isNotBlank()) parts.add(colorDesc)

        // 2. ML Kit Image Labeling — what's in the image
        val labels = analyzeLabels(bitmap)
        if (labels.isNotBlank()) parts.add(labels)

        // 3. ML Kit Face Detection — expression and pose
        val faceDesc = analyzeFace(bitmap)
        if (faceDesc.isNotBlank()) parts.add(faceDesc)

        if (parts.isEmpty()) return@withContext ""
        parts.joinToString(". ") + "."
    }

    private suspend fun analyzeColors(bitmap: Bitmap): String {
        return try {
            val palette = withContext(Dispatchers.IO) {
                Palette.from(bitmap).generate()
            }

            val descriptions = mutableListOf<String>()

            palette.dominantSwatch?.let { swatch ->
                descriptions.add("dominant color is ${describeColor(swatch.rgb)}")
            }
            palette.vibrantSwatch?.let { swatch ->
                descriptions.add("vibrant accent of ${describeColor(swatch.rgb)}")
            }
            palette.mutedSwatch?.let { swatch ->
                if (swatch.rgb != palette.dominantSwatch?.rgb) {
                    descriptions.add("muted tones of ${describeColor(swatch.rgb)}")
                }
            }

            if (descriptions.isEmpty()) ""
            else "Color palette: ${descriptions.joinToString(", ")}"
        } catch (_: Exception) { "" }
    }

    private suspend fun analyzeLabels(bitmap: Bitmap): String {
        return try {
            val options = ImageLabelerOptions.Builder()
                .setConfidenceThreshold(0.5f)
                .build()
            val labeler = ImageLabeling.getClient(options)
            val image = InputImage.fromBitmap(bitmap, 0)
            val results: List<ImageLabel> = labeler.process(image).awaitResult()
            labeler.close()

            val labelTexts = results
                .sortedByDescending { it.confidence }
                .take(8)
                .map { "${it.text} (${(it.confidence * 100).toInt()}%)" }

            if (labelTexts.isEmpty()) ""
            else "Image contains: ${labelTexts.joinToString(", ")}"
        } catch (_: Exception) { "" }
    }

    private suspend fun analyzeFace(bitmap: Bitmap): String {
        return try {
            val options = FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .build()
            val detector = FaceDetection.getClient(options)
            val image = InputImage.fromBitmap(bitmap, 0)
            val faces: List<Face> = detector.process(image).awaitResult()
            detector.close()

            if (faces.isEmpty()) return@analyzeFace ""

            val face = faces[0]
            val details = mutableListOf<String>()

            val smileProb: Float? = face.smilingProbability
            if (smileProb != null) {
                when {
                    smileProb > 0.8f -> details.add("smiling broadly")
                    smileProb > 0.5f -> details.add("with a slight smile")
                    smileProb > 0.2f -> details.add("with a neutral expression")
                    else -> details.add("with a serious expression")
                }
            }

            val leftEye: Float? = face.leftEyeOpenProbability
            val rightEye: Float? = face.rightEyeOpenProbability
            if (leftEye != null && rightEye != null) {
                if (leftEye < 0.3f && rightEye < 0.3f) details.add("eyes closed")
                else if (leftEye < 0.4f || rightEye < 0.4f) details.add("with a wink or half-closed eyes")
            }

            val yaw: Float = face.headEulerAngleY
            when {
                yaw > 20f -> details.add("looking to the left")
                yaw < -20f -> details.add("looking to the right")
                yaw > 8f -> details.add("turned slightly to the left")
                yaw < -8f -> details.add("turned slightly to the right")
                else -> details.add("facing forward")
            }

            // Face proportions relative to image
            val faceBox = face.boundingBox
            val imgArea = bitmap.width.toFloat() * bitmap.height
            val faceArea = faceBox.width().toFloat() * faceBox.height()
            val faceRatio = faceArea / imgArea
            when {
                faceRatio > 0.4f -> details.add("close-up portrait")
                faceRatio > 0.15f -> details.add("upper body portrait")
                else -> details.add("full body or distant shot")
            }

            if (details.isEmpty()) ""
            else "Face: ${details.joinToString(", ")}"
        } catch (_: Exception) { "" }
    }

    private fun describeColor(rgb: Int): String {
        val r = Color.red(rgb)
        val g = Color.green(rgb)
        val b = Color.blue(rgb)

        val hsv = FloatArray(3)
        Color.RGBToHSV(r, g, b, hsv)
        val hue = hsv[0]
        val sat = hsv[1]
        val value = hsv[2]

        // Achromatic
        if (sat < 0.15f) {
            return when {
                value < 0.15f -> "black"
                value < 0.35f -> "dark gray"
                value < 0.65f -> "gray"
                value < 0.85f -> "light gray"
                else -> "white"
            }
        }

        val lightness = when {
            value < 0.3f -> "dark "
            value > 0.8f && sat < 0.5f -> "light "
            value > 0.85f -> "pale "
            else -> ""
        }

        val colorName = when {
            hue < 15f -> "red"
            hue < 40f -> "orange"
            hue < 65f -> "yellow"
            hue < 80f -> "yellow-green"
            hue < 160f -> "green"
            hue < 190f -> "teal"
            hue < 250f -> "blue"
            hue < 290f -> "purple"
            hue < 330f -> "pink"
            else -> "red"
        }

        return "$lightness$colorName".trim()
    }
}
