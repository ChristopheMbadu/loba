import SwiftUI

private let wordColors: [Color] = [.sky, .leaf, .coral, .plum, .sunShade, Color(hex: "ff8a3d")]

// MARK: - Choosing a puzzle

/// Mots mêlés: one puzzle per unit, opened one after the other.
struct PuzzleHub: View {
    @Environment(AppStore.self) private var store
    @Environment(\.dismiss) private var dismiss
    @State private var playing: Unit?
    @State private var scores = PuzzleScores.all
    #if DEBUG
    var autostart = false
    #endif

    var body: some View {
        let units = store.units, ui = store.state.ui
        let open = (units.firstIndex { scores[$0.id] == nil } ?? units.count)
        VStack(spacing: 0) {
            HStack {
                Button { dismiss() } label: {
                    Image(systemName: "xmark").font(.system(size: 17, weight: .heavy)).foregroundStyle(Color.ink).frame(width: 42, height: 42)
                        .background(RoundedRectangle(cornerRadius: 13).fill(Color.surface))
                }
                Spacer()
                HStack(spacing: 6) {
                    Art(name: "star", size: 22)
                    Text("\(scores.values.reduce(0, +)) / \(units.count * 3)").font(.text(16, .heavy)).foregroundStyle(Color.sunShade)
                }
                .padding(.horizontal, 12).padding(.vertical, 8).background(Capsule().fill(Color.sunTint))
            }
            .padding(.horizontal, 16).padding(.vertical, 8)
            ScrollView {
                VStack(spacing: 14) {
                    HStack(alignment: .center, spacing: 6) {
                        LobaView(mood: .wave, size: 104)
                        Bubble {
                            VStack(alignment: .leading, spacing: 4) {
                                Text(L("Mots mêlés", "Word hunt")).font(.display(26)).foregroundStyle(Color.ink)
                                Text(L("Trouve les mots cachés. Chacun te révèle son sens et une phrase.", "Find the hidden words. Each one reveals its meaning and a sentence."))
                                    .font(.text(15)).foregroundStyle(Color.ink2).fixedSize(horizontal: false, vertical: true)
                            }
                        }
                    }
                    KubaBand(height: 10).clipShape(Capsule())
                    ForEach(Array(units.enumerated()), id: \.element.id) { i, unit in
                        let locked = i > open, stars = scores[unit.id] ?? 0
                        Button { playing = unit } label: {
                            HStack(spacing: 14) {
                                Text(locked ? "🔒" : unit.icon).font(.system(size: 30)).frame(width: 60, height: 60)
                                    .background(RoundedRectangle(cornerRadius: 18).fill(locked ? Color.surface : wordColors[i % wordColors.count].opacity(0.16)))
                                VStack(alignment: .leading, spacing: 4) {
                                    Text(L("Grille \(i + 1)", "Puzzle \(i + 1)")).font(.text(12, .heavy)).textCase(.uppercase).tracking(1.2).foregroundStyle(Color.muted)
                                    Text(unit.name(ui)).font(.text(18, .heavy)).foregroundStyle(locked ? Color.muted : .ink).multilineTextAlignment(.leading)
                                }
                                Spacer(minLength: 0)
                                if !locked {
                                    HStack(spacing: 2) { ForEach(0..<3, id: \.self) { n in Art(name: "star", size: 22).saturation(n < stars ? 1 : 0).opacity(n < stars ? 1 : 0.28) } }
                                }
                            }
                            .padding(14).card(selected: i == open, tint: .sun)
                        }
                        .buttonStyle(.plain).disabled(locked)
                    }
                }
                .padding(20)
            }
        }
        .background(Color.white)
        .fullScreenCover(item: $playing, onDismiss: { scores = PuzzleScores.all }) { unit in
            PuzzleGame(unit: unit) { next in
                scores = PuzzleScores.all
                playing = nil
                if let next { DispatchQueue.main.asyncAfter(deadline: .now() + 0.45) { playing = next } }
            }
        }
        #if DEBUG
        .onAppear { if autostart, playing == nil { playing = units.first } }
        #endif
    }
}

// MARK: - Playing

struct PuzzleGame: View {
    let unit: Unit
    /// Called when the learner leaves: with the next unit when they asked for the next puzzle.
    let leave: (Unit?) -> Void
    @Environment(AppStore.self) private var store
    @State private var puzzle: Puzzle?
    @State private var found: [String] = []
    @State private var hinted: Set<String> = []
    @State private var start: Puzzle.Cell?
    @State private var selection: [Puzzle.Cell] = []
    @State private var hint: Puzzle.Cell?
    @State private var last: Puzzle.Entry?
    @State private var burst = 0
    @State private var burstAt = CGPoint.zero
    @State private var shake = 0
    @State private var dealt = false
    @State private var finished = false
    @State private var reward: Rules.Reward?

