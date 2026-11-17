import Foundation
import CryptoKit

/// Per-attempt proof and callback state. No Google client secret belongs in the app.
struct GoogleAuthRequest {
    enum Failure: Error { case invalidCallback, provider(String) }
    let verifier: String
    let state: String

    init() {
        var generator = SystemRandomNumberGenerator()
        func token() -> String {
            String((0..<43).map { _ in "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_".randomElement(using: &generator)! })
        }
        verifier = token()
        state = token()
    }

    var challenge: String {
        Data(SHA256.hash(data: Data(verifier.utf8))).base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }

    func startURL(origin: URL) -> URL {
        var url = URLComponents(url: origin.appendingPathComponent("api/learn/google/start"), resolvingAgainstBaseURL: false)!
        url.queryItems = [URLQueryItem(name: "app", value: "1"), URLQueryItem(name: "challenge", value: challenge), URLQueryItem(name: "app_state", value: state)]
        return url.url!
    }

    func code(from url: URL) throws -> String {
        guard url.scheme == "loba", url.host == "auth", url.path.isEmpty,
              url.user == nil, url.password == nil, url.port == nil, url.fragment == nil,
              let items = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems else { throw Failure.invalidCallback }
        func unique(_ name: String) -> String? {
            let matches = items.filter { $0.name == name }
            return matches.count == 1 ? matches[0].value : nil
        }
        guard unique("state") == state else { throw Failure.invalidCallback }
        if items.contains(where: { $0.name == "error" }) {
            guard let reason = unique("error"), !items.contains(where: { $0.name == "code" }) else { throw Failure.invalidCallback }
            throw Failure.provider(reason)
        }
        guard let code = unique("code"), code.count == 43,
              code.range(of: "^[A-Za-z0-9_-]{43}$", options: .regularExpression) != nil else { throw Failure.invalidCallback }
        return code
    }
}
