import Foundation

// MARK: - Grading (learn/src/game/grading.ts)

enum Grade { case correct, typo, wrong }

enum Grading {
    /// Lower-case, strip accents and punctuation, collapse whitespace.
    static func normalize(_ s: String) -> String {
        var t = s.lowercased().folding(options: .diacriticInsensitive, locale: nil)
        t = t.replacingOccurrences(of: "ɛ", with: "e").replacingOccurrences(of: "ɔ", with: "o")
        t = t.replacingOccurrences(of: "[’`]", with: "'", options: .regularExpression)
        t = t.replacingOccurrences(of: "[?!.,;:…«»\"¿¡()\\-]", with: " ", options: .regularExpression)
        t = t.replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression)
        return t.trimmingCharacters(in: .whitespaces)
    }

    static func levenshtein(_ a: String, _ b: String) -> Int {
        let x = Array(a), y = Array(b)
        var prev = Array(0...y.count)
        for i in 0..<x.count {
            var cur = [i + 1]
            for j in 0..<y.count { cur.append(min(prev[j + 1] + 1, cur[j] + 1, prev[j] + (x[i] == y[j] ? 0 : 1))) }
            prev = cur
        }
        return prev[y.count]
    }

    /// Typed answers forgive case, accents, punctuation and one small typo.
    static func typed(_ input: String, _ expected: String) -> Grade {
        let a = normalize(input), b = normalize(expected)
        if a.isEmpty { return .wrong }
        if a == b { return .correct }
        let allowed = b.count >= 10 ? 2 : b.count >= 5 ? 1 : 0
        return levenshtein(a, b) <= allowed ? .typo : .wrong
    }

    /// Word-bank answers must match exactly (ignoring case and punctuation).
    static func tiles(_ tiles: [String], _ expected: String) -> Grade {
        normalize(tiles.joined(separator: " ")) == normalize(expected) ? .correct : .wrong
    }

    /// Splits an answer into tappable tiles, dropping punctuation-only fragments.
    static func toTiles(_ sentence: String) -> [String] {
        sentence.split(whereSeparator: { $0.isWhitespace })
            .map { String($0).replacingOccurrences(of: "^[«\"(¿¡]+|[?!.,;:…»\")]+$", with: "", options: .regularExpression) }
            .filter { !$0.isEmpty }
    }
}

// MARK: - Exercises (learn/src/game/exercises.ts)

struct Exercise: Identifiable {
    enum Kind {
        case meaning(Word, options: [Word], isNew: Bool)
        case term(Word, options: [Word])
        case listen(Word, options: [Word])
        case match([Word])
        case build(Sentence, tiles: [String])
        case type(Word)
    }
    var id: String
    var kind: Kind
    var retry = false

    /// The word an exercise trains, for spaced-repetition tracking.
    var word: Word? {
        switch kind {
        case .meaning(let w, _, _), .term(let w, _), .listen(let w, _), .type(let w): w
        case .match, .build: nil
        }
    }
}

enum LessonBuilder {
    private static var counter = 0
    private static func make(_ kind: Exercise.Kind) -> Exercise {
        counter += 1
        return Exercise(id: "e\(counter)", kind: kind)
    }

    /// Picks 3 distractors whose term and meaning differ from the answer, so no two options look identical.
    private static func options(_ word: Word, _ pool: [Word]) -> [Word] {
        var terms: Set = [word.term.lowercased()], meanings: Set = [word.fr.lowercased()]
        var distractors: [Word] = []
        for w in pool.shuffled() {
            let t = w.term.lowercased(), m = w.fr.lowercased()
            if terms.contains(t) || meanings.contains(m) { continue }
            terms.insert(t)
            meanings.insert(m)
            distractors.append(w)
            if distractors.count == 3 { break }
        }
        return ([word] + distractors).shuffled()
    }

    private static func tiles(_ s: Sentence) -> [String] {
        let answer = Grading.toTiles(s.answer)
        let lower = Set(answer.map { $0.lowercased() })
        return (answer + s.distractors.filter { !lower.contains($0.lowercased()) }.prefix(4)).shuffled()
    }

