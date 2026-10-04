import CoreImage
import Observation
import Photos
import PhotosUI
import SwiftUI

@MainActor
@Observable
final class EditorModel {
    var luts: [LUT] = []
    var edit = EditState()
    var previewImage: CGImage?
    var originalPreview: CGImage?
    var originalThumbnail: CGImage?
    var thumbnails: [String: CGImage] = [:]
    var leakThumbnails: [Int: CGImage] = [:]
    /// While true the preview shows the whole straightened photo so the crop box can be edited.
    private(set) var isCropping = false
    var isLoading = false
    var isExporting = false
    var message: String?

    var hasPhoto: Bool { fullImage != nil }

    /// The frame the crop box is drawn in: the preview photo after quarter turns.
    var cropFrameSize: CGSize {
        guard previewSourceSize.width > 0 else { return CGSize(width: 1, height: 1) }
        return Geometry.frameSize(of: previewSourceSize, crop: edit.crop)
    }

    /// The crop box's width/height ratio in normalized units, or nil for a free crop.
    var cropRatio: CGFloat? {
        let size = cropFrameSize
        return CropMath.normalizedRatio(edit.crop.aspect.ratio(for: size), imageSize: size)
    }

    private var fullImage: CIImage?
    @ObservationIgnored private var previewSource: CIImage? {
        didSet { previewSourceSize = previewSource?.extent.size ?? .zero }
    }
    /// Observed copy of the preview size, so `cropFrameSize` updates views.
    private var previewSourceSize = CGSize.zero
    @ObservationIgnored private var lastRender: (edit: EditState, cropping: Bool, source: CIImage)?
    @ObservationIgnored private var comparisonCrop: CropState?
    @ObservationIgnored private var thumbnailSource: CIImage?
    @ObservationIgnored private var isRendering = false
    @ObservationIgnored private var needsRender = false
    @ObservationIgnored private let renderer = Renderer()

    func loadLUTs() async {
        luts = await Task.detached(priority: .userInitiated) { LUT.loadBundled() }.value
        makeThumbnails()
    }

    func load(_ item: PhotosPickerItem) async {
        isLoading = true
        defer { isLoading = false }
        do {
            guard let data = try await item.loadTransferable(type: Data.self),
                  let image = CIImage(data: data, options: [.applyOrientationProperty: true])
            else {
                message = "Couldn't open that photo."
                return
            }
            let renderer = renderer
            let (preview, thumbnail, original) = await Task.detached(priority: .userInitiated) { () -> (CIImage, CIImage, CGImage?) in
                let preview = renderer.downscaled(image, maxSide: 1600)
                return (preview, renderer.downscaled(image, maxSide: 240), renderer.cgImage(preview))
            }.value

            fullImage = image
            previewSource = preview
            thumbnailSource = thumbnail
            originalPreview = original
            previewImage = original
            edit = EditState()
            lastRender = nil
            comparisonCrop = CropState()
            render()
            makeThumbnails()
        } catch {
            message = error.localizedDescription
        }
    }

    func lut(for id: String?) -> LUT? {
        luts.first { $0.id == id }
    }

    /// Re-renders the preview. Calls that arrive mid-render are merged into one follow-up render,
    /// so dragging a slider never queues up stale frames.
    func render() {
        guard let source = previewSource else { return }
        if isRendering {
            needsRender = true
            return
        }

        let cropping = !isCropping
        var edit = edit
        // While the crop box is being edited the whole photo is shown, so moving the box changes nothing.
        if !cropping { edit.crop.rect = CropMath.full }
        if let last = lastRender, last.edit == edit, last.cropping == cropping, last.source === source { return }
        lastRender = (edit, cropping, source)

        // "Hold to compare" shows the original with the same crop.
        let comparisonCrop = cropping && edit.crop != self.comparisonCrop ? edit.crop : nil

        isRendering = true
        let lut = lut(for: edit.lutID)
        let renderer = renderer
        Task {
            let (image, comparison) = await Task.detached(priority: .userInitiated) { () -> (CGImage?, CGImage?) in
                let image = renderer.cgImage(FilterPipeline.apply(edit, lut: lut, to: source, cropping: cropping))
                let comparison = comparisonCrop.map {
                    renderer.cgImage(Geometry.apply($0, to: source, cropping: true))
                } ?? nil
                return (image, comparison)
            }.value
            if source === previewSource {
                previewImage = image
                if let comparisonCrop, let comparison {
                    originalPreview = comparison
                    self.comparisonCrop = comparisonCrop
                }
            }
            isRendering = false
            if needsRender {
                needsRender = false
                render()
            }
        }
    }

    func resetAdjustments() {
        edit = edit.withoutAdjustments()
    }

    func setCropping(_ cropping: Bool) {
        guard cropping != isCropping else { return }
        isCropping = cropping
        render()
    }

    func setAspect(_ aspect: CropAspect) {
        edit.crop.aspect = aspect
        edit.crop.rect = CropMath.fitted(cropRatio)
    }

    func rotateClockwise() {
        edit.crop.quarterTurns = (edit.crop.quarterTurns + 1) % 4
        edit.crop.rect = CropMath.fitted(cropRatio) // the frame's shape changed
    }

    func flip() {
        edit.crop.flipped.toggle()
        edit.crop.rect.origin.x = 1 - edit.crop.rect.maxX
    }

    func resetCrop() {
        edit.crop = CropState()
    }

    func export() async {
        guard let full = fullImage else { return }
        isExporting = true
        defer { isExporting = false }

        let status = await PHPhotoLibrary.requestAuthorization(for: .addOnly)
        guard status == .authorized || status == .limited else {
            message = "To save photos, allow FilmLab to add photos in Settings."
            return
        }

        let edit = edit
        let lut = lut(for: edit.lutID)
        let renderer = renderer
        do {
            let data = try await Task.detached(priority: .userInitiated) {
                try renderer.encoded(FilterPipeline.apply(edit, lut: lut, to: full))
            }.value
            try await PHPhotoLibrary.shared().performChanges {
                PHAssetCreationRequest.forAsset().addResource(with: .photo, data: data, options: nil)
            }
            message = "Saved to Photos"
        } catch {
            message = error.localizedDescription
        }
    }

    private func makeThumbnails() {
        guard let source = thumbnailSource, !luts.isEmpty else { return }
        let luts = luts
        let renderer = renderer
        Task {
            let rendered = await Task.detached(priority: .utility) {
                () -> (CGImage?, [String: CGImage], [Int: CGImage]) in
                var looks: [String: CGImage] = [:]
                for lut in luts {
                    let look = EditState(lutID: lut.id)
                    looks[lut.id] = renderer.cgImage(FilterPipeline.apply(look, lut: lut, to: source))
                }
                var leaks: [Int: CGImage] = [:]
                for style in LightLeak.styles {
                    let leak = EditState(leak: style.id, leakAmount: 1)
                    leaks[style.id] = renderer.cgImage(FilterPipeline.apply(leak, lut: nil, to: source))
                }
                return (renderer.cgImage(source), looks, leaks)
            }.value
            guard source === thumbnailSource else { return }
            originalThumbnail = rendered.0
            thumbnails = rendered.1
            leakThumbnails = rendered.2
        }
    }
}
