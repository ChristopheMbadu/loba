import SwiftUI

/// Mboka na biso ("our country"): the map of the DRC as a puzzle, a quiz, an atlas and a little book.
struct MapGameView: View {
    enum Screen: Equatable { case hub, puzzle(String), quiz([String]), atlas, book }
    @Environment(\.dismiss) private var dismiss
    @State private var screen: Screen = .hub

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 12) {
                Button { if screen == .hub { dismiss() } else { go(.hub) } } label: {
                    Image(systemName: screen == .hub ? "xmark" : "chevron.left").font(.system(size: 17, weight: .heavy)).foregroundStyle(Color.ink)
                        .frame(width: 42, height: 42).background(RoundedRectangle(cornerRadius: 13).fill(Color.surface))
                }
                VStack(alignment: .leading, spacing: 0) {
                    Text("Mboka na biso").font(.display(20)).foregroundStyle(Color.ink)
                    Text(L("La carte du Congo", "The map of Congo")).font(.text(13, .bold)).foregroundStyle(Color.muted)
                }
                Spacer()
                UiSwitch()
            }
            .padding(.horizontal, 16).padding(.vertical, 8)

            switch screen {
            case .hub: MapHub(go: go)
            case .puzzle(let level): MapPuzzle(level: level, go: go).id(level)
            case .quiz(let ids): MapQuiz(ids: ids, go: go).id(ids)
            case .atlas: MapAtlas()
            case .book: MapBook()
            }
        }
        .background(Color.white.ignoresSafeArea())
        .onAppear {
            Satellite.shared.load()
            #if DEBUG
            if let level = UserDefaults.standard.string(forKey: "maplevel") { screen = ["atlas": .atlas, "book": .book, "quiz": .quiz(Array(DRC.shared.ids.shuffled().prefix(8)))][level] ?? .puzzle(level) }
            #endif
        }
    }

    private func go(_ next: Screen) {
        Sfx.play(.whoosh)
        withAnimation(.easeInOut(duration: 0.25)) { screen = next }
    }
}

// MARK: - The map

enum MapTone { case placed, slot, quiet, hint, right, wrong, open }

/// The country drawn in one canvas: satellite image, a veil over the neighbours, then each province in its tone.
struct MapCanvas: View {
    var window: CGRect = DRC.shared.whole
    var outlines = true
    var labels: Set<String> = []
    let tone: (String) -> MapTone
    var onTap: ((String?) -> Void)?
    private let drc = DRC.shared

