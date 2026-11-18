import SwiftUI

enum LessonMode {
    case lesson(Unit, Int)
    case practice(LessonBuilder.PracticeMode)

    var isLesson: Bool { if case .lesson = self { true } else { false } }
}

/// What the learner has entered for the exercise on screen.
private enum Answer: Equatable {
    case choice(Word)
    case tiles([String])
    case text(String)
    case matched
}

struct LessonView: View {
    let mode: LessonMode
    @Environment(AppStore.self) private var store
    @Environment(\.dismiss) private var dismiss
    @State private var session = LessonSession([])
    @State private var answer: Answer?
    @State private var reward: Rules.Reward?
    @State private var confirmQuit = false
    @State private var outOfHearts = false
    @State private var shake = false
    @State private var sparks = 0

    var body: some View {
        ZStack {
            if let reward {
                CompleteView(reward: reward, accuracy: session.accuracy, combo: session.bestCombo, practice: !mode.isLesson) {
                    dismiss()
                    // Once the celebration is over, offer the evening reminder (iOS asks only the first time).
                    Reminder.afterLesson()
                }
                    .transition(.opacity.combined(with: .scale(scale: 0.96)))
            } else if let exercise = session.current {
                VStack(spacing: 0) {
                    header
                    ScrollView {
                        ExerciseView(exercise: exercise, answer: $answer, locked: session.status != .answering) { check() }
                            .padding(.horizontal, 20).padding(.top, 8).padding(.bottom, 24)
                            .id(exercise.id)
                            .transition(.asymmetric(insertion: .move(edge: .trailing).combined(with: .opacity), removal: .move(edge: .leading).combined(with: .opacity)))
                    }
                    .scrollDismissesKeyboard(.interactively)
                    footer(exercise)
                }
                .offset(x: shake ? -8 : 0)
            }
        }
        .background(Color.white)
        .overlay(alignment: .bottom) { Burst(trigger: sparks).frame(height: 420).padding(.bottom, 60) }
        .onAppear(perform: begin)
        .onDisappear { Voice.stop() }
        #if DEBUG
        .task {
            // `-autoplay` answers every exercise correctly (and `-autoplay wrong` misses the second one), for screenshots.
            guard let style = UserDefaults.standard.string(forKey: "autoplay") else { return }
            var n = 0
            while reward == nil, let e = session.current {
                try? await Task.sleep(for: .seconds(1.1))
                n += 1
                switch e.kind {
                case .meaning(let w, let o, _), .term(let w, let o), .listen(let w, let o): answer = .choice(style == "wrong" && n == 2 ? o.first { $0 != w }! : w)
                case .build(let s, _): answer = .tiles(Grading.toTiles(s.answer))
                case .type(let w): answer = .text(w.term)
                case .match: answer = .matched
                }
                if style == "hold" { return }
                try? await Task.sleep(for: .seconds(0.5))
                check()
                try? await Task.sleep(for: .seconds(1.3))
                advance()
            }
        }
        #endif
        .confirmationDialog(L("Attends, ne pars pas !", "Wait, don’t go!"), isPresented: $confirmQuit, titleVisibility: .visible) {
            Button(L("Quitter la leçon", "Leave the lesson"), role: .destructive) { dismiss() }
            Button(L("Continuer à apprendre", "Keep learning"), role: .cancel) {}
        } message: { Text(L("Ta progression dans cette leçon sera perdue.", "Your progress in this lesson will be lost.")) }
        .alert(L("Plus de cœurs", "Out of hearts"), isPresented: $outOfHearts) {
            if Rules.canBuy(store.state, .refill, Game.now()) {
                Button(L("Recharger (50 gemmes)", "Refill (50 gems)")) { store.buy(.refill); Sfx.play(.heart); advance() }
            }
            Button(L("Quitter", "Leave"), role: .cancel) { dismiss() }
        } message: { Text(L("Un cœur revient toutes les 30 minutes.", "A heart comes back every 30 minutes.")) }
    }

