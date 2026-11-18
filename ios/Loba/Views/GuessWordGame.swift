import SwiftUI
import CoreMotion

struct GuessWordGame: View {
    let words: [Word]
    let ui: UiLang
    @Environment(\.dismiss) private var dismiss
    @Environment(\.scenePhase) private var scenePhase
    @State private var deck: [Word] = []
    @State private var index = 0
    @State private var missed: [Word] = []
    @State private var phase = Phase.intro
    private enum Phase { case intro, positioning, countdown, playing, recap }

    @State private var rotation = 90.0
    @State private var seconds = 60
    @State private var count = 3
    @State private var feedback: Bool?
    @State private var scored = 0
    @State private var useButtons = false

    private var horizontal: Bool { phase == .countdown || phase == .playing }

    var body: some View {
        GeometryReader { geo in
            let turn = horizontal && geo.size.height > geo.size.width
            content
                .frame(width: turn ? geo.size.height : geo.size.width,
                       height: turn ? geo.size.width : geo.size.height)
                .rotationEffect(.degrees(turn ? rotation : 0))
                .position(x: geo.size.width / 2, y: geo.size.height / 2)
        }
        .background(backdrop.ignoresSafeArea())
    }

    private var backdrop: Color {
        if let feedback { return feedback ? Color(hex: "087844") : Color(hex: "BA3044") }
        return horizontal ? Color(hex: "143957") : Color.skyTint
    }

