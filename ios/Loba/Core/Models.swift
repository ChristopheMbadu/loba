import Foundation

/// Lessons per unit before the unit's chest unlocks.
let lessonsPerUnit = 4

enum UiLang: String { case fr, en }

struct Word: Hashable {
    let term: String
    let fr: String
    let en: String
    /// Id of a teacher's recording, played from /api/v1/audio/:id.
    let audio: String?
    /// A sentence using the word, written in Studio, with its translations. Shown in the word puzzle.
    var guessWord = true
    var example = ""
    var exampleFr = ""
    var exampleEn = ""

    func meaning(_ ui: UiLang) -> String { ui == .en && !en.isEmpty ? en : fr }
}

struct Sentence: Hashable {
    let fr: String
    let en: String
    let answer: String
    let distractors: [String]

    func prompt(_ ui: UiLang) -> String { ui == .en && !en.isEmpty ? en : fr }
}

struct Unit: Identifiable, Hashable {
    let id: String
    let language: String
    let position: Double
    let title: String
    let titleEn: String
    let icon: String
    let tip: String
    let words: [Word]
    let sentences: [Sentence]

    func name(_ ui: UiLang) -> String { ui == .en && !titleEn.isEmpty ? titleEn : title }
}

/// A learning language, as configured in Loba Studio.
struct Language: Identifiable, Hashable {
    let code: String
    let nameFr: String
    let nameEn: String
    let greeting: String
    let cheer: String
    let regionFr: String
    let regionEn: String
    let color: String
    var id: String { code }

    func name(_ ui: UiLang) -> String { ui == .en ? nameEn : nameFr }
    func region(_ ui: UiLang) -> String { ui == .en ? regionEn : regionFr }
}

/// Everything learners can study: visible languages that have at least one usable unit.
struct Catalog {
    var languages: [Language]
    var curriculum: [String: [Unit]]

    func units(_ lang: String) -> [Unit] { curriculum[lang] ?? [] }
}

// MARK: - Parsing. Anything malformed is dropped, never shown.

private func str(_ v: Any?, _ max: Int = 500) -> String {
    guard let s = v as? String else { return "" }
    return String(s.trimmingCharacters(in: .whitespacesAndNewlines).prefix(max))
}

private func matches(_ s: String, _ pattern: String) -> Bool {
    s.range(of: pattern, options: .regularExpression) != nil
}

enum CatalogParser {
    static let bundledLanguages = [
        Language(code: "ln", nameFr: "lingala", nameEn: "Lingala", greeting: "Mbote", cheer: "Malamu mingi !",
                 regionFr: "Kinshasa, Brazzaville, l’Équateur", regionEn: "Kinshasa, Brazzaville, Équateur", color: "#1c9bef"),
        Language(code: "sw", nameFr: "swahili", nameEn: "Swahili", greeting: "Jambo", cheer: "Vizuri sana !",
                 regionFr: "Lubumbashi, Goma, Bukavu, Kisangani", regionEn: "Lubumbashi, Goma, Bukavu, Kisangani", color: "#2bb673"),
    ]

    static func word(_ v: Any) -> Word? {
        guard let o = v as? [String: Any] else { return nil }
        let term = str(o["term"]), fr = str(o["fr"])
        guard !term.isEmpty, !fr.isEmpty else { return nil }
        let audio = (o["audio"] as? String).flatMap { matches($0, "^[0-9a-f-]{36}$") ? $0 : nil }
        let example = str(o["example"], 300)
        return Word(term: term, fr: fr, en: str(o["en"]), audio: audio, guessWord: o["guessWord"] as? Bool ?? true, example: example,
                    exampleFr: example.isEmpty ? "" : str(o["exampleFr"], 300), exampleEn: example.isEmpty ? "" : str(o["exampleEn"], 300))
    }

    static func sentence(_ v: Any) -> Sentence? {
        guard let o = v as? [String: Any] else { return nil }
        let answer = str(o["answer"]), fr = str(o["fr"])
        guard !answer.isEmpty, !fr.isEmpty else { return nil }
        let distractors = (o["distractors"] as? [Any] ?? []).map { str($0, 60) }.filter { !$0.isEmpty }
        return Sentence(fr: fr, en: str(o["en"]), answer: answer, distractors: distractors)
    }

    static func unit(id: String, _ v: Any?) -> Unit? {
        guard let o = v as? [String: Any], let language = o["language"] as? String, matches(language, "^[a-z]{2,3}$") else { return nil }
        let words = (o["words"] as? [Any] ?? []).compactMap(word)
        guard words.count >= 4 else { return nil }
        let icon = str(o["icon"], 8)
        return Unit(id: id, language: language, position: (o["position"] as? NSNumber)?.doubleValue ?? 999,
                    title: str(o["title"], 120), titleEn: str(o["titleEn"], 120), icon: icon.isEmpty ? "📘" : icon,
                    tip: str(o["tip"], 1000), words: words, sentences: (o["sentences"] as? [Any] ?? []).compactMap(sentence))
    }

    /// Studio's language rows (snake_case).
    static func language(_ v: Any) -> Language? {
        guard let o = v as? [String: Any], let code = o["code"] as? String, matches(code, "^[a-z]{2,3}$") else { return nil }
        let en = str(o["name_en"], 60)
        guard !en.isEmpty else { return nil }
        let fr = str(o["name_fr"], 60)
        let regionFr = str(o["region_fr"], 120), regionEn = str(o["region_en"], 120)
        let color = (o["color"] as? String).flatMap { matches($0, "^#[0-9a-fA-F]{6}$") ? $0 : nil } ?? "#1c9bef"
        return Language(code: code, nameFr: fr.isEmpty ? en : fr, nameEn: en, greeting: str(o["greeting"], 40), cheer: str(o["cheer"], 60),
                        regionFr: regionFr, regionEn: regionEn.isEmpty ? regionFr : regionEn, color: color)
    }

    /// Only languages that have at least one usable unit are offered, in Studio's order.
    static func catalog(languages: [Language], units: [Unit]) -> Catalog {
        var byLang: [String: [Unit]] = [:]
        for u in units { byLang[u.language, default: []].append(u) }
        for key in byLang.keys { byLang[key]!.sort { $0.position < $1.position } }
        let known = languages.filter { !(byLang[$0.code] ?? []).isEmpty }
        return Catalog(languages: known, curriculum: byLang.filter { pair in known.contains { $0.code == pair.key } })
    }

    /// The curriculum shipped inside the app, so it works offline and on first launch.
    static func bundled() -> Catalog {
        guard let url = Bundle.main.url(forResource: "lessons", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let list = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]] else {
            return Catalog(languages: [], curriculum: [:])
        }
        return catalog(languages: bundledLanguages, units: list.compactMap { unit(id: str($0["id"]), $0) })
    }

    /// The response of GET /api/v1/lessons.
    static func published(_ data: Data) -> Catalog? {
        guard let body = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return nil }
        let units = (body["lessons"] as? [[String: Any]] ?? []).compactMap { unit(id: str($0["id"]), $0["content"]) }
        let languages = (body["languages"] as? [Any])?.compactMap(language) ?? bundledLanguages
        let result = catalog(languages: languages, units: units)
        return result.languages.isEmpty ? nil : result
    }
}
