import AVFoundation
import UIKit
import Observation

/// Loba's sounds: the same likembe, ngoma and shaker cues as the website, recorded from its synthesizer.
enum Sfx {
    enum Cue: String {
        case tap, pop, whoosh, wrong, heart, coin, chest, tick, complete, streak, splash, call, drum, shaker
        /// These change with `n`: the note in a tune, the length of a combo, the page of a story.
        case tile, correct, page
    }

    private static var players: [String: AVAudioPlayer] = [:]
    private static var configured = false
    /// Loading and starting a sound takes 10 to 150 ms: far too long for the main thread, where it would freeze the screen on every tap.
    private static let queue = DispatchQueue(label: "loba.sfx", qos: .userInteractive)

    private static func configure() {
        guard !configured else { return }
        configured = true
        // Game sounds respect the silent switch and mix with the learner's music.
        try? AVAudioSession.sharedInstance().setCategory(.ambient, options: [.mixWithOthers])
        try? AVAudioSession.sharedInstance().setActive(true)
    }

    private static func player(_ name: String) -> AVAudioPlayer? {
        if players[name] == nil, let url = Bundle.main.url(forResource: name, withExtension: "wav") {
            players[name] = try? AVAudioPlayer(contentsOf: url)
            players[name]?.prepareToPlay()
        }
        return players[name]
    }

    /// Loads the sounds heard on almost every screen, so the first tap is not late.
    static func warmUp() {
        queue.async {
            configure()
            for name in ["tap", "pop", "tile0", "tile1", "tile2", "tile3", "tile4", "tile5", "correct1", "whoosh", "tick"] { _ = player(name) }
        }
    }

    static func play(_ cue: Cue, _ n: Int = 1) {
        guard AppStore.shared.state.sound else { return }
        let name: String
        switch cue {
        case .tile: name = "tile\(((n % 6) + 6) % 6)"
        case .correct: name = "correct\(max(1, min(5, n)))"
        case .page: name = "page\(((n % 8) + 8) % 8)"
        default: name = cue.rawValue
        }
        queue.async {
            configure()
            guard let player = player(name) else { return }
            player.currentTime = 0
            player.play()
        }
    }
}

/// A teacher's recording of a word.
@MainActor
enum Voice {
    private static var completion: NSObjectProtocol?
    private static var statusObservation: NSKeyValueObservation?
    private static var player: AVPlayer?

    static func say(_ word: Word, automatic: Bool = false) {
        guard (!automatic || AppStore.shared.state.voice), let id = word.audio else { return }
        stop()
        BackgroundMusic.shared.voice(true)
        let item = AVPlayerItem(url: API.audioURL(id))
        player = AVPlayer(playerItem: item)
        statusObservation = item.observe(\.status, options: [.new]) { item, _ in
            if item.status == .failed { Task { @MainActor in BackgroundMusic.shared.voice(false) } }
        }
        completion = NotificationCenter.default.addObserver(forName: .AVPlayerItemDidPlayToEndTime, object: item, queue: .main) { _ in
            Task { @MainActor in BackgroundMusic.shared.voice(false) }
        }
        player?.play()
    }

    static func stop() { statusObservation?.invalidate(); statusObservation = nil; player?.pause(); player = nil; if let completion { NotificationCenter.default.removeObserver(completion) }; completion = nil; BackgroundMusic.shared.voice(false) }
}

enum Haptic {
    static func tap() { guard AppStore.shared.state.sound else { return }; UIImpactFeedbackGenerator(style: .light).impactOccurred() }
    static func thud() { guard AppStore.shared.state.sound else { return }; UIImpactFeedbackGenerator(style: .medium).impactOccurred() }
    static func success() { guard AppStore.shared.state.sound else { return }; UINotificationFeedbackGenerator().notificationOccurred(.success) }
    static func error() { guard AppStore.shared.state.sound else { return }; UINotificationFeedbackGenerator().notificationOccurred(.error) }
}

