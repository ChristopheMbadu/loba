import SwiftUI

/// Loba, the mascot: an African grey parrot (native to the Congo basin) with a flag-blue scarf.
/// Drawn and animated in code, from the same shapes as the website: it breathes, blinks, waves,
/// flaps when it cheers, shakes its head when it is sad, and hops when tapped.
struct LobaView: View {
    enum Mood { case idle, happy, cheer, sad, think, wave }
    var mood: Mood = .idle
    var size: CGFloat = 140
    var tappable = true
    @State private var hop = false
    @State private var born = Date()

    var body: some View {
        TimelineView(.animation) { timeline in
            Canvas { ctx, canvas in
                ctx.scaleBy(x: canvas.width / 200, y: canvas.height / 200)
                LobaArt.draw(&ctx, mood: mood, t: timeline.date.timeIntervalSince(born))
            }
        }
        .frame(width: size, height: size)
        .offset(y: hop ? -size * 0.16 : 0)
        .rotationEffect(.degrees(hop ? -7 : 0), anchor: .bottom)
        .contentShape(Rectangle())
        .onTapGesture {
            guard tappable else { return }
            Sfx.play(.pop); Haptic.tap()
            withAnimation(.spring(duration: 0.2, bounce: 0.5)) { hop = true }
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) { withAnimation(.spring(duration: 0.45, bounce: 0.65)) { hop = false } }
        }
        .accessibilityHidden(true)
    }
}

enum LobaArt {
    private static let grey = Color(hex: "98a2b8"), greyD = Color(hex: "77819a"), belly = Color(hex: "c9d0de")
    private static let mask = Color(hex: "f6f8fc"), beak = Color(hex: "322e40"), red = Color(hex: "f0524f"), redD = Color(hex: "c93a3c")

    private static let feathers = SVGPath.parse("M86 128q7 6 14 0m0 0q7 6 14 0M79 144q7 6 14 0m0 0q7 6 14 0m0 0q7 6 14 0M86 160q7 6 14 0m0 0q7 6 14 0")
    private static let crest = SVGPath.parse("M84 34q4-12 10-4 5-12 10 0 7-8 10 6z")
    private static let smile = SVGPath.parse("M72 74q10-14 20 0M108 74q10-14 20 0")
    private static let brows = SVGPath.parse("M68 58l22-8M132 58l-22-8")
    private static let tear = SVGPath.parse("M133 86q5 8 0 12-5-4 0-12z")
    private static let beakShape = SVGPath.parse("M100 72c-12 0-17 14-11 24 4 7 11 11 11 11s7-4 11-11c6-10 1-24-11-24z")
    private static let beakShine = SVGPath.parse("M94 80q3-4 8-3")
    private static let scarf = SVGPath.parse("M60 106q40 26 80 0l3 12q-43 28-86 0z")
    private static let scarfEnd = SVGPath.parse("M126 118l14 24-16-4-6 10-4-26z")
    private static let star = SVGPath.parse("m100 120 1.8 3.8 4.2.5-3.1 2.9.8 4.1-3.7-2-3.7 2 .8-4.1-3.1-2.9 4.2-.5z")

    private static func ellipse(_ ctx: GraphicsContext, _ cx: CGFloat, _ cy: CGFloat, _ rx: CGFloat, _ ry: CGFloat, _ color: Color, rotate: Double = 0, opacity: Double = 1) {
        var c = ctx
        c.translateBy(x: cx, y: cy)
        c.rotate(by: .degrees(rotate))
        c.opacity = opacity
        c.fill(Path(ellipseIn: CGRect(x: -rx, y: -ry, width: rx * 2, height: ry * 2)), with: .color(color))
    }

    /// A copy of the context turned about a point, like SVG's transform-origin.
    private static func pivot(_ ctx: GraphicsContext, _ x: CGFloat, _ y: CGFloat, degrees: Double, dy: CGFloat = 0) -> GraphicsContext {
        var c = ctx
        c.translateBy(x: x, y: y + dy)
        c.rotate(by: .degrees(degrees))
        c.translateBy(x: -x, y: -y)
        return c
    }

