import Foundation

/// The colour bands of the HSL tool, by centre hue in degrees.
enum HSLBand: Int, CaseIterable, Identifiable {
    case red, orange, yellow, green, aqua, blue, purple, magenta

    var id: Self { self }
    var title: String { "\(self)".capitalized }

    static let centers: [Double] = [0, 30, 60, 120, 180, 240, 270, 300]
    static let zeros = [Double](repeating: 0, count: 8)

    var hue: Double { Self.centers[rawValue] }
}

/// Highlights, shadows and HSL baked into one 3D LUT, so the same maths runs on iOS and Android
/// (ToneColor.kt) and the GPU applies it in a single lookup. Rebuilt only when those sliders change.
enum ToneColor {
    private struct Settings: Hashable {
        var highlights: Double
        var shadows: Double
        var hue: [Double]
        var saturation: [Double]
        var luminance: [Double]
    }

    private static let size = 33
    private static let lock = NSLock()
    private static var cache: [Settings: LUT] = [:]

    /// The LUT for this edit, or nil when highlights, shadows and HSL are all untouched.
    static func lut(for edit: EditState) -> LUT? {
        guard edit.highlights != 0 || edit.shadows != 0 || edit.hasHSL else { return nil }
        let settings = Settings(
            highlights: edit.highlights, shadows: edit.shadows,
            hue: edit.hslHue, saturation: edit.hslSaturation, luminance: edit.hslLuminance
        )
        lock.lock()
        if let cached = cache[settings] {
            lock.unlock()
            return cached
        }
        lock.unlock()

        let lut = build(settings)
        lock.lock()
        if cache.count > 16 { cache.removeAll() }
        cache[settings] = lut
        lock.unlock()
        return lut
    }

    private static func build(_ s: Settings) -> LUT {
        var values = [Float]()
        values.reserveCapacity(size * size * size * 4)
        let step = 1 / Double(size - 1)
        for b in 0..<size {
            for g in 0..<size {
                for r in 0..<size {
                    let out = transform(Double(r) * step, Double(g) * step, Double(b) * step, s)
                    values.append(Float(out.0))
                    values.append(Float(out.1))
                    values.append(Float(out.2))
                    values.append(1)
                }
            }
        }
        return LUT(id: "tone", name: "Tone", dimension: size, data: values.withUnsafeBufferPointer { Data(buffer: $0) })
    }

    /// sRGB in, sRGB out. Keep in step with ToneColor.kt.
    private static func transform(_ r0: Double, _ g0: Double, _ b0: Double, _ s: Settings) -> (Double, Double, Double) {
        var r = r0, g = g0, b = b0

        // Highlights and shadows move luminance along two bumps that leave pure black and white alone,
        // then scale the colour to match so hues don't shift.
        if s.highlights != 0 || s.shadows != 0 {
            let l = 0.2126 * r + 0.7152 * g + 0.0722 * b
            let shadowBump = 6.75 * l * (1 - l) * (1 - l)
            let highlightBump = 6.75 * l * l * (1 - l)
            let newL = min(max(l + 0.25 * (s.shadows * shadowBump + s.highlights * highlightBump), 0), 1)
            if l > 1e-4 {
                let k = newL / l
                r = min(r * k, 1)
                g = min(g * k, 1)
                b = min(b * k, 1)
            }
        }

        if s.hue != HSLBand.zeros || s.saturation != HSLBand.zeros || s.luminance != HSLBand.zeros {
            var (h, sat, lum) = rgbToHSL(r, g, b)
            // Blend the two bands either side of this hue.
            let centers = HSLBand.centers
            var i = centers.count - 1
            for j in 0..<centers.count where centers[j] <= h { i = j }
            let next = (i + 1) % centers.count
            let span = (next == 0 ? 360 : centers[next]) - centers[i]
            var t = (h - centers[i]) / span
            t = t * t * (3 - 2 * t)
            let dh = s.hue[i] * (1 - t) + s.hue[next] * t
            let ds = s.saturation[i] * (1 - t) + s.saturation[next] * t
            let dl = s.luminance[i] * (1 - t) + s.luminance[next] * t

            h = (h + dh * 30).truncatingRemainder(dividingBy: 360)
            if h < 0 { h += 360 }
            let originalSat = sat
            sat = min(max(sat * (1 + ds), 0), 1)
            lum = min(max(lum + dl * 0.2 * originalSat, 0), 1) // greys have no colour, so they don't move
            (r, g, b) = hslToRGB(h, sat, lum)
        }
        return (r, g, b)
    }

    private static func rgbToHSL(_ r: Double, _ g: Double, _ b: Double) -> (Double, Double, Double) {
        let maxV = max(r, g, b), minV = min(r, g, b)
        let l = (maxV + minV) / 2
        let d = maxV - minV
        guard d > 1e-6 else { return (0, 0, l) }
        let s = l > 0.5 ? d / (2 - maxV - minV) : d / (maxV + minV)
        var h: Double
        if maxV == r {
            h = (g - b) / d + (g < b ? 6 : 0)
        } else if maxV == g {
            h = (b - r) / d + 2
        } else {
            h = (r - g) / d + 4
        }
        h *= 60
        return (h, s, l)
    }

    private static func hslToRGB(_ h: Double, _ s: Double, _ l: Double) -> (Double, Double, Double) {
        guard s > 1e-6 else { return (l, l, l) }
        let q = l < 0.5 ? l * (1 + s) : l + s - l * s
        let p = 2 * l - q
        func channel(_ t0: Double) -> Double {
            var t = t0
            if t < 0 { t += 1 }
            if t > 1 { t -= 1 }
            if t < 1.0 / 6 { return p + (q - p) * 6 * t }
            if t < 0.5 { return q }
            if t < 2.0 / 3 { return p + (q - p) * (2.0 / 3 - t) * 6 }
            return p
        }
        let hk = h / 360
        return (channel(hk + 1.0 / 3), channel(hk), channel(hk - 1.0 / 3))
    }
}