    private var header: some View {
        HStack(spacing: 14) {
            Button { confirmQuit = true } label: { Image(systemName: "xmark").font(.system(size: 20, weight: .heavy)).foregroundStyle(Color.muted) }
            Meter(value: session.progress, color: session.combo >= 3 ? .sun : .leaf)
            if mode.isLesson {
                HStack(spacing: 5) { Art(name: "heart", size: 26); Text("\(store.state.hearts)").font(.text(18, .heavy)).foregroundStyle(Color.coral).contentTransition(.numericText()) }
            }
        }
        .padding(.horizontal, 20).padding(.vertical, 14)
        .overlay(alignment: .bottomTrailing) {
            if session.combo >= 3 {
                Text("\(session.combo) \(L("d’affilée", "in a row")) 🔥").font(.text(13, .heavy)).foregroundStyle(Color.sunShade).padding(.trailing, 24).offset(y: 10)
                    .transition(.scale.combined(with: .opacity))
            }
        }
        .animation(.spring(duration: 0.3, bounce: 0.5), value: session.combo)
    }

    @ViewBuilder
    private func footer(_ exercise: Exercise) -> some View {
        let status = session.status
        VStack(alignment: .leading, spacing: 12) {
            if status != .answering {
                HStack(alignment: .top, spacing: 12) {
                    LobaView(mood: status == .wrong ? .sad : .cheer, size: 64, tappable: false)
                    VStack(alignment: .leading, spacing: 3) {
                        Text(status == .wrong ? L("Pas tout à fait", "Not quite") : status == .typo ? L("Presque parfait !", "Almost perfect!") : cheer)
                            .font(.display(21)).foregroundStyle(status == .wrong ? Color.coralShade : .leafShade)
                        if status != .correct, let solution = solution(exercise) {
                            Text("\(L("Réponse", "Answer")) : \(solution)").font(.text(16, .bold)).foregroundStyle(status == .wrong ? Color.coralShade : .leafShade)
                        }
                    }
                    Spacer(minLength: 0)
                }
            }
            if status == .answering {
                if case .match = exercise.kind { EmptyView() } else {
                    Button(L("Vérifier", "Check")) { check() }.buttonStyle(.chunky(.green)).disabled(!ready)
                }
            } else {
                Button(L("Continuer", "Continue")) { advance() }.buttonStyle(.chunky(status == .wrong ? .danger : .green))
            }
        }
        .padding(.horizontal, 20).padding(.top, 14).padding(.bottom, 8)
        .frame(maxWidth: .infinity)
        .background((status == .answering ? Color.white : status == .wrong ? Color.coralTint : Color.leafTint).ignoresSafeArea(edges: .bottom))
        .overlay(alignment: .top) { Rectangle().fill(Color.line).frame(height: status == .answering ? 2 : 0) }
        .animation(.spring(duration: 0.3, bounce: 0.3), value: status)
    }

    private var cheer: String {
        let own = store.language?.cheer ?? ""
        return [own.isEmpty ? L("Bravo !", "Well done!") : own, L("Bien joué !", "Nicely done!"), L("Excellent !", "Excellent!")][session.solved % 3]
    }

    private var ready: Bool {
        switch answer {
        case .choice, .matched: true
        case .tiles(let t): !t.isEmpty
        case .text(let t): !t.trimmingCharacters(in: .whitespaces).isEmpty
        case nil: false
        }
    }

    private func solution(_ e: Exercise) -> String? {
        switch e.kind {
        case .meaning(let w, _, _): w.meaning(store.state.ui)
        case .term(let w, _), .listen(let w, _), .type(let w): w.term
        case .build(let s, _): s.answer
        case .match: nil
        }
    }

