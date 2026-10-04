import PhotosUI
import SwiftUI

struct EditorView: View {
    private enum Tool: String, CaseIterable {
        case looks = "Looks"
        case adjust = "Adjust"
    }

    @State private var model = EditorModel()
    @State private var pickerItem: PhotosPickerItem?
    @State private var tool = Tool.looks
    @State private var adjustment = Adjustment.exposure
    @State private var showingOriginal = false
    @State private var showingCredits = false

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
            .navigationTitle("FilmLab")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Color.black, for: .navigationBar)
            .toolbar {
                ToolbarItemGroup(placement: .topBarLeading) {
                    PhotosPicker(selection: $pickerItem, matching: .images) {
                        Image(systemName: "photo.on.rectangle")
                    }
                    Button { showingCredits = true } label: {
                        Image(systemName: "info.circle")
                    }
                }
                ToolbarItem(placement: .topBarTrailing) {
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
        .onChange(of: model.edit) { model.render() }
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

    // MARK: Controls

    private var controls: some View {
        VStack(spacing: 14) {
            switch tool {
            case .looks: looksPanel
            case .adjust: adjustPanel
            }
            Picker("Tool", selection: $tool) {
                ForEach(Tool.allCases, id: \.self) { Text($0.rawValue) }
            }
            .pickerStyle(.segmented)
            .padding(.horizontal)
        }
        .padding(.vertical, 14)
        .frame(height: 230, alignment: .bottom)
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
                    ForEach(model.luts) { lut in
                        lookButton(id: lut.id, name: lut.name, thumbnail: model.thumbnails[lut.id])
                    }
                }
                .padding(.horizontal)
            }
        }
    }

    private func lookButton(id: String?, name: String, thumbnail: CGImage?) -> some View {
        let selected = model.edit.lutID == id
        return Button {
            guard !selected else { return }
            model.edit.lutID = id
            model.edit.intensity = 1
        } label: {
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
            LabeledSlider(
                title: adjustment.title,
                value: Binding(
                    get: { model.edit[keyPath: adjustment.keyPath] },
                    set: { model.edit[keyPath: adjustment.keyPath] = $0 }
                ),
                range: adjustment.range
            )
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 18) {
                    ForEach(Adjustment.allCases) { item in
                        adjustmentButton(item)
                    }
                    Button("Reset") { model.resetAdjustments() }
                        .font(.caption)
                        .disabled(model.edit == EditState(lutID: model.edit.lutID, intensity: model.edit.intensity))
                }
                .padding(.horizontal)
            }
        }
    }

    private func adjustmentButton(_ item: Adjustment) -> some View {
        let selected = item == adjustment
        let changed = model.edit[keyPath: item.keyPath] != 0
        return Button { adjustment = item } label: {
            VStack(spacing: 6) {
                Image(systemName: item.symbol)
                    .font(.title3)
                    .frame(width: 44, height: 44)
                    .background(selected ? Color(white: 0.25) : .clear, in: Circle())
                Text(item.title).font(.caption2)
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

    var body: some View {
        VStack(spacing: 2) {
            HStack {
                Text(title)
                Spacer()
                Text("\(Int((value * 100).rounded()))").monospacedDigit()
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
