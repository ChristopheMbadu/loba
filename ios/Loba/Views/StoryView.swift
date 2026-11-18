import SwiftUI

/// Masapo: the story book. A Kongo tale from the mouth of the Congo river, retold from the version
/// written down by R. E. Dennett in "Notes on the Folklore of the Fjort" (1898).
enum Tale {
    struct Entry { let fr: String, en: String, forms: [String: String] }

    static let words: [String: Entry] = [
        "croc": Entry(fr: "le crocodile", en: "the crocodile", forms: ["ln": "Ngando", "sw": "Mamba"]),
        "hen": Entry(fr: "la poule", en: "the hen", forms: ["ln": "Nsoso", "sw": "Kuku"]),
        "river": Entry(fr: "le fleuve", en: "the river", forms: ["ln": "ebale", "sw": "mto"]),
        "sibling": Entry(fr: "frère, sœur", en: "brother, sister", forms: ["ln": "Ndeko", "sw": "Ndugu"]),
        "water": Entry(fr: "l’eau", en: "water", forms: ["ln": "mai", "sw": "maji"]),
        "eggs": Entry(fr: "les œufs", en: "eggs", forms: ["ln": "maki", "sw": "mayai"]),
    ]
    static let order = ["croc", "hen", "river", "sibling", "water", "eggs"]

    static let pages: [(fr: String, en: String)] = [
        ("Chaque matin, {hen} la poule descendait au bord du fleuve, {river}, pour picorer son déjeuner. Le soleil se levait, l’eau chantait, et tout allait bien.",
         "Every morning, {hen} the hen walked down to the river, {river}, to peck at her breakfast. The sun came up, the water sang, and all was well."),
        ("Mais un jour, l’eau se mit à bouger. Deux yeux. Un long museau. C’était {croc} le crocodile ! « Petite poule, dit-il en ouvrant grand la gueule, je vais te manger ! »",
         "But one day, the water began to stir. Two eyes. One long snout. It was {croc} the crocodile! “Little hen,” he said, opening his jaws wide, “I am going to eat you!”"),
        ("{hen} ne s’enfuit pas. Elle cria de toutes ses forces : « {sibling} ! Mon frère, ne fais pas ça ! » {croc} referma la gueule, tout étonné. Son frère ? Il replongea sans dire un mot.",
         "{hen} did not run. She cried with all her might: “{sibling}! My brother, don’t do that!” {croc} shut his jaws, astonished. Her brother? He sank back without a word."),
        ("Le lendemain il revint, bien décidé à la croquer. Mais la poule cria encore : « {sibling} ! Ne fais pas ça ! » « Comment serais-je son frère ? grogna-t-il. Elle vit sur la terre, et moi dans l’eau, {water} ! »",
         "The next day he came back, determined to gobble her up. But the hen cried again: “{sibling}! Don’t do that!” “How can I be her brother?” he grumbled. “She lives on land, and I live in the water, {water}!”"),
        ("Cette question l’empêchait de dormir. Alors {croc} sortit du fleuve et partit demander la réponse à Nzambi, qui sait toutes choses. En chemin, il croisa son ami Mbambi, le grand lézard.",
         "The question kept him awake. So {croc} left the river and set off to ask Nzambi, who knows all things. On the way he met his friend Mbambi, the great lizard."),
        ("« N’y va pas, dit Mbambi, tu montrerais seulement ton ignorance ! Écoute : le canard vit dans l’eau et pond des œufs, {eggs}. La tortue aussi. Moi aussi, je ponds des œufs. La poule en pond. Et toi aussi, mon nigaud d’ami ! »",
         "“Don’t go,” said Mbambi, “you will only show how little you know! Listen: the duck lives in the water and lays eggs, {eggs}. So does the turtle. I lay eggs too. The hen lays them. And so do you, my silly friend!”"),
        ("« Alors, d’une certaine façon, nous sommes tous frères. » {croc} rentra au fleuve en souriant. Et voilà pourquoi, depuis ce jour, le crocodile ne mange pas la poule.",
         "“So, in a way, we are all brothers.” {croc} went home to the river, smiling. And that is why, from that day to this, the crocodile does not eat the hen."),
    ]

    /// Sky behind the book on each page: dawn, day, dusk, night.
    static let skies: [Color] = [Color(hex: "fde3c0"), Color(hex: "fde3c0"), Color(hex: "d9eefc"), Color(hex: "d9eefc"), Color(hex: "d9eefc"), Color(hex: "f9c9a0"), Color(hex: "f9c9a0"), Color(hex: "1c1d52"), Color(hex: "1c1d52")]
}