    private func begin() {
        let s = store.state, units = store.units, pool = Rules.unlockedWords(s, units)
        let seen = Set(s.words.keys.filter { $0.hasPrefix("\(s.lang):") }.map { String($0.dropFirst(s.lang.count + 1)) })
        switch mode {
        case .lesson(let unit, let index):
            session = LessonSession(LessonBuilder.lesson(unit, index, pool: pool, canListen: s.voice, seen: seen))
        case .practice(let kind):
            var words = Rules.reviewWords(s, s.lang, pool, Game.now())
            // Nothing due yet: practise the words met so far, weakest first.
            if words.count < 4 { words = pool.filter { seen.contains($0.term) }.shuffled() }
            session = LessonSession(LessonBuilder.practice(kind == .listen ? pool.shuffled() : words, pool: pool, mode: kind, canListen: s.voice))
        }
        if session.queue.isEmpty { dismiss() }
    }

    private func check() {
        guard session.status == .answering, let e = session.current, let answer else { return }
        let grade: Grade
        switch (e.kind, answer) {
        case (.meaning(let w, _, _), .choice(let c)), (.term(let w, _), .choice(let c)), (.listen(let w, _), .choice(let c)): grade = c == w ? .correct : .wrong
        case (.build(let s, _), .tiles(let t)): grade = Grading.tiles(t, s.answer)
        case (.type(let w), .text(let t)): grade = Grading.typed(t, w.term)
        case (.match, .matched): grade = .correct
        default: return
        }
        session.grade(grade)
        if let w = e.word { store.recordWord(w.term, correct: grade != .wrong) }
        if grade == .wrong {
            Sfx.play(.wrong); Haptic.error()
            withAnimation(.default.repeatCount(3, autoreverses: true).speed(6)) { shake = true }
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { shake = false }
            if mode.isLesson { store.loseHeart() }
        } else {
            Sfx.play(.correct, session.combo); Haptic.success()
            sparks += 1
            if case .listen = e.kind {} else if let w = e.word { Voice.say(w, automatic: true) }
        }
    }

    private func advance() {
        if mode.isLesson && store.state.hearts == 0 && session.status == .wrong { outOfHearts = true; return }
        answer = nil
        withAnimation(.spring(duration: 0.35)) { session.next() }
        guard session.finished else { Sfx.play(.whoosh); return }
        var result = Rules.SessionResult(kind: mode.isLesson ? .lesson : .practice, mistakes: session.mistakes, bestCombo: session.bestCombo)
        if case .lesson(let unit, let index) = mode { result.unitId = unit.id; result.lessonIndex = index }
        let earned = store.complete(result)
        Sfx.play(.complete)
        withAnimation(.spring(duration: 0.5)) { reward = earned }
    }
}

// MARK: - Exercises

private struct ExerciseView: View {
    let exercise: Exercise
    @Binding var answer: Answer?
    let locked: Bool
    let submit: () -> Void
    @Environment(AppStore.self) private var store

