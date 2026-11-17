import Foundation

struct Learner: Equatable {
    var name: String
    var email: String
    var googleLinked: Bool
    var passwordLogin: Bool

    init?(_ v: Any?) {
        guard let o = v as? [String: Any], let email = o["email"] as? String else { return nil }
        name = o["name"] as? String ?? ""
        self.email = email
        googleLinked = o["googleLinked"] as? Bool ?? false
        passwordLogin = o["passwordLogin"] as? Bool ?? true
    }
}

struct APIError: LocalizedError {
    let status: Int
    let message: String
    var errorDescription: String? { message }
    /// The server could not be reached at all.
    var offline: Bool { status == 0 }
}

/// Talks to the Loba server. The session lives in a cookie that iOS keeps for the app.
enum API {
    /// The address learners use on the web. Sign-in cookies belong to this host.
    static let origin = URL(string: "https://congolingo.com")!

    private static let session: URLSession = {
        let config = URLSessionConfiguration.default
        config.httpCookieStorage = .shared
        config.httpShouldSetCookies = true
        config.timeoutIntervalForRequest = 15
        return URLSession(configuration: config)
    }()

    struct Session {
        var learner: Learner
        var progress: Any?
    }

    static func url(_ path: String) -> URL { URL(string: path, relativeTo: origin)!.absoluteURL }

    private static func call(_ path: String, method: String = "GET", body: [String: Any]? = nil) async throws -> [String: Any] {
        var req = URLRequest(url: url(path))
        req.httpMethod = method
        req.setValue("application/json", forHTTPHeaderField: "Accept")
        if method != "GET" {
            req.setValue("application/json", forHTTPHeaderField: "Content-Type")
            // The server only accepts writes that name it as their origin. This app is its own client, so it says so.
            req.setValue(origin.absoluteString, forHTTPHeaderField: "Origin")
            req.httpBody = try JSONSerialization.data(withJSONObject: body ?? [:])
        }
        let data: Data, response: URLResponse
        do { (data, response) = try await session.data(for: req) } catch { throw APIError(status: 0, message: "offline") }
        let status = (response as? HTTPURLResponse)?.statusCode ?? 0
        let json = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any]
        guard (200..<300).contains(status), let json else {
            throw APIError(status: json == nil ? 0 : status, message: json?["error"] as? String ?? "offline")
        }
        return json
    }

    static func catalog() async -> Catalog? {
        var req = URLRequest(url: url("/api/v1/lessons"))
        req.setValue("application/json", forHTTPHeaderField: "Accept")
        guard let (data, response) = try? await session.data(for: req), (response as? HTTPURLResponse)?.statusCode == 200 else { return nil }
        return CatalogParser.published(data)
    }

    /// The signed-in learner, nil when signed out. Throws when the server can't be reached.
    static func me() async throws -> Session? {
        let body = try await call("/api/learn/me")
        guard let learner = Learner(body["learner"]) else { return nil }
        return Session(learner: learner, progress: (body["progress"] as? [String: Any])?["state"])
    }

    private static func session(_ body: [String: Any]) throws -> Session {
        guard let learner = Learner(body["learner"]) else { throw APIError(status: 0, message: "offline") }
        return Session(learner: learner, progress: (body["progress"] as? [String: Any])?["state"])
    }

    static func signup(name: String, email: String, password: String, state: [String: Any]?) async throws -> Session {
        var input: [String: Any] = ["name": name, "email": email, "password": password]
        if let state { input["state"] = state }
        return try session(await call("/api/learn/signup", method: "POST", body: input))
    }

    static func login(email: String, password: String) async throws -> Session {
        try session(await call("/api/learn/login", method: "POST", body: ["email": email, "password": password]))
    }

    static func logout() async throws { _ = try await call("/api/learn/logout", method: "POST") }

    static func rename(_ name: String) async throws { _ = try await call("/api/learn/profile", method: "PUT", body: ["name": name]) }

    static func deleteAccount(password: String) async throws {
        _ = try await call("/api/learn/account", method: "DELETE", body: ["password": password])
    }

    static func saveProgress(_ state: [String: Any]) async throws {
        _ = try await call("/api/learn/progress", method: "PUT", body: ["state": state])
    }

    /// Whether this server offers Google sign-in to the app (it must hand the session back to the app, not to a browser).
    static func googleForApp() async -> Bool {
        guard let body = try? await call("/api/learn/auth-options") else { return false }
        return body["google"] as? Bool == true && body["app"] as? Bool == true
    }

    /// Swaps the one-time code the server sent back after Google sign-in for a session.
    static func redeemGoogle(code: String, verifier: String) async throws -> Session {
        try session(await call("/api/learn/google/redeem", method: "POST", body: ["code": code, "verifier": verifier]))
    }

    static func audioURL(_ id: String) -> URL { url("/api/v1/audio/\(id)") }
}
