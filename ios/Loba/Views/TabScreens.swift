import SwiftUI

private struct PageHead: View {
    let title: String
    let text: String
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title).font(.display(30)).foregroundStyle(Color.ink)
            Text(text).font(.text(16)).foregroundStyle(Color.ink2)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private struct TileCard: View {
    let art: String
    let tint: Color
    let title: String
    let text: String
    var body: some View {
        HStack(spacing: 16) {
            Art(name: art, size: 40).frame(width: 68, height: 68).background(RoundedRectangle(cornerRadius: 18).fill(tint))
            VStack(alignment: .leading, spacing: 3) {
                Text(title).font(.text(19, .heavy)).foregroundStyle(Color.ink)
                Text(text).font(.text(15)).foregroundStyle(Color.muted).multilineTextAlignment(.leading)
            }
            Spacer(minLength: 0)
        }
        .padding(16).card()
    }
}

// MARK: - Practice

struct PracticeView: View {
    @Environment(AppStore.self) private var store
    @State private var mode: LessonBuilder.PracticeMode?
    @State private var story = false
    @State private var puzzle = false
    @State private var guessing = false
    @State private var phrasebook = false
    @State private var map = false

    var body: some View {
        let s = store.state, pool = Rules.unlockedWords(s, store.units)
        let due = Rules.reviewWords(s, s.lang, pool, Game.now()).count
        let seen = s.words.keys.contains { $0.hasPrefix("\(s.lang):") }
        let listen = s.voice && pool.filter { $0.audio != nil }.count >= 4
        ScrollView {
            VStack(spacing: 14) {
                PageHead(title: L("Pratique", "Practice"), text: L("La pratique ne coûte jamais de cœurs. Les mots ratés reviennent plus souvent.", "Practice never costs hearts. Words you miss come back more often."))
                Button { mode = .review } label: {
                    TileCard(art: "cards", tint: .leafTint, title: L("Révision intelligente", "Smart review"),
                             text: seen ? L("\(due) mots à revoir aujourd’hui", "\(due) words to review today") : L("Termine une leçon pour débloquer la révision.", "Finish a lesson to unlock review."))
                }
                .buttonStyle(.plain).disabled(!seen).opacity(seen ? 1 : 0.55)
                Button { mode = .listen } label: {
                    TileCard(art: "headphones", tint: Color.plum.opacity(0.14), title: L("Écoute", "Listening"),
                             text: listen ? L("Reconnais les mots à l’oreille.", "Recognise words by ear.") : L("Pas encore assez d’enregistrements pour ces leçons.", "Not enough recordings for these lessons yet."))
                }
                .buttonStyle(.plain).disabled(!listen).opacity(listen ? 1 : 0.55)
                Button { puzzle = true } label: {
                    TileCard(art: "word", tint: .skyTint, title: L("Mots mêlés", "Word hunt"),
                             text: L("Trouve les mots cachés : chacun révèle son sens et une phrase.", "Find the hidden words: each reveals its meaning and a sentence."))
                }
                .buttonStyle(.plain)
                Button { guessing = true } label: {
                    TileCard(art: "cards", tint: .sunTint, title: L("Devine le mot", "Guess the word"),
                             text: L("À deux, fais deviner les mots de tes leçons.", "Play with a partner and guess words from your lessons."))
                }
                .buttonStyle(.plain)
                Button { phrasebook = true } label: {
                    TileCard(art: "book", tint: .leafTint, title: L("Mon carnet", "My phrasebook"),
                             text: L("Garde tes mots et tes phrases avec leur traduction.", "Save your words and sentences with their translations."))
                }.buttonStyle(.plain)
                Button { map = true } label: {
                    HStack(spacing: 16) {
                        Text("🗺️").font(.system(size: 36)).frame(width: 68, height: 68).background(RoundedRectangle(cornerRadius: 18).fill(Color.leafTint))
                        VStack(alignment: .leading, spacing: 3) {
                            Text(L("Mboka na biso · La carte du Congo", "Mboka na biso · The map of Congo")).font(.text(19, .heavy)).foregroundStyle(Color.ink).multilineTextAlignment(.leading)
                            Text(L("Remets les 26 provinces à leur place et découvre le pays.", "Put the 26 provinces back in place and discover the country.")).font(.text(15)).foregroundStyle(Color.muted).multilineTextAlignment(.leading)
                        }
                        Spacer(minLength: 0)
                    }
                    .padding(16).card()
                }
                .buttonStyle(.plain)
                Button { story = true } label: {
                    TileCard(art: "book", tint: .sunTint, title: L("Masapo · Contes du Congo", "Masapo · Tales of Congo"),
                             text: L("Un conte illustré, avec des mots de ta langue.", "An illustrated folktale with words from your language."))
                }
                .buttonStyle(.plain)
            }
            .padding(20)
        }
        .fullScreenCover(item: $mode) { LessonView(mode: .practice($0)) }
        .fullScreenCover(isPresented: $story) { StoryView() }
        .fullScreenCover(isPresented: $puzzle) { PuzzleHub() }
        .fullScreenCover(isPresented: $guessing) { GuessWordGame(words: pool.filter(\.guessWord), ui: s.ui, notebook: Notebook.key(store)) }
        .sheet(isPresented: $phrasebook) { PhrasebookView(key: Notebook.key(store)) }
        .fullScreenCover(isPresented: $map) { MapGameView() }
    }
}