    var body: some View {
        let ui = store.state.ui
        VStack(alignment: .leading, spacing: 18) {
            if exercise.retry {
                Label(L("Erreur précédente", "Previous mistake"), systemImage: "arrow.counterclockwise").font(.text(13, .heavy)).textCase(.uppercase).foregroundStyle(Color.sunShade)
            }
            switch exercise.kind {
            case .meaning(let w, let options, let isNew):
                if isNew && !exercise.retry { Label(L("Nouveau mot", "New word"), systemImage: "sparkle").font(.text(13, .heavy)).textCase(.uppercase).tracking(1).foregroundStyle(Color.plum) }
                title(L("Que veut dire ce mot ?", "What does this mean?"))
                prompt(w.term, word: w)
                choices(options, answer: w) { $0.meaning(ui) }
            case .term(let w, let options):
                title(L("Comment dit-on…", "How do you say…"))
                prompt(w.meaning(ui), word: nil)
                choices(options, answer: w) { $0.term }
            case .listen(let w, let options):
                title(L("Qu’entends-tu ?", "What do you hear?"))
                Button { Voice.say(w) } label: {
                    Image(systemName: "speaker.wave.3.fill").font(.system(size: 44, weight: .bold)).foregroundStyle(.white).frame(width: 110, height: 110)
                        .background(RoundedRectangle(cornerRadius: 28).fill(Color.sky)).background(RoundedRectangle(cornerRadius: 28).fill(Color.skyShade).offset(y: 6))
                }
                .frame(maxWidth: .infinity).padding(.vertical, 8)
                .onAppear { Voice.say(w, automatic: true) }
                choices(options, answer: w) { $0.term }
            case .match(let pairs):
                title(L("Relie les paires", "Match the pairs"))
                MatchBoard(pairs: pairs) { answer = .matched; submit() }
            case .build(let s, let tiles):
                title(L("Traduis cette phrase", "Translate this sentence"))
                prompt(s.prompt(ui), word: Word(term: s.answer, fr: s.fr, en: s.en, audio: s.audio))
                TileBoard(tiles: tiles, locked: locked) { answer = .tiles($0) }
            case .type(let w):
                title(L("Écris en \(store.language?.name(ui) ?? "")", "Type it in \(store.language?.name(ui) ?? "")"))
                prompt(w.meaning(ui), word: nil)
                TypeField(locked: locked, submit: submit) { answer = .text($0) }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func title(_ text: String) -> some View { Text(text).font(.display(25)).foregroundStyle(Color.ink) }

    private func prompt(_ text: String, word: Word?) -> some View {
        HStack(alignment: .center, spacing: 2) {
            LobaView(mood: .idle, size: 84)
            Bubble {
                HStack(spacing: 10) {
                    if let word, word.audio != nil { Image(systemName: "speaker.wave.2.fill").foregroundStyle(Color.sky) }
                    Text(text).font(.text(20, .heavy)).foregroundStyle(Color.ink)
                }
            }
            .onTapGesture { if let word { Voice.say(word) } }
        }
        .onAppear { if let word { Voice.say(word, automatic: true) } }
    }

    private func choices(_ options: [Word], answer correct: Word, label: @escaping (Word) -> String) -> some View {
        VStack(spacing: 10) {
            ForEach(Array(options.enumerated()), id: \.element) { i, option in
                let picked = answer == .choice(option)
                let tint: Color = !locked ? .sky : option == correct ? .leaf : picked ? .coral : .sky
                Button {
                    Sfx.play(.tile, i); Haptic.tap()
                    answer = .choice(option)
                } label: {
                    HStack(spacing: 14) {
                        Text("\(i + 1)").font(.text(14, .heavy)).foregroundStyle(picked ? tint : Color.muted).frame(width: 30, height: 30)
                            .overlay(RoundedRectangle(cornerRadius: 9).strokeBorder(picked ? tint : Color.line, lineWidth: 2))
                        Text(label(option)).font(.text(18, .bold)).foregroundStyle(Color.ink).multilineTextAlignment(.leading)
                        Spacer(minLength: 0)
                    }
                    .padding(.horizontal, 16).padding(.vertical, 15)
                    .card(selected: picked || (locked && option == correct), tint: tint)
                }
                .buttonStyle(.plain).disabled(locked)
                .scaleEffect(picked ? 1.02 : 1).animation(.spring(duration: 0.2, bounce: 0.5), value: picked)
            }
        }
    }
}

private struct MatchBoard: View {
    let pairs: [Word]
    let done: () -> Void
    @Environment(AppStore.self) private var store
    @State private var meanings: [Word] = []
    @State private var left: Word?
    @State private var right: Word?
    @State private var matched: Set<Word> = []
    @State private var wrong = false

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            column(pairs, selected: left) { $0.term } tap: { left = $0; Voice.say($0); resolve() }
            column(meanings, selected: right) { $0.meaning(store.state.ui) } tap: { right = $0; resolve() }
        }
        .onAppear { meanings = pairs.shuffled() }
    }

    private func column(_ words: [Word], selected: Word?, label: @escaping (Word) -> String, tap: @escaping (Word) -> Void) -> some View {
        VStack(spacing: 10) {
            ForEach(words, id: \.self) { w in
                let isMatched = matched.contains(w), picked = selected == w
                Button { Sfx.play(.tile, words.firstIndex(of: w) ?? 0); Haptic.tap(); tap(w) } label: {
                    Text(label(w)).font(.text(16, .bold)).foregroundStyle(isMatched ? Color.muted : .ink).multilineTextAlignment(.center)
                        .frame(maxWidth: .infinity, minHeight: 34).padding(.horizontal, 8).padding(.vertical, 12)
                        .card(selected: picked || isMatched, tint: isMatched ? .leaf : wrong && picked ? .coral : .sky)
                }
                .buttonStyle(.plain).disabled(isMatched).opacity(isMatched ? 0.55 : 1)
            }
        }
        .frame(maxWidth: .infinity)
    }

    private func resolve() {
        guard let l = left, let r = right else { return }
        if l == r {
            matched.insert(l)
            left = nil; right = nil
            Sfx.play(.pop); Haptic.success()
            if matched.count == pairs.count { DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) { done() } }
        } else {
            wrong = true
            Haptic.error()
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.45) { wrong = false; left = nil; right = nil }
        }
    }
}

