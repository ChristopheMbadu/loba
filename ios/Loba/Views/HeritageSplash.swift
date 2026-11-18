import SwiftUI
import WebKit

/// An offline launch interlude: Kuba-inspired geometry, the Congo river, and our mascot.
/// The system launch background uses the same named color to avoid a white flash.
struct SplashView: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var appeared = false

    var body: some View {
        GeometryReader { geometry in
            ZStack {
                Color("LaunchBackground").ignoresSafeArea()
                VStack(spacing: 0) {
                    KubaBand(color: .cocoa, background: .clear, height: 22)
                        .opacity(0.35)
                    Spacer()
                    KubaBand(color: .cocoa, background: .clear, height: 22)
                        .opacity(0.35)
                }
                .padding(.vertical, 20)
                .padding(.horizontal, 26)
                .accessibilityHidden(true)

                VStack(spacing: 18) {
                    Spacer(minLength: 28)
                    Text("MBOTE · JAMBO")
                        .font(.system(size: 12, weight: .bold, design: .rounded))
                        .tracking(4).foregroundStyle(Color.cocoa)
                    Image("launch-icon")
                        .resizable().scaledToFit()
                        .frame(width: min(geometry.size.width * 0.57, 240))
                        .clipShape(RoundedRectangle(cornerRadius: 52, style: .continuous))
                        .scaleEffect(appeared ? 1 : 0.94)
                        .accessibilityHidden(true)
                    VStack(spacing: 8) {
                        Text("Loba").font(.display(58)).foregroundStyle(Color.ink)
                        Text(L("Nos langues. Nos histoires.", "Our languages. Our stories."))
                            .font(.text(17, .bold)).foregroundStyle(Color.cocoa)
                            .multilineTextAlignment(.center)
                        Text(L("Un voyage au cœur du Congo", "A journey into the heart of Congo"))
                            .font(.text(13)).foregroundStyle(Color.muted)
                            .multilineTextAlignment(.center)
                    }
                    Spacer(minLength: 12)
                    ZStack {
                        // Remains visible if the animation cannot load.
                        Capsule().fill(Color.sky.opacity(0.12)).frame(width: 170, height: 3).offset(y: 44)
                        HeritageLottie(name: "heritage-pirogue", playing: !reduceMotion)
                            .frame(width: 180, height: 110)
                    }
                    HStack(spacing: 5) {
                        Capsule().fill(Color.sky).frame(width: 28, height: 4)
                        Capsule().fill(Color.sun).frame(width: 16, height: 4)
                        Capsule().fill(Color.coral).frame(width: 16, height: 4)
                    }.accessibilityHidden(true)
                    Text(L("LES LANGUES DU CONGO", "THE LANGUAGES OF CONGO"))
                        .font(.system(size: 10, weight: .semibold)).tracking(2)
                        .foregroundStyle(Color.cocoa)
                        .padding(.bottom, 54)
                }
                .padding(.horizontal, 24)
            }
            .onAppear {
                withAnimation(reduceMotion ? nil : .easeOut(duration: 0.45)) { appeared = true }
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(L("Loba. Chargement. Les langues du Congo.", "Loba. Loading. The languages of Congo."))
    }
}

/// Uses the project's existing Lottie assets and bundled renderer; never loads a CDN.
/// A nonpersistent, noninteractive web view is destroyed when the launch screen disappears.
struct HeritageLottie: UIViewRepresentable {
    let name: String
    var playing: Bool

    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        config.websiteDataStore = .nonPersistent()
        let view = WKWebView(frame: .zero, configuration: config)
        view.isOpaque = false
        view.backgroundColor = .clear
        view.scrollView.backgroundColor = .clear
        view.scrollView.isScrollEnabled = false
        view.isUserInteractionEnabled = false
        view.isAccessibilityElement = false
        guard let scriptURL = Bundle.main.url(forResource: "lottie_light.min", withExtension: "js"),
              let animationURL = Bundle.main.url(forResource: name, withExtension: "json"),
              let script = try? String(contentsOf: scriptURL, encoding: .utf8),
              let animation = try? Data(contentsOf: animationURL) else { return view }
        let encoded = animation.base64EncodedString()
        view.loadHTMLString("""
        <!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
        <meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; img-src data:; connect-src 'none'">
        <style>html,body,#art{margin:0;width:100%;height:100%;overflow:hidden;background:transparent}</style>
        </head><body><div id="art"></div><script>\(script)</script><script>
        window.art=lottie.loadAnimation({container:document.getElementById('art'),renderer:'svg',loop:true,
        autoplay:\(playing ? "true" : "false"),animationData:JSON.parse(new TextDecoder().decode(Uint8Array.from(atob('\(encoded)'),c=>c.charCodeAt(0))))});
        </script></body></html>
        """, baseURL: nil)
        return view
    }

    func updateUIView(_ view: WKWebView, context: Context) {
        view.evaluateJavaScript(playing ? "window.art && window.art.play()" : "window.art && window.art.goToAndStop(0,true)")
    }

    static func dismantleUIView(_ view: WKWebView, coordinator: ()) {
        view.stopLoading()
        view.evaluateJavaScript("window.art && window.art.destroy()")
    }
}