extension LessonBuilder.PracticeMode: Identifiable { var id: Int { self == .review ? 0 : 1 } }

// MARK: - Quests

struct QuestsView: View {
    @Environment(AppStore.self) private var store

    var body: some View {
        let quests = Rules.dailyQuests(store.state, store.today)
        ScrollView {
            VStack(spacing: 14) {
                PageHead(title: L("Quêtes du jour", "Daily quests"), text: L("De nouvelles quêtes chaque matin. Les gemmes s’échangent à la boutique.", "New quests every morning. Spend your gems in the shop."))
                ForEach(quests) { q in
                    let claimed = store.state.daily.claimed.contains(q.id.rawValue)
                    VStack(alignment: .leading, spacing: 12) {
                        HStack(spacing: 14) {
                            Art(name: ["xp": "bolt", "lessons": "book", "combo": "flame", "perfect": "target"][q.id.rawValue]!, size: 40)
                            VStack(alignment: .leading, spacing: 6) {
                                Text(title(q)).font(.text(17, .heavy)).foregroundStyle(Color.ink)
                                HStack(spacing: 10) {
                                    Meter(value: Double(q.value) / Double(q.goal), color: q.done ? .leaf : .sun, height: 14)
                                    Text("\(min(q.value, q.goal))/\(q.goal)").font(.text(13, .heavy)).foregroundStyle(Color.muted)
                                }
                            }
                        }
                        if q.done {
                            Button {
                                store.claim(q.id)
                                Sfx.play(.coin); Haptic.success()
                            } label: { Label(claimed ? L("Récupéré", "Claimed") : "+\(q.reward) " + L("gemmes", "gems"), systemImage: claimed ? "checkmark" : "diamond.fill") }
                                .buttonStyle(.chunky(.sky, small: true)).disabled(claimed)
                        }
                    }
                    .padding(16).frame(maxWidth: .infinity, alignment: .leading).card(selected: q.done && !claimed, tint: .sun)
                }
            }
            .padding(20)
        }
    }

    private func title(_ q: Rules.Quest) -> String {
        switch q.id {
        case .xp: L("Gagne \(q.goal) XP", "Earn \(q.goal) XP")
        case .lessons: L("Termine \(q.goal) leçons", "Finish \(q.goal) lessons")
        case .combo: L("\(q.goal) bonnes réponses d’affilée", "\(q.goal) right answers in a row")
        case .perfect: L("Une leçon sans faute", "One flawless lesson")
        }
    }
}

// MARK: - Shop

struct ShopView: View {
    @Environment(AppStore.self) private var store

    var body: some View {
        let s = store.state
        ScrollView {
            VStack(spacing: 14) {
                PageHead(title: L("Boutique", "Shop"), text: L("Dépense tes gemmes pour protéger ta série ou aller plus vite.", "Spend gems to protect your streak or go faster."))
                item(.refill, "heart", .coralTint, L("Recharge de cœurs", "Heart refill"), L("Retrouve tes 5 cœurs tout de suite.", "Get all 5 hearts back right away."))
                item(.freeze, "freeze", .skyTint, L("Gel de série", "Streak freeze"), L("Protège ta série si tu rates un jour. Tu en as \(s.freezes) sur \(Game.maxFreezes).", "Protects your streak if you miss a day. You have \(s.freezes) of \(Game.maxFreezes)."))
                item(.doubleXp, "bolt", .sunTint, L("Double XP", "Double XP"), Game.now() < s.doubleXpUntil ? L("Actif en ce moment !", "Active right now!") : L("Deux fois plus d’XP pendant 15 minutes.", "Twice the XP for 15 minutes."))
            }
            .padding(20)
        }
    }

    private func item(_ item: Rules.ShopItem, _ art: String, _ tint: Color, _ title: String, _ text: String) -> some View {
        let can = Rules.canBuy(store.state, item, Game.now())
        return HStack(spacing: 14) {
            Art(name: art, size: 40).frame(width: 64, height: 64).background(RoundedRectangle(cornerRadius: 18).fill(tint))
            VStack(alignment: .leading, spacing: 3) {
                Text(title).font(.text(18, .heavy)).foregroundStyle(Color.ink)
                Text(text).font(.text(14)).foregroundStyle(Color.muted)
            }
            Spacer(minLength: 0)
            Button {
                store.buy(item)
                Sfx.play(item == .refill ? .heart : .coin); Haptic.success()
            } label: { HStack(spacing: 5) { Art(name: "gem", size: 18); Text("\(item.price)") } }
                .buttonStyle(.chunky(.outline, small: true)).disabled(!can)
        }
        .padding(16).card()
    }
}