    private var content: some View {
        VStack(spacing: 16) {
            HStack {
                Button { dismiss() } label: { Image(systemName: "xmark").padding(12) }
                    .accessibilityLabel(L("Fermer", "Close"))
                Spacer()
                if phase == .playing { HStack(spacing: 16) {
                    Label("\(seconds)s", systemImage: "timer").foregroundStyle(seconds <= 5 ? Color.sun : .white)
                    Label("\(scored)", systemImage: "star.fill").foregroundStyle(Color.sun)
                }.monospacedDigit() }
            }
            Spacer()
            switch phase {
            case .intro:
                LobaView(mood: .wave, size: 88)
                KubaBand(height: 8).frame(maxWidth: 170).clipShape(Capsule())
                Text(L("Devine le mot", "Guess the word")).font(.display(32))
                Text(L("Tiens le téléphone contre ton front, écran vers ton partenaire. Ton partenaire décrit le mot sans le dire. Devine-le dans la langue que tu apprends.", "Hold the phone to your forehead with the screen facing your partner. They describe the word without saying it. Guess it aloud in the language you’re learning."))
                Text(L("Incline l’écran vers le sol si tu trouves, vers le ciel pour passer. Tu peux aussi toucher les boutons.", "Tilt the screen toward the floor for correct or toward the ceiling to pass. You can also tap the buttons."))
                Button(L("Jouer", "Play")) { start(words) }.buttonStyle(.chunky(.green))
            case .positioning:
                Image(systemName: "rotate.right").font(.largeTitle)
                Text(L("Tourne ton téléphone à l’horizontale, écran vers ton partenaire.", "Turn the phone sideways with the screen facing your partner."))
                Button(L("Utiliser les boutons", "Use buttons")) {
                    useButtons = true
                    phase = .countdown
                }
            case .countdown:
                Text(L("Prêt ?", "Ready?")).font(.display(24))
                Text(String(count)).font(.display(64)).monospacedDigit()
            case .playing:
                let word = deck[index]
                if let feedback {
                    Image(systemName: feedback ? "checkmark.circle.fill" : "xmark.circle.fill")
                        .font(.system(size: 54, weight: .bold))
                    Text(feedback ? L("Trouvé !", "Got it!") : L("Raté !", "Missed!"))
                        .font(.display(46))
                    Text(word.term).font(.text(20, .heavy))
                } else {
                    VStack(spacing: 8) {
                        KubaBand(height: 6).frame(width: 84).clipShape(Capsule())
                        Text(word.term).font(.display(58)).minimumScaleFactor(0.45).lineLimit(2)
                        Text(word.meaning(ui)).font(.text(20, .bold)).foregroundStyle(Color.ink2)
                    }
                    .padding(12).frame(maxWidth: .infinity)
                    .foregroundStyle(Color.ink)
                    .background(RoundedRectangle(cornerRadius: 24).fill(.white))
                    if useButtons {
                        HStack {
                            Button(L("Passer", "Pass")) { answer(false) }.buttonStyle(.chunky(.outline))
                            Button(L("Trouvé !", "Got it!")) { answer(true) }.buttonStyle(.chunky(.green))
                        }
                    }
                }
            case .recap:
                Image(systemName: "trophy.fill").font(.largeTitle).foregroundStyle(Color.sunShade)
                Text(L("Manche terminée", "Round complete")).font(.display(30))
                Text(L("Mots trouvés", "Words guessed") + " · \(scored) / \(scored + missed.count)")
                if !missed.isEmpty {
                    ScrollView {
                        ForEach(missed, id: \.term) { word in
                            VStack(alignment: .leading) {
                                Text(word.term).bold()
                                Text(word.meaning(ui))
                                if !word.example.isEmpty { Text(word.example).italic() }
                            }.frame(maxWidth: .infinity, alignment: .leading).padding(12)
                        }
                    }
                    Button(L("Rejouer les mots passés", "Retry passed words")) { start(missed) }.buttonStyle(.chunky(.green))
                }
                Button(L("Nouvelle manche", "New round")) { start(words) }.buttonStyle(.chunky(.outline))
            }
            Spacer()
        }
        .font(.text(18)).foregroundStyle(horizontal ? Color.white : Color.ink).multilineTextAlignment(.center)
        .padding(20).frame(maxWidth: .infinity).background(backdrop.ignoresSafeArea())
        .task(id: scenePhase == .active ? feedback : nil) {
            guard feedback != nil, scenePhase == .active else { return }
            do { try await Task.sleep(for: .milliseconds(900)) } catch { return }
            feedback = nil
            if seconds == 0 || index + 1 == deck.count {
                Sfx.play(.complete)
                phase = .recap
            } else {
                index += 1
            }
        }
        .task(id: scenePhase == .active && (phase == .positioning || horizontal)) {
            guard scenePhase == .active, phase == .positioning || horizontal else { return }
            let motion = CMMotionManager()
            motion.deviceMotionUpdateInterval = 0.04
            if motion.isDeviceMotionAvailable { motion.startDeviceMotionUpdates() }
            defer { motion.stopDeviceMotionUpdates() }
            var armed = false
            while !Task.isCancelled {
                if let g = motion.deviceMotion?.gravity {
                    let upright = abs(g.x) > 0.82 && abs(g.z) < 0.32
                    if phase == .positioning && upright {
                        rotation = g.x < 0 ? 90 : -90
                        phase = .countdown
                    }
                    if phase == .playing && !useButtons {
                        if upright { armed = true }
                        if armed && abs(g.z) > 0.72 {
                            armed = false
                            answer(g.z > 0)
                        }
                    }
                }
                do { try await Task.sleep(for: .milliseconds(40)) } catch { return }
            }
        }
        .task(id: scenePhase == .active && horizontal) {
            guard scenePhase == .active, horizontal else { return }
            while phase == .countdown {
                Sfx.play(.tick)
                do { try await Task.sleep(for: .seconds(1)) } catch { return }
                if count > 1 { count -= 1 } else { Sfx.play(.pop); phase = .playing }
            }
            while phase == .playing {
                do { try await Task.sleep(for: .seconds(1)) } catch { return }
                guard phase == .playing else { return }
                seconds -= 1
                if seconds == 0 {
                    if feedback == nil { Sfx.play(.complete); phase = .recap }
                    return
                }
                else if seconds <= 5 { Sfx.play(.tick) }
            }
        }
    }

    private func start(_ pool: [Word]) {
        var seen = Set<String>()
        deck = Array(pool.shuffled().filter { !$0.term.isEmpty && seen.insert($0.term.lowercased()).inserted }.prefix(8))
        guard !deck.isEmpty else { return }
        missed = []
        index = 0
        scored = 0
        count = 3
        seconds = 60
        useButtons = false
        phase = .positioning
    }

    private func answer(_ correct: Bool) {
        guard phase == .playing, feedback == nil, seconds > 0 else { return }
        feedback = correct
        if correct {
            scored += 1
            Sfx.play(.correct)
            Haptic.success()
        } else {
            missed.append(deck[index])
            Sfx.play(.tick)
            Haptic.thud()
        }

    }
}
