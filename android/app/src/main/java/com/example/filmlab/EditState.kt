package com.example.filmlab

/** Everything the user has changed. The original photo is never modified; edits are re-applied from this. */
data class EditState(
    val lutId: String? = null,
    val intensity: Float = 1f,
    val exposure: Float = 0f,
    val contrast: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val saturation: Float = 0f,
    val warmth: Float = 0f,
    val tint: Float = 0f,
    val fade: Float = 0f,
    val sharpen: Float = 0f,
    val vignette: Float = 0f,
    val grain: Float = 0f,
    /** Per colour band (see [HslBand]), each -1..1. */
    val hslHue: List<Float> = HslBand.zeros,
    val hslSaturation: List<Float> = HslBand.zeros,
    val hslLuminance: List<Float> = HslBand.zeros,
    /** Index into [LightLeak.styles], or null for none. */
    val leak: Int? = null,
    val leakAmount: Float = 0.8f,
    /** Which corner the leak comes from; see [LightLeak.placed]. */
    val leakPlacement: Int = 0,
    val crop: CropState = CropState(),
) {
    val hasHsl get() = hslHue != HslBand.zeros || hslSaturation != HslBand.zeros || hslLuminance != HslBand.zeros

    /** The same edit with the Adjust tab's sliders, including HSL, back at zero. */
    fun withoutAdjustments() = Adjustment.entries
        .fold(this) { edit, adjustment -> adjustment.set(edit, 0f) }
        .copy(hslHue = HslBand.zeros, hslSaturation = HslBand.zeros, hslLuminance = HslBand.zeros)
}

enum class Adjustment(
    val title: String,
    val range: ClosedFloatingPointRange<Float>,
    val get: (EditState) -> Float,
    val set: (EditState, Float) -> EditState,
) {
    Exposure("Exposure", -1f..1f, { it.exposure }, { e, v -> e.copy(exposure = v) }),
    Contrast("Contrast", -1f..1f, { it.contrast }, { e, v -> e.copy(contrast = v) }),
    Highlights("Highlights", -1f..1f, { it.highlights }, { e, v -> e.copy(highlights = v) }),
    Shadows("Shadows", -1f..1f, { it.shadows }, { e, v -> e.copy(shadows = v) }),
    Saturation("Saturation", -1f..1f, { it.saturation }, { e, v -> e.copy(saturation = v) }),
    Warmth("Warmth", -1f..1f, { it.warmth }, { e, v -> e.copy(warmth = v) }),
    Tint("Tint", -1f..1f, { it.tint }, { e, v -> e.copy(tint = v) }),
    Fade("Fade", 0f..1f, { it.fade }, { e, v -> e.copy(fade = v) }),
    Sharpen("Sharpen", 0f..1f, { it.sharpen }, { e, v -> e.copy(sharpen = v) }),
    Vignette("Vignette", 0f..1f, { it.vignette }, { e, v -> e.copy(vignette = v) }),
    Grain("Grain", 0f..1f, { it.grain }, { e, v -> e.copy(grain = v) }),
}
