import Foundation

/// The learner's progress. Stored as the same JSON the website uses, so one account works on both.
struct WordStat: Equatable {
    var level: Int // 0–5, spaced-repetition box
    var misses: Int
    var due: Double // epoch ms
}

struct Daily: Equatable {
    var day: String // YYYY-MM-DD (local)
    var xp = 0
    var lessons = 0
    var bestCombo = 0
    var perfect = 0
    var claimed: [String] = []
}

struct GameState: Equatable {
    var onboarded = false
    var ui: UiLang = .fr
    var lang = "ln"
    var goal = 20
    var sound = true
    var voice = true
    var name = ""
    var avatar = "🦜"
    var xp = 0
    var gems = 50
    var hearts = Game.maxHearts
    var heartsRefillAt: Double?
    var streak = 0
    var bestStreak = 0
    var lastActiveDay: String?
    var activeDays: [String] = []
    var freezes = 0
    var doubleXpUntil: Double = 0
    var lessonsDone = 0
    /// unit id → lessons completed (0…lessonsPerUnit)
    var progress: [String: Int] = [:]
    var chests: Set<String> = []
    /// "lang:term" → stats
    var words: [String: WordStat] = [:]
    var daily = Daily(day: Game.dayKey())
}

enum Game {
    static let maxHearts = 5
    static let heartRegenMs: Double = 30 * 60 * 1000
    static let maxFreezes = 2
    static let goals = [10, 20, 40, 60]
    static let avatars = ["🦜", "🦁", "🐘", "🦒", "🐆", "🦓", "🦩", "🐒", "🦅", "🐢", "🐊", "🦋", "🌍", "⚽", "🥁", "🎸", "🌺", "👑"]
    static let chestReward = 25
    static let perfectReward = 5

    static func now() -> Double { Date().timeIntervalSince1970 * 1000 }

    static func dayKey(_ date: Date = Date()) -> String {
        let c = Calendar.current.dateComponents([.year, .month, .day], from: date)
        return String(format: "%04d-%02d-%02d", c.year!, c.month!, c.day!)
    }
}

// MARK: - JSON, in the website's shape

extension GameState {
    private static func int(_ v: Any?, _ fallback: Int, min: Int = 0, max: Int = 10_000_000) -> Int {
        guard let n = v as? NSNumber else { return fallback }
        let d = n.doubleValue
        return d.isFinite ? Swift.min(max, Swift.max(min, Int(d))) : fallback
    }

    private static func day(_ v: Any?) -> String? {
        guard let s = v as? String, s.range(of: "^\\d{4}-\\d{2}-\\d{2}$", options: .regularExpression) != nil else { return nil }
        return s
    }

    /// Reads progress saved by this app or by the website. Returns nil when it isn't a progress object at all.
    init?(json: Any?) {
        guard let o = json as? [String: Any] else { return nil }
        self.init()
        onboarded = o["onboarded"] as? Bool ?? false
        ui = UiLang(rawValue: o["ui"] as? String ?? "") ?? .fr
        if let l = o["lang"] as? String, l.range(of: "^[a-z]{2,3}$", options: .regularExpression) != nil { lang = l }
        let g = Self.int(o["goal"], 20)
        goal = Game.goals.contains(g) ? g : 20
        sound = o["sound"] as? Bool ?? true
        voice = o["voice"] as? Bool ?? true
        name = String((o["name"] as? String ?? "").prefix(20))
        if let a = o["avatar"] as? String, Game.avatars.contains(a) { avatar = a }
        xp = Self.int(o["xp"], 0)
        gems = Self.int(o["gems"], 50)
        hearts = Self.int(o["hearts"], Game.maxHearts, max: Game.maxHearts)
        heartsRefillAt = (o["heartsRefillAt"] as? NSNumber)?.doubleValue
        streak = Self.int(o["streak"], 0)
        bestStreak = Swift.max(streak, Self.int(o["bestStreak"], 0))
        lastActiveDay = Self.day(o["lastActiveDay"])
        activeDays = (o["activeDays"] as? [Any] ?? []).compactMap(Self.day).suffix(30).map { $0 }
        freezes = Self.int(o["freezes"], 0, max: Game.maxFreezes)
        doubleXpUntil = (o["doubleXpUntil"] as? NSNumber)?.doubleValue ?? 0
        lessonsDone = Self.int(o["lessonsDone"], 0)
        for (k, v) in o["progress"] as? [String: Any] ?? [:] where v is NSNumber { progress[k] = Self.int(v, 0, max: lessonsPerUnit) }
        chests = Set((o["chests"] as? [String: Any] ?? [:]).keys)
        for (k, v) in o["words"] as? [String: Any] ?? [:] {
            guard let w = v as? [String: Any] else { continue }
            words[k] = WordStat(level: Self.int(w["level"], 0, max: 5), misses: Self.int(w["misses"], 0), due: (w["due"] as? NSNumber)?.doubleValue ?? 0)
        }
        if let d = o["daily"] as? [String: Any], let key = Self.day(d["day"]) {
            daily = Daily(day: key, xp: Self.int(d["xp"], 0), lessons: Self.int(d["lessons"], 0), bestCombo: Self.int(d["bestCombo"], 0),
                          perfect: Self.int(d["perfect"], 0), claimed: (d["claimed"] as? [Any] ?? []).compactMap { $0 as? String })
        }
    }

    var json: [String: Any] {
        [
            "v": 1, "onboarded": onboarded, "ui": ui.rawValue, "lang": lang, "goal": goal, "sound": sound, "voice": voice,
            "name": name, "avatar": avatar, "xp": xp, "gems": gems, "hearts": hearts,
            "heartsRefillAt": heartsRefillAt.map { $0 as Any } ?? NSNull(),
            "streak": streak, "bestStreak": bestStreak, "lastActiveDay": lastActiveDay.map { $0 as Any } ?? NSNull(),
            "activeDays": activeDays, "freezes": freezes, "doubleXpUntil": doubleXpUntil, "lessonsDone": lessonsDone,
            "progress": progress, "chests": Dictionary(uniqueKeysWithValues: chests.map { ($0, true) }),
            "words": words.mapValues { ["level": $0.level, "misses": $0.misses, "due": $0.due] as [String: Any] },
            "daily": ["day": daily.day, "xp": daily.xp, "lessons": daily.lessons, "bestCombo": daily.bestCombo, "perfect": daily.perfect, "claimed": daily.claimed] as [String: Any],
        ]
    }
}
