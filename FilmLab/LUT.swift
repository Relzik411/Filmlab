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
}

/// A named group of looks, as listed in looks.json.
struct LookCategory: Identifiable, Sendable {
    let name: String
    let lutIDs: [String]
    var id: String { name }
}

/// The bundled looks and how they are grouped. looks.json (next to the .cube files, shared with
/// Android) sets the categories and their order; any .cube file it doesn't list goes under "More".
struct LookLibrary: Sendable {
    let luts: [LUT]
    let categories: [LookCategory]

    private struct Manifest: Decodable {
        struct Category: Decodable {
            let name: String
            let looks: [String]
        }
        let categories: [Category]
    }

    static func loadBundled() -> LookLibrary {
        let urls = Bundle.main.urls(forResourcesWithExtension: "cube", subdirectory: nil) ?? []
        var byID: [String: LUT] = [:]
        for url in urls {
            do {
                let lut = try LUT(contentsOf: url)
                byID[lut.id] = lut
            } catch {
                print("Skipping LUT: \(error.localizedDescription)")
            }
        }

        var manifest: Manifest?
        if let url = Bundle.main.url(forResource: "looks", withExtension: "json"),
           let data = try? Data(contentsOf: url) {
            manifest = try? JSONDecoder().decode(Manifest.self, from: data)
        }

        var categories = (manifest?.categories ?? []).map { category in
            LookCategory(name: category.name, lutIDs: category.looks.filter { byID[$0] != nil })
        }
        let listed = Set(categories.flatMap(\.lutIDs))
        let unlisted = byID.values.filter { !listed.contains($0.id) }.sorted { $0.name < $1.name }.map(\.id)
        if !unlisted.isEmpty {
            categories.append(LookCategory(name: "More", lutIDs: unlisted))
        }
        categories.removeAll { $0.lutIDs.isEmpty }

        var seen = Set<String>()
        let ordered = categories.flatMap(\.lutIDs).filter { seen.insert($0).inserted }.compactMap { byID[$0] }
        return LookLibrary(luts: ordered, categories: categories)
    }
}
