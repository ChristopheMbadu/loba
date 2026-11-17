import AuthenticationServices
import CryptoKit
import SwiftUI

@main
struct LobaApp: App {
    @State private var store = AppStore.shared
    @Environment(\.scenePhase) private var phase

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(store)
                .preferredColorScheme(.light)
                .tint(.sky)
                .task { await store.start() }
                .onChange(of: phase) { _, new in
                    if new == .active { store.tick() } else if new == .background { store.flush() }
                }
        }
    }
}

struct RootView: View {
    @Environment(AppStore.self) private var store

    var body: some View {
        Group {
            if !store.admitted {
                LandingView()
            } else if !store.state.onboarded {
                OnboardingView()
            } else {
                TabsView()
            }
        }
        .animation(.easeInOut(duration: 0.3), value: store.admitted)
        .animation(.easeInOut(duration: 0.3), value: store.state.onboarded)
        #if DEBUG
        .modifier(DebugLaunch())
        #endif
    }
}

#if DEBUG
/// For screenshots while developing: `-demo` skips sign-in and onboarding, `-screen story|lesson|practice` opens that screen.
struct DebugLaunch: ViewModifier {
    @Environment(AppStore.self) private var store
    @State private var cover: String?
    static var screen: String? { UserDefaults.standard.string(forKey: "screen") }

    func body(content: Content) -> some View {
        content
            .onAppear {
                guard ProcessInfo.processInfo.arguments.contains("-demo") else { return }
                store.continueAsGuest()
                if !store.state.onboarded { store.finishOnboarding(lang: "ln", goal: 20) }
                if ["story", "lesson", "build", "review"].contains(Self.screen ?? "") { cover = Self.screen }
            }
            .fullScreenCover(item: $cover) { name in
                if name == "story" { StoryView() }
                else if name == "review" { LessonView(mode: .practice(.review)) }
                else if let unit = store.units.first { LessonView(mode: .lesson(unit, name == "build" ? 3 : 0)) }
            }
    }
}

extension String: @retroactive Identifiable { public var id: String { self } }
#endif

// MARK: - Welcome

struct LandingView: View {
    @Environment(AppStore.self) private var store
    @State private var auth: AuthView.Mode?
    @State private var story = false
    @State private var greeting = 0
    @State private var appeared = false

    private var greetings: [(String, String)] {
        let all = store.catalog.languages.filter { !$0.greeting.isEmpty }.map { ("\($0.greeting) !", $0.name(store.state.ui).capitalized) }
        return all.isEmpty ? [("Mbote !", "Lingala")] : all
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Text("loba").font(.display(30)).foregroundStyle(Color.sky)
                Spacer()
                UiSwitch()
            }
            .padding(.horizontal, 24).padding(.top, 8)

            Spacer(minLength: 12)
            ZStack(alignment: .topLeading) {
                Circle().fill(Color.skyTint).frame(width: 290, height: 290)
                    .overlay(alignment: .topTrailing) { Circle().fill(Color.sun).frame(width: 64, height: 64).padding(.top, 44).padding(.trailing, 40).shadow(color: .sun.opacity(0.5), radius: 18) }
                    .overlay(alignment: .bottom) { River().frame(height: 96).clipShape(Circle().size(width: 290, height: 290).offset(y: -194)) }
                    .overlay(alignment: .bottom) { Pirogue().frame(width: 250, height: 52).offset(y: -46) }
                    .overlay { LobaView(mood: .wave, size: 180).offset(y: -8) }
                    .overlay(alignment: .bottom) { Pirogue(front: true).frame(width: 250, height: 52).offset(y: -46) }
                let g = greetings[greeting % greetings.count]
                Bubble {
                    VStack(spacing: 0) {
                        Text(g.0).font(.display(24)).foregroundStyle(Color.skyShade)
                        Text(g.1).font(.text(14)).foregroundStyle(Color.muted)
                    }
                }
                .id(greeting)
                .transition(.scale(scale: 0.6, anchor: .bottomTrailing).combined(with: .opacity))
                .offset(x: -18, y: 4)
            }
            .scaleEffect(appeared ? 1 : 0.85).opacity(appeared ? 1 : 0)

