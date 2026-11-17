import SwiftUI

/// A puff of coloured sparks, fired each time `trigger` changes. Used when an answer is right.
struct Burst: View {
    let trigger: Int
    var colors: [Color] = [.sun, .leaf, .sky, .coral]
    @State private var fired: Date?
    private let sparks: [(angle: Double, speed: Double, size: Double, spin: Double)] = (0..<26).map { _ in
        (.random(in: 0..<(2 * .pi)), .random(in: 90...240), .random(in: 5...11), .random(in: -8...8))
    }

    var body: some View {
        TimelineView(.animation(paused: fired == nil)) { timeline in
            Canvas { ctx, size in
                guard let fired else { return }
                let t = timeline.date.timeIntervalSince(fired)
                guard t < 0.9 else { return }
                for (i, s) in sparks.enumerated() {
                    let ease = 1 - pow(1 - min(1, t / 0.9), 3)
                    let x = size.width / 2 + cos(s.angle) * s.speed * ease
                    let y = size.height / 2 + sin(s.angle) * s.speed * ease + t * t * 220
                    var spark = ctx
                    spark.translateBy(x: x, y: y)
                    spark.rotate(by: .radians(t * s.spin))
                    spark.opacity = max(0, 1 - t / 0.9)
                    let shape = i.isMultiple(of: 3) ? Path(ellipseIn: CGRect(x: -s.size / 2, y: -s.size / 2, width: s.size, height: s.size))
                        : Path(roundedRect: CGRect(x: -s.size / 2, y: -s.size / 4, width: s.size, height: s.size / 2), cornerRadius: 2)
                    spark.fill(shape, with: .color(colors[i % colors.count]))
                }
            }
        }
        .allowsHitTesting(false)
        .onChange(of: trigger) {
            fired = Date()
            DispatchQueue.main.asyncAfter(deadline: .now() + 1) { if let f = fired, Date().timeIntervalSince(f) >= 0.95 { fired = nil } }
        }
    }
}

/// Stars that twinkle and fireflies that wander: the night pages of the story book.
struct NightSky: View {
    private let stars: [(x: Double, y: Double, r: Double, p: Double)] = (0..<46).map { _ in (.random(in: 0...1), .random(in: 0...0.42), .random(in: 0.8...2.2), .random(in: 0...6)) }
    private let flies: [(x: Double, y: Double, p: Double, s: Double)] = (0..<14).map { _ in (.random(in: 0.05...0.95), .random(in: 0.35...0.92), .random(in: 0...6), .random(in: 0.4...1)) }

    var body: some View {
        TimelineView(.animation) { timeline in
            Canvas { ctx, size in
                let t = timeline.date.timeIntervalSinceReferenceDate
                for s in stars {
                    var c = ctx
                    c.opacity = 0.35 + 0.65 * abs(sin(t * 1.4 + s.p))
                    c.fill(Path(ellipseIn: CGRect(x: s.x * size.width, y: s.y * size.height, width: s.r * 2, height: s.r * 2)), with: .color(.white))
                }
                for f in flies {
                    let x = (f.x + sin(t * 0.5 * f.s + f.p) * 0.05) * size.width, y = (f.y + cos(t * 0.7 * f.s + f.p * 2) * 0.04) * size.height
                    let glow = 0.25 + 0.75 * pow(abs(sin(t * 1.8 * f.s + f.p)), 2)
                    var c = ctx
                    c.opacity = glow
                    c.fill(Path(ellipseIn: CGRect(x: x - 7, y: y - 7, width: 14, height: 14)), with: .radialGradient(Gradient(colors: [Color(hex: "fff27a").opacity(0.7), .clear]), center: CGPoint(x: x, y: y), startRadius: 0, endRadius: 7))
                    c.fill(Path(ellipseIn: CGRect(x: x - 1.8, y: y - 1.8, width: 3.6, height: 3.6)), with: .color(Color(hex: "fffbc4")))
                }
            }
        }
        .allowsHitTesting(false)
    }
}

