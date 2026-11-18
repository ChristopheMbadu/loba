import Foundation
import Observation
import UserNotifications

/// Everything the screens read: the learner's progress, the lessons, and who is signed in.
@Observable
final class AppStore {
    static let shared = AppStore()

    private(set) var state: GameState
    private(set) var catalog: Catalog
    private(set) var learner: Learner?
    /// False until the first answer from the account service (or its failure).
    private(set) var ready = false
    /// Learning on this phone only, without an account.
    private(set) var guest: Bool
    var googleAvailable = false

    private let defaults = UserDefaults.standard
    private var unsynced: Bool {
        get { defaults.bool(forKey: "loba-unsynced") }
        set { defaults.set(newValue, forKey: "loba-unsynced") }
    }
    private var pushTask: Task<Void, Never>?
    private var applying = false

    private init() {
        let saved = defaults.data(forKey: "loba-state").flatMap { try? JSONSerialization.jsonObject(with: $0) }
        var initial = GameState(json: saved) ?? GameState()
        if saved == nil, Locale.current.language.languageCode?.identifier != "fr" { initial.ui = .en }
        state = initial
        catalog = CatalogParser.bundled()
        guest = defaults.bool(forKey: "loba-guest")
        tick()
    }

    var units: [Unit] { catalog.units(state.lang) }
    var language: Language? { catalog.languages.first { $0.code == state.lang } }
    var signedIn: Bool { learner != nil }
    /// May use the learning screens: signed in, or chose to learn on this phone.
    var admitted: Bool { signedIn || guest }
    var today: String { Game.dayKey() }

    // MARK: launch

    func start() async {
        async let fresh = API.catalog()
        async let google = API.googleForApp()
        do {
            if let session = try await API.me() {
                learner = session.learner
                if unsynced && state.onboarded { await push() } else { adopt(session.progress) }
            }
        } catch {
            // Offline: keep going with what is on the phone.
        }
        if let fresh = await fresh { catalog = fresh }
        googleAvailable = await google
        fixLanguage()
        ready = true
    }

    /// If the learner's language was hidden in Studio, continue with one that is available.
    private func fixLanguage() {
        if catalog.curriculum[state.lang] == nil, let first = catalog.languages.first { update { $0.lang = first.code } }
    }

    // MARK: state changes. All of them go through `update`, which saves and syncs.

    private func update(_ change: (inout GameState) -> Void) {
        var next = state
        change(&next)
        guard next != state else { return }
        state = next
        if let data = try? JSONSerialization.data(withJSONObject: next.json) { defaults.set(data, forKey: "loba-state") }
        guard !applying else { return }
        unsynced = true
        guard signedIn else { return }
        pushTask?.cancel()
        pushTask = Task { [weak self] in
            try? await Task.sleep(for: .seconds(2))
            if !Task.isCancelled { await self?.push() }
        }
    }

    private func replace(_ make: (GameState) -> GameState) { update { $0 = make($0) } }

    /// Daily rollover and heart regeneration, whenever the learner comes back.
    func tick() { replace { Rules.rollDaily(Rules.syncHearts($0, Game.now()), Game.dayKey()) } }

    func finishOnboarding(lang: String, goal: Int) { update { $0.lang = lang; $0.goal = goal; $0.onboarded = true } }
    func setUi(_ ui: UiLang) { update { $0.ui = ui } }
    func setLang(_ lang: String) { update { $0.lang = lang } }
    func setGoal(_ goal: Int) { update { $0.goal = goal } }
    func setProfile(name: String, avatar: String) { update { $0.name = String(name.prefix(20)); $0.avatar = avatar } }
    func toggleSound() { update { $0.sound.toggle() } }
    func toggleVoice() { update { $0.voice.toggle() } }
    func loseHeart() { replace { Rules.loseHeart($0, Game.now()) } }
    func recordWord(_ term: String, correct: Bool) { replace { Rules.recordWord($0, $0.lang, term, correct, Game.now()) } }
    func claim(_ quest: Rules.QuestId) { replace { Rules.claimQuest($0, quest, Game.dayKey()) } }
    func buy(_ item: Rules.ShopItem) { replace { Rules.buy($0, item, Game.now()) } }
    func openChest(_ unit: Unit) { replace { Rules.openChest($0, unit) } }

