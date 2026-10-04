import CoreImage
import CoreImage.CIFilterBuiltins

/// Builds the Core Image recipe for an edit. Nothing is rendered here; `Renderer` does that on the GPU.
/// Every effect is sized relative to the image, so the small preview matches the full-size export.
enum FilterPipeline {
    /// `cropping: false` shows the whole straightened photo, for editing the crop box.
    static func apply(_ edit: EditState, lut: LUT?, to input: CIImage, cropping: Bool = true) -> CIImage {
        var image = Geometry.apply(edit.crop, to: input, cropping: cropping)
        let extent = image.extent

        if edit.sharpen > 0 {
            let f = CIFilter.sharpenLuminance()
            f.inputImage = image.clampedToExtent() // so the edges have neighbours too
            f.sharpness = Float(edit.sharpen) * 1.5
            f.radius = Float(1.5 * max(1, max(extent.width, extent.height) / 1500))
            image = f.outputImage?.cropped(to: extent) ?? image
        }

        // 1. Basic corrections, before the look, like correcting exposure before printing.
        if edit.exposure != 0 {
            let f = CIFilter.exposureAdjust()
            f.inputImage = image
            f.ev = Float(edit.exposure)
            image = f.outputImage ?? image
        }
        if edit.contrast != 0 || edit.saturation != 0 {
            let f = CIFilter.colorControls()
            f.inputImage = image
            f.brightness = 0
            f.contrast = Float(1 + edit.contrast * 0.5)
            f.saturation = Float(1 + edit.saturation)
            image = f.outputImage ?? image
        }
        if edit.warmth != 0 || edit.tint != 0 {
            // Warmth trades red against blue; tint trades green against magenta.
            let w = CGFloat(edit.warmth) * 0.12
            let t = CGFloat(edit.tint)
            let f = CIFilter.colorMatrix()
            f.inputImage = image
            f.rVector = CIVector(x: (1 + w) * (1 + t * 0.05), y: 0, z: 0, w: 0)
            f.gVector = CIVector(x: 0, y: 1 - t * 0.1, z: 0, w: 0)
            f.bVector = CIVector(x: 0, y: 0, z: (1 - w) * (1 + t * 0.05), w: 0)
            image = f.outputImage ?? image
        }
        if let tone = ToneColor.lut(for: edit) {
            image = applyCube(tone, to: image) ?? image
        }

        // 2. The film look, blended with the original by its strength.
        if let lut, edit.intensity > 0 {
            if let graded = applyCube(lut, to: image) {
                if edit.intensity >= 1 {
                    image = graded
                } else {
                    let mix = CIFilter.dissolveTransition()
                    mix.inputImage = image
                    mix.targetImage = graded
                    mix.time = Float(edit.intensity)
                    image = mix.outputImage ?? graded
                }
            }
        }

        // 3. Finishing: lifted blacks, vignette, light leak, grain.
        if edit.fade > 0 {
            let k = CGFloat(edit.fade) * 0.06 // working space is linear, so a small lift goes a long way
            let f = CIFilter.colorMatrix()
            f.inputImage = image
            f.rVector = CIVector(x: 1 - k, y: 0, z: 0, w: 0)
            f.gVector = CIVector(x: 0, y: 1 - k, z: 0, w: 0)
            f.bVector = CIVector(x: 0, y: 0, z: 1 - k, w: 0)
            f.biasVector = CIVector(x: k, y: k, z: k, w: 0)
            image = f.outputImage ?? image
        }
        if edit.vignette > 0 {
            let f = CIFilter.vignette()
            f.inputImage = image
            f.intensity = Float(edit.vignette) * 1.5
            f.radius = 1.5
            image = f.outputImage ?? image
        }
        if let index = edit.leak, LightLeak.styles.indices.contains(index), edit.leakAmount > 0 {
            image = LightLeak.styles[index].apply(to: image, amount: edit.leakAmount, placement: edit.leakPlacement)
        }
        if edit.grain > 0 {
            image = addGrain(to: image, amount: CGFloat(edit.grain), extent: extent)
        }

        return image.cropped(to: extent)
    }

    private static func applyCube(_ lut: LUT, to image: CIImage) -> CIImage? {
        let f = CIFilter.colorCubeWithColorSpace()
        f.inputImage = image
        f.cubeDimension = Float(lut.dimension)
        f.cubeData = lut.data
        f.colorSpace = LUT.colorSpace
        return f.outputImage
    }

    private static func addGrain(to image: CIImage, amount: CGFloat, extent: CGRect) -> CIImage {
        guard let noise = CIFilter.randomGenerator().outputImage else { return image }

        // Grain size follows image size; scaling up with linear sampling also softens it like film grain.
        let scale = max(1, max(extent.width, extent.height) / 1500)
        let sized = noise.transformed(by: CGAffineTransform(scaleX: scale, y: scale))

        // Monochrome noise centred on mid-grey, so an overlay blend leaves average brightness unchanged.
        let a = amount * 0.35
        let mono = CIFilter.colorMatrix()
        mono.inputImage = sized
        mono.rVector = CIVector(x: a, y: 0, z: 0, w: 0)
        mono.gVector = CIVector(x: a, y: 0, z: 0, w: 0)
        mono.bVector = CIVector(x: a, y: 0, z: 0, w: 0)
        mono.aVector = CIVector(x: 0, y: 0, z: 0, w: 0)
        mono.biasVector = CIVector(x: 0.5 - a / 2, y: 0.5 - a / 2, z: 0.5 - a / 2, w: 1)
        guard let grain = mono.outputImage?.cropped(to: extent) else { return image }

        let blend = CIFilter.overlayBlendMode()
        blend.inputImage = grain
        blend.backgroundImage = image
        return blend.outputImage ?? image
    }
}
