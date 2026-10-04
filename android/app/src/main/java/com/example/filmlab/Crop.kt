package com.example.filmlab

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

enum class CropAspect(val title: String) {
    Free("Free"), Original("Original"), Square("1:1"), Portrait("4:5"), Story("9:16"), Wide("16:9");

    /** Width divided by height in pixels, or null when the crop is free. */
    fun ratio(width: Float, height: Float): Float? = when (this) {
        Free -> null
        Original -> width / height
        Square -> 1f
        Portrait -> 4f / 5f
        Story -> 9f / 16f
        Wide -> 16f / 9f
    }
}

/** A rectangle in normalized coordinates (0..1, origin top left). */
data class NRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top

    companion object {
        val Full = NRect(0f, 0f, 1f, 1f)
    }
}

/** Rotation, flip, straighten and crop. Applied before any colour work. */
data class CropState(
    /** Clockwise quarter turns. */
    val quarterTurns: Int = 0,
    val flipped: Boolean = false,
    /** Degrees, positive is clockwise. */
    val straighten: Float = 0f,
    /** Normalized to the straightened photo. */
    val rect: NRect = NRect.Full,
    val aspect: CropAspect = CropAspect.Free,
)

/** Crop-box arithmetic in normalized coordinates. Mirrors CropMath in Crop.swift. */
object CropMath {
    private const val MIN_SIZE = 0.1f

    /** A pixel width/height ratio expressed in normalized units for a photo of this size. */
    fun normalizedRatio(ratio: Float?, width: Float, height: Float): Float? =
        ratio?.let { it * height / width }

    /** The largest centred box with this normalized ratio. */
    fun fitted(k: Float?): NRect {
        if (k == null) return NRect.Full
        val w = if (k >= 1f) 1f else k
        val h = if (k >= 1f) 1f / k else 1f
        return NRect((1 - w) / 2, (1 - h) / 2, (1 + w) / 2, (1 + h) / 2)
    }

    /** Drags one corner (0 top left, 1 top right, 2 bottom right, 3 bottom left) while the opposite stays put. */
    fun dragCorner(corner: Int, start: NRect, dx: Float, dy: Float, k: Float?): NRect {
        val left = corner == 0 || corner == 3
        val top = corner < 2
        val anchorX = if (left) start.right else start.left
        val anchorY = if (top) start.bottom else start.top
        val movingX = (if (left) start.left else start.right) + dx
        val movingY = (if (top) start.top else start.bottom) + dy

        val maxW = if (left) anchorX else 1 - anchorX
        val maxH = if (top) anchorY else 1 - anchorY
        var w = min(max(abs(movingX - anchorX), MIN_SIZE), maxW)
        var h = min(max(abs(movingY - anchorY), MIN_SIZE), maxH)
        if (if (left) movingX > anchorX else movingX < anchorX) w = MIN_SIZE
        if (if (top) movingY > anchorY else movingY < anchorY) h = MIN_SIZE

        if (k != null) {
            // Follow whichever side was pulled further, then fit inside the photo.
            if (w / k > h) h = w / k else w = h * k
            if (w > maxW) { w = maxW; h = w / k }
            if (h > maxH) { h = maxH; w = h * k }
        }
        val x = if (left) anchorX - w else anchorX
        val y = if (top) anchorY - h else anchorY
        return NRect(x, y, x + w, y + h)
    }

    fun move(start: NRect, dx: Float, dy: Float): NRect {
        val x = (start.left + dx).coerceIn(0f, 1 - start.width)
        val y = (start.top + dy).coerceIn(0f, 1 - start.height)
        return NRect(x, y, x + start.width, y + start.height)
    }
}

object Geometry {
    /** Width and height of the frame the crop box lives in: the photo after quarter turns. */
    fun frameSize(width: Int, height: Int, crop: CropState): Pair<Int, Int> =
        if (crop.quarterTurns % 2 == 0) width to height else height to width

    /** Flip, quarter turns and straighten, then the crop unless [cropping] is false (while editing the crop). */
    fun apply(source: Bitmap, crop: CropState, cropping: Boolean): Bitmap {
        var bitmap = source
        val turns = ((crop.quarterTurns % 4) + 4) % 4
        if (crop.flipped || turns != 0) {
            val m = Matrix()
            if (crop.flipped) m.postScale(-1f, 1f)
            m.postRotate(90f * turns)
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
        }
        if (crop.straighten != 0f) {
            val w = bitmap.width.toFloat()
            val h = bitmap.height.toFloat()
            val angle = Math.toRadians(crop.straighten.toDouble())
            // Scale up just enough that the turned photo still covers the frame, with no empty corners.
            val scale = (abs(cos(angle)) + max(w / h, h / w) * abs(sin(angle))).toFloat() * 1.002f
            val m = Matrix().apply {
                postTranslate(-w / 2, -h / 2)
                postRotate(crop.straighten)
                postScale(scale, scale)
                postTranslate(w / 2, h / 2)
            }
            val out = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            Canvas(out).drawBitmap(bitmap, m, Paint(Paint.FILTER_BITMAP_FLAG))
            bitmap = out
        }
        if (cropping && crop.rect != NRect.Full) {
            val r = crop.rect
            val x = (r.left * bitmap.width).roundToInt().coerceIn(0, bitmap.width - 1)
            val y = (r.top * bitmap.height).roundToInt().coerceIn(0, bitmap.height - 1)
            val w = (r.width * bitmap.width).roundToInt().coerceIn(1, bitmap.width - x)
            val h = (r.height * bitmap.height).roundToInt().coerceIn(1, bitmap.height - y)
            bitmap = Bitmap.createBitmap(bitmap, x, y, w, h)
        }
        return bitmap
    }
}
