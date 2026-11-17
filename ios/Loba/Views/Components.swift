import CoreText
import SwiftUI

// MARK: - Palette and type, matching the website

extension Color {
    init(hex: String) {
        let v = UInt64(hex.trimmingCharacters(in: CharacterSet(charactersIn: "#")), radix: 16) ?? 0
        self.init(red: Double((v >> 16) & 255) / 255, green: Double((v >> 8) & 255) / 255, blue: Double(v & 255) / 255)
    }
    static let sky = Color(hex: "1c9bef"), skyShade = Color(hex: "1479bf"), skyTint = Color(hex: "e1f1fd")
    static let leaf = Color(hex: "2bb673"), leafShade = Color(hex: "1f9259"), leafTint = Color(hex: "dcf5e8")
    static let sun = Color(hex: "ffc22e"), sunShade = Color(hex: "e09f00"), sunTint = Color(hex: "fff4d6")
    static let coral = Color(hex: "f0524f"), coralShade = Color(hex: "c93a3c"), coralTint = Color(hex: "ffe3e1")
    static let plum = Color(hex: "8b5cf6"), plumShade = Color(hex: "6d3fe0")
    static let ink = Color(hex: "2a2540"), ink2 = Color(hex: "5b5670"), muted = Color(hex: "8f8aa3")
    static let line = Color(hex: "ece5db"), lineShade = Color(hex: "ddd3c4"), surface = Color(hex: "f9f5ef")
    static let cocoa = Color(hex: "5a3a22"), paper = Color(hex: "fff6e6"), sand = Color(hex: "f3e3c3")
}

extension Font {
    /// The website's typefaces, shipped with the app: Fredoka for headings, Nunito for everything else.
    private static let registered: Bool = {
        for name in ["Fredoka", "Nunito"] {
            if let url = Bundle.main.url(forResource: name, withExtension: "ttf") { CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil) }
        }
        return true
    }()

    private static func variable(_ family: String, _ size: CGFloat, _ weight: CGFloat) -> Font {
        _ = registered
        let wght = 2003265652 // the 'wght' axis of a variable font
        let descriptor = UIFontDescriptor(fontAttributes: [.family: family, kCTFontVariationAttribute as UIFontDescriptor.AttributeName: [wght: weight]])
        return Font(UIFontMetrics.default.scaledFont(for: UIFont(descriptor: descriptor, size: size), maximumPointSize: size * 1.35))
    }

    static func display(_ size: CGFloat) -> Font { variable("Fredoka", size, 650) }

    static func text(_ size: CGFloat, _ weight: Font.Weight = .semibold) -> Font {
        let w: CGFloat = switch weight { case .black: 900; case .heavy: 850; case .bold: 750; case .regular: 550; default: 650 }
        return variable("Nunito", size, w)
    }
}

// MARK: - Buttons with a pressable bottom edge

struct ChunkyButtonStyle: ButtonStyle {
    enum Kind { case green, sky, sun, outline, danger }
    var kind: Kind = .green
    var small = false
    @Environment(\.isEnabled) private var enabled

    private var colors: (face: Color, edge: Color, text: Color) {
        guard enabled else { return (.line, .lineShade, .muted) }
        switch kind {
        case .green: return (.leaf, .leafShade, .white)
        case .sky: return (.sky, .skyShade, .white)
        case .sun: return (.sun, .sunShade, .ink)
        case .outline: return (.white, .line, .sky)
        case .danger: return (.coral, .coralShade, .white)
        }
    }

    func makeBody(configuration: Configuration) -> some View {
        let c = colors, depth: CGFloat = configuration.isPressed ? 0 : 5
        configuration.label
            .font(.text(small ? 15 : 17, .heavy))
            .textCase(.uppercase)
            .tracking(0.8)
            .foregroundStyle(c.text)
            .frame(maxWidth: small ? nil : .infinity)
            .padding(.vertical, small ? 10 : 16)
            .padding(.horizontal, small ? 18 : 20)
            .background(RoundedRectangle(cornerRadius: 18).fill(c.face))
            .overlay(RoundedRectangle(cornerRadius: 18).strokeBorder(kind == .outline ? Color.line : .clear, lineWidth: 2))
            .background(RoundedRectangle(cornerRadius: 18).fill(c.edge).offset(y: depth))
            .offset(y: 5 - depth)
            .padding(.bottom, 5)
            .animation(.spring(duration: 0.12), value: configuration.isPressed)
            .onChange(of: configuration.isPressed) { _, pressed in if pressed { Sfx.play(.tap); Haptic.tap() } }
    }
}