            Spacer(minLength: 12)
            VStack(spacing: 10) {
                Text(L("Parle enfin la langue de la famille.", "Finally speak the family language."))
                    .font(.display(32)).foregroundStyle(Color.ink).multilineTextAlignment(.center).minimumScaleFactor(0.8)
                Text(L("Les langues du Congo, cinq minutes par jour. Gratuit, sans pub.", "The languages of Congo, five minutes a day. Free, no ads."))
                    .font(.text(17)).foregroundStyle(Color.ink2).multilineTextAlignment(.center)
            }
            .padding(.horizontal, 28)

            Spacer(minLength: 16)
            VStack(spacing: 10) {
                Button(L("Commencer", "Get started")) { auth = .signup }.buttonStyle(.chunky(.green))
                Button(L("J’ai déjà un compte", "I already have an account")) { auth = .login }.buttonStyle(.chunky(.outline))
                HStack(spacing: 18) {
                    Button { story = true } label: { Label(L("Lire un conte", "Read a folktale"), systemImage: "book.fill") }
                    Button(L("Plus tard, sans compte", "Later, without an account")) { store.continueAsGuest() }
                }
                .font(.text(14, .bold)).foregroundStyle(Color.ink2).padding(.top, 4)
            }
            .padding(.horizontal, 24).padding(.bottom, 12)
        }
        .background(Color.white)
        .onAppear { withAnimation(.spring(duration: 0.6, bounce: 0.4)) { appeared = true } }
        .task {
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(2.6))
                withAnimation(.spring(duration: 0.4, bounce: 0.4)) { greeting += 1 }
            }
        }
        .sheet(item: $auth) { AuthView(mode: $0) }
        .fullScreenCover(isPresented: $story) { StoryView() }
    }
}

/// A dugout canoe. Drawn twice: the whole hull behind Loba, then the near side in front, so Loba sits inside it.
struct Pirogue: View {
    var front = false
    var body: some View {
        Canvas { ctx, size in
            let w = size.width, h = size.height
            var hull = Path()
            hull.move(to: CGPoint(x: 0, y: h * 0.18))
            hull.addQuadCurve(to: CGPoint(x: w, y: h * 0.18), control: CGPoint(x: w / 2, y: h * 0.5))
            hull.addQuadCurve(to: CGPoint(x: w * 0.9, y: h * 0.78), control: CGPoint(x: w * 0.98, y: h * 0.55))
            hull.addQuadCurve(to: CGPoint(x: w * 0.1, y: h * 0.78), control: CGPoint(x: w / 2, y: h * 1.12))
            hull.addQuadCurve(to: CGPoint(x: 0, y: h * 0.18), control: CGPoint(x: w * 0.02, y: h * 0.55))
            if !front {
                var inside = Path()
                inside.move(to: CGPoint(x: 0, y: h * 0.18))
                inside.addQuadCurve(to: CGPoint(x: w, y: h * 0.18), control: CGPoint(x: w / 2, y: -h * 0.12))
                inside.addQuadCurve(to: CGPoint(x: 0, y: h * 0.18), control: CGPoint(x: w / 2, y: h * 0.5))
                ctx.fill(inside, with: .color(Color(hex: "6b3f1c")))
                return
            }
            ctx.fill(hull, with: .color(Color(hex: "8c4f1f")))
            var rim = Path()
            rim.move(to: CGPoint(x: 0, y: h * 0.18))
            rim.addQuadCurve(to: CGPoint(x: w, y: h * 0.18), control: CGPoint(x: w / 2, y: h * 0.5))
            ctx.stroke(rim, with: .color(Color(hex: "b5682a")), style: StrokeStyle(lineWidth: 7, lineCap: .round))
            var band = Path()
            band.move(to: CGPoint(x: w * 0.12, y: h * 0.5))
            band.addQuadCurve(to: CGPoint(x: w * 0.88, y: h * 0.5), control: CGPoint(x: w / 2, y: h * 0.82))
            ctx.stroke(band, with: .color(.sun), style: StrokeStyle(lineWidth: 3.5, lineCap: .round))
        }
    }
}

