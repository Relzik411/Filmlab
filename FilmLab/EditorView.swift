import PhotosUI
import SwiftUI

struct EditorView: View {
    private enum Tool: String, CaseIterable {
        case looks = "Looks"
        case adjust = "Adjust"
        case effects = "Effects"
        case crop = "Crop"
    }

    @State private var model = EditorModel()
    @State private var pickerItem: PhotosPickerItem?
    @State private var tool = Tool.looks
    @State private var adjustment = Adjustment.exposure
    @State private var showingHSL = false
    @State private var hslBand = HSLBand.red
    @State private var showingOriginal = false
    @State private var showingCredits = false
    @State private var showingSavePreset = false
    @State private var presetName = ""
    @State private var renamingPreset: Preset?
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                preview
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                if model.hasPhoto {
                    controls
                }
            }
            .background(Color.black)
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Color.black, for: .navigationBar)
            .toolbar {
                ToolbarItemGroup(placement: .topBarLeading) {
                    PhotosPicker(selection: $pickerItem, matching: .images) {
                        Image(systemName: "photo.on.rectangle")
                    }
                    Button { model.undo() } label: {
                        Image(systemName: "arrow.uturn.backward")
                    }
                    .disabled(!model.canUndo)
                    Button { model.redo() } label: {
                        Image(systemName: "arrow.uturn.forward")
                    }
                    .disabled(!model.canRedo)
                }
                ToolbarItemGroup(placement: .topBarTrailing) {
                    Menu {
                        Button("Revert to Original", systemImage: "arrow.counterclockwise") {
                            model.revertToOriginal()
                        }
                        .disabled(!model.hasPhoto || model.edit == EditState())
                        Button("Credits", systemImage: "info.circle") { showingCredits = true }
                    } label: {
                        Image(systemName: "ellipsis.circle")
                    }
                    Button {
                        Task { await model.export() }
                    } label: {
                        if model.isExporting {
                            ProgressView()
                        } else {
                            Text("Save").bold()
                        }
                    }
                    .disabled(!model.hasPhoto || model.isExporting)
                }
            }
        }
        .preferredColorScheme(.dark)
        .task { await model.loadLUTs() }
        .onChange(of: pickerItem) {
            guard let item = pickerItem else { return }
            Task { await model.load(item) }
        }
        .onChange(of: model.edit) { model.editDidChange() }
        .onChange(of: scenePhase) {
            if scenePhase != .active { model.commit() }
        }
        .onChange(of: tool) { model.setCropping(tool == .crop) }
        .alert(model.message ?? "", isPresented: Binding(
            get: { model.message != nil },
            set: { if !$0 { model.message = nil } }
        )) {
            Button("OK") {}
        }
        .sheet(isPresented: $showingCredits) { CreditsView() }
    }

    // MARK: Preview

    @ViewBuilder
    private var preview: some View {
        if let image = showingOriginal ? model.originalPreview : model.previewImage {
            if model.isCropping {
                Image(decorative: image, scale: 1)
                    .resizable()
                    .scaledToFit()
                    .overlay {
                        CropOverlay(rect: $model.edit.crop.rect, ratio: model.cropRatio)
                    }
                    .padding(28)
            } else {
                comparablePreview(image)
            }
        } else if model.isLoading {
            ProgressView()
        } else {
            PhotosPicker(selection: $pickerItem, matching: .images) {
                Label("Choose a Photo", systemImage: "photo.on.rectangle")
                    .font(.headline)
                    .padding(.horizontal, 20)
                    .padding(.vertical, 12)
                    .background(Color(white: 0.15), in: Capsule())
            }
            .buttonStyle(.plain)
        }
    }

    private func comparablePreview(_ image: CGImage) -> some View {
        Image(decorative: image, scale: 1)
            .resizable()
            .scaledToFit()
            .padding()
            .overlay(alignment: .top) {
                if showingOriginal {
                    Text("Original")
                        .font(.caption.bold())
                        .padding(.horizontal, 10)
                        .padding(.vertical, 4)
                        .background(.ultraThinMaterial, in: Capsule())
                        .padding(.top, 24)
                }
            }
            // Press and hold to compare with the original.
            .onLongPressGesture(minimumDuration: .infinity, maximumDistance: .infinity, perform: {}) { pressing in
                showingOriginal = pressing
            }
    }

    // MARK: Controls

    private var controls: some View {
        VStack(spacing: 14) {
            switch tool {
            case .looks: looksPanel
            case .adjust: adjustPanel
            case .effects: effectsPanel
            case .crop: cropPanel
            }
            Picker("Tool", selection: $tool) {
                ForEach(Tool.allCases, id: \.self) { Text($0.rawValue) }
            }
            .pickerStyle(.segmented)
            .padding(.horizontal)
        }
        .padding(.vertical, 14)
        .frame(height: 280, alignment: .bottom)
        .background(Color(white: 0.07))
    }

    private var looksPanel: some View {
        VStack(spacing: 12) {
            if model.edit.lutID != nil {
                LabeledSlider(title: "Strength", value: $model.edit.intensity, range: 0...1)
            }
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 10) {
                    lookButton(id: nil, name: "Original", thumbnail: model.originalThumbnail)
                    newPresetTile
                    ForEach(model.presets) { preset in
                        tile(name: preset.name, thumbnail: model.presetThumbnails[preset.id], selected: model.isApplied(preset)) {
                            model.apply(preset)
                        }
                        .contextMenu {
                            Button("Rename", systemImage: "pencil") {
                                presetName = preset.name
                                renamingPreset = preset
                            }
                            Button("Delete", systemImage: "trash", role: .destructive) {
                                model.delete(preset)
                            }
                        }
                    }
                    Rectangle()
                        .fill(Color(white: 0.25))
                        .frame(width: 1, height: 56)
                        .padding(.bottom, 18)
                    ForEach(model.luts) { lut in
                        lookButton(id: lut.id, name: lut.name, thumbnail: model.thumbnails[lut.id])
                    }
                }
                .padding(.horizontal)
            }
        }
        .alert("Save Preset", isPresented: $showingSavePreset) {
            TextField("Name", text: $presetName)
            Button("Save") { model.savePreset(named: presetName) }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("Saves the look, adjustments and effects. Crop isn't included.")
        }
        .alert("Rename Preset", isPresented: Binding(
            get: { renamingPreset != nil },
            set: { if !$0 { renamingPreset = nil } }
        )) {
            TextField("Name", text: $presetName)
            Button("Save") {
                if let renamingPreset { model.rename(renamingPreset, to: presetName) }
            }
            Button("Cancel", role: .cancel) {}
        }
    }

    private var newPresetTile: some View {
        Button {
            presetName = ""
            showingSavePreset = true
        } label: {
            VStack(spacing: 6) {
                RoundedRectangle(cornerRadius: 6)
                    .strokeBorder(Color(white: 0.4), style: StrokeStyle(lineWidth: 1.5, dash: [4, 3]))
                    .frame(width: 68, height: 68)
                    .overlay {
                        Image(systemName: "plus").font(.title3).foregroundStyle(Color.secondary)
                    }
                Text("New").font(.caption2).foregroundStyle(Color.secondary)
            }
        }
        .buttonStyle(.plain)
    }

    private func lookButton(id: String?, name: String, thumbnail: CGImage?) -> some View {
        tile(name: name, thumbnail: thumbnail, selected: model.edit.lutID == id) {
            guard model.edit.lutID != id else { return }
            model.edit.lutID = id
            model.edit.intensity = 1
        }
    }

    private func tile(name: String, thumbnail: CGImage?, selected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(spacing: 6) {
                Group {
                    if let thumbnail {
                        Image(decorative: thumbnail, scale: 1).resizable().scaledToFill()
                    } else {
                        Color(white: 0.2)
                    }
                }
                .frame(width: 68, height: 68)
                .clipShape(RoundedRectangle(cornerRadius: 6))
                .overlay {
                    RoundedRectangle(cornerRadius: 6).stroke(selected ? Color.white : .clear, lineWidth: 2)
                }
                Text(name)
                    .font(.caption2)
                    .foregroundStyle(selected ? Color.primary : Color.secondary)
            }
        }
        .buttonStyle(.plain)
    }

    private var adjustPanel: some View {
        VStack(spacing: 12) {
            if showingHSL {
                hslControls
            } else {
                LabeledSlider(
                    title: adjustment.title,
                    value: Binding(
                        get: { model.edit[keyPath: adjustment.keyPath] },
                        set: { model.edit[keyPath: adjustment.keyPath] = $0 }
                    ),
                    range: adjustment.range
                )
            }
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 18) {
                    ForEach(Adjustment.allCases) { item in
                        adjustmentButton(item)
                    }
                    toolButton(title: "HSL", symbol: "paintpalette", selected: showingHSL, changed: model.edit.hasHSL) {
                        showingHSL = true
                    }
                    Button("Reset") { model.resetAdjustments() }
                        .font(.caption)
                        .disabled(model.edit == model.edit.withoutAdjustments())
                }
                .padding(.horizontal)
            }
        }
    }

    private var effectsPanel: some View {
        VStack(spacing: 12) {
            if model.edit.leak != nil {
                HStack(alignment: .bottom, spacing: 0) {
                    LabeledSlider(title: "Light Leak", value: $model.edit.leakAmount, range: 0...1)
                    Button {
                        model.edit.leakPlacement = (model.edit.leakPlacement + 1) % LightLeak.placementCount
                    } label: {
                        Label("Shift", systemImage: "arrow.triangle.2.circlepath")
                            .font(.caption)
                    }
                    .padding(.trailing)
                    .padding(.bottom, 6)
                }
            }
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 10) {
                    tile(name: "None", thumbnail: model.originalThumbnail, selected: model.edit.leak == nil) {
                        model.edit.leak = nil
                    }
                    ForEach(LightLeak.styles) { style in
                        tile(name: style.name, thumbnail: model.leakThumbnails[style.id], selected: model.edit.leak == style.id) {
                            if model.edit.leak != style.id {
                                model.edit.leak = style.id
                                model.edit.leakAmount = 0.8
                            }
                        }
                    }
                }
                .padding(.horizontal)
            }
        }
    }

    private var cropPanel: some View {
        VStack(spacing: 12) {
            HStack(alignment: .bottom, spacing: 0) {
                LabeledSlider(
                    title: "Straighten",
                    value: $model.edit.crop.straighten,
                    range: -20...20,
                    format: { String(format: "%.1f°", $0) }
                )
                Button { model.rotateClockwise() } label: {
                    Image(systemName: "rotate.right").font(.title3)
                }
                .padding(.trailing, 12)
                .padding(.bottom, 4)
                Button { model.flip() } label: {
                    Image(systemName: "arrow.left.and.right.righttriangle.left.righttriangle.right").font(.title3)
                }
                .padding(.trailing)
                .padding(.bottom, 4)
            }
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(CropAspect.allCases) { aspect in
                        let selected = model.edit.crop.aspect == aspect
                        Button(aspect.rawValue) { model.setAspect(aspect) }
                            .font(.caption)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 8)
                            .background(selected ? Color(white: 0.25) : .clear, in: Capsule())
                            .foregroundStyle(selected ? Color.primary : Color.secondary)
                    }
                    Button("Reset") { model.resetCrop() }
                        .font(.caption)
                        .disabled(model.edit.crop == CropState())
                }
                .padding(.horizontal)
            }
            .buttonStyle(.plain)
        }
    }

    private var hslControls: some View {
        VStack(spacing: 2) {
            HStack(spacing: 12) {
                ForEach(HSLBand.allCases) { band in
                    Button { hslBand = band } label: {
                        Circle()
                            .fill(Color(hue: band.hue / 360, saturation: 0.75, brightness: 0.95))
                            .frame(width: 22, height: 22)
                            .padding(3)
                            .overlay {
                                Circle().stroke(Color.white, lineWidth: band == hslBand ? 2 : 0)
                            }
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(band.title)
                }
            }
            .padding(.bottom, 2)
            hslSlider("Hue", \.hslHue)
            hslSlider("Saturation", \.hslSaturation)
            hslSlider("Luminance", \.hslLuminance)
        }
    }

    private func hslSlider(_ title: String, _ keyPath: WritableKeyPath<EditState, [Double]>) -> some View {
        let index = hslBand.rawValue
        let value = Binding(
            get: { model.edit[keyPath: keyPath][index] },
            set: { model.edit[keyPath: keyPath][index] = $0 }
        )
        return HStack(spacing: 8) {
            Text(title)
                .frame(width: 72, alignment: .leading)
            Slider(value: value, in: -1...1)
            Text("\(Int((value.wrappedValue * 100).rounded()))")
                .monospacedDigit()
                .frame(width: 34, alignment: .trailing)
        }
        .font(.caption)
        .foregroundStyle(.secondary)
        .padding(.horizontal)
    }

    private func adjustmentButton(_ item: Adjustment) -> some View {
        toolButton(
            title: item.title,
            symbol: item.symbol,
            selected: !showingHSL && item == adjustment,
            changed: model.edit[keyPath: item.keyPath] != 0
        ) {
            adjustment = item
            showingHSL = false
        }
    }

    private func toolButton(title: String, symbol: String, selected: Bool, changed: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(spacing: 6) {
                Image(systemName: symbol)
                    .font(.title3)
                    .frame(width: 44, height: 44)
                    .background(selected ? Color(white: 0.25) : .clear, in: Circle())
                Text(title).font(.caption2)
                Circle()
                    .fill(changed ? Color.white : .clear)
                    .frame(width: 4, height: 4)
            }
            .foregroundStyle(selected ? Color.primary : Color.secondary)
        }
        .buttonStyle(.plain)
    }
}

struct LabeledSlider: View {
    let title: String
    @Binding var value: Double
    let range: ClosedRange<Double>
    var format: (Double) -> String = { "\(Int(($0 * 100).rounded()))" }

    var body: some View {
        VStack(spacing: 2) {
            HStack {
                Text(title)
                Spacer()
                Text(format(value)).monospacedDigit()
            }
            .font(.caption)
            .foregroundStyle(.secondary)
            Slider(value: $value, in: range)
        }
        .padding(.horizontal)
    }
}

#Preview {
    EditorView()
}
