import AVFoundation
import UIKit

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
enum Voice {
    private static var player: AVPlayer?

    static func say(_ word: Word) {
        guard AppStore.shared.state.voice, let id = word.audio else { return }
        player = AVPlayer(url: API.audioURL(id))
        player?.play()
    }

    static func stop() { player?.pause() }
}

enum Haptic {
    static func tap() { guard AppStore.shared.state.sound else { return }; UIImpactFeedbackGenerator(style: .light).impactOccurred() }
    static func thud() { guard AppStore.shared.state.sound else { return }; UIImpactFeedbackGenerator(style: .medium).impactOccurred() }
    static func success() { guard AppStore.shared.state.sound else { return }; UINotificationFeedbackGenerator().notificationOccurred(.success) }
    static func error() { guard AppStore.shared.state.sound else { return }; UINotificationFeedbackGenerator().notificationOccurred(.error) }
}