    var body: some View {
        ZStack {
            if let puzzle {
                if finished, let reward {
                    PuzzleDone(puzzle: puzzle, stars: Puzzle.stars(hints: hinted.count), reward: reward, next: next, leave: leave).transition(.opacity)
                } else {
                    board(puzzle)
                }
            } else {
                VStack(spacing: 16) {
                    LobaView(mood: .think, size: 140)
                    Text(L("Pas encore assez de mots courts dans cette unité.", "Not enough short words in this unit yet.")).font(.text(17, .bold)).foregroundStyle(Color.ink2).multilineTextAlignment(.center)
                    Button(L("Retour", "Back")) { leave(nil) }.buttonStyle(.chunky(.outline))
                }
                .padding(30)
            }
        }
        .background(Color.white.ignoresSafeArea())
        .onAppear(perform: deal)
    }

    private var next: Unit? {
        let units = store.units
        guard let i = units.firstIndex(where: { $0.id == unit.id }), i + 1 < units.count else { return nil }
        return units[i + 1]
    }

    private func deal() {
        guard puzzle == nil else { return }
        var rng = SystemRandomNumberGenerator()
        puzzle = Puzzle.make(unit: unit, of: store.units, using: &rng)
        Sfx.play(.whoosh)
        withAnimation(.spring(duration: 0.5, bounce: 0.4).delay(0.1)) { dealt = true }
        #if DEBUG
        // `-autoplay yes` finds every word, `-autoplay hold` stops halfway: for screenshots.
        guard let style = UserDefaults.standard.string(forKey: "autoplay"), let puzzle else { return }
        Task {
            try? await Task.sleep(for: .seconds(1.2))
            for (i, entry) in puzzle.entries.enumerated() {
                if style == "hold", i == 3 { selection = Array(entry.cells.prefix(3)); return }
                if style == "wrong", i == 1 { help(entry, in: puzzle); try? await Task.sleep(for: .seconds(0.5)) }
                selection = entry.cells
                try? await Task.sleep(for: .seconds(0.35))
                release(in: puzzle, side: 44)
                try? await Task.sleep(for: .seconds(0.7))
            }
        }
        #endif
    }

    private func color(_ entry: Puzzle.Entry, in puzzle: Puzzle) -> Color {
        wordColors[(puzzle.entries.firstIndex(of: entry) ?? 0) % wordColors.count]
    }

    // MARK: layout

    private func board(_ puzzle: Puzzle) -> some View {
        let ui = store.state.ui
        return VStack(spacing: 12) {
            HStack(spacing: 12) {
                Button { leave(nil) } label: {
                    Image(systemName: "xmark").font(.system(size: 17, weight: .heavy)).foregroundStyle(Color.ink).frame(width: 42, height: 42)
                        .background(RoundedRectangle(cornerRadius: 13).fill(Color.surface))
                }
                VStack(alignment: .leading, spacing: 5) {
                    Text("\(unit.icon) \(unit.name(ui))").font(.text(15, .heavy)).foregroundStyle(Color.ink).lineLimit(1)
                    Meter(value: Double(found.count) / Double(puzzle.entries.count), color: .leaf, height: 12)
                }
                Text("\(found.count)/\(puzzle.entries.count)").font(.display(20)).foregroundStyle(Color.leafShade).contentTransition(.numericText())
            }
            .padding(.horizontal, 16).padding(.top, 8)

            Flow(spacing: 8) {
                ForEach(puzzle.entries) { entry in clue(entry, in: puzzle, ui) }
            }
            .padding(.horizontal, 16).frame(maxWidth: .infinity, alignment: .leading)

            grid(puzzle).padding(.horizontal, 14)

            Group {
                if let last { discovery(last, color: color(last, in: puzzle), ui).id(last.id).transition(.asymmetric(insertion: .move(edge: .bottom).combined(with: .opacity), removal: .opacity)) }
                else {
                    HStack(spacing: 4) {
                        LobaView(mood: .think, size: 84)
                        Bubble {
                            Text(L("Glisse ton doigt sur les lettres. Besoin d’aide ? Touche un indice.", "Slide your finger across the letters. Stuck? Tap a clue."))
                                .font(.text(15, .bold)).foregroundStyle(Color.ink2).fixedSize(horizontal: false, vertical: true)
                        }
                    }
                }
            }
            .padding(.horizontal, 16)
            Spacer(minLength: 0)
        }
    }

