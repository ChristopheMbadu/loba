import Foundation

/// The word puzzle: words from a Studio unit hidden in a grid of letters.
/// Finding a word reveals what it means and a sentence that uses it.
struct Puzzle {
    struct Cell: Hashable { let row: Int, col: Int }

    struct Example: Equatable {
        let text: String
        let fr: String
        let en: String
        func translation(_ ui: UiLang) -> String { ui == .en && !en.isEmpty ? en : fr }
    }

    struct Entry: Identifiable, Equatable {
        let word: Word
        let letters: [Character]
        let cells: [Cell]
        let example: Example?
        var id: String { word.term }
    }

    static let size = 8
    /// How many words one grid hides.
    static let count = 6

    let unit: Unit
    let entries: [Entry]
    let grid: [[Character]]

    // MARK: words

    /// The letters a term takes in the grid: capitals, no spaces or punctuation. Nil when it doesn't fit.
    static func letters(_ term: String) -> [Character]? {
        guard term.split(separator: " ").count <= 2 else { return nil }
        let letters = Array(term.precomposedStringWithCanonicalMapping.uppercased().filter(\.isLetter))
        return (3...size).contains(letters.count) ? letters : nil
    }

    private static func tokens(_ text: String) -> [String] {
        text.lowercased().split { !$0.isLetter && $0 != "'" && $0 != "’" }.map(String.init)
    }

    /// The sentence written for this word in Studio; otherwise a sentence of the course that uses it.
    static func example(for word: Word, in unit: Unit, of units: [Unit]) -> Example? {
        if !word.example.isEmpty { return Example(text: word.example, fr: word.exampleFr, en: word.exampleEn) }
        let wanted = tokens(word.term)
        guard !wanted.isEmpty else { return nil }
        for candidate in [unit] + units.filter({ $0.id != unit.id }) {
            for sentence in candidate.sentences {
                let have = tokens(sentence.answer)
                // A sentence that is only the word itself teaches nothing more.
                guard have.count > wanted.count else { continue }
                if (0...(have.count - wanted.count)).contains(where: { Array(have[$0..<($0 + wanted.count)]) == wanted }) {
                    return Example(text: sentence.answer, fr: sentence.fr, en: sentence.en)
                }
            }
        }
        return nil
    }

    /// Words for a unit's puzzle: its own first, topped up from the units that follow when it has few that fit.
    static func words(for unit: Unit, of units: [Unit], using rng: inout some RandomNumberGenerator) -> [Word] {
        var picked: [Word] = [], seen = Set<String>()
        let start = units.firstIndex { $0.id == unit.id } ?? 0
        let others = units.indices.map { units[(start + 1 + $0) % max(1, units.count)] }.filter { $0.id != unit.id }
        for source in [unit] + others {
            for word in source.words.shuffled(using: &rng) {
                guard picked.count < count, let key = letters(word.term).map({ String($0) }), seen.insert(key).inserted else { continue }
                picked.append(word)
            }
            if picked.count >= count { break }
        }
        return picked
    }

    // MARK: grid

    /// Steps a word can run in. Early puzzles read left to right and downwards; later ones also run backwards.
    static func directions(level: Int) -> [(Int, Int)] {
        var d = [(0, 1), (1, 0)]
        if level >= 1 { d += [(1, 1), (-1, 1)] }
        if level >= 4 { d += [(0, -1), (-1, 0)] }
        if level >= 8 { d += [(-1, -1), (1, -1)] }
        return d
    }

    static func make(unit: Unit, of units: [Unit], using rng: inout some RandomNumberGenerator) -> Puzzle? {
        let level = units.firstIndex { $0.id == unit.id } ?? 0
        let words = words(for: unit, of: units, using: &rng)
        guard words.count >= 3 else { return nil }
        let steps = directions(level: level)
        for _ in 0..<40 {
            var grid = [[Character?]](repeating: [Character?](repeating: nil, count: size), count: size)
            var entries: [Entry] = []
            // Longest first: they are the hardest to fit.
            for word in words.sorted(by: { (letters($0.term)?.count ?? 0) > (letters($1.term)?.count ?? 0) }) {
                guard let letters = letters(word.term) else { continue }
                var placed: [Cell]?
                for _ in 0..<160 where placed == nil {
                    let (dr, dc) = steps.randomElement(using: &rng)!
                    let r = Int.random(in: 0..<size, using: &rng), c = Int.random(in: 0..<size, using: &rng)
                    let endR = r + dr * (letters.count - 1), endC = c + dc * (letters.count - 1)
                    guard (0..<size).contains(endR), (0..<size).contains(endC) else { continue }
                    let cells = letters.indices.map { Cell(row: r + dr * $0, col: c + dc * $0) }
                    if zip(cells, letters).allSatisfy({ grid[$0.row][$0.col] == nil || grid[$0.row][$0.col] == $1 }) { placed = cells }
                }
                guard let placed else { break }
                for (cell, letter) in zip(placed, letters) { grid[cell.row][cell.col] = letter }
                entries.append(Entry(word: word, letters: letters, cells: placed, example: example(for: word, in: unit, of: units)))
            }
            guard entries.count == words.count else { continue }
            // The rest is filled with letters of the hidden words, so the grid looks like the language.
            let pool = entries.flatMap(\.letters)
            let filled = grid.map { row in row.map { $0 ?? pool.randomElement(using: &rng)! } }
            // Show the clues in the order the unit teaches them, not by length.
            let order = Dictionary(uniqueKeysWithValues: words.enumerated().map { ($1.term, $0) })
            return Puzzle(unit: unit, entries: entries.sorted { order[$0.id]! < order[$1.id]! }, grid: filled)
        }
        return nil
    }

    // MARK: play

    /// The straight line of cells from where the finger started to where it is, snapped to the nearest of 8 directions.
    static func line(from a: Cell, to b: Cell) -> [Cell] {
        let dr = b.row - a.row, dc = b.col - a.col
        guard dr != 0 || dc != 0 else { return [a] }
        let angle = atan2(Double(dr), Double(dc)), sector = Int((angle / (.pi / 4)).rounded())
        let step = [(0, 1), (1, 1), (1, 0), (1, -1), (0, -1), (-1, -1), (-1, 0), (-1, 1)][((sector % 8) + 8) % 8]
        let length = max(abs(dr), abs(dc))
        return (0...length).map { Cell(row: a.row + step.0 * $0, col: a.col + step.1 * $0) }.filter { (0..<size).contains($0.row) && (0..<size).contains($0.col) }
    }

    /// The hidden word these cells spell, read either way, among those not found yet.
    func match(_ cells: [Cell], found: Set<String>) -> Entry? {
        let spelled = cells.map { grid[$0.row][$0.col] }
        return entries.first { !found.contains($0.id) && ($0.letters == spelled || $0.letters == spelled.reversed()) }
    }

    /// Three stars without help, two with a little, one otherwise.
    static func stars(hints: Int) -> Int { hints == 0 ? 3 : hints <= 2 ? 2 : 1 }
}

/// Best stars per puzzle, kept on this phone.
enum PuzzleScores {
    private static let key = "loba-puzzle-stars"
    static var all: [String: Int] { UserDefaults.standard.dictionary(forKey: key) as? [String: Int] ?? [:] }
    static func record(_ unit: String, stars: Int) {
        var scores = all
        scores[unit] = max(scores[unit] ?? 0, stars)
        UserDefaults.standard.set(scores, forKey: key)
    }
    static func clear() { UserDefaults.standard.removeObject(forKey: key) }
}
