import CoreGraphics
import CoreImage

enum CropAspect: String, CaseIterable, Identifiable, Sendable, Codable {
    case free = "Free"
    case original = "Original"
    case square = "1:1"
    case portrait = "4:5"
    case story = "9:16"
    case wide = "16:9"

    var id: Self { self }

    /// Width divided by height in pixels, or nil when the crop is free.
    func ratio(for imageSize: CGSize) -> CGFloat? {
        switch self {
        case .free: nil
        case .original: imageSize.width / imageSize.height
        case .square: 1
        case .portrait: 4.0 / 5.0
        case .story: 9.0 / 16.0
        case .wide: 16.0 / 9.0
        }
    }
}

/// Rotation, flip, straighten and crop. Applied before any colour work.
struct CropState: Equatable, Sendable, Codable {
    /// Clockwise quarter turns.
    var quarterTurns = 0
    var flipped = false
    /// Degrees, positive is clockwise.
    var straighten: Double = 0
    /// Normalized to the straightened image, origin at the top left.
    var rect = CropMath.full
    var aspect = CropAspect.free
}

/// Crop-box arithmetic in normalized coordinates (0...1, origin top left).
enum CropMath {
    static let full = CGRect(x: 0, y: 0, width: 1, height: 1)
    static let minSize: CGFloat = 0.1

    /// A pixel width/height ratio expressed in normalized units for an image of this size.
    static func normalizedRatio(_ ratio: CGFloat?, imageSize: CGSize) -> CGFloat? {
        guard let ratio, imageSize.width > 0 else { return nil }
        return ratio * imageSize.height / imageSize.width
    }

    /// The largest centred box with this normalized ratio.
    static func fitted(_ k: CGFloat?) -> CGRect {
        guard let k else { return full }
        let (w, h): (CGFloat, CGFloat) = k >= 1 ? (1, 1 / k) : (k, 1)
        return CGRect(x: (1 - w) / 2, y: (1 - h) / 2, width: w, height: h)
    }

    /// Drags one corner (0 top left, 1 top right, 2 bottom right, 3 bottom left) while the opposite stays put.
    static func dragCorner(_ corner: Int, of start: CGRect, by t: CGSize, ratio k: CGFloat?) -> CGRect {
        let left = corner == 0 || corner == 3
        let top = corner < 2
        let anchor = CGPoint(x: left ? start.maxX : start.minX, y: top ? start.maxY : start.minY)
        let moving = CGPoint(x: (left ? start.minX : start.maxX) + t.width, y: (top ? start.minY : start.maxY) + t.height)

        let maxW = left ? anchor.x : 1 - anchor.x
        let maxH = top ? anchor.y : 1 - anchor.y
        var w = min(max(abs(moving.x - anchor.x), minSize), maxW)
        var h = min(max(abs(moving.y - anchor.y), minSize), maxH)
        if (left ? moving.x > anchor.x : moving.x < anchor.x) { w = minSize }
        if (top ? moving.y > anchor.y : moving.y < anchor.y) { h = minSize }

        if let k {
            // Follow whichever side was pulled further, then fit inside the image.
            if w / k > h { h = w / k } else { w = h * k }
            if w > maxW { w = maxW; h = w / k }
            if h > maxH { h = maxH; w = h * k }
        }
        return CGRect(x: left ? anchor.x - w : anchor.x, y: top ? anchor.y - h : anchor.y, width: w, height: h)
    }

    static func move(_ start: CGRect, by t: CGSize) -> CGRect {
        var rect = start
        rect.origin.x = min(max(start.minX + t.width, 0), 1 - start.width)
        rect.origin.y = min(max(start.minY + t.height, 0), 1 - start.height)
        return rect
    }
}

enum Geometry {
    /// Size of the frame the crop box lives in: the photo after quarter turns.
    static func frameSize(of size: CGSize, crop: CropState) -> CGSize {
        crop.quarterTurns % 2 == 0 ? size : CGSize(width: size.height, height: size.width)
    }

    /// Flip, quarter turns and straighten, then the crop unless `cropping` is false (while editing the crop).
    /// The result's extent starts at zero.
    static func apply(_ crop: CropState, to input: CIImage, cropping: Bool) -> CIImage {
        var image = atOrigin(input)
        if crop.flipped {
            image = atOrigin(image.transformed(by: CGAffineTransform(scaleX: -1, y: 1)))
        }
        let turns = ((crop.quarterTurns % 4) + 4) % 4
        if turns != 0 {
            // Core Image's y axis points up, so a negative angle turns clockwise.
            image = atOrigin(image.transformed(by: CGAffineTransform(rotationAngle: -CGFloat(turns) * .pi / 2)))
        }
        if crop.straighten != 0 {
            let frame = image.extent
            let angle = CGFloat(crop.straighten) * .pi / 180
            // Scale up just enough that the turned photo still covers the frame, with no empty corners.
            let longToShort = max(frame.width / frame.height, frame.height / frame.width)
            let scale = abs(cos(angle)) + longToShort * abs(sin(angle))
            let transform = CGAffineTransform(translationX: frame.midX, y: frame.midY)
                .rotated(by: -angle)
                .scaledBy(x: scale, y: scale)
                .translatedBy(x: -frame.midX, y: -frame.midY)
            image = image.clampedToExtent().transformed(by: transform).cropped(to: frame)
        }
        if cropping && crop.rect != CropMath.full {
            let e = image.extent
            let r = crop.rect
            let box = CGRect(
                x: (e.minX + r.minX * e.width).rounded(),
                y: (e.minY + (1 - r.maxY) * e.height).rounded(),
                width: max(1, (r.width * e.width).rounded()),
                height: max(1, (r.height * e.height).rounded())
            )
            image = atOrigin(image.cropped(to: box))
        }
        return image
    }

    private static func atOrigin(_ image: CIImage) -> CIImage {
        let e = image.extent
        let aligned = image.transformed(by: CGAffineTransform(translationX: -e.minX, y: -e.minY))
        // Turning by quarter turns can leave tiny floating-point fractions on the extent.
        return aligned.cropped(to: CGRect(x: 0, y: 0, width: e.width.rounded(), height: e.height.rounded()))
    }
}
