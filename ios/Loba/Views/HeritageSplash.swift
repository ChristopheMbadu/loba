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
                    .mask {
                        LinearGradient(stops: [
                            .init(color: .clear, location: 0.14),
                            .init(color: .white, location: 0.22)
                        ], startPoint: .top, endPoint: .bottom)
                    }
                    .ignoresSafeArea()
                    .accessibilityHidden(true)
                VStack(spacing: 8) {
                    HeritageLottie(name: "owl-laughing", playing: !reduceMotion)
                        .frame(width: min(geometry.size.width * 0.78, 340),
                               height: min(geometry.size.width * 0.78, 340))
                    Text("Loba").font(.display(44)).foregroundStyle(Color.ink)
                    Text(L("Les langues du Congo", "The languages of Congo"))
                        .font(.text(15, .bold)).foregroundStyle(Color.muted)
                    CongoFlagBadge()
                        .frame(width: 40, height: 30)
                        .padding(.top, 6)
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

/// A jersey-style flag patch, drawn in a fixed 4:3 coordinate space.
private struct CongoFlagBadge: View {
    var body: some View {
        Canvas { context, size in
            context.scaleBy(x: size.width / 40, y: size.height / 30)
            let field = CGRect(x: 0, y: 0, width: 40, height: 30)
            context.fill(Path(field), with: .color(Color(red: 0, green: 0.50, blue: 1)))
            let gold = Color(red: 1, green: 0.84, blue: 0)
            var stripe = Path()
            stripe.move(to: CGPoint(x: -8, y: 36))
            stripe.addLine(to: CGPoint(x: 48, y: -6))
            context.stroke(stripe, with: .color(gold), lineWidth: 9)
            context.stroke(stripe, with: .color(Color(red: 0.81, green: 0.13, blue: 0.17)), lineWidth: 6)
            var star = Path()
            for i in 0..<10 {
                let angle = Double(i) * .pi / 5 - .pi / 2
                let radius = i.isMultiple(of: 2) ? 6.0 : 2.3
                let point = CGPoint(x: 7 + cos(angle) * radius, y: 7 + sin(angle) * radius)
                if i == 0 { star.move(to: point) } else { star.addLine(to: point) }
            }
            star.closeSubpath()
            context.fill(star, with: .color(gold))
        }
        .clipShape(RoundedRectangle(cornerRadius: 2))
        .accessibilityHidden(true)
    }
}