    static func draw(_ root: inout GraphicsContext, mood: LobaView.Mood, t: Double) {
        let wingsUp = mood == .cheer
        let smiling = mood == .happy || mood == .cheer
        let open = mood == .cheer || mood == .wave
        let jump: CGFloat = mood == .cheer ? -abs(sin(t * 5)) * 10 : 0
        let breathe = 1 + sin(t * 2.1) * 0.012
        // Blink every few seconds; twice in a row now and then.
        let phase = t.truncatingRemainder(dividingBy: 3.7)
        let blinking = phase < 0.13 || (phase > 0.3 && phase < 0.4 && Int(t / 3.7) % 3 == 0)

        ellipse(root, 100, 190, 46 + jump * 0.8, 6, .black, opacity: 0.08)

        var ctx = root
        ctx.translateBy(x: 100, y: 186 + jump)
        ctx.scaleBy(x: 2 - breathe, y: breathe)
        ctx.rotate(by: .degrees(mood == .happy ? sin(t * 2.4) * 2.5 : 0))
        ctx.translateBy(x: -100, y: -186)

        // tail, wagging a little
        let tail = pivot(ctx, 128, 164, degrees: sin(t * 3) * (smiling ? 7 : 3))
        ellipse(tail, 148, 170, 10, 24, redD, rotate: -58)
        ellipse(tail, 144, 176, 10, 24, red, rotate: -74)
        ellipse(ctx, 82, 184, 13, 7, Color(hex: "4b465b"))
        ellipse(ctx, 118, 184, 13, 7, Color(hex: "4b465b"))

        // raised wings sit behind the body
        if wingsUp {
            let flap = sin(t * 10) * 13
            ellipse(pivot(ctx, 60, 118, degrees: flap), 40, 96, 15, 34, greyD, rotate: -38)
            ellipse(pivot(ctx, 140, 118, degrees: -flap), 160, 96, 15, 34, greyD, rotate: 38)
        }
        if mood == .wave { ellipse(pivot(ctx, 140, 118, degrees: sin(t * 7) * 15 - 4), 160, 96, 15, 34, greyD, rotate: 38) }

        ellipse(ctx, 100, 130, 52, 56, grey)
        ellipse(ctx, 100, 140, 35, 40, belly)
        var soft = ctx
        soft.opacity = 0.7
        soft.stroke(feathers, with: .color(grey), style: StrokeStyle(lineWidth: 3, lineCap: .round))
        if !wingsUp { ellipse(ctx, 52, 134, 15, 34, greyD, rotate: 10) }
        if !wingsUp && mood != .wave { ellipse(ctx, 148, 134, 15, 34, greyD, rotate: -10) }

        // head
        let tilt: Double = switch mood {
        case .sad: -5 + sin(t * 9) * (t < 0.9 ? 7 : 0) // a quick "no", then it hangs its head
        case .think: 5 + sin(t * 1.6) * 2
        case .wave: sin(t * 3.5) * 3
        default: sin(t * 1.3) * 1.5
        }
        let head = pivot(ctx, 100, 110, degrees: tilt, dy: mood == .sad ? 3 : 0)
        head.fill(crest, with: .color(greyD))
        ellipse(head, 100, 74, 46, 46, grey)
        ellipse(head, 80, 72, 23, 25, mask)
        ellipse(head, 120, 72, 23, 25, mask)
        ellipse(head, 66, 88, 7, 7, Color(hex: "ff9aa8"), opacity: 0.55)
        ellipse(head, 134, 88, 7, 7, Color(hex: "ff9aa8"), opacity: 0.55)
        if smiling {
            head.stroke(smile, with: .color(beak), style: StrokeStyle(lineWidth: 5.5, lineCap: .round))
        } else {
            let up = mood == .think
            // The eyes wander slowly, as if following something.
            let lx = sin(t * 0.7) * 2.2 + (up ? 3 : 0), ly = cos(t * 0.9) * 1.2 + (up ? -4 : mood == .sad ? 3 : 0)
            for cx in [82.0, 118.0] {
                if blinking {
                    var lid = Path()
                    lid.move(to: CGPoint(x: cx - 9 + lx, y: 71 + ly)); lid.addLine(to: CGPoint(x: cx + 9 + lx, y: 71 + ly))
                    head.stroke(lid, with: .color(beak), style: StrokeStyle(lineWidth: 4.5, lineCap: .round))
                } else {
                    ellipse(head, cx + lx, 71 + ly, 9.5, 9.5, beak)
                    ellipse(head, cx + 3 + lx * 1.3, 67 + ly * 1.3, 3.2, 3.2, .white)
                }
            }
        }
        if mood == .sad {
            head.stroke(brows, with: .color(beak), style: StrokeStyle(lineWidth: 4.5, lineCap: .round))
            var drop = head
            let fall = (t * 0.7).truncatingRemainder(dividingBy: 1)
            drop.translateBy(x: 0, y: fall * 16)
            drop.opacity = 1 - fall
            drop.fill(tear, with: .color(Color(hex: "6cc7ff")))
        }
        // beak: it opens and closes when Loba talks or cheers
        let gape: CGFloat = open ? 3 + abs(sin(t * (mood == .cheer ? 9 : 6))) * 3 : 0
        if open { ellipse(head, 100, 104, 8, 4 + gape, Color(hex: "e8608a")) }
        var upper = head
        upper.translateBy(x: 0, y: -gape)
        upper.fill(beakShape, with: .color(beak))
        upper.stroke(beakShine, with: .color(.white.opacity(0.35)), style: StrokeStyle(lineWidth: 3, lineCap: .round))

        // scarf, its end fluttering
        ctx.fill(scarf, with: .color(.sky))
        pivot(ctx, 126, 118, degrees: sin(t * 4) * (wingsUp ? 10 : 5)).fill(scarfEnd, with: .color(.skyShade))
        ctx.fill(star, with: .color(.sun))
    }
}

