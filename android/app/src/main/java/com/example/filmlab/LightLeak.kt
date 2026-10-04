package com.example.filmlab

/**
 * A light leak drawn from a few soft coloured glows, screened over the photo.
 * Generated rather than loaded from images, so there is nothing to license.
 * Keep in step with LightLeak.swift on iOS.
 */
class LightLeak(val id: Int, val name: String, val glows: List<Glow>) {
    /**
     * [x] and [y] are the centre as a fraction of width and height from the top left (may sit outside
     * the photo); [radius] is a fraction of the photo's longest side.
     */
    data class Glow(
        val x: Float, val y: Float, val radius: Float,
        val red: Float, val green: Float, val blue: Float, val strength: Float,
    )

    /** The glows mirrored for a placement: 0 as designed, 1 mirrored left-right, 2 top-bottom, 3 both. */
    fun placed(placement: Int) = glows.map {
        it.copy(
            x = if ((placement and 1) == 0) it.x else 1 - it.x,
            y = if ((placement and 2) == 0) it.y else 1 - it.y,
        )
    }

    companion object {
        const val PLACEMENT_COUNT = 4

        val styles = listOf(
            LightLeak(0, "Amber", listOf(
                Glow(1.05f, 0.15f, 0.75f, 1f, 0.55f, 0.15f, 0.9f),
                Glow(0.95f, 0.65f, 0.45f, 1f, 0.3f, 0.1f, 0.6f),
            )),
            LightLeak(1, "Rose", listOf(
                Glow(-0.05f, 0.1f, 0.7f, 1f, 0.35f, 0.45f, 0.85f),
                Glow(0.1f, 0.9f, 0.4f, 1f, 0.6f, 0.3f, 0.5f),
            )),
            LightLeak(2, "Sunset", listOf(
                Glow(1.0f, 1.0f, 0.85f, 1f, 0.45f, 0.1f, 0.9f),
                Glow(0.0f, 0.0f, 0.5f, 0.9f, 0.2f, 0.4f, 0.55f),
            )),
            LightLeak(3, "Haze", listOf(
                Glow(0.5f, -0.15f, 0.95f, 1f, 0.85f, 0.6f, 0.7f),
            )),
            LightLeak(4, "Prism", listOf(
                Glow(1.0f, 0.5f, 0.55f, 0.4f, 0.6f, 1f, 0.7f),
                Glow(0.85f, 0.15f, 0.35f, 1f, 0.3f, 0.6f, 0.6f),
            )),
        )
    }
}