private struct TileBoard: View {
    let tiles: [String]
    let locked: Bool
    let changed: ([String]) -> Void
    /// Indexes into `tiles`, in the order the learner placed them.
    @State private var placed: [Int] = []
    @Namespace private var space

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            Flow(spacing: 8) {
                ForEach(placed, id: \.self) { i in tile(i, placed: true) }
            }
            .frame(maxWidth: .infinity, minHeight: 104, alignment: .topLeading)
            .padding(.bottom, 8)
            .overlay(alignment: .bottom) { Rectangle().fill(Color.line).frame(height: 2) }
            Flow(spacing: 8) {
                ForEach(tiles.indices, id: \.self) { i in
                    if placed.contains(i) {
                        Text(tiles[i]).font(.text(17, .bold)).padding(.horizontal, 14).padding(.vertical, 11).foregroundStyle(.clear)
                            .background(RoundedRectangle(cornerRadius: 13).fill(Color.line))
                    } else { tile(i, placed: false) }
                }
            }
        }
    }

    private func tile(_ i: Int, placed isPlaced: Bool) -> some View {
        Button {
            Haptic.tap()
            withAnimation(.spring(duration: 0.3, bounce: 0.35)) {
                if isPlaced { placed.removeAll { $0 == i }; Sfx.play(.tap) } else { placed.append(i); Sfx.play(.tile, placed.count - 1) }
            }
            changed(placed.map { tiles[$0] })
        } label: {
            Text(tiles[i]).font(.text(17, .bold)).foregroundStyle(Color.ink).padding(.horizontal, 14).padding(.vertical, 11)
                .background(RoundedRectangle(cornerRadius: 13).fill(.white))
                .overlay(RoundedRectangle(cornerRadius: 13).strokeBorder(Color.line, lineWidth: 2))
                .background(RoundedRectangle(cornerRadius: 13).fill(Color.line).offset(y: 3))
        }
        .buttonStyle(.plain).disabled(locked)
        .matchedGeometryEffect(id: i, in: space)
    }
}

private struct TypeField: View {
    let locked: Bool
    let submit: () -> Void
    let changed: (String) -> Void
    @State private var text = ""
    @FocusState private var focused: Bool

    var body: some View {
        TextField(L("Écris ici", "Type here"), text: $text)
            .textInputAutocapitalization(.never).autocorrectionDisabled().submitLabel(.done)
            .focused($focused).disabled(locked).fieldStyle()
            .onChange(of: text) { changed(text) }
            .onSubmit { submit() }
            .onAppear { focused = true }
    }
}

