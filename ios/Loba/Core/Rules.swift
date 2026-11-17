import Foundation

/// The game's rules, kept identical to the website's (learn/src/game/rules.ts) so progress means the same on both.
enum Rules {
    // MARK: dates
    static func daysBetween(_ a: String, _ b: String) -> Int {
        func utc(_ key: String) -> Double {
            let p = key.split(separator: "-").compactMap { Int($0) }
            guard p.count == 3 else { return 0 }
            var cal = Calendar(identifier: .gregorian)
            cal.timeZone = TimeZone(identifier: "UTC")!
            return cal.date(from: DateComponents(year: p[0], month: p[1], day: p[2]))?.timeIntervalSince1970 ?? 0
        }
        return Int(((utc(b) - utc(a)) / 86400).rounded())
    }

    // MARK: hearts
    /// Applies passive heart regeneration up to `now`.
    static func syncHearts(_ s: GameState, _ now: Double) -> GameState {
        var s = s
        if s.hearts >= Game.maxHearts { s.heartsRefillAt = nil; return s }
        var at = s.heartsRefillAt ?? now + Game.heartRegenMs
        while s.hearts < Game.maxHearts && now >= at {
            s.hearts += 1
            at += Game.heartRegenMs
        }
        s.heartsRefillAt = s.hearts >= Game.maxHearts ? nil : at
        return s
    }

    static func loseHeart(_ s: GameState, _ now: Double) -> GameState {
        var n = syncHearts(s, now)
        n.hearts = max(0, n.hearts - 1)
        n.heartsRefillAt = n.heartsRefillAt ?? now + Game.heartRegenMs
        return n
    }

    // MARK: streak
    /// The streak as it should be shown today (0 if broken and not covered by freezes).
    static func visibleStreak(_ s: GameState, _ today: String) -> Int {
        guard let last = s.lastActiveDay else { return 0 }
        let gap = daysBetween(last, today)
        if gap <= 1 { return s.streak }
        return gap - 1 <= s.freezes ? s.streak : 0
    }

    static func recordActivity(_ s: GameState, _ today: String) -> GameState {
        if s.lastActiveDay == today { return s }
        var n = s
        let gap = s.lastActiveDay.map { daysBetween($0, today) }
        if gap == 1 { n.streak += 1 }
        else if let gap, gap > 1, gap - 1 <= n.freezes {
            n.freezes -= gap - 1
            n.streak += 1
        } else { n.streak = 1 }
        n.bestStreak = max(s.bestStreak, n.streak)
        n.lastActiveDay = today
        n.activeDays = Array((s.activeDays + [today]).suffix(30))
        return n
    }

    static func rollDaily(_ s: GameState, _ today: String) -> GameState {
        if s.daily.day == today { return s }
        var n = s
        n.daily = Daily(day: today)
        return n
    }

    // MARK: progression
    static func unitProgress(_ s: GameState, _ u: Unit) -> Int { min(lessonsPerUnit, s.progress[u.id] ?? 0) }
    static func unitDone(_ s: GameState, _ u: Unit) -> Bool { unitProgress(s, u) >= lessonsPerUnit }
    static func unitUnlocked(_ s: GameState, _ units: [Unit], _ i: Int) -> Bool { i == 0 || unitDone(s, units[i - 1]) }
    static func currentUnitIndex(_ s: GameState, _ units: [Unit]) -> Int {
        units.firstIndex { !unitDone(s, $0) } ?? max(0, units.count - 1)
    }
    static func unlockedWords(_ s: GameState, _ units: [Unit]) -> [Word] {
        units.enumerated().filter { unitUnlocked(s, units, $0.offset) }.flatMap { $0.element.words }
    }
    static func xpMultiplier(_ s: GameState, _ now: Double) -> Int { now < s.doubleXpUntil ? 2 : 1 }

    struct SessionResult {
        enum Kind { case lesson, practice }
        var kind: Kind
        var unitId: String?
        var lessonIndex: Int?
        var mistakes: Int
        var bestCombo: Int
    }

    struct Reward {
        var xp: Int
        var gems: Int
        var perfect: Bool
        var streakExtended: Bool
    }

    static func lessonXp(_ r: SessionResult) -> Int { (r.kind == .lesson ? 10 : 8) + (r.mistakes == 0 ? 5 : 0) }

