package com.example.filmlab

import kotlin.math.max
import kotlin.math.min

/** The colour bands of the HSL tool, by centre hue in degrees. */
enum class HslBand(val title: String, val hue: Float) {
    Red("Red", 0f), Orange("Orange", 30f), Yellow("Yellow", 60f), Green("Green", 120f),
    Aqua("Aqua", 180f), Blue("Blue", 240f), Purple("Purple", 270f), Magenta("Magenta", 300f);

    companion object {
        val zeros = List(8) { 0f }
        val centers = entries.map { it.hue.toDouble() }
    }
}

/**
 * Highlights, shadows and HSL baked into one 3D LUT, so the same maths runs on iOS (ToneColor.swift)
 * and Android, and each pixel needs a single lookup. Rebuilt only when those sliders change.
 */
object ToneColor {
    private data class Settings(
        val highlights: Float,
        val shadows: Float,
        val hue: List<Float>,
        val saturation: List<Float>,
        val luminance: List<Float>,
    )

    private const val SIZE = 33
    private val cache = HashMap<Settings, Lut>()

    /** The LUT for this edit, or null when highlights, shadows and HSL are all untouched. */
    fun lut(edit: EditState): Lut? {
        if (edit.highlights == 0f && edit.shadows == 0f && !edit.hasHsl) return null
        val settings = Settings(edit.highlights, edit.shadows, edit.hslHue, edit.hslSaturation, edit.hslLuminance)
        synchronized(cache) { cache[settings]?.let { return it } }
        val lut = build(settings)
        synchronized(cache) {
            if (cache.size > 16) cache.clear()
            cache[settings] = lut
        }
        return lut
    }

    private fun build(s: Settings): Lut {
        val table = FloatArray(SIZE * SIZE * SIZE * 3)
        val out = DoubleArray(3)
        val step = 1.0 / (SIZE - 1)
        var i = 0
        for (b in 0 until SIZE) for (g in 0 until SIZE) for (r in 0 until SIZE) {
            transform(r * step, g * step, b * step, s, out)
            table[i++] = out[0].toFloat()
            table[i++] = out[1].toFloat()
            table[i++] = out[2].toFloat()
        }
        return Lut("tone", "Tone", SIZE, table)
    }

    /** sRGB in, sRGB out. Keep in step with ToneColor.swift. */
    private fun transform(r0: Double, g0: Double, b0: Double, s: Settings, out: DoubleArray) {
        var r = r0
        var g = g0
        var b = b0

        // Highlights and shadows move luminance along two bumps that leave pure black and white alone,
        // then scale the colour to match so hues don't shift.
        if (s.highlights != 0f || s.shadows != 0f) {
            val l = 0.2126 * r + 0.7152 * g + 0.0722 * b
            val shadowBump = 6.75 * l * (1 - l) * (1 - l)
            val highlightBump = 6.75 * l * l * (1 - l)
            val newL = (l + 0.25 * (s.shadows * shadowBump + s.highlights * highlightBump)).coerceIn(0.0, 1.0)
            if (l > 1e-4) {
                val k = newL / l
                r = min(r * k, 1.0)
                g = min(g * k, 1.0)
                b = min(b * k, 1.0)
            }
        }

        if (s.hue != HslBand.zeros || s.saturation != HslBand.zeros || s.luminance != HslBand.zeros) {
            rgbToHsl(r, g, b, out)
            var h = out[0]
            var sat = out[1]
            var lum = out[2]
            // Blend the two bands either side of this hue.
            val centers = HslBand.centers
            var i = centers.size - 1
            for (j in centers.indices) if (centers[j] <= h) i = j
            val next = (i + 1) % centers.size
            val span = (if (next == 0) 360.0 else centers[next]) - centers[i]
            var t = (h - centers[i]) / span
            t = t * t * (3 - 2 * t)
            val dh = s.hue[i] * (1 - t) + s.hue[next] * t
            val ds = s.saturation[i] * (1 - t) + s.saturation[next] * t
            val dl = s.luminance[i] * (1 - t) + s.luminance[next] * t

            h = (h + dh * 30) % 360
            if (h < 0) h += 360
            val originalSat = sat
            sat = (sat * (1 + ds)).coerceIn(0.0, 1.0)
            lum = (lum + dl * 0.2 * originalSat).coerceIn(0.0, 1.0) // greys have no colour, so they don't move
            hslToRgb(h, sat, lum, out)
            return
        }
        out[0] = r
        out[1] = g
        out[2] = b
    }

    private fun rgbToHsl(r: Double, g: Double, b: Double, out: DoubleArray) {
        val maxV = max(r, max(g, b))
        val minV = min(r, min(g, b))
        val l = (maxV + minV) / 2
        val d = maxV - minV
        if (d <= 1e-6) {
            out[0] = 0.0; out[1] = 0.0; out[2] = l
            return
        }
        val s = if (l > 0.5) d / (2 - maxV - minV) else d / (maxV + minV)
        var h = when (maxV) {
            r -> (g - b) / d + (if (g < b) 6 else 0)
            g -> (b - r) / d + 2
            else -> (r - g) / d + 4
        }
        h *= 60
        out[0] = h; out[1] = s; out[2] = l
    }

    private fun hslToRgb(h: Double, s: Double, l: Double, out: DoubleArray) {
        if (s <= 1e-6) {
            out[0] = l; out[1] = l; out[2] = l
            return
        }
        val q = if (l < 0.5) l * (1 + s) else l + s - l * s
        val p = 2 * l - q
        fun channel(t0: Double): Double {
            var t = t0
            if (t < 0) t += 1
            if (t > 1) t -= 1
            return when {
                t < 1.0 / 6 -> p + (q - p) * 6 * t
                t < 0.5 -> q
                t < 2.0 / 3 -> p + (q - p) * (2.0 / 3 - t) * 6
                else -> p
            }
        }
        val hk = h / 360
        out[0] = channel(hk + 1.0 / 3)
        out[1] = channel(hk)
        out[2] = channel(hk - 1.0 / 3)
    }
}
