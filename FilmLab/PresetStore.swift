import Foundation

/// A saved look: the look, its strength, adjustments and effects. Crop belongs to each photo, so it is left out.
struct Preset: Identifiable, Codable, Equatable, Sendable {
    var id = UUID()
    var name: String
    var edit: EditState
}

enum PresetStore {
    private struct Saved: Codable {
        var version = 1
        var presets: [Preset]
    }

    static func load() -> [Preset] {
        guard let data = try? Data(contentsOf: url) else { return [] }
        return (try? JSONDecoder().decode(Saved.self, from: data).presets) ?? []
    }

    static func save(_ presets: [Preset]) {
        do {
            try FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
            try JSONEncoder().encode(Saved(presets: presets)).write(to: url, options: .atomic)
        } catch {
            print("Couldn't save presets: \(error.localizedDescription)")
        }
    }

    private static var url: URL {
        URL.applicationSupportDirectory.appending(path: "Presets.json")
    }
}