    var body: some View {
        let pulsing = drc.ids.contains { tone($0) == .hint }
        GeometryReader { geo in
            let scale = geo.size.width / window.width
            TimelineView(ambient(paused: !pulsing)) { timeline in
                let beat = pulsing ? (sin(timeline.date.timeIntervalSinceReferenceDate * 6) + 1) / 2 : 0
                Canvas { ctx, size in
                    ctx.fill(Path(CGRect(origin: .zero, size: size)), with: .color(Color(hex: "0d2a45")))
                    var map = ctx
                    map.scaleBy(x: scale, y: scale)
                    map.translateBy(x: -window.minX, y: -window.minY)
                    var country = Path()
                    for id in drc.ids { country.addPath(drc.paths[id]!) }
                    if let picture = Satellite.shared.image {
                        map.draw(Image(uiImage: picture), in: drc.whole)
                    } else {
                        map.fill(Path(drc.whole), with: .color(Color(hex: "a9c9e2")))
                        map.fill(country, with: .color(Color(hex: "3f7d4e")))
                    }
                    // Everything outside the country fades, and the border is drawn only on its outer side.
                    var outside = Path(drc.whole)
                    outside.addPath(country)
                    var veil = map
                    veil.clip(to: outside, style: FillStyle(eoFill: true))
                    veil.fill(Path(drc.whole), with: .color(.white.opacity(0.62)))
                    veil.stroke(country, with: .color(.white), style: StrokeStyle(lineWidth: 7 * window.width / 640, lineJoin: .round))

                    let line = 1.3 * window.width / 640
                    for id in drc.ids {
                        let path = drc.paths[id]!, color = drc.color(id)
                        switch tone(id) {
                        case .placed: map.fill(path, with: .color(color.opacity(0.9))); map.stroke(path, with: .color(.white), lineWidth: line)
                        case .right: map.fill(path, with: .color(.leaf)); map.stroke(path, with: .color(.white), lineWidth: line)
                        case .wrong: map.fill(path, with: .color(.coral)); map.stroke(path, with: .color(.white), lineWidth: line)
                        case .hint: map.fill(path, with: .color(Color.sun.opacity(0.5 + beat * 0.4))); map.stroke(path, with: .color(.sun), lineWidth: line * 2.4)
                        case .quiet: map.fill(path, with: .color(.white.opacity(0.55))); map.stroke(path, with: .color(.white.opacity(0.7)), lineWidth: line)
                        case .open: map.fill(path, with: .color(.white.opacity(0.2))); map.stroke(path, with: .color(.white), lineWidth: line)
                        case .slot:
                            if outlines { map.fill(path, with: .color(.white.opacity(0.14))); map.stroke(path, with: .color(.white.opacity(0.85)), lineWidth: line) }
                        }
                    }
                    for id in labels {
                        let p = drc.province(id)
                        let at = CGPoint(x: (p.centre.x - window.minX) * scale, y: (p.centre.y - window.minY) * scale)
                        let text = Text(p.name).font(.display(max(11, 15 * scale * window.width / 640)))
                        for (dx, dy) in [(-1.0, 0.0), (1, 0), (0, -1), (0, 1), (-0.7, -0.7), (0.7, 0.7), (-0.7, 0.7), (0.7, -0.7)] {
                            ctx.draw(text.foregroundStyle(Color(hex: "14203a").opacity(0.8)), at: CGPoint(x: at.x + dx * 1.4, y: at.y + dy * 1.4))
                        }
                        ctx.draw(text.foregroundStyle(.white), at: at)
                    }
                }
            }
            .contentShape(Rectangle())
            .onTapGesture { location in
                onTap?(drc.province(at: CGPoint(x: window.minX + location.x / scale, y: window.minY + location.y / scale)))
            }
        }
        .aspectRatio(window.width / window.height, contentMode: .fit)
        .clipShape(RoundedRectangle(cornerRadius: 22))
        .allowsHitTesting(onTap != nil)
    }
}

/// One province as a puzzle piece.
struct PieceShape: View {
    let id: String
    var body: some View {
        let p = DRC.shared.province(id), box = p.bounds
        GeometryReader { geo in
            let scale = min(geo.size.width / box.width, geo.size.height / box.height)
            let path = DRC.shared.paths[id]!
                .applying(CGAffineTransform(translationX: -box.minX, y: -box.minY))
                .applying(CGAffineTransform(scaleX: scale, y: scale))
                .applying(CGAffineTransform(translationX: (geo.size.width - box.width * scale) / 2, y: (geo.size.height - box.height * scale) / 2))
            path.fill(DRC.shared.color(id))
            path.stroke(.white, style: StrokeStyle(lineWidth: 2.5, lineJoin: .round))
        }
    }
}

private struct MapStars: View {
    let n: Int
    var size: CGFloat = 20
    var body: some View {
        HStack(spacing: 2) { ForEach(0..<3, id: \.self) { i in Art(name: "star", size: size).saturation(i < n ? 1 : 0).opacity(i < n ? 1 : 0.28) } }
    }
}

private struct FactCard: View {
    let p: Province
    var full = false
    @Environment(AppStore.self) private var store
    var body: some View {
        let ui = store.state.ui, tint = DRC.shared.color(p.id)
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 12) {
                Text(p.emoji).font(.system(size: 32)).frame(width: 56, height: 56).background(RoundedRectangle(cornerRadius: 16).fill(.white))
                VStack(alignment: .leading, spacing: 1) {
                    Text(p.name).font(.display(24)).foregroundStyle(tint)
                    HStack(spacing: 6) {
                        Text(L("Chef-lieu", "Capital")).font(.text(12, .heavy)).textCase(.uppercase).foregroundStyle(Color.muted)
                        Text(p.capital).font(.text(16, .heavy)).foregroundStyle(Color.ink)
                    }
                }
            }
            Text(p.fact(ui)).font(.text(16, .bold)).foregroundStyle(Color.ink).fixedSize(horizontal: false, vertical: true)
            row(L("On y parle", "People speak"), p.language.capitalized)
            if full {
                row(L("Peuples", "Peoples"), p.peoples.joined(separator: ", "))
                row(L("Superficie", "Area"), "\(p.area.formatted(.number.locale(Locale(identifier: ui.rawValue)))) km²")
                row(L("Ancienne province", "Former province"), p.former)
                row(L("Voisins", "Neighbours"), p.borders.isEmpty ? L("aucun", "none") : p.borders.joined(separator: ", "))
            }
        }
        .padding(14).frame(maxWidth: .infinity, alignment: .leading).card(selected: true, tint: tint)
    }

    private func row(_ label: String, _ value: String) -> some View {
        HStack(alignment: .top, spacing: 8) {
            Text(label).font(.text(13, .heavy)).foregroundStyle(Color.muted).frame(width: 124, alignment: .leading)
            Text(value).font(.text(14, .bold)).foregroundStyle(Color.ink).fixedSize(horizontal: false, vertical: true)
        }
    }
}

