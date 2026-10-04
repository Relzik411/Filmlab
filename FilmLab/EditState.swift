import Foundation

/// Everything the user has changed. The original photo is never modified; edits are re-applied from this.
struct EditState: Equatable, Sendable {
    var lutID: String?
    var intensity: Double = 1
    var exposure: Double = 0
    var contrast: Double = 0
    var saturation: Double = 0
    var warmth: Double = 0
    var fade: Double = 0
    var vignette: Double = 0
    var grain: Double = 0
    /// Index into `LightLeak.styles`, or nil for none.
    var leak: Int?
    var leakAmount: Double = 0.8
    /// Which corner the leak comes from; see `LightLeak.placed(_:)`.
    var leakPlacement = 0
    var crop = CropState()

    /// The same edit with the Adjust tab's sliders back at zero.
    func withoutAdjustments() -> EditState {
        var copy = self
        for adjustment in Adjustment.allCases {
            copy[keyPath: adjustment.keyPath] = 0
        }
        return copy
    }
}

enum Adjustment: String, CaseIterable, Identifiable {
    case exposure, contrast, saturation, warmth, fade, vignette, grain

    var id: Self { self }

    var title: String { rawValue.capitalized }

    var range: ClosedRange<Double> {
        switch self {
        case .exposure, .contrast, .saturation, .warmth: -1...1
        case .fade, .vignette, .grain: 0...1
        }
    }

    var symbol: String {
        switch self {
        case .exposure: "plusminus.circle"
        case .contrast: "circle.lefthalf.filled"
        case .saturation: "drop"
        case .warmth: "thermometer.medium"
        case .fade: "sun.haze"
        case .vignette: "camera.aperture"
        case .grain: "circle.dotted"
        }
    }

    var keyPath: WritableKeyPath<EditState, Double> {
        switch self {
        case .exposure: \.exposure
        case .contrast: \.contrast
        case .saturation: \.saturation
        case .warmth: \.warmth
        case .fade: \.fade
        case .vignette: \.vignette
        case .grain: \.grain
        }
    }
}