struct StoryView: View {
    @Environment(AppStore.self) private var store
    @Environment(\.dismiss) private var dismiss
    @State private var page = 0
    @State private var code = "ln"
    @State private var meaning: String?
    @State private var forward = true
    @State private var drift = false

    private var last: Int { Tale.pages.count + 1 }
    private var night: Bool { page >= 7 }

    var body: some View {
        let ui = store.state.ui
        VStack(spacing: 0) {
            HStack(spacing: 10) {
                Button { dismiss() } label: {
                    Image(systemName: "xmark").font(.system(size: 17, weight: .heavy)).foregroundStyle(Color.ink).frame(width: 42, height: 42).background(RoundedRectangle(cornerRadius: 13).fill(.white.opacity(0.75)))
                }
                Spacer()
                HStack(spacing: 6) {
                    ForEach(["ln", "sw"], id: \.self) { c in
                        Button { Sfx.play(.tap); code = c } label: { LangBadge(code: c, color: c == "ln" ? .sky : .leaf).opacity(code == c ? 1 : 0.45) }
                    }
                }
                .padding(6).background(RoundedRectangle(cornerRadius: 13).fill(.white.opacity(0.75)))
                UiSwitch()
            }
            .padding(.horizontal, 16).padding(.vertical, 8)

            VStack(spacing: 10) {
                StoryStage(page: min(page, 7), drift: drift, shout: "\(word("sibling")) !")
                    .frame(maxWidth: .infinity).aspectRatio(330 / 264, contentMode: .fit)
                    .overlay { if page == 0 { cover(ui) } }
                    .clipShape(RoundedRectangle(cornerRadius: 18))
                    .overlay(RoundedRectangle(cornerRadius: 18).strokeBorder(Color.cocoa, lineWidth: 3))
                    .id(min(page, 7))
                    .transition(.asymmetric(insertion: .opacity.combined(with: .scale(scale: 1.04)), removal: .opacity))
                KubaBand(height: 12).clipShape(Capsule())
                ScrollView {
                    Group {
                        if page == 0 { opening(ui) } else if page == last { ending(ui) } else { reading(ui) }
                    }
                    .padding(18).frame(maxWidth: .infinity)
                    .id("\(page)-\(ui.rawValue)-\(code)")
                    .transition(.asymmetric(insertion: .move(edge: forward ? .trailing : .leading).combined(with: .opacity), removal: .opacity))
                }
                .background(RoundedRectangle(cornerRadius: 18).fill(Color.paper))
                .overlay(RoundedRectangle(cornerRadius: 12).strokeBorder(Color(hex: "e2cfa0"), style: StrokeStyle(lineWidth: 2, dash: [7, 6])).padding(8).allowsHitTesting(false))
                .overlay(RoundedRectangle(cornerRadius: 18).strokeBorder(Color.cocoa, lineWidth: 3))
            }
            .padding(12)
            .background(RoundedRectangle(cornerRadius: 28).fill(Color.sand))
            .overlay(RoundedRectangle(cornerRadius: 28).strokeBorder(Color.cocoa, lineWidth: 4))
            .background(RoundedRectangle(cornerRadius: 28).fill(Color.cocoa.opacity(0.55)).offset(y: 8))
            .padding(.horizontal, 14).padding(.bottom, 8)

            HStack(spacing: 18) {
                arrow("chevron.left", disabled: page == 0) { go(page - 1) }
                HStack(spacing: 7) {
                    ForEach(0...last, id: \.self) { i in
                        Circle().fill(i == page ? Color.sun : i < page ? Color.cocoa : .clear).frame(width: i == page ? 13 : 8, height: i == page ? 13 : 8)
                            .overlay(Circle().strokeBorder(i == page ? Color.sunShade : Color.cocoa.opacity(0.6), lineWidth: 2))
                    }
                }
                arrow("chevron.right", disabled: page == last || page == 0) { go(page + 1) }
            }
            .padding(.vertical, 10)
        }
        .background(Tale.skies[min(page, Tale.skies.count - 1)].ignoresSafeArea())
        .animation(.easeInOut(duration: 0.6), value: page)
        .gesture(DragGesture(minimumDistance: 40).onEnded { g in
            guard page > 0, abs(g.translation.width) > abs(g.translation.height) else { return }
            go(page + (g.translation.width < 0 ? 1 : -1))
        })
        .onAppear {
            if Tale.words["croc"]!.forms[store.state.lang] != nil { code = store.state.lang }
            #if DEBUG
            var still = Transaction()
            still.disablesAnimations = true
            withTransaction(still) { page = min(last, UserDefaults.standard.integer(forKey: "page")) }
            #endif
            withAnimation(.easeInOut(duration: 9).repeatForever(autoreverses: true)) { drift = true }
        }
    }