// MARK: - Hub

private struct MapHub: View {
    let go: (MapGameView.Screen) -> Void
    @Environment(AppStore.self) private var store
    private let drc = DRC.shared

    var body: some View {
        let ui = store.state.ui, stars = MapProgress.stars, stamps = MapProgress.stamps
        let next = drc.regions.first { stars[$0.id] == nil }
        let expertOpen = stars["hard"] != nil || drc.regions.allSatisfy { stars[$0.id] != nil }
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                MapCanvas(tone: { stamps.contains($0) ? .placed : .slot })
                HStack(spacing: 4) {
                    LobaView(mood: .wave, size: 92)
                    Bubble { Text(L("Remets chaque province à sa place et découvre notre pays.", "Put every province back in its place and discover our country.")).font(.text(15, .bold)).foregroundStyle(Color.ink2).fixedSize(horizontal: false, vertical: true) }
                }
                VStack(alignment: .leading, spacing: 8) {
                    HStack(alignment: .firstTextBaseline, spacing: 8) {
                        Text("\(stamps.count)").font(.display(34)).foregroundStyle(Color.sunShade)
                        Text("/ \(drc.ids.count)").font(.display(18)).foregroundStyle(Color.sunShade)
                        Text(L("provinces découvertes", "provinces discovered")).font(.text(15, .heavy)).foregroundStyle(Color(hex: "6b4d00"))
                    }
                    Meter(value: Double(stamps.count) / Double(drc.ids.count), color: .sun, height: 12)
                }
                .padding(16).background(RoundedRectangle(cornerRadius: 18).fill(Color.sunTint))
                if let next {
                    Button("\(L("Jouer", "Play")) · \(next.name(ui))") { go(.puzzle(next.id)) }.buttonStyle(.chunky(.green))
                }

                Text(L("Les régions", "The regions")).font(.display(22)).foregroundStyle(Color.ink).padding(.top, 8)
                ForEach(Array(drc.regions.enumerated()), id: \.element.id) { i, region in
                    card(tint: region.tint, selected: region.id == next?.id, eyebrow: "\(i + 1) · \(region.provinces.count) provinces", title: region.name(ui), stars: stars[region.id] ?? 0, action: { go(.puzzle(region.id)) }) {
                        ZStack {
                            ForEach(Array(region.provinces.prefix(3).enumerated()), id: \.element) { n, id in
                                PieceShape(id: id).frame(width: 30, height: 30).offset(x: [-12, 13, -2][n], y: [-12, -2, 15][n])
                            }
                        }
                    }
                }

                Text(L("Pour les experts", "For experts")).font(.display(22)).foregroundStyle(Color.ink).padding(.top, 8)
                card(tint: .coral, eyebrow: "26 provinces", title: L("Mode difficile", "Hard mode"), text: stars["hard"] == nil ? L("Tout le pays d’un coup : pas de régions, pas d’indices.", "The whole country at once: no regions, no hints.") : nil, stars: stars["hard"], action: { go(.puzzle("hard")) }) { Text("🔥").font(.system(size: 32)) }
                card(tint: .sun, eyebrow: "26 provinces", title: L("Le grand défi", "The big challenge"), text: expertOpen ? (stars["all"] == nil ? L("Les 26 provinces, sans les contours.", "All 26 provinces, without the outlines.") : nil) : L("Termine le mode difficile ou les 6 régions pour l’ouvrir.", "Finish hard mode or the 6 regions to open it."), stars: expertOpen ? stars["all"] : nil, action: { go(.puzzle("all")) }) { Text(expertOpen ? "🏆" : "🔒").font(.system(size: 32)) }
                    .disabled(!expertOpen).opacity(expertOpen ? 1 : 0.6)

                Text(L("Encore plus", "Even more")).font(.display(22)).foregroundStyle(Color.ink).padding(.top, 8)
                card(tint: .plum, title: L("Quiz éclair", "Quick quiz"), text: L("Où est-ce ? Quel est le chef-lieu ?", "Where is it? What is its capital?"), action: { go(.quiz(Array((stamps.count >= 4 ? stamps : drc.ids).shuffled().prefix(8)))) }) { Text("⚡").font(.system(size: 32)) }
                card(tint: .leaf, title: "Atlas", text: L("Touche une province pour tout savoir.", "Tap a province to learn all about it."), action: { go(.atlas) }) { Text("🧭").font(.system(size: 32)) }
                card(tint: .sunShade, title: L("Histoire et cultures", "History and cultures"), text: L("Des royaumes à la rumba.", "From kingdoms to rumba."), action: { go(.book) }) { Text("📜").font(.system(size: 32)) }
                MapCredits()
            }
            .padding(.horizontal, 16).padding(.bottom, 20)
        }
    }

    private func card(tint: Color, selected: Bool = false, eyebrow: String? = nil, title: String, text: String? = nil, stars: Int? = nil, action: @escaping () -> Void, @ViewBuilder art: () -> some View) -> some View {
        Button(action: action) {
            HStack(spacing: 14) {
                art().frame(width: 66, height: 66).background(RoundedRectangle(cornerRadius: 18).fill(tint.opacity(0.16)))
                VStack(alignment: .leading, spacing: 3) {
                    if let eyebrow { Text(eyebrow).font(.text(12, .heavy)).textCase(.uppercase).tracking(1).foregroundStyle(Color.muted) }
                    Text(title).font(.display(19)).foregroundStyle(Color.ink).multilineTextAlignment(.leading)
                    if let text { Text(text).font(.text(14)).foregroundStyle(Color.ink2).multilineTextAlignment(.leading) }
                    if let stars { MapStars(n: stars) }
                }
                Spacer(minLength: 0)
            }
            .padding(12).card(selected: selected, tint: tint)
        }
        .buttonStyle(.plain)
    }
}