extension ButtonStyle where Self == ChunkyButtonStyle {
    static func chunky(_ kind: ChunkyButtonStyle.Kind = .green, small: Bool = false) -> ChunkyButtonStyle { .init(kind: kind, small: small) }
}

/// A white card with the soft bottom edge used all over Loba.
struct CardModifier: ViewModifier {
    var selected = false
    var tint: Color = .sky
    func body(content: Content) -> some View {
        content
            .background(RoundedRectangle(cornerRadius: 20).fill(.white).overlay(RoundedRectangle(cornerRadius: 20).fill(tint.opacity(selected ? 0.12 : 0))))
            .overlay(RoundedRectangle(cornerRadius: 20).strokeBorder(selected ? tint : Color.line, lineWidth: 2))
            .background(RoundedRectangle(cornerRadius: 20).fill(selected ? tint : Color.line).offset(y: 4))
            .padding(.bottom, 4)
    }
}

extension View {
    func card(selected: Bool = false, tint: Color = .sky) -> some View { modifier(CardModifier(selected: selected, tint: tint)) }
}

// MARK: - Artwork

/// One of Loba's illustrated icons (flame, gem, heart, chest…).
struct Art: View {
    let name: String
    var size: CGFloat = 32
    var body: some View { Image("art-\(name)").resizable().scaledToFit().frame(width: size, height: size) }
}

/// A band of Kuba cloth: the diamond pattern woven in the Kasaï.
struct KubaBand: View {
    var color: Color = .cocoa
    var background: Color = .sand
    var height: CGFloat = 16

    var body: some View {
        Canvas { ctx, size in
            ctx.fill(Path(CGRect(origin: .zero, size: size)), with: .color(background))
            let w = size.height * 2
            var x: CGFloat = -w / 2
            while x < size.width + w {
                var diamond = Path()
                diamond.move(to: CGPoint(x: x, y: size.height / 2))
                diamond.addLine(to: CGPoint(x: x + w / 2, y: 1))
                diamond.addLine(to: CGPoint(x: x + w, y: size.height / 2))
                diamond.addLine(to: CGPoint(x: x + w / 2, y: size.height - 1))
                diamond.closeSubpath()
                ctx.stroke(diamond, with: .color(color.opacity(0.55)), lineWidth: 1.5)
                let r = size.height * 0.14
                ctx.fill(Path(ellipseIn: CGRect(x: x + w / 2 - r, y: size.height / 2 - r, width: r * 2, height: r * 2)), with: .color(color))
                x += w
            }
        }
        .frame(height: height)
        .overlay(alignment: .top) { Rectangle().fill(color).frame(height: 2) }
        .overlay(alignment: .bottom) { Rectangle().fill(color).frame(height: 2) }
        .accessibilityHidden(true)
    }
}

/// The diamond weave drawn faintly over coloured banners.
struct KubaWeave: View {
    var body: some View {
        Canvas { ctx, size in
            let step: CGFloat = 44
            var y: CGFloat = -step / 2
            var row = 0
            while y < size.height + step {
                var x: CGFloat = row.isMultiple(of: 2) ? 0 : step / 2
                while x < size.width + step {
                    for scale in [1.0, 0.6, 0.25] {
                        let r = step / 2 * scale
                        var d = Path()
                        d.move(to: CGPoint(x: x - r, y: y)); d.addLine(to: CGPoint(x: x, y: y - r))
                        d.addLine(to: CGPoint(x: x + r, y: y)); d.addLine(to: CGPoint(x: x, y: y + r)); d.closeSubpath()
                        if scale == 0.25 { ctx.fill(d, with: .color(.white.opacity(0.16))) } else { ctx.stroke(d, with: .color(.white.opacity(0.13)), lineWidth: 2) }
                    }
                    x += step
                }
                y += step / 2
                row += 1
            }
        }
        .allowsHitTesting(false)
    }
}

struct Meter: View {
    var value: Double
    var color: Color = .leaf
    var height: CGFloat = 16
    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .leading) {
                Capsule().fill(Color.line)
                Capsule().fill(color).frame(width: max(height, geo.size.width * min(1, max(0, value))))
                    .overlay(alignment: .top) { Capsule().fill(.white.opacity(0.3)).frame(height: height * 0.28).padding(.horizontal, height * 0.4).padding(.top, height * 0.2) }
                    .opacity(value > 0 ? 1 : 0)
            }
        }
        .frame(height: height)
        .animation(.spring(duration: 0.5, bounce: 0.3), value: value)
    }
}

