import SwiftUI

struct TabsView: View {
    @Environment(AppStore.self) private var store
    @State private var tab: Int = {
        #if DEBUG
        ["practice": 1, "quests": 2, "shop": 3, "profile": 4][DebugLaunch.screen ?? ""] ?? 0
        #else
        0
        #endif
    }()

    var body: some View {
        let claimable = Rules.dailyQuests(store.state, store.today).filter { $0.done && !store.state.daily.claimed.contains($0.id.rawValue) }.count
        TabView(selection: $tab) {
            tabPage(PathView(), "navLearn", L("Apprendre", "Learn"), 0)
            tabPage(PracticeView(), "dumbbell", L("Pratique", "Practice"), 1)
            tabPage(QuestsView(), "target", L("Quêtes", "Quests"), 2).badge(claimable)
            tabPage(ShopView(), "navShop", L("Boutique", "Shop"), 3)
            tabPage(ProfileView(), "navProfile", L("Profil", "Profile"), 4)
        }
        .onChange(of: tab) { Sfx.play(.tap); Haptic.tap() }
    }

    private func tabPage(_ view: some View, _ art: String, _ label: String, _ tag: Int) -> some View {
        VStack(spacing: 0) {
            StatsBar()
            view
        }
        .background(Color.white)
        .tabItem {
            Image(uiImage: Self.icon(art)).renderingMode(.original)
            Text(label)
        }
        .tag(tag)
    }

    /// Tab bars want small fixed-size images; the artwork is drawn at 30 points.
    private static func icon(_ name: String) -> UIImage {
        let size = CGSize(width: 30, height: 30)
        guard let art = UIImage(named: "art-\(name)") else { return UIImage() }
        return UIGraphicsImageRenderer(size: size).image { _ in art.draw(in: CGRect(origin: .zero, size: size)) }.withRenderingMode(.alwaysOriginal)
    }
}

// MARK: - The river path

private let unitColors: [(Color, Color)] = [(.sky, .skyShade), (.leaf, .leafShade), (.plum, .plumShade), (.coral, .coralShade), (Color(hex: "ff8a1f"), Color(hex: "d96c05"))]

struct LessonTarget: Identifiable {
    let unit: Unit
    let index: Int
    var id: String { "\(unit.id)-\(index)" }
}

struct PathView: View {
    @Environment(AppStore.self) private var store
    @State private var picked: LessonTarget?
    @State private var playing: LessonTarget?
    @State private var guide: Unit?
    @State private var noHearts = false

    var body: some View {
        let units = store.units, s = store.state, current = Rules.currentUnitIndex(s, units)
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(spacing: 0) {
                    TodayCard()
                    ForEach(Array(units.enumerated()), id: \.element.id) { i, unit in
                        UnitSection(unit: unit, number: i + 1, colors: unitColors[i % unitColors.count], unlocked: Rules.unitUnlocked(s, units, i),
                                    isCurrent: i == current, flip: !i.isMultiple(of: 2), guide: $guide) { index in
                            picked = LessonTarget(unit: unit, index: index)
                        }
                        .id(unit.id)
                    }
                    KubaBand().padding(.top, 8)
                    Text(L("D’autres unités arrivent bientôt.", "More units are on their way.")).font(.text(15)).foregroundStyle(Color.muted).padding(24)
                }
            }
            .onAppear { if current > 0, current < units.count { proxy.scrollTo(units[current].id, anchor: .top) } }
        }
        .sheet(item: $picked) { target in
            StartSheet(target: target) {
                picked = nil
                if store.state.hearts > 0 { playing = target } else { noHearts = true }
            }
            .presentationDetents([.height(250)])
        }
        .sheet(item: $guide) { unit in GuideSheet(unit: unit).presentationDetents([.medium, .large]) }
        .fullScreenCover(item: $playing) { LessonView(mode: .lesson($0.unit, $0.index)) }
        .alert(L("Plus de cœurs", "Out of hearts"), isPresented: $noHearts) {
            if Rules.canBuy(store.state, .refill, Game.now()) {
                Button(L("Recharger (50 gemmes)", "Refill (50 gems)")) { store.buy(.refill); Sfx.play(.heart) }
            }
            Button("OK", role: .cancel) {}
        } message: {
            Text(L("Un cœur revient toutes les 30 minutes. La pratique, elle, n’en coûte jamais.", "A heart comes back every 30 minutes. Practice never costs any."))
        }
    }
}