    func complete(_ result: Rules.SessionResult) -> Rules.Reward {
        let (next, reward) = Rules.completeSession(state, result, Game.now(), Game.dayKey())
        update { $0 = next }
        return reward
    }

    func eraseProgress() {
        let ui = state.ui
        update { $0 = GameState(); $0.ui = ui }
        PuzzleScores.clear()
    }

    // MARK: account

    /// Replaces this phone's progress with the account's (signing in on a new device).
    private func adopt(_ progress: Any?) {
        guard let saved = GameState(json: progress) else { return }
        applying = true
        update { $0 = saved }
        applying = false
        unsynced = false
        tick()
    }

    private func push() async {
        guard signedIn, unsynced else { return }
        do {
            try await API.saveProgress(state.json)
            unsynced = false
        } catch {
            // Offline: the flag stays set and the next change or launch retries.
        }
    }

    /// Called when the app goes to the background: save now, don't wait for the timer.
    func flush() {
        pushTask?.cancel()
        Task { await push() }
    }

    func signedUp(_ session: API.Session) {
        learner = session.learner
        if session.progress == nil { unsynced = true; Task { await push() } } else { adopt(session.progress) }
    }

    /// After signing in, the account's progress wins unless it has none yet.
    func signedInWith(_ session: API.Session) {
        learner = session.learner
        if GameState(json: session.progress) != nil { adopt(session.progress) } else { unsynced = true; Task { await push() } }
        fixLanguage()
    }

    func continueAsGuest() {
        guest = true
        defaults.set(true, forKey: "loba-guest")
    }

    func signOut() async {
        await push()
        try? await API.logout()
        forget()
    }

    func deleteAccount(password: String) async throws {
        try await API.deleteAccount(password: password)
        forget()
    }

    func rename(_ name: String) async throws {
        try await API.rename(name)
        learner?.name = name
    }

    /// Forgets everything stored on this phone (signing out on a shared phone).
    private func forget() {
        pushTask?.cancel()
        learner = nil
        guest = false
        defaults.set(false, forKey: "loba-guest")
        applying = true
        eraseProgress()
        applying = false
        unsynced = false
    }
}

/// The current interface language, for strings written inline: `L("Bonjour", "Hello")`.
func L(_ fr: String, _ en: String) -> String { AppStore.shared.state.ui == .fr ? fr : en }

// MARK: - Evening reminder

enum Reminder {
    private static let key = "loba-reminder"
    private static let id = "loba-daily"

    static var isOn: Bool { UserDefaults.standard.string(forKey: key) == "on" }

    /// Turns the 7 pm reminder on or off. Returns whether it is on afterwards.
    @discardableResult
    static func set(_ on: Bool) async -> Bool {
        let center = UNUserNotificationCenter.current()
        center.removePendingNotificationRequests(withIdentifiers: [id])
        var on = on
        if on { on = (try? await center.requestAuthorization(options: [.alert, .sound])) ?? false }
        if on {
            let content = UNMutableNotificationContent()
            content.title = L("Loba t’attend !", "Loba is waiting!")
            content.body = L("Cinq minutes suffisent pour garder ta série.", "Five minutes is enough to keep your streak.")
            content.sound = .default
            let trigger = UNCalendarNotificationTrigger(dateMatching: DateComponents(hour: 19, minute: 0), repeats: true)
            try? await center.add(UNNotificationRequest(identifier: id, content: content, trigger: trigger))
        }
        UserDefaults.standard.set(on ? "on" : "off", forKey: key)
        return on
    }

    /// After a first finished lesson, offer the reminder once; later, keep its wording in the learner's language.
    static func afterLesson() {
        let choice = UserDefaults.standard.string(forKey: key)
        if choice == nil || choice == "on" { Task { await set(true) } }
    }
}
