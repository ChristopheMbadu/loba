import Foundation

@main struct GoogleAuthRequestTests {
    static func main() throws {
        let request = GoogleAuthRequest(), second = GoogleAuthRequest()
        precondition(request.verifier.count == 43 && request.challenge.count == 43 && request.state.count == 43)
        precondition(request.verifier != second.verifier && request.state != second.state)
        let start = URLComponents(url: request.startURL(origin: URL(string: "https://congolingo.com")!), resolvingAgainstBaseURL: false)!
        precondition(start.path == "/api/learn/google/start")
        precondition(start.queryItems!.contains(URLQueryItem(name: "challenge", value: request.challenge)))
        precondition(!start.url!.absoluteString.contains(request.verifier))
        let code = String(repeating: "c", count: 43)
        let valid = "loba://auth?code=\(code)&state=\(request.state)"
        let parsed = try request.code(from: URL(string: valid)!)
        precondition(parsed == code)
        for value in [
            valid.replacingOccurrences(of: "loba:", with: "https:"),
            valid.replacingOccurrences(of: "//auth?", with: "//wrong?"),
            valid.replacingOccurrences(of: "//auth?", with: "//auth/unexpected?"),
            valid.replacingOccurrences(of: request.state, with: second.state),
            valid + "&state=\(request.state)", valid + "&code=\(code)",
            valid + "&error=google_failed", valid + "#fragment",
            valid.replacingOccurrences(of: code, with: "short"),
            "loba://auth?code=\(code)", "loba://auth?state=\(request.state)"
        ] {
            do { _ = try request.code(from: URL(string: value)!); preconditionFailure("Accepted invalid callback") }
            catch GoogleAuthRequest.Failure.invalidCallback {}
        }
        do {
            _ = try request.code(from: URL(string: "loba://auth?error=google_cancelled&state=\(request.state)")!)
            preconditionFailure("Cancellation was accepted as success")
        } catch GoogleAuthRequest.Failure.provider(let reason) { precondition(reason == "google_cancelled") }
        print("GoogleAuthRequest checks passed: state, proof, callback validation, replay-state rejection, cancellation")
    }
}
