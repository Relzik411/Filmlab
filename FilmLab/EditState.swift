import Foundation

/// Everything the user has changed. The original photo is never modified; edits are re-applied from this.
struct EditState: Equatable, Sendable, Codable {
    var lutID: String?
    var intensity: Double = 1
    var exposure: Double = 0
    var contrast: Double = 0
    var highlights: Double = 0
    var shadows: Double = 0
    var saturation: Double = 0
    var warmth: Double = 0
    var tint: Double = 0
    var fade: Double = 0
    var sharpen: Double = 0
    var vignette: Double = 0
    var grain: Double = 0
    /// Per colour band (see `HSLBand`), each -1...1.
    var hslHue = HSLBand.zeros
    var hslSaturation = HSLBand.zeros
    var hslLuminance = HSLBand.zeros
    /// Index into `LightLeak.styles`, or nil for none.
    var leak: Int?
    var leakAmount: Double = 0.8
    /// Which corner the leak comes from; see `LightLeak.placed(_:)`.
    var leakPlacement = 0
    var crop = CropState()

    /// The same edit with the Adjust tab's sliders, including HSL, back at zero.
    func withoutAdjustments() -> EditState {
        var copy = self
        for adjustment in Adjustment.allCases {
            copy[keyPath: adjustment.keyPath] = 0
        }
        copy.hslHue = HSLBand.zeros
        copy.hslSaturation = HSLBand.zeros
        copy.hslLuminance = HSLBand.zeros
        return copy
    }

    var hasHSL: Bool {
        hslHue != HSLBand.zeros || hslSaturation != HSLBand.zeros || hslLuminance != HSLBand.zeros
    }
}

extension EditState {
    /// Decodes saved edits leniently: anything missing (for example from an older version) keeps its default.
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.init()
        func value<T: Decodable>(_ key: CodingKeys, _ fallback: T) throws -> T {
            try c.decodeIfPresent(T.self, forKey: key) ?? fallback
        }
        lutID = try c.decodeIfPresent(String.self, forKey: .lutID)
        intensity = try value(.intensity, intensity)
        exposure = try value(.exposure, exposure)
        contrast = try value(.contrast, contrast)
        highlights = try value(.highlights, highlights)
        shadows = try value(.shadows, shadows)
        saturation = try value(.saturation, saturation)
        warmth = try value(.warmth, warmth)
        tint = try value(.tint, tint)
        fade = try value(.fade, fade)
        sharpen = try value(.sharpen, sharpen)
        vignette = try value(.vignette, vignette)
        grain = try value(.grain, grain)
        hslHue = try value(.hslHue, hslHue)
        hslSaturation = try value(.hslSaturation, hslSaturation)
        hslLuminance = try value(.hslLuminance, hslLuminance)
        leak = try c.decodeIfPresent(Int.self, forKey: .leak)
        leakAmount = try value(.leakAmount, leakAmount)
        leakPlacement = try value(.leakPlacement, leakPlacement)
        crop = try value(.crop, crop)
        if hslHue.count != HSLBand.allCases.count { hslHue = HSLBand.zeros }
        if hslSaturation.count != HSLBand.allCases.count { hslSaturation = HSLBand.zeros }
        if hslLuminance.count != HSLBand.allCases.count { hslLuminance = HSLBand.zeros }
    }
}

enum Adjustment: String, CaseIterable, Identifiable {
    case exposure, contrast, highlights, shadows, saturation, warmth, tint, fade, sharpen, vignette, grain

    var id: Self { self }

    var title: String { rawValue.capitalized }

    var range: ClosedRange<Double> {
        switch self {
        case .exposure, .contrast, .highlights, .shadows, .saturation, .warmth, .tint: -1...1
        case .fade, .sharpen, .vignette, .grain: 0...1
        }
    }

    var symbol: String {
        switch self {
        case .exposure: "plusminus.circle"
        case .contrast: "circle.lefthalf.filled"
        case .highlights: "sun.max"
        case .shadows: "moon"
        case .saturation: "drop"
        case .warmth: "thermometer.medium"
        case .tint: "eyedropper.halffull"
        case .fade: "sun.haze"
        case .sharpen: "scope"
        case .vignette: "camera.aperture"
        case .grain: "circle.dotted"
        }
    }

    var keyPath: WritableKeyPath<EditState, Double> {
        switch self {
        case .exposure: \.exposure
        case .contrast: \.contrast
        case .highlights: \.highlights
        case .shadows: \.shadows
        case .saturation: \.saturation
        case .warmth: \.warmth
        case .tint: \.tint
        case .fade: \.fade
        case .sharpen: \.sharpen
        case .vignette: \.vignette
        case .grain: \.grain
        }
    }
}
