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