/// A shuffled Studio playlist, scoped to browsing and paused whenever a teacher speaks.
@MainActor @Observable
final class BackgroundMusic {
    static let shared = BackgroundMusic()
    var enabled = UserDefaults.standard.object(forKey: "loba-background-music") as? Bool ?? true
    private var player: AVPlayer?
    private var tracks: [String] = []
    private var remaining: [String] = []
    private var current = ""
    private var browsing = false
    private var front = true
    private var speaking = false
    private var librarySessions = 0
    private var interrupted = false
    private var lastFetch = Date.distantPast
    private var observers: [NSObjectProtocol] = []
    private var completion: NSObjectProtocol?

    private init() {
        observers.append(NotificationCenter.default.addObserver(forName: AVAudioSession.interruptionNotification, object: nil, queue: .main) { [weak self] note in
            let began = (note.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt) == AVAudioSession.InterruptionType.began.rawValue
            Task { @MainActor in self?.interrupted = began; self?.sync() }
        })
        observers.append(NotificationCenter.default.addObserver(forName: AVAudioSession.routeChangeNotification, object: nil, queue: .main) { [weak self] note in
            if (note.userInfo?[AVAudioSessionRouteChangeReasonKey] as? UInt) == AVAudioSession.RouteChangeReason.oldDeviceUnavailable.rawValue {
                Task { @MainActor in self?.interrupted = true; self?.sync() }
            }
        })
    }
    func toggle() {
        enabled.toggle(); interrupted = false
        UserDefaults.standard.set(enabled, forKey: "loba-background-music")
        sync()
    }
    func browse(_ value: Bool) { browsing = value; if value { Task { await refresh() } }; sync() }
    func foreground(_ value: Bool) { front = value; if value { Task { await refresh() } }; sync() }
    func library(_ value: Bool) { librarySessions = max(0,librarySessions + (value ? 1 : -1)); sync() }
    func voice(_ value: Bool) { speaking = value; sync() }
    private var wanted: Bool { enabled && browsing && front && librarySessions == 0 && !speaking && !interrupted && !AVAudioSession.sharedInstance().secondaryAudioShouldBeSilencedHint }
    private func refresh() async {
        guard Date().timeIntervalSince(lastFetch) > 60 else { return }
        lastFetch = Date()
        do {
            var request = URLRequest(url: API.url("/api/v1/music")); request.timeoutInterval = 12
            let (data,response) = try await URLSession.shared.data(for: request)
            guard (response as? HTTPURLResponse)?.statusCode == 200,
                  let json = try JSONSerialization.jsonObject(with: data) as? [String:Any], let rows = json["tracks"] as? [[String:Any]] else { return }
            tracks = rows.compactMap { row in guard let id = row["id"] as? String, UUID(uuidString:id) != nil else { return nil }; return id }
            remaining = []
            if !tracks.contains(current) { player?.pause(); player = nil; current = "" }
            sync()
        } catch { /* Music must never block learning when offline. */ }
    }
    private func sync() {
        guard wanted else { player?.pause(); return }
        if player == nil { next() } else { player?.play() }
    }
    private func next() {
        guard wanted, !tracks.isEmpty else { return }
        if remaining.isEmpty {
            remaining = tracks.shuffled()
            if remaining.count > 1 && remaining.first == current { remaining.append(remaining.removeFirst()) }
        }
        current = remaining.removeFirst()
        if let completion { NotificationCenter.default.removeObserver(completion) }
        let item = AVPlayerItem(url: API.url("/api/v1/music/" + current))
        player = AVPlayer(playerItem:item); player?.volume = 0.16
        completion = NotificationCenter.default.addObserver(forName: .AVPlayerItemDidPlayToEndTime, object:item, queue:.main) { [weak self] _ in
            Task { @MainActor in self?.next() }
        }
        player?.play()
    }
}