/// Moving water: three bands of waves sliding past each other.
struct River: View {
    var body: some View {
        TimelineView(.animation) { timeline in
            Canvas { ctx, size in
                let t = timeline.date.timeIntervalSinceReferenceDate
                ctx.fill(Path(CGRect(origin: .zero, size: size)), with: .color(.sky))
                for (i, y) in [0.3, 0.55, 0.8].enumerated() {
                    var wave = Path()
                    let phase = t * (0.6 + Double(i) * 0.25)
                    for x in stride(from: 0.0, through: Double(size.width), by: 6) {
                        let p = CGPoint(x: x, y: size.height * y + sin(x / 26 + phase + Double(i)) * 4)
                        if x == 0 { wave.move(to: p) } else { wave.addLine(to: p) }
                    }
                    ctx.stroke(wave, with: .color(.white.opacity(0.35)), style: StrokeStyle(lineWidth: 3, lineCap: .round))
                }
            }
        }
    }
}

// MARK: - Sign up, sign in

struct AuthView: View {
    enum Mode: String, Identifiable { case signup, login; var id: String { rawValue } }
    @State var mode: Mode
    @Environment(AppStore.self) private var store
    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var email = ""
    @State private var password = ""
    @State private var error = ""
    @State private var busy = false
    @State private var google = GoogleSignIn()

    private var valid: Bool {
        email.contains("@") && password.count >= 8 && (mode == .login || !name.trimmingCharacters(in: .whitespaces).isEmpty)
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                HStack(alignment: .bottom, spacing: 4) {
                    LobaView(mood: mode == .signup ? .happy : .wave, size: 92)
                    Bubble { Text(mode == .signup ? L("Enchanté !", "Nice to meet you!") : L("Te revoilà !", "Welcome back!")).font(.text(16, .bold)).foregroundStyle(Color.ink) }
                        .padding(.bottom, 26)
                }
                Text(mode == .signup ? L("Crée ton compte", "Create your account") : L("Connexion", "Sign in")).font(.display(30)).foregroundStyle(Color.ink)
                Text(mode == .signup ? L("Ta progression te suit sur tous tes appareils.", "Your progress follows you on every device.") : L("Reprends là où tu t’es arrêté.", "Pick up where you left off."))
                    .font(.text(16)).foregroundStyle(Color.ink2)

                if store.googleAvailable {
                    Button {
                        Task { await run { try await google.signIn() } }
                    } label: { Label(L("Continuer avec Google", "Continue with Google"), systemImage: "g.circle.fill") }
                        .buttonStyle(.chunky(.outline)).disabled(busy)
                }

                if mode == .signup { field(L("Prénom", "First name"), text: $name, content: .givenName) }
                field("Email", text: $email, content: .emailAddress, keyboard: .emailAddress)
                VStack(alignment: .leading, spacing: 6) {
                    Text(L("Mot de passe", "Password")).font(.text(15, .bold)).foregroundStyle(Color.ink)
                    SecureField(mode == .signup ? L("8 caractères ou plus", "8 characters or more") : "", text: $password)
                        .textContentType(mode == .signup ? .newPassword : .password).fieldStyle()
                }
                if !error.isEmpty {
                    Text(error).font(.text(15, .bold)).foregroundStyle(Color.coralShade).padding(12).frame(maxWidth: .infinity, alignment: .leading)
                        .background(RoundedRectangle(cornerRadius: 14).fill(Color.coralTint))
                }
                Button(busy ? "…" : mode == .signup ? L("Créer mon compte", "Create my account") : L("Me connecter", "Sign in")) {
                    Task { await run(submit) }
                }
                .buttonStyle(.chunky(.green)).disabled(!valid || busy)

                Button(mode == .signup ? L("J’ai déjà un compte", "I already have an account") : L("Nouveau ? Créer un compte", "New here? Create an account")) {
                    error = ""
                    withAnimation { mode = mode == .signup ? .login : .signup }
                }
                .font(.text(15, .heavy)).foregroundStyle(Color.sky).frame(maxWidth: .infinity)

                if mode == .signup {
                    Link(L("En créant un compte, tu acceptes notre politique de confidentialité.", "By creating an account you accept our privacy policy."), destination: API.url("/privacy.html"))
                        .font(.text(13)).foregroundStyle(Color.muted).multilineTextAlignment(.center).frame(maxWidth: .infinity)
                }
            }
            .padding(24)
        }
        .scrollDismissesKeyboard(.interactively)
        .background(Color.white)
        .presentationDragIndicator(.visible)
        .interactiveDismissDisabled(busy)
        .onDisappear { google.cancel() }
    }

    private func field(_ label: String, text: Binding<String>, content: UITextContentType, keyboard: UIKeyboardType = .default) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label).font(.text(15, .bold)).foregroundStyle(Color.ink)
            TextField("", text: text).textContentType(content).keyboardType(keyboard)
                .textInputAutocapitalization(keyboard == .emailAddress ? .never : .words).autocorrectionDisabled().fieldStyle()
        }
    }

    private func submit() async throws {
        let mail = email.trimmingCharacters(in: .whitespaces).lowercased()
        if mode == .signup {
            let trimmed = name.trimmingCharacters(in: .whitespaces)
            // Progress made before signing up is kept with the new account.
            store.signedUp(try await API.signup(name: trimmed, email: mail, password: password, state: store.state.xp > 0 ? store.state.json : nil))
        } else {
            store.signedInWith(try await API.login(email: mail, password: password))
        }
    }

    private func run(_ work: () async throws -> Void) async {
        guard !busy else { return }
        busy = true
        error = ""
        do {
            try await work()
            Sfx.play(.coin); Haptic.success()
            dismiss()
        } catch let e as APIError {
            error = e.offline ? L("Impossible de joindre Loba. Vérifie ta connexion.", "Can’t reach Loba. Check your connection.") : e.message
            Haptic.error()
        } catch is CancellationError {
        } catch {
            self.error = L("La connexion a échoué. Réessaie.", "Sign-in failed. Please try again.")
        }
        busy = false
    }
}

