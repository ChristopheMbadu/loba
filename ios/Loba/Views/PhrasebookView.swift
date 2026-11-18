import SwiftUI

enum Notebook {
    struct Entry: Identifiable, Codable {
        var id = UUID()
        let term: String
        let translation: String
        let sentence: Bool
    }

    static func key(_ store: AppStore) -> String {
        "phrasebook.\(store.learner?.email.lowercased() ?? "guest").\(store.state.lang)"
    }

    static func read(_ key: String) -> [Entry] {
        guard let data = UserDefaults.standard.data(forKey: key) else { return [] }
        return (try? JSONDecoder().decode([Entry].self, from: data)) ?? []
    }
}

struct PhrasebookView: View {
    let key: String
    let words: [Word]
    let ui: UiLang
    @Environment(AppStore.self) private var store
    @Environment(\.dismiss) private var dismiss
    @State private var entries: [Notebook.Entry] = []
    @State private var writing = false
    @State private var phrases = false
    @State private var term = ""
    @State private var translation = ""
    @State private var revealed: Set<UUID> = []

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                HStack {
                    Text("🇨🇩  LOBA").font(.text(16, .heavy))
                    Spacer()
                    Button { dismiss() } label: { Image(systemName: "xmark.circle.fill").font(.title) }
                        .accessibilityLabel(L("Fermer", "Close"))
                }
                HStack {
                    VStack(alignment: .leading, spacing: 6) {
                        Text(L("Mon carnet", "My phrasebook")).font(.display(32))
                        Text(L("Prépare tes cartes pour Devine le mot.", "Build your deck for Guess the Word.")).font(.text(16))
                    }
                    Spacer()
                    LobaView(mood: .wave, size: 84)
                }
                KubaBand(height: 9).clipShape(Capsule())
                HStack {
                    modeButton(L("Cartes Loba", "Loba cards"), art: "cards", active: !writing) { writing = false }
                    modeButton(L("Écrire", "Write my own"), art: "book", active: writing) { writing = true }
                }
                HStack {
                    kindButton(L("Mots", "Words"), false)
                    kindButton(L("Phrases", "Phrases"), true)
                }
                if writing {
                    VStack(spacing: 12) {
                        field(phrases ? L("Ta phrase", "Your phrase") : L("Ton mot", "Your word"), $term)
                        field(L("Traduction", "Translation"), $translation)
                        Button(L("Ajouter à mon jeu", "Add to my deck")) {
                            add(clean(term), clean(translation))
                            term = ""
                            translation = ""
                        }.buttonStyle(.chunky(.green)).disabled(clean(term).isEmpty || clean(translation).isEmpty || contains(clean(term), clean(translation)))
                    }
                } else {
                    if library.isEmpty {
                        Text(L("Aucune carte de ce type dans tes leçons débloquées. Tu peux en écrire une.", "Your unlocked lessons have no cards of this kind yet. Write one of your own.")).font(.text(16))
                    }
                    ForEach(library, id: \.self) { card in
                        let saved = contains(card[0], card[1])
                        Button { add(card[0], card[1]) } label: {
                            HStack {
                                cardText(card[0], card[1])
                                Spacer()
                                Image(systemName: saved ? "checkmark.circle.fill" : "plus.circle.fill")
                                    .font(.title2).foregroundStyle(saved ? Color.leaf : Color.sky)
                            }.padding(14).card()
                        }.buttonStyle(.plain).disabled(saved)
                    }
                }
                Text(L("Mon jeu", "My deck") + " · " + String(entries.count)).font(.display(24))
                ForEach(entries) { entry in
                    HStack {
                        if entry.sentence && !revealed.contains(entry.id) {
                            Button { revealed.insert(entry.id); Sfx.play(.page) } label: {
                                cardText(entry.translation, L("Révéler la phrase", "Reveal phrase"))
                            }
                        } else { cardText(entry.term, entry.translation) }
                        Spacer()
                        Button { entries.removeAll { $0.id == entry.id }; save() } label: {
                            Image(systemName: "minus.circle").padding(8)
                        }.accessibilityLabel(L("Retirer", "Remove") + " " + entry.term)
                    }.padding(14).card()
                }
            }.padding(20)
        }.scrollDismissesKeyboard(.interactively)
        .foregroundStyle(Color.ink).background(Color.skyTint.ignoresSafeArea())
        .onAppear { entries = Notebook.read(key) }
    }

    private var library: [[String]] {
        var seen = Set<[String]>()
        var pairs = words.map { word in
            let meaning = ui == .en && !word.exampleEn.isEmpty ? word.exampleEn : word.exampleFr
            return phrases ? [clean(word.example), clean(meaning)] : [clean(word.term), clean(word.meaning(ui))]
        }
        if phrases {
            let units = store.units
            for (index, unit) in units.enumerated() where Rules.unitUnlocked(store.state, units, index) {
                pairs += unit.sentences.map { [clean($0.answer), clean($0.prompt(ui))] }
            }
        }
        return pairs.filter { !$0.contains("") && seen.insert($0).inserted }
    }

    private func modeButton(_ title: String, art: String, active: Bool, action: @escaping () -> Void) -> some View {
        Button { action(); Sfx.play(.shaker) } label: {
            VStack {
                Art(name: art, size: 40)
                Text(title).font(.text(16, .heavy))
            }.frame(maxWidth: .infinity).padding(12)
            .background(active ? Color.sunTint : .white, in: RoundedRectangle(cornerRadius: 18))
            .overlay(RoundedRectangle(cornerRadius: 18).stroke(active ? Color.sun : Color.line, lineWidth: 2))
        }.buttonStyle(.plain).accessibilityAddTraits(active ? .isSelected : [])
    }

    private func kindButton(_ title: String, _ value: Bool) -> some View {
        Button(title) { phrases = value; Sfx.play(.tap) }
            .buttonStyle(.chunky(phrases == value ? .sky : .outline, small: true))
            .accessibilityAddTraits(phrases == value ? .isSelected : [])
    }

    private func field(_ label: String, _ value: Binding<String>) -> some View {
        TextField(label, text: value, axis: .vertical).accessibilityLabel(label)
            .font(.text(18)).padding(16).card()
    }

    private func cardText(_ title: String, _ subtitle: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title).font(.text(19, .heavy))
            Text(subtitle).font(.text(15)).foregroundStyle(Color.ink2)
        }.multilineTextAlignment(.leading)
    }

    private func clean(_ text: String) -> String { text.trimmingCharacters(in: .whitespacesAndNewlines) }

    private func contains(_ term: String, _ translation: String) -> Bool {
        entries.contains { $0.term == term && $0.translation == translation }
    }

    private func add(_ term: String, _ translation: String) {
        guard !contains(term, translation) else { return }
        entries.insert(.init(term: term, translation: translation, sentence: phrases), at: 0)
        save()
        Sfx.play(.coin)
    }

    private func save() {
        if let data = try? JSONEncoder().encode(entries) { UserDefaults.standard.set(data, forKey: key) }
    }
}
