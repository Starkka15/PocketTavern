package com.pockettavern.app.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.media.FaceDetector

/**
 * Extracts a face from a bitmap and composites it onto a portrait-sized canvas,
 * centered in the upper third. This produces an img2img init image where only
 * the face area has content, giving the model freedom to generate the body and scene.
 */
object FaceExtractor {

    /**
     * Detect the face in [bitmap], crop tightly (face + hair only, no body),
     * and place it centered in the upper third of a [targetWidth]x[targetHeight] canvas.
     *
     * @param bitmap Source avatar bitmap.
     * @param targetWidth  Output canvas width (default 512).
     * @param targetHeight Output canvas height (default 768).
     * @return Composited bitmap with face centered at top, or null if no face found.
     */
    fun extractFace(bitmap: Bitmap, targetWidth: Int = 512, targetHeight: Int = 768): Bitmap? {
        // FaceDetector requires RGB_565 and even width
        val width = bitmap.width.let { if (it % 2 != 0) it - 1 else it }
        val height = bitmap.height
        if (width < 2 || height < 2) return null

        val rgb565 = if (bitmap.config == Bitmap.Config.RGB_565 && bitmap.width == width) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, width, height, true)
                .copy(Bitmap.Config.RGB_565, false)
        }

        val detector = FaceDetector(rgb565.width, rgb565.height, 1)
        val faces = arrayOfNulls<FaceDetector.Face>(1)
        val count = detector.findFaces(rgb565, faces)

        if (count == 0 || faces[0] == null) return null

        val face = faces[0]!!
        val midPoint = PointF()
        face.getMidPoint(midPoint)
        val eyesDist = face.eyesDistance()

        // Tight face crop: face width ≈ 2x eye distance
        val faceWidth = (eyesDist * 2f).toInt()

        val faceCenterX = midPoint.x.toInt()
        val faceCenterY = midPoint.y.toInt()

        // Tight crop: just enough for face + hair on top, chin on bottom
        // No extra body/clothing
        val cropWidth = (faceWidth * 1.2f).toInt()        // slight padding for ears
        val cropHeightAbove = (faceWidth * 0.9f).toInt()   // forehead + hair
        val cropHeightBelow = (faceWidth * 0.5f).toInt()   // chin + tiny bit below

        val left = (faceCenterX - cropWidth / 2).coerceAtLeast(0)
        val top = (faceCenterY - cropHeightAbove).coerceAtLeast(0)
        val right = (faceCenterX + cropWidth / 2).coerceAtMost(bitmap.width)
        val bottom = (faceCenterY + cropHeightBelow).coerceAtMost(bitmap.height)

        val cropRect = Rect(left, top, right, bottom)
        if (cropRect.width() < 10 || cropRect.height() < 10) return null

        // Crop the face from the original bitmap
        val faceCrop = Bitmap.createBitmap(
            bitmap,
            cropRect.left,
            cropRect.top,
            cropRect.width(),
            cropRect.height()
        )

        // Create the target canvas (neutral gray — blends into any scene)
        val canvas = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val c = Canvas(canvas)
        c.drawColor(Color.rgb(128, 128, 128))

        // Scale the face crop to about 30% of canvas width — smaller reference gives
        // the model more freedom to stylize (anime, etc.) instead of clinging to realism
        val desiredFaceWidth = (targetWidth * 0.3f)
        val scale = desiredFaceWidth / faceCrop.width
        val scaledWidth = (faceCrop.width * scale)
        val scaledHeight = (faceCrop.height * scale)

        // Center horizontally, place in upper quarter vertically
        val destLeft = (targetWidth - scaledWidth) / 2f
        val destTop = targetHeight * 0.12f  // ~12% from top — natural head position

        c.drawBitmap(
            faceCrop,
            null,
            RectF(destLeft, destTop, destLeft + scaledWidth, destTop + scaledHeight),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        )

        faceCrop.recycle()
        return canvas
    }
}