/// Reads the path strings used by the website's SVG artwork (M L H V Q C S Z, absolute and relative).
enum SVGPath {
    static func parse(_ d: String) -> Path {
        var path = Path()
        var numbers: [CGFloat] = []
        var command: Character = "M"
        var current = CGPoint.zero, start = CGPoint.zero, lastControl: CGPoint?
        var token = ""
        var tokens: [String] = []
        func flushToken() { if !token.isEmpty { tokens.append(token); token = "" } }
        for ch in d {
            if ch.isLetter && ch != "e" { flushToken(); tokens.append(String(ch)) }
            else if ch == "-" { if !token.isEmpty && token.last != "e" { flushToken() }; token.append(ch) }
            else if ch == "." { if token.contains(".") { flushToken() }; token.append(ch) }
            else if ch == "," || ch.isWhitespace { flushToken() }
            else { token.append(ch) }
        }
        flushToken()

        func run() {
            let rel = command.isLowercase
            func pt(_ i: Int) -> CGPoint { CGPoint(x: numbers[i] + (rel ? current.x : 0), y: numbers[i + 1] + (rel ? current.y : 0)) }
            switch command.lowercased() {
            case "m" where numbers.count >= 2:
                current = pt(0); start = current; path.move(to: current); lastControl = nil
                numbers.removeFirst(2); command = rel ? "l" : "L"
            case "l" where numbers.count >= 2:
                current = pt(0); path.addLine(to: current); lastControl = nil; numbers.removeFirst(2)
            case "h" where numbers.count >= 1:
                current.x = numbers[0] + (rel ? current.x : 0); path.addLine(to: current); lastControl = nil; numbers.removeFirst(1)
            case "v" where numbers.count >= 1:
                current.y = numbers[0] + (rel ? current.y : 0); path.addLine(to: current); lastControl = nil; numbers.removeFirst(1)
            case "q" where numbers.count >= 4:
                let c = pt(0), end = pt(2)
                path.addQuadCurve(to: end, control: c); current = end; lastControl = nil; numbers.removeFirst(4)
            case "c" where numbers.count >= 6:
                let c1 = pt(0), c2 = pt(2), end = pt(4)
                path.addCurve(to: end, control1: c1, control2: c2); current = end; lastControl = c2; numbers.removeFirst(6)
            case "s" where numbers.count >= 4:
                let c1 = lastControl.map { CGPoint(x: 2 * current.x - $0.x, y: 2 * current.y - $0.y) } ?? current
                let c2 = pt(0), end = pt(2)
                path.addCurve(to: end, control1: c1, control2: c2); current = end; lastControl = c2; numbers.removeFirst(4)
            default: break
            }
        }

        for item in tokens {
            if let first = item.first, first.isLetter, item.count == 1 {
                command = first
                numbers = []
                if first == "z" || first == "Z" { path.closeSubpath(); current = start; lastControl = nil }
            } else if let value = Double(item) {
                numbers.append(CGFloat(value))
                let need = ["m": 2, "l": 2, "h": 1, "v": 1, "q": 4, "c": 6, "s": 4][command.lowercased()] ?? 99
                if numbers.count >= need { run() }
            }
        }
        return path
    }
}
