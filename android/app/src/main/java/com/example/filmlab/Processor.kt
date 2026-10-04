package com.example.filmlab

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.floor
import kotlin.math.sqrt
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Applies an edit to a bitmap on the CPU, split across cores. Effects are sized relative to the
 * image, so the small preview matches the full-size export. Mirrors FilterPipeline.swift on iOS.
 */
object Processor {
    private val toLinear = FloatArray(256) { srgbToLinear(it / 255f) }
    private const val STEPS = 4096
    private val toSrgb = FloatArray(STEPS + 1) { linearToSrgb(it / STEPS.toFloat()) }
    private val fromSrgb = FloatArray(STEPS + 1) { srgbToLinear(it / STEPS.toFloat()) }

    /** A light leak's glows in pixels, with linear-light colours scaled by strength and amount. */
    private class Leak(edit: EditState, w: Int, h: Int) {
        val glows = LightLeak.styles[edit.leak!!].placed(edit.leakPlacement)
        val n = glows.size
        val side = max(w, h).toFloat()
        val cx = FloatArray(n) { glows[it].x * w }
        val cy = FloatArray(n) { glows[it].y * h }
        val radius = FloatArray(n) { glows[it].radius * side }
        val red = FloatArray(n) { Processor.srgbToLinear(glows[it].red * glows[it].strength * edit.leakAmount) }
        val green = FloatArray(n) { Processor.srgbToLinear(glows[it].green * glows[it].strength * edit.leakAmount) }
        val blue = FloatArray(n) { Processor.srgbToLinear(glows[it].blue * glows[it].strength * edit.leakAmount) }
    }

    suspend fun apply(edit: EditState, lut: Lut?, source: Bitmap): Bitmap = withContext(Dispatchers.Default) {
        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)

        val leak = edit.leak?.takeIf { it in LightLeak.styles.indices && edit.leakAmount > 0f }
            ?.let { Leak(edit, w, h) }

