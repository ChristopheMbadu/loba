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
    private enum Phase { case intro, playing, recap }

    var body: some View {
        VStack(spacing: 16) {
            HStack {
                Button { dismiss() } label: { Image(systemName: "xmark").padding(12) }
                    .accessibilityLabel(L("Fermer", "Close"))
                Spacer()
                if phase == .playing { Text("\(index + 1) / \(deck.count)").monospacedDigit() }
            }
            Spacer()
            switch phase {
            case .intro:
                Text(L("Devine le mot", "Guess the word")).font(.display(32))
                Text(L("Tiens le téléphone contre ton front, écran vers ton partenaire. Ton partenaire décrit le mot sans le dire. Devine-le dans la langue que tu apprends.", "Hold the phone to your forehead with the screen facing your partner. They describe the word without saying it. Guess it aloud in the language you’re learning."))
                Text(L("Incline l’écran vers le sol si tu trouves, vers le ciel pour passer. Tu peux aussi toucher les boutons.", "Tilt the screen toward the floor for correct or toward the ceiling to pass. You can also tap the buttons."))
                Button(L("Jouer", "Play")) { start(words) }.buttonStyle(.chunky(.green))
            case .playing:
                let word = deck[index]
                Text(word.term).font(.display(50)).minimumScaleFactor(0.45).lineLimit(3)
                Text(word.meaning(ui)).font(.text(21, .bold)).foregroundStyle(Color.ink2)
                Spacer()
                HStack {
                    Button(L("Passer", "Pass")) { answer(false) }.buttonStyle(.chunky(.outline))
                    Button(L("Trouvé !", "Got it!")) { answer(true) }.buttonStyle(.chunky(.green))
                }
            case .recap:
                Text(L("Manche terminée", "Round complete")).font(.display(30))
                Text(L("Mots trouvés", "Words guessed") + " · \(deck.count - missed.count) / \(deck.count)")
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
        .font(.text(18)).foregroundStyle(Color.ink).multilineTextAlignment(.center)
        .padding(20).frame(maxWidth: .infinity).background(Color.skyTint.ignoresSafeArea())
        .task(id: phase == .playing && scenePhase == .active) {
            guard phase == .playing, scenePhase == .active else { return }
            let motion = CMMotionManager()
            guard motion.isAccelerometerAvailable else { return }
            motion.accelerometerUpdateInterval = 0.08
            motion.startAccelerometerUpdates()
            defer { motion.stopAccelerometerUpdates() }
            var armed = false
            while !Task.isCancelled {
                if let z = motion.accelerometerData?.acceleration.z {
                    if abs(z) < 0.3 { armed = true }
                    if armed && abs(z) > 0.78 {
                        armed = false
                        answer(z > 0)
                    }
                }
                do { try await Task.sleep(for: .milliseconds(80)) } catch { return }
            }
        }
    }

    private func start(_ pool: [Word]) {
        var seen = Set<String>()
        deck = Array(pool.shuffled().filter { !$0.term.isEmpty && seen.insert($0.term.lowercased()).inserted }.prefix(8))
        guard !deck.isEmpty else { return }
        missed = []
        index = 0
        phase = .playing
    }

    private func answer(_ correct: Bool) {
        guard phase == .playing else { return }
        if !correct { missed.append(deck[index]) }
        if index + 1 == deck.count { phase = .recap } else { index += 1 }
    }
}