/// Daytime sky life: clouds sliding past and a pair of birds crossing.
struct DaySky: View {
    var body: some View {
        TimelineView(.animation) { timeline in
            Canvas { ctx, size in
                let t = timeline.date.timeIntervalSinceReferenceDate
                for (i, c) in [(0.16, 0.06, 1.0), (0.3, 0.035, 0.7), (0.1, 0.05, 0.55)].enumerated() {
                    let span = size.width + 160
                    let x = ((t * c.1 * size.width + Double(i) * span / 3).truncatingRemainder(dividingBy: span)) - 80
                    let y = c.0 * size.height, w = 62 * c.2
                    var cloud = Path()
                    cloud.addEllipse(in: CGRect(x: x, y: y, width: w, height: w * 0.42))
                    cloud.addEllipse(in: CGRect(x: x + w * 0.22, y: y - w * 0.2, width: w * 0.5, height: w * 0.5))
                    cloud.addEllipse(in: CGRect(x: x + w * 0.5, y: y - w * 0.08, width: w * 0.4, height: w * 0.36))
                    ctx.fill(cloud, with: .color(.white.opacity(0.85)))
                }
                let span = size.width + 120
                let bx = span - (t * 22).truncatingRemainder(dividingBy: span) - 60
                for (k, offset) in [(0.0, 0.0), (22.0, 12.0), (-18.0, 18.0)].enumerated() {
                    let flap = sin(t * 7 + Double(k)) * 4
                    var bird = Path()
                    let x = bx + offset.0, y = size.height * 0.2 + offset.1 + sin(t * 1.2 + Double(k)) * 4
                    bird.move(to: CGPoint(x: x - 8, y: y + flap)); bird.addQuadCurve(to: CGPoint(x: x, y: y + 2), control: CGPoint(x: x - 4, y: y - 5))
                    bird.addQuadCurve(to: CGPoint(x: x + 8, y: y + flap), control: CGPoint(x: x + 4, y: y - 5))
                    ctx.stroke(bird, with: .color(Color.ink.opacity(0.55)), style: StrokeStyle(lineWidth: 1.8, lineCap: .round))
                }
            }
        }
        .allowsHitTesting(false)
    }
}

/// Sunbeams turning slowly behind the sun.
struct SunRays: View {
    var body: some View {
        TimelineView(.animation) { timeline in
            Canvas { ctx, size in
                let t = timeline.date.timeIntervalSinceReferenceDate
                ctx.translateBy(x: size.width / 2, y: size.height / 2)
                ctx.rotate(by: .radians(t * 0.25))
                for i in 0..<12 {
                    var ray = ctx
                    ray.rotate(by: .degrees(Double(i) * 30))
                    let reach = size.width / 2 * (0.82 + 0.18 * sin(t * 2 + Double(i)))
                    ray.fill(Path(roundedRect: CGRect(x: -2.5, y: -reach, width: 5, height: reach * 0.26), cornerRadius: 2.5), with: .color(Color.sun.opacity(0.75)))
                }
            }
        }
        .allowsHitTesting(false)
    }
}

/// Today's goal as a ring that fills, with the count inside.
struct GoalRing: View {
    var value: Double
    var size: CGFloat = 58
    var body: some View {
        ZStack {
            Circle().stroke(Color.white.opacity(0.28), lineWidth: 8)
            Circle().trim(from: 0, to: max(0.001, min(1, value))).stroke(Color.sun, style: StrokeStyle(lineWidth: 8, lineCap: .round)).rotationEffect(.degrees(-90)).opacity(value > 0 ? 1 : 0)
            Art(name: value >= 1 ? "star" : "bolt", size: size * 0.42)
        }
        .frame(width: size, height: size)
        .animation(.spring(duration: 0.8, bounce: 0.3), value: value)
    }
}

/// The last seven days, with a flame on each day the learner practised.
struct WeekStrip: View {
    @Environment(AppStore.self) private var store
    var body: some View {
        let cal = Calendar.current, today = Date()
        HStack(spacing: 0) {
            ForEach((0..<7).reversed(), id: \.self) { back in
                let day = cal.date(byAdding: .day, value: -back, to: today)!
                let active = store.state.activeDays.contains(Game.dayKey(day))
                VStack(spacing: 6) {
                    Text(day.formatted(.dateTime.weekday(.narrow).locale(Locale(identifier: store.state.ui.rawValue)))).font(.text(13, .heavy)).foregroundStyle(back == 0 ? Color.sunShade : .muted)
                    ZStack {
                        Circle().fill(active ? Color.sunTint : .surface).frame(width: 36, height: 36)
                        Circle().strokeBorder(back == 0 ? Color.sun : .clear, lineWidth: 2).frame(width: 36, height: 36)
                        Art(name: active ? "flame" : "flameOff", size: 22).opacity(active ? 1 : 0.5)
                    }
                }
                .frame(maxWidth: .infinity)
            }
        }
    }
}