        val parts = Runtime.getRuntime().availableProcessors().coerceIn(1, 8)
        val rowsPerPart = (h + parts - 1) / parts
        val jobs = (0 until parts).map { part ->
            launch {
                val scratch = FloatArray(3)
                val end = min(h, (part + 1) * rowsPerPart)
                for (y in part * rowsPerPart until end) {
                    ensureActive()
                    processRow(pixels, y, w, h, edit, lut, leak, scratch)
                }
            }
        }
        jobs.joinAll()
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { it.setPixels(pixels, 0, w, 0, 0, w, h) }
    }

    private fun processRow(
        pixels: IntArray, y: Int, w: Int, h: Int, edit: EditState, lut: Lut?, leak: Leak?, scratch: FloatArray,
    ) {
        val gain = 2f.pow(edit.exposure)
        val warm = edit.warmth * 0.12f
        val contrast = 1f + edit.contrast * 0.5f
        val saturation = 1f + edit.saturation
        val fade = edit.fade * 0.2f
        val grain = edit.grain * 0.35f
        val grainCell = max(1f, max(w, h) / 1500f)
        val cx = w / 2f
        val cy = h / 2f
        val maxDistance = sqrt(cx * cx + cy * cy)

        var i = y * w
        for (x in 0 until w) {
            val c = pixels[i]

            // 1. Exposure and warmth in linear light, then contrast and saturation on sRGB values.
            var r = encode(toLinear[(c shr 16) and 0xff] * gain * (1f + warm))
            var g = encode(toLinear[(c shr 8) and 0xff] * gain)
            var b = encode(toLinear[c and 0xff] * gain * (1f - warm))
            if (contrast != 1f) {
                r = (r - 0.5f) * contrast + 0.5f
                g = (g - 0.5f) * contrast + 0.5f
                b = (b - 0.5f) * contrast + 0.5f
            }
            if (saturation != 1f) {
                val luma = 0.2126f * r + 0.7152f * g + 0.0722f * b
                r = luma + (r - luma) * saturation
                g = luma + (g - luma) * saturation
                b = luma + (b - luma) * saturation
            }
            r = r.coerceIn(0f, 1f)
            g = g.coerceIn(0f, 1f)
            b = b.coerceIn(0f, 1f)

            // 2. The film look, blended with the original by its strength.
            if (lut != null && edit.intensity > 0f) {
                sample(lut, r, g, b, scratch)
                r += (scratch[0] - r) * edit.intensity
                g += (scratch[1] - g) * edit.intensity
                b += (scratch[2] - b) * edit.intensity
            }

            // 3. Finishing: lifted blacks, vignette, light leak, grain.
            if (fade > 0f) {
                r = r * (1f - fade) + fade
                g = g * (1f - fade) + fade
                b = b * (1f - fade) + fade
            }
            if (edit.vignette > 0f) {
                val d = sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy)) / maxDistance
                val t = ((d - 0.35f) / 0.65f).coerceIn(0f, 1f)
                val k = 1f - edit.vignette * 0.8f * t * t * (3f - 2f * t)
                r *= k
                g *= k
                b *= k
            }
            if (leak != null) {
                // Screen the glows together, then over the photo, in linear light as Core Image does.
                var lr = 0f
                var lg = 0f
                var lb = 0f
                for (k in 0 until leak.n) {
                    val dx = x - leak.cx[k]
                    val dy = y - leak.cy[k]
                    val d = sqrt(dx * dx + dy * dy)
                    if (d >= leak.radius[k]) continue
                    val t = 1f - d / leak.radius[k]
                    lr = 1f - (1f - lr) * (1f - leak.red[k] * t)
                    lg = 1f - (1f - lg) * (1f - leak.green[k] * t)
                    lb = 1f - (1f - lb) * (1f - leak.blue[k] * t)
                }
                r = encode(1f - (1f - decode(r)) * (1f - lr))
                g = encode(1f - (1f - decode(g)) * (1f - lg))
                b = encode(1f - (1f - decode(b)) * (1f - lb))
            }
            if (grain > 0f) {
                val grey = 0.5f + (valueNoise(x / grainCell, y / grainCell) - 0.5f) * grain
                r = overlay(r, grey)
                g = overlay(g, grey)
                b = overlay(b, grey)
            }

            pixels[i] = (0xff shl 24) or (to8(r) shl 16) or (to8(g) shl 8) or to8(b)
            i++
        }
    }

    /** Trilinear lookup. */
    private fun sample(lut: Lut, r: Float, g: Float, b: Float, out: FloatArray) {
        val n = lut.size
        val m = n - 1
        val fr = r * m
        val fg = g * m
        val fb = b * m
        val r0 = min(fr.toInt(), m - 1)
        val g0 = min(fg.toInt(), m - 1)
        val b0 = min(fb.toInt(), m - 1)
        val dr = fr - r0
        val dg = fg - g0
        val db = fb - b0
        val t = lut.table
        val i000 = ((b0 * n + g0) * n + r0) * 3
        val i100 = i000 + 3
        val i010 = i000 + n * 3
        val i110 = i010 + 3
        val i001 = i000 + n * n * 3
        val i101 = i001 + 3
        val i011 = i001 + n * 3
        val i111 = i011 + 3
        for (ch in 0..2) {
            val c00 = t[i000 + ch] + (t[i100 + ch] - t[i000 + ch]) * dr
            val c10 = t[i010 + ch] + (t[i110 + ch] - t[i010 + ch]) * dr
            val c01 = t[i001 + ch] + (t[i101 + ch] - t[i001 + ch]) * dr
            val c11 = t[i011 + ch] + (t[i111 + ch] - t[i011 + ch]) * dr
            val c0 = c00 + (c10 - c00) * dg
            val c1 = c01 + (c11 - c01) * dg
            out[ch] = c0 + (c1 - c0) * db
        }
    }

    /** Smooth random values in 0..1, so scaled-up grain stays soft instead of blocky. */
    private fun valueNoise(x: Float, y: Float): Float {
        val x0 = floor(x).toInt()
        val y0 = floor(y).toInt()
        val tx = x - x0
        val ty = y - y0
        val top = hash(x0, y0) + (hash(x0 + 1, y0) - hash(x0, y0)) * tx
        val bottom = hash(x0, y0 + 1) + (hash(x0 + 1, y0 + 1) - hash(x0, y0 + 1)) * tx
        return top + (bottom - top) * ty
    }

    private fun hash(x: Int, y: Int): Float {
        var h = x * 374761393 + y * 668265263
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return (h and 0xffff) / 65535f
    }

    private fun overlay(base: Float, blend: Float) =
        if (base < 0.5f) 2f * base * blend else 1f - 2f * (1f - base) * (1f - blend)

    private fun encode(linear: Float) = toSrgb[(linear.coerceIn(0f, 1f) * STEPS).toInt()]

    private fun decode(srgb: Float) = fromSrgb[(srgb.coerceIn(0f, 1f) * STEPS).toInt()]

    private fun to8(v: Float) = (v.coerceIn(0f, 1f) * 255f + 0.5f).toInt()

    private fun srgbToLinear(v: Float) =
        if (v <= 0.04045f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f)

    private fun linearToSrgb(v: Float) =
        if (v <= 0.0031308f) v * 12.92f else 1.055f * v.pow(1f / 2.4f) - 0.055f
}
