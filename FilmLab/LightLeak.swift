import CoreImage
import CoreImage.CIFilterBuiltins

/// A light leak drawn from a few soft coloured glows, screened over the photo.
/// Generated rather than loaded from images, so there is nothing to license.
/// Keep in step with LightLeak.kt on Android.
struct LightLeak: Identifiable, Sendable {
    struct Glow: Sendable {
        /// Centre, as a fraction of width and height from the top left. May sit outside the photo.
        let x: CGFloat
        let y: CGFloat
        /// Radius, as a fraction of the photo's longest side.
        let radius: CGFloat
        let red: CGFloat
        let green: CGFloat
        let blue: CGFloat
        let strength: CGFloat
    }

    let id: Int
    let name: String
    let glows: [Glow]

    static let styles: [LightLeak] = [
        LightLeak(id: 0, name: "Amber", glows: [
            Glow(x: 1.05, y: 0.15, radius: 0.75, red: 1, green: 0.55, blue: 0.15, strength: 0.9),
            Glow(x: 0.95, y: 0.65, radius: 0.45, red: 1, green: 0.3, blue: 0.1, strength: 0.6),
        ]),
        LightLeak(id: 1, name: "Rose", glows: [
            Glow(x: -0.05, y: 0.1, radius: 0.7, red: 1, green: 0.35, blue: 0.45, strength: 0.85),
            Glow(x: 0.1, y: 0.9, radius: 0.4, red: 1, green: 0.6, blue: 0.3, strength: 0.5),
        ]),
        LightLeak(id: 2, name: "Sunset", glows: [
            Glow(x: 1.0, y: 1.0, radius: 0.85, red: 1, green: 0.45, blue: 0.1, strength: 0.9),
            Glow(x: 0.0, y: 0.0, radius: 0.5, red: 0.9, green: 0.2, blue: 0.4, strength: 0.55),
        ]),
        LightLeak(id: 3, name: "Haze", glows: [
            Glow(x: 0.5, y: -0.15, radius: 0.95, red: 1, green: 0.85, blue: 0.6, strength: 0.7),
        ]),
        LightLeak(id: 4, name: "Prism", glows: [
            Glow(x: 1.0, y: 0.5, radius: 0.55, red: 0.4, green: 0.6, blue: 1, strength: 0.7),
            Glow(x: 0.85, y: 0.15, radius: 0.35, red: 1, green: 0.3, blue: 0.6, strength: 0.6),
        ]),
    ]

    static let placementCount = 4

    /// The glows mirrored for a placement: 0 as designed, 1 mirrored left-right, 2 top-bottom, 3 both.
    func placed(_ placement: Int) -> [Glow] {
        glows.map { g in
            Glow(
                x: placement & 1 == 0 ? g.x : 1 - g.x,
                y: placement & 2 == 0 ? g.y : 1 - g.y,
                radius: g.radius, red: g.red, green: g.green, blue: g.blue, strength: g.strength
            )
        }
    }

    func apply(to image: CIImage, amount: Double, placement: Int) -> CIImage {
        let e = image.extent
        let side = max(e.width, e.height)
        let sRGB = CGColorSpace(name: CGColorSpace.sRGB)!
        var leak: CIImage?

        for glow in placed(placement) {
            let k = glow.strength * CGFloat(amount)
            let gradient = CIFilter.radialGradient()
            gradient.center = CGPoint(x: e.minX + glow.x * e.width, y: e.minY + (1 - glow.y) * e.height)
            gradient.radius0 = 0
            gradient.radius1 = Float(glow.radius * side)
            gradient.color0 = CIColor(red: glow.red * k, green: glow.green * k, blue: glow.blue * k, alpha: 1, colorSpace: sRGB)
                ?? CIColor(red: glow.red * k, green: glow.green * k, blue: glow.blue * k)
            gradient.color1 = CIColor(red: 0, green: 0, blue: 0)
            guard let glowImage = gradient.outputImage?.cropped(to: e) else { continue }
            leak = leak.map { Self.screen(glowImage, over: $0) } ?? glowImage
        }
        guard let leak else { return image }
        return Self.screen(leak, over: image)
    }

    private static func screen(_ top: CIImage, over bottom: CIImage) -> CIImage {
        let f = CIFilter.screenBlendMode()
        f.inputImage = top
        f.backgroundImage = bottom
        return f.outputImage ?? bottom
    }
}