// MARK: - Profile

struct ProfileView: View {
    @Environment(AppStore.self) private var store
    @State private var reminder = Reminder.isOn
    @State private var editing = false
    @State private var auth: AuthView.Mode?
    @State private var confirmErase = false
    @State private var confirmSignOut = false
    @State private var confirmDelete = false
    @State private var password = ""
    @State private var accountError = ""

    var body: some View {
        let s = store.state, ui = s.ui
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                Button { editing = true } label: {
                    HStack(spacing: 16) {
                        Text(s.avatar).font(.system(size: 46)).frame(width: 84, height: 84).background(Circle().fill(Color.skyTint))
                        VStack(alignment: .leading, spacing: 3) {
                            Text(s.name.isEmpty ? (store.learner?.name ?? L("Apprenant", "Learner")) : s.name).font(.display(26)).foregroundStyle(Color.ink)
                            Text(L("Modifier le profil", "Edit profile")).font(.text(14, .bold)).foregroundStyle(Color.sky)
                        }
                        Spacer()
                    }
                }
                .buttonStyle(.plain)

                LazyVGrid(columns: [GridItem(.flexible(), spacing: 12), GridItem(.flexible())], spacing: 12) {
                    stat("flame", "\(Rules.visibleStreak(s, store.today))", L("Jours de série", "Day streak"))
                    stat("bolt", "\(s.xp)", "XP")
                    stat("word", "\(Rules.wordsLearned(s, s.lang))", L("Mots appris", "Words learned"))
                    stat("trophy", "\(s.bestStreak)", L("Meilleure série", "Best streak"))
                }

                VStack(alignment: .leading, spacing: 10) {
                    Text(L("Cette semaine", "This week")).font(.display(22)).foregroundStyle(Color.ink)
                    WeekStrip().padding(.vertical, 12).padding(.horizontal, 6).card()
                }

                section(L("Réglages", "Settings")) {
                    row(L("J’apprends", "I’m learning")) {
                        Picker("", selection: Binding(get: { s.lang }, set: { store.setLang($0) })) {
                            ForEach(store.catalog.languages) { Text($0.name(ui).capitalized).tag($0.code) }
                        }
                        .tint(.sky)
                    }
                    row(L("Langue de l’interface", "Interface language")) { UiSwitch() }
                    row(L("Objectif du jour", "Daily goal")) {
                        Picker("", selection: Binding(get: { s.goal }, set: { store.setGoal($0) })) {
                            ForEach(Game.goals, id: \.self) { Text("\($0) XP").tag($0) }
                        }
                        .tint(.sky)
                    }
                    toggle(L("Effets sonores", "Sound effects"), s.sound) { store.toggleSound() }
                    toggle(L("Enregistrements audio", "Audio recordings"), s.voice) { store.toggleVoice() }
                    toggle(L("Rappel du soir (19 h)", "Evening reminder (7 pm)"), reminder) { Task { reminder = await Reminder.set(!reminder) } }
                }

                section(L("Compte", "Account")) {
                    if let learner = store.learner {
                        row(learner.name, sub: learner.email) { Button(L("Déconnexion", "Sign out")) { confirmSignOut = true }.buttonStyle(.chunky(.outline, small: true)) }
                    } else {
                        VStack(alignment: .leading, spacing: 10) {
                            Text(L("Ta progression est gardée sur ce téléphone. Crée un compte pour la retrouver partout.", "Your progress is kept on this phone. Create an account to find it everywhere."))
                                .font(.text(15)).foregroundStyle(Color.ink2)
                            Button(L("Créer un compte", "Create an account")) { auth = .signup }.buttonStyle(.chunky(.green))
                            Button(L("J’ai déjà un compte", "I already have an account")) { auth = .login }.buttonStyle(.chunky(.outline))
                        }
                        .padding(.vertical, 12)
                    }
                }

                if !accountError.isEmpty { Text(accountError).font(.text(14, .bold)).foregroundStyle(Color.coralShade) }
                Link(L("Politique de confidentialité", "Privacy policy"), destination: API.url("/privacy.html")).font(.text(15, .bold))
                Button(L("Effacer ma progression", "Erase my progress")) { confirmErase = true }.font(.text(14, .heavy)).textCase(.uppercase).foregroundStyle(Color.coral)
                if store.signedIn {
                    Button(L("Supprimer mon compte", "Delete my account")) { password = ""; confirmDelete = true }.font(.text(14, .heavy)).textCase(.uppercase).foregroundStyle(Color.coral)
                }
            }
            .padding(20)
        }
        .sheet(isPresented: $editing) { EditProfile().presentationDetents([.medium, .large]) }
        .sheet(item: $auth) { AuthView(mode: $0) }
        .alert(L("Effacer ta progression ?", "Erase your progress?"), isPresented: $confirmErase) {
            Button(L("Tout effacer", "Erase everything"), role: .destructive) { store.eraseProgress() }
            Button(L("Annuler", "Cancel"), role: .cancel) {}
        } message: { Text(L("XP, série, mots appris : tout repart de zéro.", "XP, streak, words learned: everything starts over.")) }
        .alert(L("Se déconnecter ?", "Sign out?"), isPresented: $confirmSignOut) {
            Button(L("Déconnexion", "Sign out"), role: .destructive) { Task { await store.signOut() } }
            Button(L("Annuler", "Cancel"), role: .cancel) {}
        } message: { Text(L("Ta progression reste sur ton compte.", "Your progress stays on your account.")) }
        .alert(L("Supprimer ton compte ?", "Delete your account?"), isPresented: $confirmDelete) {
            if store.learner?.passwordLogin != false { SecureField(L("Mot de passe", "Password"), text: $password) }
            Button(L("Supprimer définitivement", "Delete permanently"), role: .destructive) {
                Task {
                    do { try await store.deleteAccount(password: password); accountError = "" }
                    catch { accountError = (error as? APIError)?.message ?? L("La suppression a échoué.", "Deletion failed.") }
                }
            }
            Button(L("Annuler", "Cancel"), role: .cancel) {}
        } message: { Text(L("Ton compte et toute ta progression seront supprimés. C’est irréversible.", "Your account and all your progress will be deleted. This cannot be undone.")) }
    }

    private func stat(_ art: String, _ value: String, _ label: String) -> some View {
        HStack(spacing: 12) {
            Art(name: art, size: 34)
            VStack(alignment: .leading, spacing: 0) {
                Text(value).font(.display(22)).foregroundStyle(Color.ink)
                Text(label).font(.text(13)).foregroundStyle(Color.muted)
            }
            Spacer(minLength: 0)
        }
        .padding(14).card()
    }

    private func section(_ title: String, @ViewBuilder content: () -> some View) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(title).font(.display(22)).foregroundStyle(Color.ink).padding(.bottom, 4)
            content()
        }
    }

    private func row(_ label: String, sub: String? = nil, @ViewBuilder trailing: () -> some View) -> some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(label).font(.text(17, .bold)).foregroundStyle(Color.ink)
                if let sub { Text(sub).font(.text(14)).foregroundStyle(Color.muted) }
            }
            Spacer()
            trailing()
        }
        .padding(.vertical, 12)
        .overlay(alignment: .bottom) { Rectangle().fill(Color.line).frame(height: 2) }
    }

    private func toggle(_ label: String, _ on: Bool, _ flip: @escaping () -> Void) -> some View {
        row(label) { Toggle("", isOn: Binding(get: { on }, set: { _ in Sfx.play(.tap); flip() })).labelsHidden().tint(.sky) }
    }
}