private struct MapCredits: View {
    var body: some View {
        Text((Satellite.shared.image == nil ? "" : "\(L("Image satellite", "Satellite image")) © Mapbox © Maxar · ") + "© OpenStreetMap")
            .font(.text(11)).foregroundStyle(Color.muted).frame(maxWidth: .infinity)
    }
}

// MARK: - Puzzle: drag each province to its place

private struct MapFrameKey: PreferenceKey {
    static var defaultValue: CGRect = .zero
    static func reduce(value: inout CGRect, nextValue: () -> CGRect) { value = nextValue() }
}

private struct MapPuzzle: View {
    let level: String
    let go: (MapGameView.Screen) -> Void
    @Environment(AppStore.self) private var store
    @State private var queue: [String] = []
    @State private var placed: [String] = []
    @State private var misses: [String: Int] = [:]
    @State private var note: String?
    @State private var reveal: String?
    @State private var wrong: String?
    @State private var finger: CGPoint?
    @State private var mapFrame = CGRect.zero
    @State private var xp: Int?
    private let drc = DRC.shared

    private var region: MapRegion? { drc.regions.first { $0.id == level } }
    private var ids: [String] { region?.provinces ?? drc.ids }
    private var window: CGRect { region == nil ? drc.whole : drc.frame(ids) }
    private var hard: Bool { level == "hard" }
    private var totalMisses: Int { misses.values.reduce(0, +) }
    private var stars: Int { totalMisses <= 1 ? 3 : totalMisses <= ids.count ? 2 : 1 }
    /// Above the finger, so it doesn't hide the piece.
    private let lift: CGFloat = 54