    private func go(_ to: Int) {
        let next = max(0, min(last, to))
        guard next != page else { return }
        forward = next > page
        if page == 0 { Sfx.play(.call) } else { Sfx.play(.page, next) }
        if next == 2 { DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) { Sfx.play(.splash) } }
        Haptic.tap()
        meaning = nil
        page = next
    }

    private func arrow(_ symbol: String, disabled: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: symbol).font(.system(size: 22, weight: .black)).foregroundStyle(Color.ink).frame(width: 60, height: 60)
                .background(Circle().fill(Color.sun)).background(Circle().fill(Color.sunShade).offset(y: 5))
        }
        .disabled(disabled).opacity(disabled ? 0.35 : 1)
    }

    private func word(_ key: String) -> String { Tale.words[key]?.forms[code] ?? Tale.words[key]?.forms["ln"] ?? key }

    private func cover(_ ui: UiLang) -> some View {
        VStack(spacing: 6) {
            Text(L("Un conte kongo", "A Kongo tale")).font(.text(13, .heavy)).textCase(.uppercase).tracking(1.5).foregroundStyle(Color.cocoa)
            Text(L("Pourquoi le crocodile ne mange pas la poule", "Why the Crocodile Does Not Eat the Hen")).font(.display(23)).foregroundStyle(Color.ink).multilineTextAlignment(.center)
        }
        .padding(14).background(RoundedRectangle(cornerRadius: 16).fill(Color.paper.opacity(0.92))).padding(18).frame(maxHeight: .infinity, alignment: .top)
    }

    /// The storyteller calls "Lisapo !" and everyone answers "Onge !".
    private func opening(_ ui: UiLang) -> some View {
        VStack(spacing: 14) {
            Text("Lisapo !").font(.display(40)).foregroundStyle(Color.coral).shadow(color: .sun, radius: 0, x: 2, y: 3)
            Text(L("Chez nous, le conteur lance « Lisapo ! » et tout le monde répond « Onge ! ». Réponds pour ouvrir le conte.", "Back home, the storyteller calls “Lisapo!” and everyone answers “Onge!”. Answer to open the tale."))
                .font(.text(16)).foregroundStyle(Color.cocoa).multilineTextAlignment(.center)
            Button("Onge !") { go(1) }.buttonStyle(.chunky(.sun))
        }
    }

    private func reading(_ ui: UiLang) -> some View {
        let raw = ui == .fr ? Tale.pages[page - 1].fr : Tale.pages[page - 1].en
        var text = AttributedString()
        for (i, part) in raw.components(separatedBy: CharacterSet(charactersIn: "{}")).enumerated() {
            if i.isMultiple(of: 2) {
                var plain = AttributedString(part)
                plain.foregroundColor = Color(hex: "3a2a1c")
                text += plain
            } else {
                var chip = AttributedString(word(part))
                chip.foregroundColor = .skyShade
                chip.font = .system(size: 21, weight: .black, design: .rounded)
                chip.underlineStyle = Text.LineStyle(pattern: .solid, color: .sun)
                chip.link = URL(string: "loba-word:\(part)")
                text += chip
            }
        }
        return VStack(alignment: .leading, spacing: 12) {
            Text("\(L("Page", "Page")) \(page) / \(Tale.pages.count)").font(.text(13, .heavy)).textCase(.uppercase).tracking(1.5).foregroundStyle(Color(hex: "a8763e"))
            Text(text).font(.text(20, .bold)).lineSpacing(7)
                .environment(\.openURL, OpenURLAction { url in
                    let key = url.absoluteString.replacingOccurrences(of: "loba-word:", with: "")
                    Sfx.play(.tile, Tale.order.firstIndex(of: key) ?? 0); Haptic.tap()
                    withAnimation(.spring(duration: 0.3, bounce: 0.5)) { meaning = meaning == key ? nil : key }
                    return .handled
                })
            if let key = meaning, let entry = Tale.words[key] {
                HStack(spacing: 8) {
                    Text(word(key)).font(.text(17, .black)).foregroundStyle(Color.skyShade)
                    Text("=").foregroundStyle(Color.muted)
                    Text(ui == .fr ? entry.fr : entry.en).font(.text(17, .bold)).foregroundStyle(Color.ink)
                }
                .padding(.horizontal, 14).padding(.vertical, 9).background(Capsule().fill(Color.sunTint)).overlay(Capsule().strokeBorder(Color.sun, lineWidth: 2))
                .transition(.scale(scale: 0.7, anchor: .leading).combined(with: .opacity))
            } else if page == 1 {
                Text(L("Touche les mots en couleur.", "Tap the coloured words.")).font(.text(14, .bold)).foregroundStyle(Color(hex: "a8763e"))
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func ending(_ ui: UiLang) -> some View {
        VStack(spacing: 12) {
            Text("Lisapo esili.").font(.display(32)).foregroundStyle(Color.coral).shadow(color: .sun, radius: 0, x: 2, y: 3)
            Text(L("Avant de te fâcher, cherche ce qui te rapproche de l’autre.", "Before you get angry, look for what you share with the other."))
                .font(.text(18, .heavy)).foregroundStyle(Color.skyShade).multilineTextAlignment(.center)
            Text(L("Les mots du conte", "Words from the tale")).font(.text(13, .heavy)).textCase(.uppercase).tracking(1.5).foregroundStyle(Color(hex: "a8763e")).padding(.top, 4)
            LazyVGrid(columns: [GridItem(.flexible(), spacing: 10), GridItem(.flexible())], spacing: 10) {
                ForEach(Tale.order, id: \.self) { key in
                    VStack(spacing: 2) {
                        Text(word(key)).font(.text(18, .black)).foregroundStyle(Color.skyShade)
                        Text(ui == .fr ? Tale.words[key]!.fr : Tale.words[key]!.en).font(.text(14)).foregroundStyle(Color.cocoa)
                    }
                    .frame(maxWidth: .infinity).padding(.vertical, 10)
                    .background(RoundedRectangle(cornerRadius: 14).fill(.white)).overlay(RoundedRectangle(cornerRadius: 14).strokeBorder(Color(hex: "e2cfa0"), lineWidth: 2))
                }
            }
            Button(L("Relire", "Read again")) { go(0) }.buttonStyle(.chunky(.outline))
            Button(L("Fermer le livre", "Close the book")) { dismiss() }.buttonStyle(.chunky(.green))
            Text(L("Conte kongo, raconté d’après la version notée par R. E. Dennett en 1898.", "A Kongo tale, retold from the version written down by R. E. Dennett in 1898."))
                .font(.text(12)).foregroundStyle(Color(hex: "a8763e")).multilineTextAlignment(.center)
        }
    }
}

/// One page's picture: the landscape, a living sky, and the characters, who arrive, breathe and jump when tapped.
private struct StoryStage: View {
    let page: Int
    let drift: Bool
    let shout: String
    @State private var arrived = false
    @State private var hop = false
    private var night: Bool { page >= 7 }
    private var dusk: Bool { page == 5 || page == 6 }

    var body: some View {
        ZStack {
            Image("story-back-\(page)").resizable().scaledToFill()
            if night { NightSky() } else { DaySky().opacity(dusk ? 0.6 : 1) }
            TimelineView(ambient()) { timeline in
                let t = timeline.date.timeIntervalSinceReferenceDate
                Image("story-cast-\(page)").resizable().scaledToFill()
                    .offset(y: sin(t * 2.2) * 2.2 + (hop ? -16 : 0))
                    .rotationEffect(.degrees(sin(t * 1.1) * 0.6), anchor: .bottom)
            }
            .offset(x: arrived ? 0 : (page.isMultiple(of: 2) ? -70 : 70))
            .opacity(arrived ? 1 : 0)
            .onTapGesture {
                Sfx.play(page.isMultiple(of: 2) ? .drum : .pop, page); Haptic.thud()
                withAnimation(.spring(duration: 0.18, bounce: 0.4)) { hop = true }
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.18) { withAnimation(.spring(duration: 0.5, bounce: 0.7)) { hop = false } }
            }
        }
        .overlay {
            // The hen's cry, in the learner's language.
            if page == 3 || page == 4 {
                GeometryReader { geo in
                    Text(shout).font(.display(26)).foregroundStyle(Color.coral)
                        .shadow(color: .white, radius: 0, x: 0, y: 0).shadow(color: .white, radius: 1.5).shadow(color: .white, radius: 1.5)
                        .rotationEffect(.degrees(-8)).scaleEffect(arrived ? 1 : 0.2).opacity(arrived ? 1 : 0)
                        .position(x: geo.size.width * 0.62, y: geo.size.height * 0.4)
                }
                .allowsHitTesting(false)
            }
        }
        // Slightly enlarged so the slow drift never shows an edge.
        .scaleEffect(drift ? 1.1 : 1.05, anchor: page.isMultiple(of: 2) ? .bottomLeading : .bottomTrailing)
        .onAppear { withAnimation(.spring(duration: 0.9, bounce: 0.35).delay(0.25)) { arrived = true } }
    }
}
