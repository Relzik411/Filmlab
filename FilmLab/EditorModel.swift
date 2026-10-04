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
    var isLoading = false
    var isExporting = false
    var message: String?

    var hasPhoto: Bool { fullImage != nil }

    private var fullImage: CIImage?
    @ObservationIgnored private var previewSource: CIImage?
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
        isRendering = true
        let edit = edit
        let lut = lut(for: edit.lutID)
        let renderer = renderer
        Task {
            let image = await Task.detached(priority: .userInitiated) {
                renderer.cgImage(FilterPipeline.apply(edit, lut: lut, to: source))
            }.value
            if source === previewSource { previewImage = image }
            isRendering = false
            if needsRender {
                needsRender = false
                render()
            }
        }
    }

    func resetAdjustments() {
        edit = EditState(lutID: edit.lutID, intensity: edit.intensity)
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
            let rendered = await Task.detached(priority: .utility) { () -> (CGImage?, [String: CGImage]) in
                var result: [String: CGImage] = [:]
                for lut in luts {
                    let look = EditState(lutID: lut.id)
                    result[lut.id] = renderer.cgImage(FilterPipeline.apply(look, lut: lut, to: source))
                }
                return (renderer.cgImage(source), result)
            }.value
            guard source === thumbnailSource else { return }
            originalThumbnail = rendered.0
            thumbnails = rendered.1
        }
    }
}