    static func completeSession(_ s: GameState, _ r: SessionResult, _ now: Double, _ today: String) -> (GameState, Reward) {
        var n = rollDaily(s, today)
        let perfect = r.mistakes == 0
        let xp = lessonXp(r) * xpMultiplier(n, now)
        let gems = perfect && r.kind == .lesson ? Game.perfectReward : 0
        let before = n.lastActiveDay
        n = recordActivity(n, today)
        if r.kind == .lesson, let id = r.unitId, let index = r.lessonIndex {
            // Replaying an earlier lesson never moves progress backwards.
            n.progress[id] = min(lessonsPerUnit, max(n.progress[id] ?? 0, index + 1))
        }
        n.xp += xp
        n.gems += gems
        n.lessonsDone += 1
        n.daily.xp += xp
        n.daily.lessons += 1
        n.daily.bestCombo = max(n.daily.bestCombo, r.bestCombo)
        n.daily.perfect += perfect ? 1 : 0
        return (n, Reward(xp: xp, gems: gems, perfect: perfect, streakExtended: before != today))
    }

    // MARK: spaced repetition
    private static let intervalDays: [Double] = [0, 1, 2, 4, 7, 14]
    static func wordKey(_ lang: String, _ term: String) -> String { "\(lang):\(term)" }

    static func recordWord(_ s: GameState, _ lang: String, _ term: String, _ correct: Bool, _ now: Double) -> GameState {
        var n = s
        let key = wordKey(lang, term)
        let prev = s.words[key] ?? WordStat(level: 0, misses: 0, due: now)
        let level = correct ? min(5, prev.level + 1) : max(0, prev.level - 2)
        n.words[key] = WordStat(level: level, misses: prev.misses + (correct ? 0 : 1), due: correct ? now + intervalDays[level] * 864e5 : now)
        return n
    }

    /// Words seen before, weakest and most overdue first.
    static func reviewWords(_ s: GameState, _ lang: String, _ pool: [Word], _ now: Double) -> [Word] {
        pool.compactMap { w in s.words[wordKey(lang, w.term)].map { (w, $0) } }
            .filter { $0.1.due <= now }
            .sorted { a, b in
                if a.1.level != b.1.level { return a.1.level < b.1.level }
                if a.1.misses != b.1.misses { return a.1.misses > b.1.misses }
                return a.1.due < b.1.due
            }
            .map { $0.0 }
    }

    static func wordsLearned(_ s: GameState, _ lang: String) -> Int {
        s.words.filter { $0.key.hasPrefix("\(lang):") && $0.value.level >= 1 }.count
    }

    // MARK: quests
    enum QuestId: String, CaseIterable { case xp, lessons, combo, perfect }
    struct Quest: Identifiable {
        let id: QuestId
        let goal: Int
        let value: Int
        let reward: Int
        var done: Bool { value >= goal }
    }

    static func dailyQuests(_ s: GameState, _ today: String) -> [Quest] {
        let d = s.daily.day == today ? s.daily : Daily(day: today)
        return [
            Quest(id: .xp, goal: s.goal, value: d.xp, reward: 15),
            Quest(id: .lessons, goal: 2, value: d.lessons, reward: 15),
            Quest(id: .combo, goal: 8, value: d.bestCombo, reward: 20),
            Quest(id: .perfect, goal: 1, value: d.perfect, reward: 20),
        ]
    }

    static func claimQuest(_ s: GameState, _ id: QuestId, _ today: String) -> GameState {
        var n = rollDaily(s, today)
        guard let q = dailyQuests(n, today).first(where: { $0.id == id }), q.done, !n.daily.claimed.contains(id.rawValue) else { return s }
        n.gems += q.reward
        n.daily.claimed.append(id.rawValue)
        return n
    }

    // MARK: shop
    enum ShopItem: CaseIterable {
        case refill, freeze, doubleXp
        var price: Int { switch self { case .refill: 50; case .freeze: 100; case .doubleXp: 80 } }
    }

    static func canBuy(_ s: GameState, _ item: ShopItem, _ now: Double) -> Bool {
        if s.gems < item.price { return false }
        switch item {
        case .refill: return syncHearts(s, now).hearts < Game.maxHearts
        case .freeze: return s.freezes < Game.maxFreezes
        case .doubleXp: return now >= s.doubleXpUntil
        }
    }

    static func buy(_ s: GameState, _ item: ShopItem, _ now: Double) -> GameState {
        guard canBuy(s, item, now) else { return s }
        var n = s
        n.gems -= item.price
        switch item {
        case .refill: n.hearts = Game.maxHearts; n.heartsRefillAt = nil
        case .freeze: n.freezes += 1
        case .doubleXp: n.doubleXpUntil = now + 15 * 60 * 1000
        }
        return n
    }

    static func openChest(_ s: GameState, _ u: Unit) -> GameState {
        guard unitDone(s, u), !s.chests.contains(u.id) else { return s }
        var n = s
        n.gems += Game.chestReward
        n.chests.insert(u.id)
        return n
    }
}
