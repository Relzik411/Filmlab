package com.example.filmlab

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class FrameStyle(val title: String) {
    None("None"), Instant("Instant"), Thin("Thin"), Gallery("Gallery"), Square("Square"), Film("Film");

    /** Whether the border colour can be chosen (the film strip is always black). */
    val hasColor get() = this != None && this != Film
}

enum class FrameColor(val title: String, val argb: Int) {
    White("White", Color.rgb(255, 255, 255)),
    Cream("Cream", Color.rgb(243, 237, 224)),
    Black("Black", Color.rgb(17, 17, 17)),
}

/**
 * Borders, the film strip and the date stamp, added after every other effect.
 * Sizes are fractions of the photo, so the preview matches the export. Mirrors Frame.swift.
 */
object Frames {
    private class Layout(val width: Int, val height: Int, val left: Int, val top: Int)

    private fun layout(style: FrameStyle, w: Int, h: Int): Layout {
        val short = min(w, h).toFloat()
        val long = max(w, h).toFloat()
        fun edges(l: Float, t: Float, r: Float, b: Float) =
            Layout((w + l + r).roundToInt(), (h + t + b).roundToInt(), l.roundToInt(), t.roundToInt())
        return when (style) {
            FrameStyle.None -> edges(0f, 0f, 0f, 0f)
            FrameStyle.Instant -> edges(0.06f * short, 0.06f * short, 0.06f * short, 0.24f * short)
            FrameStyle.Thin -> edges(0.025f * short, 0.025f * short, 0.025f * short, 0.025f * short)
            FrameStyle.Gallery -> edges(0.1f * short, 0.1f * short, 0.1f * short, 0.1f * short)
            FrameStyle.Square -> {
                val side = (long * 1.08f).roundToInt()
                Layout(side, side, (side - w) / 2, (side - h) / 2)
            }
            FrameStyle.Film -> edges(0.03f * short, 0.16f * short, 0.03f * short, 0.16f * short)
        }
    }

    /** "’26 10 04", the way date-stamping film cameras print it. */
    fun stampText(date: Date): String =
        "’" + SimpleDateFormat("yy MM dd", Locale.US).format(date)

    fun apply(photo: Bitmap, edit: EditState, dateText: String?): Bitmap {
        val stamp = if (edit.dateStamp) dateText else null
        if (edit.frame == FrameStyle.None && stamp == null) return photo

        val w = photo.width
        val h = photo.height
        val short = min(w, h).toFloat()
        val l = layout(edit.frame, w, h)
        val out = Bitmap.createBitmap(l.width, l.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        if (edit.frame != FrameStyle.None) {
            canvas.drawColor(if (edit.frame == FrameStyle.Film) Color.rgb(18, 18, 18) else edit.frameColor.argb)
        }
        canvas.drawBitmap(photo, l.left.toFloat(), l.top.toFloat(), null)

        if (edit.frame == FrameStyle.Film) {
            val holeW = 0.035f * short
            val holeH = 0.05f * short
            val spacing = 0.075f * short
            val border = 0.16f * short
            val hole = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(219, 219, 219) }
            for (rowY in listOf(border * 0.35f - holeH / 2, l.height - border * 0.35f - holeH / 2)) {
                var x = spacing / 2
                while (x + holeW < l.width) {
                    canvas.drawRoundRect(RectF(x, rowY, x + holeW, rowY + holeH), holeW * 0.2f, holeW * 0.2f, hole)
                    x += spacing
                }
            }
            val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(232, 163, 61)
                textSize = 0.032f * short
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                letterSpacing = 0.15f
            }
            // Between the photo and the sprocket holes. Text is drawn from its baseline, so add the ascent
            // to match iOS, which places text by its top.
            val baseline = l.top + h + 0.012f * short - edge.ascent()
            canvas.drawText("FILMLAB 400", l.left + 0.04f * short, baseline, edge)
            val number = "▸ 12A"
            canvas.drawText(number, l.left + w - 0.04f * short - edge.measureText(number), baseline, edge)
        }

        if (stamp != null) {
            val size = 0.05f * short
            val inset = 0.045f * short
            val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(242, 255, 158, 64)
                textSize = size
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            }
            val x = l.left + w - inset - text.measureText(stamp)
            val y = l.top + h - inset - text.descent()
            val glow = Paint(text).apply {
                color = Color.argb(230, 255, 89, 0)
                maskFilter = BlurMaskFilter(0.012f * short, BlurMaskFilter.Blur.NORMAL)
            }
            canvas.drawText(stamp, x, y, glow)
            canvas.drawText(stamp, x, y, text)
        }
        return out
    }
}
