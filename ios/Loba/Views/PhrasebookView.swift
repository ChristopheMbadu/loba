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
    @Environment(\.dismiss) private var dismiss
    @State private var entries: [Notebook.Entry] = []
    @State private var term = ""
    @State private var translation = ""
    @State private var sentence = false
    @State private var revealed: Set<UUID> = []

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Picker(L("Type", "Type"), selection: $sentence) {
                        Text(L("Mot ou expression", "Word or phrase")).tag(false)
                        Text(L("Phrase", "Sentence")).tag(true)
                    }.pickerStyle(.segmented)
                    TextField(L("Dans la langue apprise", "In the language you’re learning"), text: $term, axis: .vertical)
                    TextField(L("Traduction", "Translation"), text: $translation, axis: .vertical)
                    Button(L("Enregistrer", "Save")) {
                        entries.insert(.init(term: clean(term), translation: clean(translation), sentence: sentence), at: 0)
                        save()
                        term = ""
                        translation = ""
                    }.disabled(clean(term).isEmpty || clean(translation).isEmpty)
                } footer: {
                    Text(L("Carnet privé sur cet appareil. Tes mots et expressions sont disponibles dans Devine le mot.", "Private to this device. Your words and phrases are available in Guess the Word."))
                }
                Section {
                    ForEach(entries) { entry in
                        VStack(alignment: .leading, spacing: 6) {
                            Text(entry.sentence ? entry.translation : entry.term).bold()
                            if !entry.sentence { Text(entry.translation) }
                            else if revealed.contains(entry.id) { Text(entry.term) }
                            else {
                                Button(L("Révéler la phrase", "Reveal sentence")) { revealed.insert(entry.id) }
                            }
                        }
                    }.onDelete { entries.remove(atOffsets: $0); save() }
                }
            }
            .navigationTitle(L("Mon carnet", "My phrasebook"))
            .toolbar { Button(L("Terminé", "Done")) { dismiss() } }
            .onAppear { entries = Notebook.read(key) }
        }
    }

    private func clean(_ text: String) -> String { text.trimmingCharacters(in: .whitespacesAndNewlines) }

    private func save() {
        if let data = try? JSONEncoder().encode(entries) { UserDefaults.standard.set(data, forKey: key) }
    }
}
