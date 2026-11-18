import AVFoundation
import Observation
import Foundation

struct LibrarySong: Decodable, Identifiable, Equatable {
    let id: String
    let title: String
    var artwork: Int { id.utf8.reduce(0) { ($0 + Int($1)) % 6 } }
}

/// Foreground listening is explicit, independent from the ambient-music preference.
@MainActor @Observable
final class SongLibrary {
    var songs: [LibrarySong] = []
    var selected: LibrarySong?
    var playing = false
    var buffering = false
    var loading = true
    var error = false
    var elapsed: Double = 0
    var duration: Double = 0
    var repeatSong = false
    var favorites = Set(UserDefaults.standard.stringArray(forKey: "loba-favorite-songs") ?? [])
    private var player = AVPlayer()
    private var tick: Any?
    private var end: NSObjectProtocol?
    private var failed: NSObjectProtocol?
    private var interruptions: [NSObjectProtocol] = []
    private var status: NSKeyValueObservation?
    private var playback: NSKeyValueObservation?
    private var opened = false
    private var generation = 0
    private static let audioQueue = DispatchQueue(label:"loba.song-library-session")

    func open() async {
        guard !opened else { return }
        opened = true
        Voice.stop()
        BackgroundMusic.shared.library(true)
        tick = player.addPeriodicTimeObserver(forInterval: CMTime(seconds: 0.25, preferredTimescale: 600), queue: .main) { [weak self] time in
            Task { @MainActor in
                guard let self else { return }
                self.elapsed = time.seconds.isFinite ? max(0,time.seconds) : 0
                let total = self.player.currentItem?.duration.seconds ?? 0
                self.duration = total.isFinite ? max(0,total) : 0
            }
        }
        playback = player.observe(\.timeControlStatus, options: [.new]) { [weak self] player, _ in
            let waiting = player.timeControlStatus == .waitingToPlayAtSpecifiedRate
            Task { @MainActor in self?.buffering = waiting }
        }
        for name in [AVAudioSession.interruptionNotification, AVAudioSession.routeChangeNotification] {
            interruptions.append(NotificationCenter.default.addObserver(forName:name,object:nil,queue:.main) { [weak self] note in
                let pause = note.name == AVAudioSession.interruptionNotification
                    ? (note.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt) == AVAudioSession.InterruptionType.began.rawValue
                    : (note.userInfo?[AVAudioSessionRouteChangeReasonKey] as? UInt) == AVAudioSession.RouteChangeReason.oldDeviceUnavailable.rawValue
                if pause { Task { @MainActor in self?.pause() } }
            })
        }
        await load()
    }
    func load() async {
        loading = true; error = false
        defer { loading = false }
        do {
            var request = URLRequest(url: API.url("/api/v1/music")); request.timeoutInterval = 15
            let (data,response) = try await URLSession.shared.data(for:request)
            guard (response as? HTTPURLResponse)?.statusCode == 200 else { throw URLError(.badServerResponse) }
            struct Catalog: Decodable { let tracks: [LibrarySong] }
            let result = try JSONDecoder().decode(Catalog.self,from:data).tracks.filter { UUID(uuidString:$0.id) != nil }
            guard opened, !Task.isCancelled else { return }
            songs = result
            if let selected, !songs.contains(where: { $0.id == selected.id }) { pause(); self.selected = nil; player.replaceCurrentItem(with:nil) }
        } catch { if opened { self.error = true } }
    }
    func favorite(_ song: LibrarySong) {
        if favorites.contains(song.id) { favorites.remove(song.id) } else { favorites.insert(song.id) }
        UserDefaults.standard.set(Array(favorites),forKey:"loba-favorite-songs")
    }
    func play(_ song: LibrarySong) {
        guard opened else { return }
        generation += 1
        let token = generation
        error = false; selected = song; elapsed = 0; duration = 0
        clearItemObservers()
        player.pause()
        let item = AVPlayerItem(url:API.url("/api/v1/music/" + song.id))
        status = item.observe(\.status, options:[.new]) { [weak self] item, _ in
            if item.status == .failed { Task { @MainActor in guard let self, self.generation == token else { return }; self.pause(); self.error = true } }
        }
        end = NotificationCenter.default.addObserver(forName:.AVPlayerItemDidPlayToEndTime,object:item,queue:.main) { [weak self] _ in
            Task { @MainActor in
                guard let self, self.opened, self.generation == token else { return }
                if self.repeatSong { self.player.seek(to:.zero); self.player.play() } else { self.next() }
            }
        }
        failed = NotificationCenter.default.addObserver(forName:.AVPlayerItemFailedToPlayToEndTime,object:item,queue:.main) { [weak self] _ in
            Task { @MainActor in guard let self, self.generation == token else { return }; self.pause(); self.error = true }
        }
        player.replaceCurrentItem(with:item); player.volume = 0.7
        playing = true; buffering = true
        activateAndPlay()
    }
    private func activateAndPlay() {
        let token = generation
        Self.audioQueue.async {
            do {
                try AVAudioSession.sharedInstance().setCategory(.playback)
                try AVAudioSession.sharedInstance().setActive(true)
                Task { @MainActor [weak self] in
                    guard let self, self.opened, self.playing, self.generation == token else { return }
                    self.player.play()
                }
            } catch { Task { @MainActor [weak self] in guard let self, self.opened, self.generation == token else { return }; self.pause(); self.error = true } }
        }
    }
    func toggle() {
        if playing { pause() }
        else if let selected { if error || player.currentItem == nil { play(selected) } else { playing = true; activateAndPlay() } }
        else if let first = songs.first { play(first) }
    }
    func pause() { playing = false; buffering = false; player.pause() }
    func seek(_ seconds: Double) { guard duration > 0 else { return }; elapsed = min(duration,max(0,seconds)); player.seek(to:CMTime(seconds:elapsed,preferredTimescale:600)) }
    func next(_ direction: Int = 1) {
        guard !songs.isEmpty else { return }
        let current = songs.firstIndex(where: { $0.id == selected?.id }) ?? (direction > 0 ? -1 : 0)
        play(songs[(current + direction + songs.count) % songs.count])
    }
    func surprise() {
        let pool = songs.count > 1 ? songs.filter { $0.id != selected?.id } : songs
        if let song = pool.randomElement() { play(song) }
    }
    private func clearItemObservers() {
        status?.invalidate(); status = nil
        for observer in [end, failed].compactMap({$0}) { NotificationCenter.default.removeObserver(observer) }
        end = nil; failed = nil
    }
    func close() {
        guard opened else { return }
        opened = false; generation += 1; pause(); clearItemObservers()
        if let tick { player.removeTimeObserver(tick) }; tick = nil
        playback?.invalidate(); playback = nil
        interruptions.forEach { NotificationCenter.default.removeObserver($0) }; interruptions = []
        player.replaceCurrentItem(with:nil)
        Self.audioQueue.async {
            try? AVAudioSession.sharedInstance().setActive(false, options:.notifyOthersOnDeactivation)
            try? AVAudioSession.sharedInstance().setCategory(.ambient, options:[.mixWithOthers])
            Task { @MainActor in BackgroundMusic.shared.library(false) }
        }
    }
    static func clock(_ seconds: Double) -> String {
        let value = seconds.isFinite ? max(0,Int(seconds)) : 0
        return String(format:"%d:%02d",value/60,value%60)
    }
}