private struct EditProfile: View {
    @Environment(AppStore.self) private var store
    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var avatar = "🦜"

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text(L("Ton profil", "Your profile")).font(.display(26)).foregroundStyle(Color.ink)
                TextField(L("Ton prénom", "Your first name"), text: $name).fieldStyle()
                LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 10), count: 6), spacing: 10) {
                    ForEach(Game.avatars, id: \.self) { a in
                        Button { Sfx.play(.tile, Game.avatars.firstIndex(of: a) ?? 0); avatar = a } label: {
                            Text(a).font(.system(size: 28)).frame(maxWidth: .infinity, minHeight: 50)
                                .background(RoundedRectangle(cornerRadius: 14).fill(avatar == a ? Color.skyTint : .surface))
                                .overlay(RoundedRectangle(cornerRadius: 14).strokeBorder(avatar == a ? Color.sky : .line, lineWidth: 2))
                        }
                        .buttonStyle(.plain)
                    }
                }
                Button(L("Enregistrer", "Save")) {
                    let trimmed = name.trimmingCharacters(in: .whitespaces)
                    store.setProfile(name: trimmed, avatar: avatar)
                    if store.signedIn, !trimmed.isEmpty { Task { try? await store.rename(trimmed) } }
                    dismiss()
                }
                .buttonStyle(.chunky(.green))
            }
            .padding(22)
        }
        .onAppear { name = store.state.name.isEmpty ? (store.learner?.name ?? "") : store.state.name; avatar = store.state.avatar }
    }
}