    /// Builds one lesson. Each unit has four lessons that ramp up:
    ///  0 – meet the first half of the words   1 – meet the second half
    ///  2 – mix everything, first sentences    3 – recall: typing and full sentences
    static func lesson(_ unit: Unit, _ index: Int, pool extra: [Word], canListen: Bool, seen: Set<String>) -> [Exercise] {
        let pool = unit.words + extra.filter { !unit.words.contains($0) }
        let half = Int((Double(unit.words.count) / 2).rounded(.up))
        let focus = index == 0 ? Array(unit.words.prefix(half)) : index == 1 ? Array(unit.words.dropFirst(half)) : unit.words
        // A short half still works; anything under 3 borrows from the whole unit.
        let words = focus.count >= 3 ? focus : unit.words

        func meaning(_ w: Word) -> Exercise { make(.meaning(w, options: options(w, pool), isNew: !seen.contains(w.term))) }
        func term(_ w: Word) -> Exercise { make(.term(w, options: options(w, pool))) }
        func listen(_ w: Word) -> Exercise { canListen && w.audio != nil ? make(.listen(w, options: options(w, pool))) : term(w) }
        func match(_ ws: [Word]) -> Exercise { make(.match(Array(ws.shuffled().prefix(5)))) }
        func build(_ s: Sentence) -> Exercise { make(.build(s, tiles: tiles(s))) }
        func type(_ w: Word) -> Exercise { make(.type(w)) }
        func sentences(_ n: Int) -> [Sentence] { Array(unit.sentences.shuffled().prefix(n)) }
        func slice(_ ws: [Word], _ from: Int, _ to: Int) -> [Word] { Array(ws[min(from, ws.count)..<min(to, ws.count)]) }

        if index <= 1 {
            // Introduce each word, then reinforce. Recall always comes after its introduction.
            let order = words.shuffled()
            let a = order[0], b = order[1], rest = Array(order.dropFirst(2))
            var out = [meaning(a), meaning(b), term([a, b].randomElement()!)]
            out += rest.map(meaning)
            out += [match(words), listen(order.randomElement()!), term((rest.isEmpty ? order : rest).randomElement()!)]
            return out
        }

        let ws = words.shuffled()
        if index == 2 {
            let builds = sentences(2).map(build)
            let fill = max(0, 2 - builds.count)
            var mix = slice(ws, 0, 2 + fill).map(term) + slice(ws, 2 + fill, 4 + fill).map(listen) + slice(ws, 4 + fill, 6 + fill).map(meaning)
            mix.append(type(ws[6 % ws.count]))
            return (mix + builds).shuffled() + [match(ws)]
        }

        let builds = sentences(3).map(build)
        let fill = max(0, 3 - builds.count)
        let mix = slice(ws, 0, 3 + fill).map(type) + slice(ws, 3 + fill, 5 + fill).map(listen) + slice(ws, 5 + fill, 7 + fill).map(term)
        return (mix + builds + [match(ws)]).shuffled()
    }

    enum PracticeMode { case review, listen }

    /// A hearts-free practice session from words due for review.
    static func practice(_ words: [Word], pool: [Word], mode: PracticeMode, canListen: Bool) -> [Exercise] {
        let p = pool.count >= 4 ? pool : words
        let ws = Array(words.prefix(8))
        let optionsPool = ws + p
        if mode == .listen && canListen {
            return words.filter { $0.audio != nil }.prefix(8).shuffled().map { make(.listen($0, options: options($0, optionsPool))) }
        }
        var out: [Exercise] = ws.enumerated().map { i, w in
            i % 3 == 2 ? make(.type(w)) : i % 3 == 1 ? make(.term(w, options: options(w, optionsPool))) : make(.meaning(w, options: options(w, optionsPool), isNew: false))
        }
        if ws.count >= 4 { out.insert(make(.match(Array(ws.prefix(5)))), at: 2) }
        return out
    }
}

// MARK: - Session (learn/src/game/session.ts)

struct LessonSession {
    enum Status { case answering, correct, typo, wrong }
    var queue: [Exercise]
    var index = 0
    let total: Int
    var solved = 0
    var status: Status = .answering
    var mistakes = 0
    var combo = 0
    var bestCombo = 0

    init(_ queue: [Exercise]) {
        self.queue = queue
        total = queue.count
    }

    var current: Exercise? { index < queue.count ? queue[index] : nil }
    var finished: Bool { index >= queue.count }
    var progress: Double { total == 0 ? 0 : min(1, Double(solved) / Double(total)) }
    var accuracy: Int {
        let attempts = solved + mistakes
        return attempts == 0 ? 100 : Int((Double(solved) / Double(attempts) * 100).rounded())
    }

    mutating func grade(_ g: Grade) {
        guard status == .answering, var again = current else { return }
        if g == .wrong {
            // A missed exercise comes back at the end, flagged as a previous mistake.
            again.id += "-r\(queue.count)"
            again.retry = true
            status = .wrong
            mistakes += 1
            combo = 0
            queue.append(again)
            return
        }
        status = g == .typo ? .typo : .correct
        solved += 1
        combo += 1
        bestCombo = max(bestCombo, combo)
    }

    mutating func next() {
        guard status != .answering else { return }
        index += 1
        status = .answering
    }
}