private struct UnitSection: View {
    @Environment(AppStore.self) private var store
    let unit: Unit
    let number: Int
    let colors: (Color, Color)
    let unlocked: Bool
    let isCurrent: Bool
    let flip: Bool
    @Binding var guide: Unit?
    let pick: (Int) -> Void

    private let spacing: CGFloat = 104
    private let top: CGFloat = 104
    private func x(_ i: Int) -> CGFloat { CGFloat(sin(Double(i) * 1.05 + 0.4)) * 62 * (flip ? -1 : 1) }
    private func point(_ i: Int, _ width: CGFloat) -> CGPoint { CGPoint(x: width / 2 + x(i), y: top + CGFloat(i) * spacing) }

    var body: some View {
        let done = Rules.unitProgress(store.state, unit), ui = store.state.ui
        VStack(spacing: 0) {
            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 4) {
                    Text("\(L("Unité", "Unit")) \(number) · \(unit.words.count) \(L("mots", "words"))".uppercased()).font(.text(13, .heavy)).tracking(1.2).foregroundStyle(.white.opacity(0.85))
                    Text(unit.name(ui)).font(.display(22)).foregroundStyle(.white).lineLimit(2).minimumScaleFactor(0.8)
                }
                Spacer()
                if !unit.tip.isEmpty {
                    Button { Sfx.play(.tap); guide = unit } label: {
                        Label(L("Guide", "Guide"), systemImage: "book.closed.fill").font(.text(14, .heavy)).foregroundStyle(.white)
                            .padding(.horizontal, 12).padding(.vertical, 9).background(RoundedRectangle(cornerRadius: 12).fill(.black.opacity(0.15)))
                    }
                }
            }
            .padding(18)
            .background(RoundedRectangle(cornerRadius: 22).fill(unlocked ? colors.0 : Color(hex: "b9b3c6")).overlay(KubaWeave()).clipShape(RoundedRectangle(cornerRadius: 22)))
            .background(RoundedRectangle(cornerRadius: 22).fill(unlocked ? colors.1 : Color(hex: "9b95ab")).offset(y: 5))
            .padding(.horizontal, 16).padding(.top, 16).zIndex(1)

            GeometryReader { geo in
                let width = geo.size.width
                ZStack {
                    riverbed(width)
                    ForEach(0..<lessonsPerUnit, id: \.self) { i in
                        let state: NodeView.State = !unlocked ? .locked : i < done ? .done : i == done ? .current : .locked
                        NodeView(state: state, index: i, colors: colors) { pick(i) }.position(point(i, width))
                    }
                    ChestView(unit: unit).position(point(lessonsPerUnit, width))
                    if isCurrent {
                        LobaView(mood: done == 0 ? .wave : .happy, size: 104)
                            .position(x: x(min(done, lessonsPerUnit - 1)) > 0 ? 70 : width - 70, y: point(min(done, lessonsPerUnit - 1), width).y + 8)
                    } else if unlocked {
                        Text(unit.icon).font(.system(size: 54)).opacity(0.9).position(x: x(1) > 0 ? 64 : width - 64, y: point(1, width).y + 20)
                    }
                }
            }
            .frame(height: top + CGFloat(lessonsPerUnit) * spacing + 70)
        }
    }

    /// The Congo river, running under the lesson stones.
    private func riverbed(_ width: CGFloat) -> some View {
        var path = Path()
        let pts = [CGPoint(x: width / 2 + x(0) * 0.4, y: -30)] + (0...lessonsPerUnit).map { point($0, width) } + [CGPoint(x: width / 2, y: top + CGFloat(lessonsPerUnit) * spacing + 110)]
        path.move(to: pts[0])
        for i in 1..<pts.count {
            let a = pts[i - 1], b = pts[i], midY = (a.y + b.y) / 2
            path.addCurve(to: b, control1: CGPoint(x: a.x, y: midY), control2: CGPoint(x: b.x, y: midY))
        }
        return ZStack {
            path.stroke(Color.sand, style: StrokeStyle(lineWidth: 84, lineCap: .butt, lineJoin: .round))
            path.stroke(unlocked ? Color(hex: "d5ebfb") : Color(hex: "ecebf0"), style: StrokeStyle(lineWidth: 68, lineCap: .butt, lineJoin: .round))
            // The current: dashes that drift downstream, and a second, fainter set that moves slower.
            // Drawn in one canvas: as three animated shapes this was the most expensive thing in the app.
            TimelineView(ambient(paused: !unlocked)) { timeline in
                Canvas { ctx, _ in
                    let t = timeline.date.timeIntervalSinceReferenceDate
                    ctx.stroke(path, with: .color(.white.opacity(0.9)), style: StrokeStyle(lineWidth: 3, lineCap: .round, dash: [12, 22], dashPhase: -t * 26))
                    ctx.stroke(path.offsetBy(dx: 15, dy: 0), with: .color(.white.opacity(0.45)), style: StrokeStyle(lineWidth: 2, lineCap: .round, dash: [5, 44], dashPhase: -t * 15))
                    ctx.stroke(path.offsetBy(dx: -17, dy: 0), with: .color(.white.opacity(0.45)), style: StrokeStyle(lineWidth: 2, lineCap: .round, dash: [7, 58], dashPhase: -t * 19))
                }
            }
            .allowsHitTesting(false)
        }
    }
}