/// Lays children out left to right, wrapping to new lines.
struct Flow: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let rows = arrange(proposal.width ?? .infinity, subviews)
        return CGSize(width: proposal.width ?? rows.map(\.width).max() ?? 0, height: rows.last.map { $0.y + $0.height } ?? 0)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        for row in arrange(bounds.width, subviews) {
            for item in row.items { subviews[item.index].place(at: CGPoint(x: bounds.minX + item.x, y: bounds.minY + row.y), proposal: .unspecified) }
        }
    }

    private struct Row { var y: CGFloat = 0, height: CGFloat = 0, width: CGFloat = 0; var items: [(index: Int, x: CGFloat)] = [] }

    private func arrange(_ width: CGFloat, _ subviews: Subviews) -> [Row] {
        var rows = [Row()]
        for (i, view) in subviews.enumerated() {
            let size = view.sizeThatFits(.unspecified)
            if rows[rows.count - 1].width + size.width > width, !rows[rows.count - 1].items.isEmpty {
                let last = rows[rows.count - 1]
                rows.append(Row(y: last.y + last.height + spacing))
            }
            rows[rows.count - 1].items.append((i, rows[rows.count - 1].width))
            rows[rows.count - 1].width += size.width + spacing
            rows[rows.count - 1].height = max(rows[rows.count - 1].height, size.height)
        }
        return rows
    }
}

// MARK: - Lesson complete

private struct CompleteView: View {
    let reward: Rules.Reward
    let accuracy: Int
    let combo: Int
    let practice: Bool
    let done: () -> Void
    @Environment(AppStore.self) private var store
    @State private var shown = false
    @State private var xp = 0

    var body: some View {
        ZStack {
            VStack(spacing: 18) {
                Spacer()
                ZStack {
                    Circle().fill(RadialGradient(colors: [Color.sunTint, .white.opacity(0)], center: .center, startRadius: 20, endRadius: 170)).frame(width: 340, height: 340)
                    SunRays().frame(width: 330, height: 330).opacity(shown ? 0.55 : 0)
                    LobaView(mood: .cheer, size: 190).offset(y: shown ? 0 : 40)
                }
                .frame(height: 250)
                Text(reward.perfect ? L("Sans faute !", "Flawless!") : practice ? L("Pratique terminée !", "Practice done!") : L("Leçon terminée !", "Lesson complete!"))
                    .font(.display(34)).foregroundStyle(Color.ink)
                HStack(spacing: 10) {
                    stat("bolt", "+\(xp)", "XP", .sunShade)
                    stat("target", "\(accuracy)%", L("Précision", "Accuracy"), .leafShade)
                    if reward.gems > 0 { stat("gem", "+\(reward.gems)", L("Gemmes", "Gems"), .sky) } else { stat("flame", "\(combo)", L("Série max", "Best combo"), .coral) }
                }
                .padding(.horizontal, 20)
                if reward.streakExtended {
                    let streak = Rules.visibleStreak(store.state, store.today)
                    HStack(spacing: 10) {
                        Art(name: "flame", size: 34)
                        Text(streak == 1 ? L("Ta série commence : 1 jour !", "Your streak starts: 1 day!") : L("Série de \(streak) jours !", "\(streak)-day streak!")).font(.text(18, .heavy)).foregroundStyle(Color.sunShade)
                    }
                    .padding(.horizontal, 18).padding(.vertical, 12).background(Capsule().fill(Color.sunTint))
                    .scaleEffect(shown ? 1 : 0.5).opacity(shown ? 1 : 0)
                }
                Spacer()
                Button(L("Continuer", "Continue"), action: done).buttonStyle(.chunky(.green)).padding(20)
            }
            Confetti()
        }
        .background(Color.white)
        .onAppear {
            Haptic.success()
            withAnimation(.spring(duration: 0.7, bounce: 0.5).delay(0.15)) { shown = true }
            withAnimation(.easeOut(duration: 0.9).delay(0.3)) { xp = reward.xp }
            if reward.streakExtended { DispatchQueue.main.asyncAfter(deadline: .now() + 1.6) { Sfx.play(.streak) } }
        }
    }

    private func stat(_ art: String, _ value: String, _ label: String, _ color: Color) -> some View {
        VStack(spacing: 6) {
            Art(name: art, size: 34)
            Text(value).font(.display(24)).foregroundStyle(color).contentTransition(.numericText())
            Text(label).font(.text(12, .heavy)).textCase(.uppercase).foregroundStyle(Color.muted)
        }
        .frame(maxWidth: .infinity).padding(.vertical, 16).card()
        .scaleEffect(shown ? 1 : 0.6).opacity(shown ? 1 : 0)
    }
}