    private func clue(_ entry: Puzzle.Entry, in puzzle: Puzzle, _ ui: UiLang) -> some View {
        let done = found.contains(entry.id), tint = color(entry, in: puzzle)
        return Button { help(entry, in: puzzle) } label: {
            HStack(spacing: 6) {
                if done { Image(systemName: "checkmark").font(.system(size: 12, weight: .black)) }
                Text(done ? entry.word.term : entry.word.meaning(ui)).font(.text(15, .heavy)).lineLimit(1)
                if !done { Text("\(entry.letters.count)").font(.text(12, .heavy)).foregroundStyle(tint).frame(width: 20, height: 20).background(Circle().fill(tint.opacity(0.16))) }
            }
            .foregroundStyle(done ? .white : Color.ink)
            .padding(.horizontal, 12).padding(.vertical, 8)
            .background(Capsule().fill(done ? tint : .white))
            .overlay(Capsule().strokeBorder(done ? tint : hinted.contains(entry.id) ? Color.sun : .line, lineWidth: 2))
            .background(Capsule().fill(done ? tint.opacity(0.5) : Color.line).offset(y: 3))
            .padding(.bottom, 3)
        }
        .buttonStyle(.plain).allowsHitTesting(!done)
        .animation(.spring(duration: 0.4, bounce: 0.5), value: done)
    }

    private func grid(_ puzzle: Puzzle) -> some View {
        GeometryReader { geo in
            let side = geo.size.width / CGFloat(Puzzle.size)
            let center = { (c: Puzzle.Cell) in CGPoint(x: (CGFloat(c.col) + 0.5) * side, y: (CGFloat(c.row) + 0.5) * side) }
            let lit = Set(selection).union(puzzle.entries.filter { found.contains($0.id) }.flatMap(\.cells))
            ZStack(alignment: .topLeading) {
                RoundedRectangle(cornerRadius: 22).fill(Color.surface)
                RoundedRectangle(cornerRadius: 22).strokeBorder(Color.line, lineWidth: 2)
                // Ribbons over the words already found, then the one being drawn.
                ForEach(puzzle.entries.filter { found.contains($0.id) }) { entry in
                    ribbon(entry.cells, side: side, center: center).stroke(color(entry, in: puzzle), style: StrokeStyle(lineWidth: side * 0.78, lineCap: .round))
                        .transition(.opacity.combined(with: .scale(scale: 0.9)))
                }
                if selection.count > 0 {
                    ribbon(selection, side: side, center: center).stroke(Color.sun, style: StrokeStyle(lineWidth: side * 0.78, lineCap: .round))
                }
                if let hint {
                    TimelineView(ambient()) { timeline in
                        let beat = (sin(timeline.date.timeIntervalSinceReferenceDate * 7) + 1) / 2
                        Circle().strokeBorder(Color.sun, lineWidth: 4).background(Circle().fill(Color.sunTint))
                            .frame(width: side * (0.8 + beat * 0.14), height: side * (0.8 + beat * 0.14)).position(center(hint))
                    }
                }
                ForEach(0..<Puzzle.size, id: \.self) { r in
                    ForEach(0..<Puzzle.size, id: \.self) { c in
                        let cell = Puzzle.Cell(row: r, col: c), on = lit.contains(cell) && hint != cell || selection.contains(cell)
                        Text(String(puzzle.grid[r][c])).font(.display(side * 0.52))
                            .foregroundStyle(selection.contains(cell) ? Color.ink : on ? .white : .ink)
                            .frame(width: side, height: side)
                            .scaleEffect(selection.contains(cell) ? 1.22 : dealt ? 1 : 0.2).opacity(dealt ? 1 : 0)
                            .animation(.spring(duration: 0.35, bounce: 0.55).delay(dealt && selection.isEmpty && found.isEmpty ? Double(r + c) * 0.025 : 0), value: dealt)
                            .animation(.spring(duration: 0.18, bounce: 0.5), value: selection)
                            .position(center(cell))
                    }
                }
                Burst(trigger: burst).frame(width: 300, height: 300).position(burstAt)
            }
            .modifier(Shake(amount: CGFloat(shake)))
            .contentShape(Rectangle())
            .gesture(DragGesture(minimumDistance: 0)
                .onChanged { g in
                    let cell = Puzzle.Cell(row: max(0, min(Puzzle.size - 1, Int(g.location.y / side))), col: max(0, min(Puzzle.size - 1, Int(g.location.x / side))))
                    if start == nil { start = cell; hint = nil }
                    let line = Puzzle.line(from: start!, to: cell)
                    if line != selection {
                        selection = line
                        Sfx.play(.tile, line.count - 1); Haptic.tap()
                    }
                }
                .onEnded { _ in release(in: puzzle, side: side) })
        }
        .aspectRatio(1, contentMode: .fit)
    }

