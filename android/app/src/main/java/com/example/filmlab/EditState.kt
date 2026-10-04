package com.example.filmlab

/** Everything the user has changed. The original photo is never modified; edits are re-applied from this. */
data class EditState(
    val lutId: String? = null,
    val intensity: Float = 1f,
    val exposure: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val warmth: Float = 0f,
    val fade: Float = 0f,
    val vignette: Float = 0f,
    val grain: Float = 0f,
) {
    fun withoutAdjustments() = EditState(lutId = lutId, intensity = intensity)
}

enum class Adjustment(
    val title: String,
    val range: ClosedFloatingPointRange<Float>,
    val get: (EditState) -> Float,
    val set: (EditState, Float) -> EditState,
) {
    Exposure("Exposure", -1f..1f, { it.exposure }, { e, v -> e.copy(exposure = v) }),
    Contrast("Contrast", -1f..1f, { it.contrast }, { e, v -> e.copy(contrast = v) }),
    Saturation("Saturation", -1f..1f, { it.saturation }, { e, v -> e.copy(saturation = v) }),
    Warmth("Warmth", -1f..1f, { it.warmth }, { e, v -> e.copy(warmth = v) }),
    Fade("Fade", 0f..1f, { it.fade }, { e, v -> e.copy(fade = v) }),
    Vignette("Vignette", 0f..1f, { it.vignette }, { e, v -> e.copy(vignette = v) }),
    Grain("Grain", 0f..1f, { it.grain }, { e, v -> e.copy(grain = v) }),
}
