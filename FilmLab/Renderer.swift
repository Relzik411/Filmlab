import CoreImage
import CoreImage.CIFilterBuiltins

/// Turns Core Image recipes into pixels. `CIContext` is thread-safe, so one renderer is shared.
final class Renderer: @unchecked Sendable {
    enum RenderError: LocalizedError {
        case encodeFailed
        var errorDescription: String? { "Couldn't create the edited photo." }
    }

    private let context = CIContext(options: [.cacheIntermediates: false])
    private let colorSpace = CGColorSpace(name: CGColorSpace.displayP3)!

    func cgImage(_ image: CIImage) -> CGImage? {
        context.createCGImage(image, from: image.extent, format: .RGBA8, colorSpace: colorSpace)
    }

    /// A smaller copy, rendered once, so slider changes only have to process preview-sized pixels.
    func downscaled(_ image: CIImage, maxSide: CGFloat) -> CIImage {
        let scale = min(1, maxSide / max(image.extent.width, image.extent.height))
        let f = CIFilter.lanczosScaleTransform()
        f.inputImage = image
        f.scale = Float(scale)
        f.aspectRatio = 1
        guard let output = f.outputImage, let cg = cgImage(output) else { return image }
        return CIImage(cgImage: cg)
    }

    /// Full-size HEIC, or JPEG where HEIC encoding isn't available (some simulators).
    func encoded(_ image: CIImage) throws -> Data {
        if let data = context.heifRepresentation(of: image, format: .RGBA8, colorSpace: colorSpace, options: [:]) {
            return data
        }
        if let data = context.jpegRepresentation(of: image, colorSpace: colorSpace, options: [:]) {
            return data
        }
        throw RenderError.encodeFailed
    }
}
