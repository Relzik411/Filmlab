import CryptoKit
import Foundation

/// Keeps each photo's edits on disk, keyed by a fingerprint of the photo's contents, so reopening
/// the same photo brings its edits back however it was opened.
enum EditStore {
    private struct Saved: Codable {
        var version = 1
        var edit: EditState
    }

    static func key(for data: Data) -> String {
        SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
    }

    static func load(key: String) -> EditState? {
        guard let data = try? Data(contentsOf: url(for: key)) else { return nil }
        return try? JSONDecoder().decode(Saved.self, from: data).edit
    }

    /// Saves the edit, or removes the saved file when the photo is back to its original state.
    static func save(_ edit: EditState, key: String) {
        let url = url(for: key)
        if edit == EditState() {
            try? FileManager.default.removeItem(at: url)
            return
        }
        do {
            try FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
            try JSONEncoder().encode(Saved(edit: edit)).write(to: url, options: .atomic)
        } catch {
            print("Couldn't save edits: \(error.localizedDescription)")
        }
    }

    private static func url(for key: String) -> URL {
        URL.applicationSupportDirectory
            .appending(path: "Edits", directoryHint: .isDirectory)
            .appending(path: "\(key).json")
    }
}
