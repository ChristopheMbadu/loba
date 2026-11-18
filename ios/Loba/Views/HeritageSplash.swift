import SwiftUI
import Lottie

/// The user's laughing owl animation is the focus of the launch screen.
struct SplashView: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        GeometryReader { geometry in
            ZStack {
                Color("LaunchBackground").ignoresSafeArea()
                Image("RaffiaSplash")
                    .resizable()
                    .ignoresSafeArea()
                    .accessibilityHidden(true)
                VStack(spacing: 8) {
                    HeritageLottie(name: "owl-laughing", playing: !reduceMotion)
                        .frame(width: min(geometry.size.width * 0.78, 340),
                               height: min(geometry.size.width * 0.78, 340))
                    Text("Loba").font(.display(44)).foregroundStyle(Color.ink)
                    Text(L("Les langues du Congo", "The languages of Congo"))
                        .font(.text(15, .bold)).foregroundStyle(Color.muted)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .padding(.horizontal, 24)
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(L("Loba. Chargement. Les langues du Congo.", "Loba. Loading. The languages of Congo."))
    }
}

/// Renders the original Lottie vectors natively, without a browser process.
struct HeritageLottie: UIViewRepresentable {
    let name: String
    var playing: Bool

    func makeUIView(context: Context) -> UIView {
        let container = UIView()
        let animation = LottieAnimationView(name: name)
        animation.contentMode = .scaleAspectFit
        animation.loopMode = .loop
        animation.backgroundBehavior = .pauseAndRestore
        animation.translatesAutoresizingMaskIntoConstraints = false
        container.addSubview(animation)
        NSLayoutConstraint.activate([
            animation.leadingAnchor.constraint(equalTo: container.leadingAnchor),
            animation.trailingAnchor.constraint(equalTo: container.trailingAnchor),
            animation.topAnchor.constraint(equalTo: container.topAnchor),
            animation.bottomAnchor.constraint(equalTo: container.bottomAnchor)
        ])
        if playing { animation.play() }
        return container
    }

    func updateUIView(_ view: UIView, context: Context) {
        guard let animation = view.subviews.first as? LottieAnimationView else { return }
        if playing {
            if !animation.isAnimationPlaying { animation.play() }
        } else {
            animation.stop()
        }
    }

    static func dismantleUIView(_ view: UIView, coordinator: ()) {
        (view.subviews.first as? LottieAnimationView)?.stop()
    }
}