    private func ribbon(_ cells: [Puzzle.Cell], side: CGFloat, center: (Puzzle.Cell) -> CGPoint) -> Path {
        var path = Path()
        guard let first = cells.first, let end = cells.last else { return path }
        path.move(to: center(first))
        // A single cell still needs a length for its round caps to draw.
        path.addLine(to: first == end ? CGPoint(x: center(first).x + 0.01, y: center(first).y) : center(end))
        return path
    }

    private func discovery(_ entry: Puzzle.Entry, color: Color, _ ui: UiLang) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 10) {
                if entry.word.audio != nil {
                    Button { Voice.say(entry.word) } label: {
                        Image(systemName: "speaker.wave.2.fill").font(.system(size: 16, weight: .bold)).foregroundStyle(.white).frame(width: 38, height: 38).background(Circle().fill(color))
                    }
                }
                Text(entry.word.term).font(.display(26)).foregroundStyle(color)
                Text("=").font(.text(18, .heavy)).foregroundStyle(Color.muted)
                Text(entry.word.meaning(ui)).font(.text(18, .heavy)).foregroundStyle(Color.ink).lineLimit(2).minimumScaleFactor(0.8)
                Spacer(minLength: 0)
            }
            if let example = entry.example {
                VStack(alignment: .leading, spacing: 2) {
                    Text(highlighted(example.text, entry.word.term, color)).font(.text(17, .bold)).fixedSize(horizontal: false, vertical: true)
                    Text(example.translation(ui)).font(.text(15)).foregroundStyle(Color.muted).fixedSize(horizontal: false, vertical: true)
                }
                .padding(.leading, 12).overlay(alignment: .leading) { Capsule().fill(color.opacity(0.5)).frame(width: 4) }
            }
        }
        .padding(14).frame(maxWidth: .infinity, alignment: .leading).card(selected: true, tint: color)
    }

    // MARK: moves

    private func release(in puzzle: Puzzle, side: CGFloat) {
        defer { start = nil }
        guard let entry = puzzle.match(selection, found: Set(found)) else {
            if selection.count > 1 { Sfx.play(.tick); withAnimation(.linear(duration: 0.3)) { shake += 1 } }
            withAnimation(.easeOut(duration: 0.15)) { selection = [] }
            return
        }
        let middle = entry.cells[entry.cells.count / 2]
        burstAt = CGPoint(x: (CGFloat(middle.col) + 0.5) * side, y: (CGFloat(middle.row) + 0.5) * side)
        burst += 1
        hint = nil
        withAnimation(.spring(duration: 0.4, bounce: 0.4)) {
            found.append(entry.id)
            selection = []
            last = entry
        }
        Sfx.play(.correct, found.count); Haptic.success()
        Voice.say(entry.word)
        // A word found without help counts as known; one found with a hint is still new.
        if !hinted.contains(entry.id) { store.recordWord(entry.word.term, correct: true) }
        if found.count == puzzle.entries.count { finish(puzzle) }
    }

    /// Tapping a clue shows where its word begins. It costs a star, not hearts.
    private func help(_ entry: Puzzle.Entry, in puzzle: Puzzle) {
        hinted.insert(entry.id)
        Sfx.play(.pop); Haptic.thud()
        withAnimation(.spring(duration: 0.3, bounce: 0.6)) { hint = entry.cells.first }
    }

    private func finish(_ puzzle: Puzzle) {
        let stars = Puzzle.stars(hints: hinted.count)
        PuzzleScores.record(unit.id, stars: stars)
        reward = store.complete(Rules.SessionResult(kind: .practice, unitId: nil, lessonIndex: nil, mistakes: hinted.count, bestCombo: puzzle.entries.count - hinted.count))
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.6) {
            Sfx.play(.complete)
            withAnimation(.easeInOut(duration: 0.35)) { finished = true }
        }
    }
}

