import SwiftUI

/// Mboka na biso: the content of the map game. It is the website's own file, data/drc-map.json, served at
/// /api/v1/map: the app downloads it at launch and uses it from the next launch on. The copy bundled with the
/// app is that same file, for the first launch and when offline.
struct MapText: Decodable {
    let fr: String, en: String
    func callAsFunction(_ ui: UiLang) -> String { ui == .en && !en.isEmpty ? en : fr }
}

struct Province: Decodable, Identifiable {
    let id, name, capital, former, language, emoji: String
    let peoples, borders: [String]
    let area: Int
    let fact, hint: MapText
    let d: String
    let c, box: [Double]
    let shapeArea: Double

    var centre: CGPoint { CGPoint(x: c[0], y: c[1]) }
    var bounds: CGRect { CGRect(x: box[0], y: box[1], width: box[2] - box[0], height: box[3] - box[1]) }
}

struct MapRegion: Decodable, Identifiable {
    let id: String
    let name: MapText
    let color: String
    let provinces: [String]
    var tint: Color { Color(hex: color) }
}

struct MapCard: Decodable, Identifiable {
    let emoji: String?, year: String?
    let title, text: MapText
    var id: String { title.fr }
}

final class DRC {
    static let shared = DRC()

    struct View: Decodable { let width, height, lon, lat, zoom: Double }
    private struct File: Decodable {
        let view: View
        let regions: [MapRegion]
        let provinces: [Province]
        let history, culture, figures: [MapCard]
    }

    let view: View
    let regions: [MapRegion]
    let history, culture, figures: [MapCard]
    /// Every province, in region order.
    let ids: [String]
    private let byId: [String: Province]
    private let regionById: [String: MapRegion]
    /// Outlines in map units, parsed once.
    let paths: [String: Path]
    let whole: CGRect

    private init() {
        let bundled = Bundle.main.url(forResource: "drc-map", withExtension: "json")!
        let file = Self.valid(try? Data(contentsOf: Self.cache)) ?? (try! JSONDecoder().decode(File.self, from: Data(contentsOf: bundled)))
        view = file.view
        regions = file.regions
        history = file.history; culture = file.culture; figures = file.figures
        byId = Dictionary(uniqueKeysWithValues: file.provinces.map { ($0.id, $0) })
        ids = file.regions.flatMap(\.provinces)
        regionById = Dictionary(uniqueKeysWithValues: file.regions.flatMap { r in r.provinces.map { ($0, r) } })
        paths = Dictionary(uniqueKeysWithValues: file.provinces.map { ($0.id, SVGPath.parse($0.d)) })
        whole = CGRect(x: 0, y: 0, width: file.view.width, height: file.view.height)
    }

    private static let cache = URL.applicationSupportDirectory.appending(path: "drc-map.json")

    /// Content is only accepted whole: every province of every region, with an outline.
    private static func valid(_ data: Data?) -> File? {
        guard let data, let file = try? JSONDecoder().decode(File.self, from: data) else { return nil }
        let known = Set(file.provinces.map(\.id)), wanted = file.regions.flatMap(\.provinces)
        guard wanted.count >= 20, Set(wanted) == known, wanted.count == known.count, file.provinces.allSatisfy({ $0.d.hasPrefix("M") && $0.c.count == 2 && $0.box.count == 4 }) else { return nil }
        return file
    }

    /// Fetches the current content from the server and keeps it for the next launch.
    static func refresh() async {
        var request = URLRequest(url: API.url("/api/v1/map"))
        request.timeoutInterval = 15
        guard let (data, response) = try? await URLSession.shared.data(for: request), (response as? HTTPURLResponse)?.statusCode == 200, valid(data) != nil else { return }
        try? FileManager.default.createDirectory(at: .applicationSupportDirectory, withIntermediateDirectories: true)
        try? data.write(to: cache, options: .atomic)
    }

    func province(_ id: String) -> Province { byId[id]! }
    func region(of id: String) -> MapRegion { regionById[id]! }
    func color(_ id: String) -> Color { regionById[id]!.tint }

    /// Which province a point of the map (in map units) falls in.
    func province(at point: CGPoint) -> String? { ids.first { paths[$0]!.contains(point) } }

    /// A square window on the map that frames these provinces with some of their neighbours.
    func frame(_ ids: [String]) -> CGRect {
        let box = ids.map { province($0).bounds }.reduce(CGRect.null) { $0.union($1) }
        let side = min(whole.width, max(box.width, box.height) + 70)
        return CGRect(x: max(0, min(whole.width - side, box.midX - side / 2)), y: max(0, min(whole.height - side, box.midY - side / 2)), width: side, height: side)
    }
}

/// Stars and discovered provinces, kept on this phone.
enum MapProgress {
    private static let starsKey = "loba-map-stars", stampsKey = "loba-map-stamps"
    static var stars: [String: Int] { UserDefaults.standard.dictionary(forKey: starsKey) as? [String: Int] ?? [:] }
    static var stamps: [String] { UserDefaults.standard.stringArray(forKey: stampsKey) ?? [] }
    static func record(_ level: String, stars n: Int) {
        var all = stars
        all[level] = max(all[level] ?? 0, n)
        UserDefaults.standard.set(all, forKey: starsKey)
    }
    static func stamp(_ id: String) {
        if !stamps.contains(id) { UserDefaults.standard.set(stamps + [id], forKey: stampsKey) }
    }
    static func clear() { UserDefaults.standard.removeObject(forKey: starsKey); UserDefaults.standard.removeObject(forKey: stampsKey) }
}

/// The Congo seen from space: one Mapbox image of exactly the view the outlines were projected to.
/// The public token is read from Resources/Mapbox.txt, which is not committed; without it the map is drawn plain.
@Observable
final class Satellite {
    static let shared = Satellite()
    private(set) var image: UIImage?
    private var asked = false

    func load() {
        guard !asked else { return }
        asked = true
        guard let file = Bundle.main.url(forResource: "Mapbox", withExtension: "txt"),
              let token = try? String(contentsOf: file, encoding: .utf8).trimmingCharacters(in: .whitespacesAndNewlines), token.hasPrefix("pk.") else { return }
        let v = DRC.shared.view
        guard let url = URL(string: "https://api.mapbox.com/styles/v1/mapbox/satellite-v9/static/\(v.lon),\(v.lat),\(v.zoom),0/\(Int(v.width))x\(Int(v.height))@2x?attribution=false&logo=false&access_token=\(token)") else { return }
        Task {
            guard let (data, response) = try? await URLSession.shared.data(from: url), (response as? HTTPURLResponse)?.statusCode == 200, let picture = UIImage(data: data) else { return }
            await MainActor.run { self.image = picture }
        }
    }
}