private struct NodeView: View {
    enum State { case done, current, locked }
    let state: State
    let index: Int
    let colors: (Color, Color)
    let action: () -> Void
    @SwiftUI.State private var pulse = false

    var body: some View {
        let face: Color = state == .locked ? .line : state == .done ? .sun : colors.0
        let edge: Color = state == .locked ? .lineShade : state == .done ? .sunShade : colors.1
        Button(action: { Sfx.play(.tap); Haptic.tap(); action() }) {
            ZStack {
                if state == .current {
                    // Ripples spreading from the stone the learner is on.
                    ForEach(0..<2, id: \.self) { i in
                        Ellipse().stroke(colors.0.opacity(pulse ? 0 : 0.55), lineWidth: 4)
                            .frame(width: 78, height: 70).scaleEffect(pulse ? 1.75 : 1).offset(y: 4)
                            .animation(.easeOut(duration: 1.8).repeatForever(autoreverses: false).delay(Double(i) * 0.9), value: pulse)
                    }
                }
                Ellipse().fill(edge).frame(width: 78, height: 70).offset(y: 8)
                Ellipse().fill(face).frame(width: 78, height: 70)
                Ellipse().fill(.white.opacity(0.28)).frame(width: 30, height: 16).offset(x: -12, y: -18)
                Image(systemName: state == .locked ? "lock.fill" : state == .done ? "checkmark" : ["star.fill", "book.fill", "speaker.wave.2.fill", "dumbbell.fill"][index % 4])
                    .font(.system(size: 26, weight: .heavy)).foregroundStyle(state == .locked ? Color.muted : .white)
            }
        }
        .buttonStyle(NodePress())
        .disabled(state == .locked)
        .overlay(alignment: .top) {
            if state == .current {
                Text(L("Commencer", "Start")).font(.text(13, .heavy)).textCase(.uppercase).foregroundStyle(colors.0)
                    .padding(.horizontal, 12).padding(.vertical, 7)
                    .background(RoundedRectangle(cornerRadius: 11).fill(.white).shadow(color: .black.opacity(0.12), radius: 0, y: 3))
                    .overlay(RoundedRectangle(cornerRadius: 11).strokeBorder(Color.line, lineWidth: 2))
                    .fixedSize().offset(y: pulse ? -44 : -38).allowsHitTesting(false)
            }
        }
        .onAppear { if state == .current { withAnimation(.easeInOut(duration: 0.8).repeatForever(autoreverses: true)) { pulse = true } } }
    }
}

private struct NodePress: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.offset(y: configuration.isPressed ? 5 : 0).animation(.spring(duration: 0.12), value: configuration.isPressed)
    }
}

private struct ChestView: View {
    @Environment(AppStore.self) private var store
    let unit: Unit
    @State private var burst = false

    var body: some View {
        let ready = Rules.unitDone(store.state, unit), opened = store.state.chests.contains(unit.id)
        Button {
            store.openChest(unit)
            Sfx.play(.chest); Haptic.success()
            burst = true
            DispatchQueue.main.asyncAfter(deadline: .now() + 2.4) { burst = false }
        } label: {
            Art(name: opened ? "chestOpen" : ready ? "chest" : "chestLocked", size: 78)
                .rotationEffect(.degrees(ready && !opened ? 4 : 0))
                .animation(ready && !opened ? .easeInOut(duration: 0.5).repeatForever(autoreverses: true) : .default, value: ready)
        }
        .buttonStyle(NodePress())
        .disabled(!ready || opened)
        .overlay(alignment: .top) {
            if burst {
                Label("+\(Game.chestReward)", systemImage: "diamond.fill").font(.text(18, .heavy)).foregroundStyle(Color.sky)
                    .padding(8).background(Capsule().fill(.white).shadow(radius: 4, y: 2)).fixedSize().offset(y: -44)
                    .transition(.scale.combined(with: .move(edge: .bottom)).combined(with: .opacity))
            }
        }
        .animation(.spring(duration: 0.4, bounce: 0.5), value: burst)
    }
}