    var body: some View {
        let ui = store.state.ui, current = queue.first
        // Hard mode gives no written hint, and the place only glows after three misses.
        let hinted = current.map { (misses[$0] ?? 0) >= (region == nil ? 3 : 2) } ?? false
        let finished = xp != nil && reveal == nil
        VStack(spacing: 12) {
            if finished { done(ui) } else {
                HStack(spacing: 10) {
                    Text(region?.name(ui) ?? (hard ? L("Mode difficile", "Hard mode") : L("Le grand défi", "The big challenge"))).font(.text(14, .heavy)).foregroundStyle(.white)
                        .padding(.horizontal, 12).padding(.vertical, 5).background(Capsule().fill(region?.tint ?? .coral))
                    Meter(value: Double(placed.count) / Double(max(1, ids.count)), color: .leaf, height: 12)
                    Text("\(placed.count)/\(ids.count)").font(.display(18)).foregroundStyle(Color.leafShade)
                }
                MapCanvas(window: window, outlines: region != nil || hard, labels: Set(placed.filter { region != nil || $0 == reveal }), tone: { id in
                    placed.contains(id) ? .placed : id == wrong ? .wrong : id == current && hinted ? .hint : ids.contains(id) ? .slot : .quiet
                }, onTap: { id in
                    // Tapping the place works as well as dragging: easier for small hands.
                    if let current, reveal == nil, let id, !placed.contains(id) { attempt(current, on: id) }
                })
                .background(GeometryReader { geo in Color.clear.preference(key: MapFrameKey.self, value: geo.frame(in: .named("mapgame"))) })
                .onPreferenceChange(MapFrameKey.self) { mapFrame = $0 }

                if let reveal {
                    VStack(alignment: .leading, spacing: 10) {
                        Text(L("Bien joué !", "Well done!")).font(.display(20)).foregroundStyle(Color.leafShade)
                        FactCard(p: drc.province(reveal))
                        Button(L("Continuer", "Continue")) { withAnimation(.spring(duration: 0.3)) { self.reveal = nil }; if placed.count == ids.count { finish() } }.buttonStyle(.chunky(.green))
                    }
                    .transition(.move(edge: .bottom).combined(with: .opacity))
                } else if let current {
                    tray(drc.province(current), ui).id(current).transition(.move(edge: .bottom).combined(with: .opacity))
                }
                Spacer(minLength: 0)
            }
        }
        .padding(.horizontal, 14)
        .coordinateSpace(name: "mapgame")
        .overlay(alignment: .topLeading) {
            // The piece in the hand, drawn at the map's own scale.
            if let finger, let current {
                let p = drc.province(current), scale = mapFrame.width / window.width
                PieceShape(id: current).frame(width: p.bounds.width * scale, height: p.bounds.height * scale)
                    .shadow(color: .black.opacity(0.35), radius: 6, y: 8).opacity(0.94)
                    .position(x: finger.x - (p.centre.x - p.bounds.midX) * scale, y: finger.y - lift - (p.centre.y - p.bounds.midY) * scale)
                    .allowsHitTesting(false)
            }
        }
        .onAppear {
            guard queue.isEmpty, placed.isEmpty else { return }
            queue = ids.shuffled()
            #if DEBUG
            // `-autoplay yes` places every province, `-autoplay hold` stops on the first fact card: for screenshots.
            guard let style = UserDefaults.standard.string(forKey: "autoplay") else { return }
            Task {
                try? await Task.sleep(for: .seconds(1.5))
                if style == "wrong", let first = queue.first, let other = ids.first(where: { $0 != first }) { attempt(first, on: other); return }
                while let next = queue.first {
                    attempt(next, on: next)
                    if style == "hold" { return }
                    try? await Task.sleep(for: .seconds(0.25))
                    reveal = nil
                }
                finish()
            }
            #endif
        }
    }