extension View {
    func fieldStyle() -> some View {
        font(.text(17)).padding(14)
            .background(RoundedRectangle(cornerRadius: 14).fill(Color.surface))
            .overlay(RoundedRectangle(cornerRadius: 14).strokeBorder(Color.line, lineWidth: 2))
    }
}

/// Google sign-in happens in the system browser sheet; the server then hands a one-time code back to the app.
@MainActor
final class GoogleSignIn: NSObject, ASWebAuthenticationPresentationContextProviding {
    private var session: ASWebAuthenticationSession?
    private var completion: CheckedContinuation<URL, Error>?
    private var attemptID: String?

    func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor {
        UIApplication.shared.connectedScenes.compactMap { ($0 as? UIWindowScene)?.keyWindow }.first ?? ASPresentationAnchor()
    }

    private func finish(_ id: String, _ result: Result<URL, Error>) {
        guard attemptID == id else { return }
        attemptID = nil
        let pending = completion
        completion = nil
        session = nil
        pending?.resume(with: result)
    }

    func cancel() {
        guard let id = attemptID else { return }
        let active = session
        finish(id, .failure(CancellationError()))
        active?.cancel()
    }

    func signIn() async throws {
        guard session == nil else { throw CancellationError() }
        let request = GoogleAuthRequest()
        let callback: URL = try await withTaskCancellationHandler {
            try Task.checkCancellation()
            return try await withCheckedThrowingContinuation { done in
                completion = done
                attemptID = request.state
                let active = ASWebAuthenticationSession(url: request.startURL(origin: API.origin), callbackURLScheme: "loba") { [weak self] url, error in
                    Task { @MainActor in
                        if let url { self?.finish(request.state, .success(url)) }
                        else if (error as? ASWebAuthenticationSessionError)?.code == .canceledLogin {
                            self?.finish(request.state, .failure(CancellationError()))
                        } else {
                            self?.finish(request.state, .failure(error ?? GoogleAuthRequest.Failure.invalidCallback))
                        }
                    }
                }
                // Retain the browser session until it completes, including cancellation and failed starts.
                session = active
                active.presentationContextProvider = self
                active.prefersEphemeralWebBrowserSession = false
                if !active.start() { finish(request.state, .failure(GoogleAuthRequest.Failure.invalidCallback)) }
            }
        } onCancel: {
            Task { @MainActor in
                if self.attemptID == request.state { self.cancel() }
            }
        }
        try Task.checkCancellation()
        let code: String
        do { code = try request.code(from: callback) }
        catch GoogleAuthRequest.Failure.provider(let reason) {
            if reason == "google_cancelled" { throw CancellationError() }
            throw APIError(status: 400, message: reason == "google_existing"
                ? L("Un compte existe déjà avec cette adresse. Connecte-toi avec ton mot de passe, puis relie Google depuis le site.", "An account already exists with this email. Sign in with your password, then link Google from the website.")
                : L("La connexion Google a échoué. Réessaie.", "Google sign-in failed. Please try again."))
        }
        AppStore.shared.signedInWith(try await API.redeemGoogle(code: code, verifier: request.verifier))
    }
}