/// The sentence with the word it illustrates picked out in colour.
private func highlighted(_ sentence: String, _ term: String, _ color: Color) -> AttributedString {
    var text = AttributedString(sentence)
    text.foregroundColor = .ink
    if let range = text.range(of: term, options: [.caseInsensitive, .diacriticInsensitive]) {
        text[range].foregroundColor = color
        text[range].underlineStyle = Text.LineStyle(pattern: .solid, color: color.opacity(0.4))
    }
    return text
}

private struct Shake: GeometryEffect {
    var amount: CGFloat
    var animatableData: CGFloat { get { amount } set { amount = newValue } }
    func effectValue(size: CGSize) -> ProjectionTransform { ProjectionTransform(CGAffineTransform(translationX: sin(amount * .pi * 4) * 7, y: 0)) }
}

// MARK: - Finished

private struct PuzzleDone: View {
    let puzzle: Puzzle
    let stars: Int
    let reward: Rules.Reward
    let next: Unit?
    let leave: (Unit?) -> Void
    @Environment(AppStore.self) private var store
    @State private var shown = 0

    var body: some View {
        let ui = store.state.ui
        ZStack {
            VStack(spacing: 0) {
                ScrollView {
                    VStack(spacing: 14) {
                        ZStack {
                            SunRays().frame(width: 280, height: 280).opacity(0.5)
                            LobaView(mood: .cheer, size: 150)
                        }
                        .frame(height: 170)
                        HStack(spacing: 10) {
                            ForEach(0..<3, id: \.self) { n in
                                Art(name: "star", size: n == 1 ? 64 : 50)
                                    .saturation(n < stars ? 1 : 0).opacity(n < stars ? 1 : 0.25)
                                    .scaleEffect(shown > n || n >= stars ? 1 : 0.1).rotationEffect(.degrees(shown > n || n >= stars ? 0 : -140)).offset(y: n == 1 ? -8 : 0)
                            }
                        }
                        Text(stars == 3 ? L("Sans aucune aide !", "No help needed!") : L("Grille terminée !", "Puzzle solved!")).font(.display(30)).foregroundStyle(Color.ink)
                        HStack(spacing: 8) {
                            Art(name: "bolt", size: 26)
                            Text("+\(reward.xp) XP").font(.display(22)).foregroundStyle(Color.sunShade)
                        }
                        .padding(.horizontal, 18).padding(.vertical, 8).background(Capsule().fill(Color.sunTint))
                        Text(L("Tes mots", "Your words")).font(.text(13, .heavy)).textCase(.uppercase).tracking(1.5).foregroundStyle(Color.muted).frame(maxWidth: .infinity, alignment: .leading).padding(.top, 6)
                        ForEach(Array(puzzle.entries.enumerated()), id: \.element.id) { i, entry in
                            let tint = wordColors[i % wordColors.count]
                            Button { Sfx.play(.tile, i); Voice.say(entry.word) } label: {
                                VStack(alignment: .leading, spacing: 4) {
                                    HStack(spacing: 8) {
                                        Text(entry.word.term).font(.display(20)).foregroundStyle(tint)
                                        Text(entry.word.meaning(ui)).font(.text(16, .bold)).foregroundStyle(Color.ink)
                                        Spacer(minLength: 0)
                                        if entry.word.audio != nil { Image(systemName: "speaker.wave.2.fill").foregroundStyle(tint) }
                                    }
                                    if let example = entry.example {
                                        Text(highlighted(example.text, entry.word.term, tint)).font(.text(15, .bold)).multilineTextAlignment(.leading)
                                        Text(example.translation(ui)).font(.text(14)).foregroundStyle(Color.muted).multilineTextAlignment(.leading)
                                    }
                                }
                                .padding(12).frame(maxWidth: .infinity, alignment: .leading).card()
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(20)
                }
                VStack(spacing: 6) {
                    if let next { Button(L("Grille suivante", "Next puzzle")) { leave(next) }.buttonStyle(.chunky(.green)) }
                    Button(L("Terminer", "Done")) { leave(nil) }.buttonStyle(.chunky(next == nil ? .green : .outline))
                }
                .padding(.horizontal, 20).padding(.top, 10).padding(.bottom, 8)
            }
            Confetti()
        }
        .background(Color.white)
        .onAppear {
            Haptic.success()
            for n in 0..<stars {
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.35 + Double(n) * 0.32) {
                    Sfx.play(.correct, n + 2)
                    withAnimation(.spring(duration: 0.5, bounce: 0.6)) { shown = n + 1 }
                }
            }
        }
    }
}