    private func tray(_ p: Province, _ ui: UiLang) -> some View {
        HStack(spacing: 14) {
            PieceShape(id: p.id).padding(12).frame(width: 118, height: 118)
                .background(RoundedRectangle(cornerRadius: 20).fill(Color.surface))
                .opacity(finger == nil ? 1 : 0.3)
                .gesture(DragGesture(minimumDistance: 0, coordinateSpace: .named("mapgame"))
                    .onChanged { g in
                        if finger == nil { Sfx.play(.pop); Haptic.tap(); note = nil }
                        finger = g.location
                    }
                    .onEnded { g in drop(p, at: g.location) })
            VStack(alignment: .leading, spacing: 3) {
                Text(L("Glisse", "Drag")).font(.text(12, .heavy)).textCase(.uppercase).tracking(1).foregroundStyle(Color.muted)
                Text(p.name).font(.display(25)).foregroundStyle(Color.ink).minimumScaleFactor(0.7).lineLimit(1)
                Text(L("sur la carte · ou touche sa place", "onto the map · or tap its place")).font(.text(12, .heavy)).textCase(.uppercase).foregroundStyle(Color.muted)
                Text(note ?? (region != nil ? p.hint(ui) : L("À toi de trouver sa place.", "Work out where it goes.")))
                    .font(.text(15, .bold)).foregroundStyle(note == nil ? Color.ink2 : .coralShade).fixedSize(horizontal: false, vertical: true).padding(.top, 4)
                if queue.count > 1 {
                    Button("\(L("Plus tard", "Later")) ›") { Sfx.play(.tap); note = nil; queue.append(queue.removeFirst()) }.font(.text(14, .heavy)).foregroundStyle(Color.sky).padding(.top, 4)
                }
            }
            Spacer(minLength: 0)
        }
        .padding(12).card()
    }

    private func drop(_ p: Province, at point: CGPoint) {
        finger = nil
        let spot = CGPoint(x: point.x, y: point.y - lift)
        // Letting go outside the map is not a mistake.
        guard mapFrame.contains(spot), mapFrame.width > 0 else { return }
        let scale = mapFrame.width / window.width
        let at = CGPoint(x: window.minX + (spot.x - mapFrame.minX) / scale, y: window.minY + (spot.y - mapFrame.minY) / scale)
        // Small provinces get a generous landing zone: the centre only has to come close.
        let near = hypot(at.x - p.centre.x, at.y - p.centre.y) < max(13, sqrt(p.shapeArea) * 0.34)
        attempt(p.id, on: near ? p.id : drc.province(at: at))
    }

    private func attempt(_ id: String, on target: String?) {
        if target == id {
            Sfx.play(.correct, placed.count + 1); Haptic.success()
            MapProgress.stamp(id)
            note = nil; wrong = nil
            withAnimation(.spring(duration: 0.4, bounce: 0.4)) {
                placed.append(id)
                queue.removeAll { $0 == id }
                reveal = id
            }
            return
        }
        // A miss is a clue, not a failure: name the place they chose.
        Sfx.play(.tick); Haptic.thud()
        misses[id, default: 0] += 1
        let again = L("Essaie encore !", "Try again!")
        note = target.map { "\(L("Ici, c’est", "That is")) \(drc.province($0).name). \(again)" } ?? "\(L("Pas tout à fait.", "Not quite.")) \(again)"
        withAnimation(.easeOut(duration: 0.15)) { wrong = target }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.9) { withAnimation { wrong = nil } }
    }

    private func finish() {
        guard xp == nil else { return }
        MapProgress.record(level, stars: stars)
        xp = store.complete(Rules.SessionResult(kind: .practice, unitId: nil, lessonIndex: nil, mistakes: totalMisses, bestCombo: ids.count)).xp
        Sfx.play(.complete); Haptic.success()
    }

    private func done(_ ui: UiLang) -> some View {
        let nextRegion = region.flatMap { r in drc.regions.firstIndex { $0.id == r.id }.flatMap { $0 + 1 < drc.regions.count ? drc.regions[$0 + 1] : nil } }
        return ZStack {
            ScrollView {
                VStack(spacing: 12) {
                    MapCanvas(window: window, labels: ids.count <= 6 ? Set(ids) : [], tone: { ids.contains($0) ? .placed : .quiet }).frame(maxHeight: 300)
                    HStack(spacing: 0) { LobaView(mood: .cheer, size: 96); MapStars(n: stars, size: 44) }
                    Text(region != nil ? L("Région terminée !", "Region complete!") : L("Tu connais tout le Congo !", "You know all of Congo!")).font(.display(28)).foregroundStyle(Color.ink).multilineTextAlignment(.center)
                    Text("+\(xp ?? 0) XP").font(.display(20)).foregroundStyle(Color.sunShade).padding(.horizontal, 18).padding(.vertical, 6).background(Capsule().fill(Color.sunTint))
                    Button(L("Petit quiz sur ces provinces", "Quick quiz on these provinces")) { go(.quiz(Array(ids.shuffled().prefix(6)))) }.buttonStyle(.chunky(.sun))
                    if let nextRegion { Button("\(L("Région suivante", "Next region")) · \(nextRegion.name(ui))") { go(.puzzle(nextRegion.id)) }.buttonStyle(.chunky(.green)) }
                    Button(L("Retour à la carte", "Back to the map")) { go(.hub) }.buttonStyle(.chunky(.outline))
                }
                .padding(.bottom, 16)
            }
            Confetti()
        }
    }
}