// MARK: - First run: language and daily goal

struct OnboardingView: View {
    @Environment(AppStore.self) private var store
    @State private var step = 0
    @State private var lang = ""
    @State private var goal = 20

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                if step == 1 { Button { withAnimation { step = 0 } } label: { Image(systemName: "chevron.left").font(.system(size: 20, weight: .heavy)).foregroundStyle(Color.muted) } }
                Meter(value: step == 0 ? 0.5 : 1, color: .sky, height: 14)
                UiSwitch()
            }
            .padding(.horizontal, 20).padding(.vertical, 12)

            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    HStack(alignment: .bottom, spacing: 4) {
                        LobaView(mood: step == 0 ? .wave : .think, size: 96)
                        Bubble {
                            Text(step == 0 ? L("Quelle langue veux-tu apprendre ?", "Which language do you want to learn?") : L("Combien veux-tu apprendre par jour ?", "How much do you want to learn each day?"))
                                .font(.text(17, .bold)).foregroundStyle(Color.ink)
                        }
                        .padding(.bottom, 24)
                    }
                    if step == 0 {
                        ForEach(store.catalog.languages) { l in
                            Button { Sfx.play(.tile, store.catalog.languages.firstIndex(of: l) ?? 0); lang = l.code } label: {
                                HStack(spacing: 14) {
                                    LangBadge(code: l.code, color: Color(hex: l.color))
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(l.name(store.state.ui).capitalized).font(.text(19, .heavy)).foregroundStyle(Color.ink)
                                        Text(l.region(store.state.ui)).font(.text(14)).foregroundStyle(Color.muted).multilineTextAlignment(.leading)
                                    }
                                    Spacer()
                                }
                                .padding(16).card(selected: lang == l.code)
                            }
                            .buttonStyle(.plain)
                        }
                    } else {
                        ForEach(Game.goals, id: \.self) { g in
                            Button { Sfx.play(.tile, Game.goals.firstIndex(of: g) ?? 0); goal = g } label: {
                                HStack {
                                    Art(name: "bolt", size: 28)
                                    Text("\(g) XP").font(.text(19, .heavy)).foregroundStyle(Color.ink)
                                    Spacer()
                                    Text([L("Tranquille", "Easy"), L("Régulier", "Regular"), L("Sérieux", "Serious"), L("Intense", "Intense")][Game.goals.firstIndex(of: g) ?? 0])
                                        .font(.text(15)).foregroundStyle(Color.muted)
                                }
                                .padding(16).card(selected: goal == g)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
                .padding(20)
            }
            Button(step == 0 ? L("Continuer", "Continue") : L("C’est parti !", "Let’s go!")) {
                if step == 0 { withAnimation { step = 1 } } else { Sfx.play(.streak); store.finishOnboarding(lang: lang, goal: goal) }
            }
            .buttonStyle(.chunky(.green)).disabled(lang.isEmpty).padding(20)
        }
        .background(Color.white)
        .onAppear {
            lang = store.catalog.curriculum[store.state.lang] != nil ? store.state.lang : store.catalog.languages.first?.code ?? ""
            goal = store.state.goal
        }
    }
}