private struct StartSheet: View {
    @Environment(AppStore.self) private var store
    let target: LessonTarget
    let start: () -> Void

    var body: some View {
        let replay = target.index < Rules.unitProgress(store.state, target.unit)
        VStack(alignment: .leading, spacing: 10) {
            Text(target.unit.name(store.state.ui)).font(.display(24)).foregroundStyle(Color.ink)
            Text("\(L("Leçon", "Lesson")) \(target.index + 1) \(L("sur", "of")) \(lessonsPerUnit) · " + [L("Découvre les premiers mots", "Meet the first words"), L("Découvre la suite", "Meet the rest"), L("Mélange et premières phrases", "Mix and first sentences"), L("Écris et construis", "Type and build")][target.index % 4])
                .font(.text(16)).foregroundStyle(Color.ink2)
            Spacer(minLength: 4)
            Button(replay ? L("Refaire · +10 XP", "Replay · +10 XP") : L("Commencer · +10 XP", "Start · +10 XP"), action: start).buttonStyle(.chunky(.green))
        }
        .padding(24)
    }
}

private struct GuideSheet: View {
    @Environment(AppStore.self) private var store
    let unit: Unit

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                HStack(alignment: .bottom, spacing: 4) {
                    LobaView(mood: .think, size: 84)
                    Bubble { Text(unit.tip).font(.text(16)).foregroundStyle(Color.ink) }.padding(.bottom, 16)
                }
                Text(L("Les mots de l’unité", "Words in this unit")).font(.text(14, .heavy)).textCase(.uppercase).tracking(1).foregroundStyle(Color.muted)
                ForEach(unit.words, id: \.self) { w in
                    Button { Sfx.play(.tile, unit.words.firstIndex(of: w) ?? 0); Voice.say(w) } label: {
                        HStack {
                            Text(w.term).font(.text(17, .heavy)).foregroundStyle(Color.skyShade)
                            Spacer()
                            Text(w.meaning(store.state.ui)).font(.text(16)).foregroundStyle(Color.ink2)
                            if w.audio != nil { Image(systemName: "speaker.wave.2.fill").foregroundStyle(Color.sky) }
                        }
                        .padding(14).card()
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(22)
        }
        .presentationDragIndicator(.visible)
    }
}

/// Today at a glance: the daily goal filling up, and the week's streak.
private struct TodayCard: View {
    @Environment(AppStore.self) private var store

    var body: some View {
        let s = store.state, xp = s.daily.day == store.today ? s.daily.xp : 0, done = xp >= s.goal
        VStack(spacing: 14) {
            HStack(spacing: 14) {
                GoalRing(value: Double(xp) / Double(max(1, s.goal)))
                VStack(alignment: .leading, spacing: 2) {
                    Text(done ? L("Objectif atteint !", "Goal reached!") : L("Objectif du jour", "Today’s goal")).font(.display(20)).foregroundStyle(.white)
                    Text("\(min(xp, s.goal)) / \(s.goal) XP").font(.text(15, .bold)).foregroundStyle(.white.opacity(0.85)).contentTransition(.numericText())
                }
                Spacer()
                LobaView(mood: done ? .cheer : .happy, size: 62)
            }
            WeekStrip().padding(.vertical, 10).padding(.horizontal, 6).background(RoundedRectangle(cornerRadius: 16).fill(.white))
        }
        .padding(16)
        .background(RoundedRectangle(cornerRadius: 24).fill(LinearGradient(colors: [Color(hex: "2aa9f5"), .skyShade], startPoint: .topLeading, endPoint: .bottomTrailing)).overlay(KubaWeave()).clipShape(RoundedRectangle(cornerRadius: 24)))
        .background(RoundedRectangle(cornerRadius: 24).fill(Color(hex: "0f5f99")).offset(y: 5))
        .padding(.horizontal, 16).padding(.top, 14).padding(.bottom, 6)
    }
}