// MARK: - Quiz: recall without the piece in hand

private struct MapQuiz: View {
    struct Question: Equatable { let find: Bool; let id: String; let choices: [String] }
    let ids: [String]
    let go: (MapGameView.Screen) -> Void
    @Environment(AppStore.self) private var store
    @State private var queue: [Question] = []
    @State private var total = 0
    @State private var answer: String?
    @State private var score = 0
    @State private var asked = 0
    @State private var retried: Set<String> = []
    private let drc = DRC.shared

    var body: some View {
        let ui = store.state.ui
        VStack(spacing: 12) {
            if let q = queue.first {
                let p = drc.province(q.id)
                HStack(spacing: 10) {
                    Text(L("Quiz éclair", "Quick quiz")).font(.text(14, .heavy)).foregroundStyle(.white).padding(.horizontal, 12).padding(.vertical, 5).background(Capsule().fill(Color.plum))
                    Meter(value: Double(asked) / Double(max(1, total)), color: .leaf, height: 12)
                    Text("\(min(asked + 1, total))/\(total)").font(.display(18)).foregroundStyle(Color.leafShade)
                }
                MapCanvas(labels: answer == nil ? [] : [q.id], tone: { id in
                    if let answer { return id == q.id ? .right : id == answer ? .wrong : .open }
                    return !q.find && id == q.id ? .hint : .open
                }, onTap: q.find && answer == nil ? { id in if let id { pick(id, q) } } : nil)
                ScrollView {
                    VStack(alignment: .leading, spacing: 12) {
                        (q.find ? Text("\(L("Touche", "Tap")) ") + Text(p.name).foregroundColor(.skyShade)
                                : Text(p.name).foregroundColor(.skyShade) + Text(L(" : quel est son chef-lieu ?", ": what is its capital?")))
                            .font(.display(23)).foregroundStyle(Color.ink)
                        if !q.find {
                            LazyVGrid(columns: [GridItem(.flexible(), spacing: 10), GridItem(.flexible())], spacing: 10) {
                                ForEach(q.choices, id: \.self) { id in
                                    let state: Color? = answer == nil ? nil : id == q.id ? .leaf : id == answer ? .coral : nil
                                    Button { pick(id, q) } label: {
                                        Text(drc.province(id).capital).font(.text(17, .heavy)).foregroundStyle(Color.ink).frame(maxWidth: .infinity, minHeight: 34).padding(10).card(selected: state != nil, tint: state ?? .sky)
                                    }
                                    .buttonStyle(.plain).allowsHitTesting(answer == nil)
                                }
                            }
                        }
                        if let answer {
                            let right = answer == q.id
                            VStack(alignment: .leading, spacing: 10) {
                                Text("\(right ? L("Exact !", "Correct!") : q.find ? L("C’était ici.", "It was here.") : "\(L("C’était", "It was")) \(p.capital).") \(p.emoji) \(p.fact(ui))")
                                    .font(.text(15, .bold)).foregroundStyle(Color.ink).fixedSize(horizontal: false, vertical: true)
                                Button(L("Continuer", "Continue")) { next(q) }.buttonStyle(.chunky(.green))
                            }
                            .padding(14).background(RoundedRectangle(cornerRadius: 18).fill(right ? Color.leafTint : .coralTint))
                        }
                    }
                }
            } else if total > 0 {
                Spacer()
                LobaView(mood: score * 2 >= total ? .cheer : .happy, size: 150)
                Text(L("Ton score", "Your score")).font(.display(28)).foregroundStyle(Color.ink)
                (Text("\(score)").font(.display(64)).foregroundColor(.leafShade) + Text(" / \(total)").font(.display(28)).foregroundColor(.muted))
                Spacer()
                Button(L("Rejouer", "Play again")) { go(.quiz(Array(drc.ids.shuffled().prefix(8)))) }.buttonStyle(.chunky(.green))
                Button(L("Retour à la carte", "Back to the map")) { go(.hub) }.buttonStyle(.chunky(.outline)).padding(.bottom, 12)
            }
        }
        .padding(.horizontal, 14)
        .onAppear {
            guard total == 0 else { return }
            queue = ids.enumerated().map { i, id in
                let others = drc.ids.filter { $0 != id && drc.province($0).capital != drc.province(id).capital }.shuffled().prefix(3)
                return Question(find: i.isMultiple(of: 2), id: id, choices: ([id] + others).shuffled())
            }
            total = queue.count
        }
    }