struct LangBadge: View {
    let code: String
    var color: Color = .sky
    var body: some View {
        Text(code.uppercased()).font(.text(13, .heavy)).foregroundStyle(.white)
            .padding(.horizontal, 9).padding(.vertical, 5)
            .background(RoundedRectangle(cornerRadius: 9).fill(color))
            .background(RoundedRectangle(cornerRadius: 9).fill(.black.opacity(0.25)).offset(y: 2.5))
    }
}

/// Streak, gems and hearts, across the top of every tab.
struct StatsBar: View {
    @Environment(AppStore.self) private var store
    var body: some View {
        let s = store.state, streak = Rules.visibleStreak(s, store.today)
        HStack(spacing: 0) {
            LangBadge(code: s.lang, color: Color(hex: store.language?.color ?? "1c9bef"))
            Spacer()
            stat(streak > 0 ? "flame" : "flameOff", "\(streak)", streak > 0 ? .sunShade : .muted)
            Spacer()
            stat("gem", "\(s.gems)", .sky)
            Spacer()
            stat(s.hearts > 0 ? "heart" : "heartOff", "\(s.hearts)", .coral)
        }
        .padding(.horizontal, 20).padding(.vertical, 10)
        .background(.white)
        .overlay(alignment: .bottom) { Rectangle().fill(Color.line).frame(height: 2) }
    }

    private func stat(_ art: String, _ value: String, _ color: Color) -> some View {
        HStack(spacing: 6) {
            Art(name: art, size: 26)
            Text(value).font(.text(18, .heavy)).foregroundStyle(color).contentTransition(.numericText())
        }
        .animation(.snappy, value: value)
    }
}

/// Paper streamers in the flag's colours, thrown once.
struct Confetti: View {
    private struct Piece { let x, drift, speed, spin, size, delay: Double; let color: Color }
    private let pieces: [Piece] = (0..<90).map { _ in
        Piece(x: .random(in: 0...1), drift: .random(in: -0.25...0.25), speed: .random(in: 0.55...1.1), spin: .random(in: -6...6),
              size: .random(in: 7...13), delay: .random(in: 0...0.5), color: [Color.sky, .sun, .coral, .leaf, .plum].randomElement()!)
    }
    @State private var start = Date()

    var body: some View {
        TimelineView(.animation) { timeline in
            Canvas { ctx, size in
                let t = timeline.date.timeIntervalSince(start)
                for p in pieces {
                    let life = t - p.delay
                    guard life > 0, life < 3.4 else { continue }
                    let y = -20 + life * p.speed * size.height * 0.5 + life * life * 60
                    let x = p.x * size.width + sin(life * 3 + p.x * 10) * 18 + p.drift * life * size.width * 0.3
                    var piece = ctx
                    piece.translateBy(x: x, y: y)
                    piece.rotate(by: .radians(life * p.spin))
                    piece.opacity = min(1, (3.4 - life) * 2)
                    piece.fill(Path(roundedRect: CGRect(x: -p.size / 2, y: -p.size / 4, width: p.size, height: p.size / 2), cornerRadius: 2), with: .color(p.color))
                }
            }
        }
        .allowsHitTesting(false)
        .ignoresSafeArea()
    }
}

/// FR | EN, wherever the interface language can be changed.
struct UiSwitch: View {
    @Environment(AppStore.self) private var store
    var body: some View {
        HStack(spacing: 2) {
            ForEach([UiLang.fr, .en], id: \.self) { lang in
                Button(lang.rawValue.uppercased()) { Sfx.play(.tap); store.setUi(lang) }
                    .font(.text(13, .heavy))
                    .foregroundStyle(store.state.ui == lang ? Color.sky : .muted)
                    .padding(.horizontal, 10).padding(.vertical, 6)
                    .background(RoundedRectangle(cornerRadius: 10).fill(store.state.ui == lang ? .white : .clear).shadow(color: .black.opacity(store.state.ui == lang ? 0.08 : 0), radius: 0, y: 2))
            }
        }
        .padding(3)
        .background(RoundedRectangle(cornerRadius: 13).fill(Color.surface))
        .overlay(RoundedRectangle(cornerRadius: 13).strokeBorder(Color.line, lineWidth: 2))
    }
}

/// A speech bubble next to Loba.
struct Bubble<Content: View>: View {
    @ViewBuilder var content: Content
    var body: some View {
        content
            .padding(.horizontal, 18).padding(.vertical, 14)
            .background(RoundedRectangle(cornerRadius: 18).fill(.white))
            .overlay(RoundedRectangle(cornerRadius: 18).strokeBorder(Color.line, lineWidth: 2))
    }
}
