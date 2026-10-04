import CoreGraphics
import Foundation

/// A 3D colour lookup table loaded from a `.cube` file, packed the way `CIColorCube` wants it.
struct LUT: Identifiable, Sendable {
    enum LoadError: LocalizedError {
        case malformed(String)
        var errorDescription: String? {
            switch self {
            case .malformed(let file): "\(file) is not a 3D .cube LUT this app can read."
            }
        }
    }

    /// The looks are graded on sRGB-encoded values, so the lookup has to happen in sRGB.
    static let colorSpace = CGColorSpace(name: CGColorSpace.sRGB)!

    /// Display order for the bundled looks. Any other .cube file is listed after these.
    static let preferredOrder = [
        "Golden", "Portrait", "Mint", "Everyday", "Summer", "Vivid", "Classic", "Warm",
        "Dusk", "Winter", "Instant", "Faded", "Cross", "Mono", "Grit",
    ]

    let id: String
    let name: String
    let dimension: Int
    /// RGBA Float32 values, red changing fastest.
    let data: Data

    init(id: String, name: String, dimension: Int, data: Data) {
        self.id = id
        self.name = name
        self.dimension = dimension
        self.data = data
    }

    init(contentsOf url: URL) throws {
        let text = try String(contentsOf: url, encoding: .utf8)
        var size = 0
        var title: String?
        var values: [Float] = []

        for rawLine in text.split(whereSeparator: \.isNewline) {
            let line = rawLine.trimmingCharacters(in: .whitespaces)
            if line.isEmpty || line.hasPrefix("#") { continue }

            if line.hasPrefix("TITLE") {
                title = line.dropFirst(5).trimmingCharacters(in: CharacterSet(charactersIn: " \t\""))
            } else if line.hasPrefix("LUT_3D_SIZE") {
                size = Int(line.split(whereSeparator: \.isWhitespace).last ?? "") ?? 0
                values.reserveCapacity(size * size * size * 4)
            } else if line.first?.isLetter == true {
                continue // DOMAIN_MIN/MAX and other keywords; only 0...1 LUTs are supported
            } else {
                let parts = line.split(whereSeparator: \.isWhitespace)
                guard parts.count == 3 else { throw LoadError.malformed(url.lastPathComponent) }
                for part in parts { values.append(Float(part) ?? 0) }
                values.append(1)
            }
        }

        guard size > 1, values.count == size * size * size * 4 else {
            throw LoadError.malformed(url.lastPathComponent)
        }

        id = url.deletingPathExtension().lastPathComponent
        name = title ?? id
        dimension = size
        data = values.withUnsafeBufferPointer { Data(buffer: $0) }
    }

    /// Loads every `.cube` file in the app bundle.
    static func loadBundled() -> [LUT] {
        let urls = Bundle.main.urls(forResourcesWithExtension: "cube", subdirectory: nil) ?? []
        let luts = urls.compactMap { url -> LUT? in
            do {
                return try LUT(contentsOf: url)
            } catch {
                print("Skipping LUT: \(error.localizedDescription)")
                return nil
            }
        }
        return luts.sorted { a, b in
            let ia = preferredOrder.firstIndex(of: a.id) ?? .max
            let ib = preferredOrder.firstIndex(of: b.id) ?? .max
            return ia == ib ? a.name < b.name : ia < ib
        }
    }
}