    private func pick(_ id: String, _ q: Question) {
        guard answer == nil else { return }
        let right = id == q.id
        if right { Sfx.play(.correct, score + 1); Haptic.success() } else { Sfx.play(.tick); Haptic.thud() }
        withAnimation(.spring(duration: 0.3)) { answer = id }
        if right && !retried.contains(q.id) { score += 1 }
    }

    private func next(_ q: Question) {
        let missed = answer != q.id
        if !retried.contains(q.id) { asked += 1 }
        // A missed question comes back once at the end: a second try is where it sticks.
        withAnimation(.spring(duration: 0.3)) {
            queue.removeFirst()
            if missed && !retried.contains(q.id) { queue.append(q) }
            answer = nil
        }
        if missed { retried.insert(q.id) }
        if queue.isEmpty { Sfx.play(.complete) }
    }
}

// MARK: - Atlas and book

private struct MapAtlas: View {
    @Environment(AppStore.self) private var store
    @State private var id: String?
    private let drc = DRC.shared
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                MapCanvas(labels: id.map { [$0] } ?? [], tone: { $0 == id ? .right : .placed }, onTap: { tapped in
                    guard let tapped else { return }
                    Sfx.play(.tile, drc.ids.firstIndex(of: tapped) ?? 0); Haptic.tap()
                    withAnimation(.spring(duration: 0.3)) { id = tapped }
                })
                if let id { FactCard(p: drc.province(id), full: true).id(id) } else {
                    HStack(spacing: 4) { LobaView(mood: .think, size: 84); Bubble { Text(L("Touche une province.", "Tap a province.")).font(.text(15, .bold)).foregroundStyle(Color.ink2) } }
                }
                Flow(spacing: 6) {
                    ForEach(drc.regions) { r in Text(r.name(store.state.ui)).font(.text(13, .heavy)).foregroundStyle(.white).padding(.horizontal, 10).padding(.vertical, 4).background(Capsule().fill(r.tint)) }
                }
                MapCredits()
            }
            .padding(.horizontal, 14).padding(.bottom, 20)
        }
    }
}

private struct MapBook: View {
    @Environment(AppStore.self) private var store
    @State private var tab = 0
    var body: some View {
        let ui = store.state.ui, drc = DRC.shared
        VStack(spacing: 12) {
            Picker("", selection: $tab) {
                Text(L("Histoire", "History")).tag(0)
                Text(L("Cultures", "Cultures")).tag(1)
                Text(L("En chiffres", "In numbers")).tag(2)
            }
            .pickerStyle(.segmented).padding(.horizontal, 16)
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 14) {
                    ForEach(tab == 0 ? drc.history : tab == 1 ? drc.culture : drc.figures) { card in
                        VStack(alignment: .leading, spacing: 6) {
                            if let year = card.year {
                                Text(year).font(.display(14)).foregroundStyle(Color(hex: "3b2a00")).padding(.horizontal, 10).padding(.vertical, 4).background(Capsule().fill(Color.sun))
                            }
                            HStack(alignment: .top, spacing: 10) {
                                if let emoji = card.emoji { Text(emoji).font(.system(size: 30)) }
                                VStack(alignment: .leading, spacing: 4) {
                                    Text(card.title(ui)).font(.display(19)).foregroundStyle(Color.ink)
                                    Text(card.text(ui)).font(tab == 2 ? .display(18) : .text(15)).foregroundStyle(tab == 2 ? Color.skyShade : .ink2).fixedSize(horizontal: false, vertical: true)
                                }
                            }
                        }
                        .padding(14).frame(maxWidth: .infinity, alignment: .leading).card()
                    }
                }
                .padding(.horizontal, 16).padding(.bottom, 20)
            }
        }
    }
}
