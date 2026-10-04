import CoreImage
import UIKit

enum FrameStyle: String, CaseIterable, Identifiable, Codable, Sendable {
    case none, instant, thin, gallery, square, film

    var id: Self { self }
    var title: String { self == .none ? "None" : rawValue.capitalized }
    /// Whether the border colour can be chosen (the film strip is always black).
    var hasColor: Bool { self != .none && self != .film }
}

enum FrameColor: String, CaseIterable, Identifiable, Codable, Sendable {
    case white, cream, black

    var id: Self { self }
    var title: String { rawValue.capitalized }

    var rgb: (CGFloat, CGFloat, CGFloat) {
        switch self {
        case .white: (1, 1, 1)
        case .cream: (0.953, 0.929, 0.878)
        case .black: (0.067, 0.067, 0.067)
        }
    }
}

/// Borders, the film strip and the date stamp, added after every other effect.
/// Sizes are fractions of the photo, so the preview matches the export. Keep in step with Frame.kt.
enum Frames {
    struct Layout {
        let canvas: CGSize
        /// Where the photo sits, origin at the top left.
        let photo: CGRect
    }

    static func layout(_ style: FrameStyle, for size: CGSize) -> Layout {
        let w = size.width, h = size.height
        let short = min(w, h), long = max(w, h)
        func edges(_ left: CGFloat, _ top: CGFloat, _ right: CGFloat, _ bottom: CGFloat) -> Layout {
            Layout(
                canvas: CGSize(width: (w + left + right).rounded(), height: (h + top + bottom).rounded()),
                photo: CGRect(x: left.rounded(), y: top.rounded(), width: w, height: h)
            )
        }
        switch style {
        case .none: return edges(0, 0, 0, 0)
        case .instant: return edges(0.06 * short, 0.06 * short, 0.06 * short, 0.24 * short)
        case .thin: return edges(0.025 * short, 0.025 * short, 0.025 * short, 0.025 * short)
        case .gallery: return edges(0.1 * short, 0.1 * short, 0.1 * short, 0.1 * short)
        case .square:
            let side = (long * 1.08).rounded()
            return Layout(
                canvas: CGSize(width: side, height: side),
                photo: CGRect(x: ((side - w) / 2).rounded(), y: ((side - h) / 2).rounded(), width: w, height: h)
            )
        case .film: return edges(0.03 * short, 0.16 * short, 0.03 * short, 0.16 * short)
        }
    }

    /// "’26 10 04", the way date-stamping film cameras print it.
    static func stampText(for date: Date) -> String {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.dateFormat = "''yy MM dd"
        return f.string(from: date).replacingOccurrences(of: "'", with: "’")
    }

    static func apply(_ edit: EditState, dateText: String?, to image: CIImage) -> CIImage {
        let stamp = edit.dateStamp ? dateText : nil
        guard edit.frame != .none || stamp != nil else { return image }

        let size = image.extent.size
        let layout = layout(edit.frame, for: size)
        let canvasRect = CGRect(origin: .zero, size: layout.canvas)
        // Core Image's origin is at the bottom left.
        let origin = CGPoint(x: layout.photo.minX, y: layout.canvas.height - layout.photo.maxY)
        var out = image.transformed(by: CGAffineTransform(translationX: origin.x - image.extent.minX, y: origin.y - image.extent.minY))

        if edit.frame != .none {
            let (r, g, b): (CGFloat, CGFloat, CGFloat) = edit.frame == .film ? (0.07, 0.07, 0.07) : edit.frameColor.rgb
            let background = CIImage(color: CIColor(red: r, green: g, blue: b)).cropped(to: canvasRect)
            out = out.composited(over: background)
        }
        if let decorations = decorations(edit.frame, stamp: stamp, layout: layout) {
            out = decorations.composited(over: out)
        }
        return out.cropped(to: canvasRect)
    }

    /// Sprocket holes, edge printing and the date stamp, drawn with UIKit (safe off the main thread).
    private static func decorations(_ style: FrameStyle, stamp: String?, layout: Layout) -> CIImage? {
        guard style == .film || stamp != nil else { return nil }
        let photo = layout.photo
        let short = min(photo.width, photo.height)

        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = false
        let image = UIGraphicsImageRenderer(size: layout.canvas, format: format).image { _ in
            if style == .film {
                let holeW = 0.035 * short, holeH = 0.05 * short, spacing = 0.075 * short
                let border = 0.16 * short
                UIColor(white: 0.86, alpha: 1).setFill()
                for rowY in [border * 0.35 - holeH / 2, layout.canvas.height - border * 0.35 - holeH / 2] {
                    var x = spacing / 2
                    while x + holeW < layout.canvas.width {
                        UIBezierPath(roundedRect: CGRect(x: x, y: rowY, width: holeW, height: holeH), cornerRadius: holeW * 0.2).fill()
                        x += spacing
                    }
                }
                let edge: [NSAttributedString.Key: Any] = [
                    .font: UIFont.systemFont(ofSize: 0.032 * short, weight: .semibold),
                    .foregroundColor: UIColor(red: 0.91, green: 0.64, blue: 0.24, alpha: 1),
                    .kern: 0.006 * short,
                ]
                let textY = photo.maxY + 0.012 * short // between the photo and the sprocket holes
                ("FILMLAB 400" as NSString).draw(at: CGPoint(x: photo.minX + 0.04 * short, y: textY), withAttributes: edge)
                let number = "▸ 12A" as NSString
                let numberWidth = number.size(withAttributes: edge).width
                number.draw(at: CGPoint(x: photo.maxX - 0.04 * short - numberWidth, y: textY), withAttributes: edge)
            }
            if let stamp {
                let glow = NSShadow()
                glow.shadowColor = UIColor(red: 1, green: 0.35, blue: 0, alpha: 0.9)
                glow.shadowBlurRadius = 0.012 * short
                glow.shadowOffset = .zero
                let attributes: [NSAttributedString.Key: Any] = [
                    .font: UIFont.monospacedDigitSystemFont(ofSize: 0.05 * short, weight: .bold),
                    .foregroundColor: UIColor(red: 1, green: 0.62, blue: 0.25, alpha: 0.95),
                    .shadow: glow,
                    .kern: 0.004 * short,
                ]
                let text = stamp as NSString
                let textSize = text.size(withAttributes: attributes)
                let inset = 0.045 * short
                text.draw(
                    at: CGPoint(x: photo.maxX - inset - textSize.width, y: photo.maxY - inset - textSize.height),
                    withAttributes: attributes
                )
            }
        }
        guard let cg = image.cgImage else { return nil }
        return CIImage(cgImage: cg)
    }
}
