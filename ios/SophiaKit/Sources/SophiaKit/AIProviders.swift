import Foundation
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif

public enum AIProviderID: String, CaseIterable, Identifiable, Sendable {
    case off, openai, anthropic, compatible, demo

    public var id: String { rawValue }

    public var label: String {
        switch self {
        case .off: return "Off"
        case .openai: return "OpenAI"
        case .anthropic: return "Anthropic Claude"
        case .compatible: return "OpenAI-compatible service"
        case .demo: return "Demo (offline, no AI)"
        }
    }

    public var defaultModel: String {
        switch self {
        case .openai: return "gpt-5-mini"
        case .anthropic: return "claude-opus-5-5"
        case .demo: return "demo"
        case .off, .compatible: return ""
        }
    }

    public var needsKey: Bool { self == .openai || self == .anthropic || self == .compatible }

    /// Hosts the provider sends material to, shown on the review screen.
    public func destinationHost(baseURL: String?) -> String? {
        switch self {
        case .openai: return "api.openai.com"
        case .anthropic: return "api.anthropic.com"
        case .compatible: return baseURL.flatMap { URLComponents(string: $0)?.host }
        case .off, .demo: return nil
        }
    }
}

public struct ProviderError: Error, Equatable, LocalizedError, Sendable {
    public enum Code: Sendable { case auth, network, refused, invalidResponse, cancelled, config, unknown }
    public let message: String
    public let code: Code
    public init(_ message: String, _ code: Code) { self.message = message; self.code = code }
    public var errorDescription: String? { message }
}

public struct AIRequest: Sendable {
    public var apiKey: String
    public var model: String
    public var userMessage: String
    /// Only for `.compatible`: an https base URL such as https://api.example.com/v1
    public var baseURL: String?
    public init(apiKey: String, model: String, userMessage: String, baseURL: String? = nil) {
        self.apiKey = apiKey; self.model = model; self.userMessage = userMessage; self.baseURL = baseURL
    }
}

/// Sends one HTTP request. The app uses `URLSessionTransport`; tests use a stub.
public protocol HTTPTransport: Sendable {
    func send(_ request: URLRequest) async throws -> (Data, HTTPURLResponse)
}

public struct URLSessionTransport: HTTPTransport {
    let session: URLSession

    public init(timeout: TimeInterval = 120) {
        let config = URLSessionConfiguration.ephemeral
        config.timeoutIntervalForRequest = timeout
        config.timeoutIntervalForResource = timeout
        config.httpCookieStorage = nil
        config.urlCache = nil
        session = URLSession(configuration: config)
    }

    public func send(_ request: URLRequest) async throws -> (Data, HTTPURLResponse) {
        final class Box: @unchecked Sendable { var task: URLSessionDataTask? }
        let box = Box()
        return try await withTaskCancellationHandler {
            try await withCheckedThrowingContinuation { (cont: CheckedContinuation<(Data, HTTPURLResponse), Error>) in
                let task = session.dataTask(with: request) { data, response, error in
                    if let error { cont.resume(throwing: error); return }
                    guard let http = response as? HTTPURLResponse else {
                        cont.resume(throwing: URLError(.badServerResponse)); return
                    }
                    cont.resume(returning: (data ?? Data(), http))
                }
                box.task = task
                task.resume()
            }
        } onCancel: {
            box.task?.cancel()
        }
    }
}

/// Every provider returns the raw text of a JSON answer; parsing and validation happen once, in the caller.
public protocol AIProvider: Sendable {
    var id: AIProviderID { get }
    func ask(_ req: AIRequest) async throws -> String
    func test(apiKey: String, model: String, baseURL: String?) async throws
}

public enum AIConfigValidation {
    private static let modelRe = try! NSRegularExpression(pattern: "^[\\w.:/-]{1,100}$")

    public static func isValidModel(_ model: String) -> Bool {
        let m = model.trimmingCharacters(in: .whitespaces)
        return modelRe.firstMatch(in: m, range: NSRange(m.startIndex..., in: m)) != nil
    }

    /// Accepts only https base URLs (or loopback http for local servers) without credentials, query or fragment.
    public static func normalizeBaseURL(_ raw: String) -> String? {
        var s = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        while s.hasSuffix("/") { s.removeLast() }
        guard let c = UrlPolicy.webURL(s), let host = c.host else { return nil }
        if c.user != nil || c.password != nil || c.query != nil || c.fragment != nil { return nil }
        if c.scheme?.lowercased() != "https" && !UrlPolicy.isLoopbackHost(host) { return nil }
        return s
    }
}

private func jsonBody(_ object: [String: Any]) throws -> Data {
    try JSONSerialization.data(withJSONObject: object, options: [])
}

private func mapTransportError(_ error: Error, label: String) -> Error {
    if error is ProviderError { return error }
    if error is CancellationError { return ProviderError("Cancelled.", .cancelled) }
    if let u = error as? URLError, u.code == .cancelled { return ProviderError("Cancelled.", .cancelled) }
    return ProviderError("Could not reach \(label). Check your internet connection.", .network)
}

/// OpenAI Chat Completions. Also serves "OpenAI-compatible" services when given a base URL; those get
/// JSON mode instead of strict schemas, because many compatible servers do not support strict mode.
public struct OpenAIProvider: AIProvider {
    public let id: AIProviderID
    let transport: HTTPTransport

    public init(id: AIProviderID = .openai, transport: HTTPTransport = URLSessionTransport()) {
        self.id = id
        self.transport = transport
    }

    private var label: String { id == .openai ? "OpenAI" : "the AI service" }

    func base(_ baseURL: String?) throws -> String {
        if id == .openai && baseURL == nil { return "https://api.openai.com/v1" }
        guard let b = baseURL.flatMap(AIConfigValidation.normalizeBaseURL) else {
            throw ProviderError("Enter a valid https base URL for the AI service in Settings.", .config)
        }
        return b
    }

    public func ask(_ req: AIRequest) async throws -> String {
        let strict = id == .openai
        let system = strict ? StudyAssistant.systemPrompt
            : StudyAssistant.systemPrompt + "\n\nThe JSON must match this JSON Schema:\n" + StudyAssistant.answerJSONSchemaString
        let format: [String: Any] = strict
            ? ["type": "json_schema", "json_schema": ["name": "study_answer", "strict": true, "schema": StudyAssistant.answerJSONSchema]]
            : ["type": "json_object"]
        let body: [String: Any] = [
            "model": req.model,
            "messages": [["role": "system", "content": system], ["role": "user", "content": req.userMessage]],
            "response_format": format,
        ]
        let data = try await execute(apiKey: req.apiKey, url: try base(req.baseURL) + "/chat/completions", body: try jsonBody(body))
        guard let root = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] else {
            throw ProviderError("The AI provider returned an unreadable response.", .invalidResponse)
        }
        let choice = (root["choices"] as? [[String: Any]])?.first
        let message = choice?["message"] as? [String: Any]
        if let refusal = message?["refusal"] as? String, !refusal.isEmpty {
            throw ProviderError("The AI provider declined to answer this request.", .refused)
        }
        if choice?["finish_reason"] as? String == "length" {
            throw ProviderError("The AI response was cut off before it finished.", .invalidResponse)
        }
        guard let content = message?["content"] as? String, !content.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            throw ProviderError("The AI provider returned an empty response.", .invalidResponse)
        }
        return content
    }

    public func test(apiKey: String, model: String, baseURL: String?) async throws {
        let path: String
        if id == .openai {
            path = "/models/" + (model.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed.subtracting(CharacterSet(charactersIn: "/"))) ?? model)
        } else {
            path = "/models"
        }
        _ = try await execute(apiKey: apiKey, url: try base(baseURL) + path, body: nil)
    }

    private func execute(apiKey: String, url: String, body: Data?) async throws -> Data {
        guard let u = URL(string: url) else { throw ProviderError("Invalid AI service address.", .config) }
        var r = URLRequest(url: u)
        r.setValue("Bearer \(apiKey)", forHTTPHeaderField: "Authorization")
        if let body {
            r.httpMethod = "POST"
            r.httpBody = body
            r.setValue("application/json", forHTTPHeaderField: "Content-Type")
        }
        let (data, res): (Data, HTTPURLResponse)
        do { (data, res) = try await transport.send(r) } catch { throw mapTransportError(error, label: label) }
        switch res.statusCode {
        case 200..<300: return data
        case 401, 403: throw ProviderError("The \(label) API key was rejected. Check it in Settings.", .auth)
        case 404: throw ProviderError("The selected \(label) model was not found. Check the model name in Settings.", .auth)
        case 429: throw ProviderError("\(label) rate limit or billing limit reached. Try again later.", .network)
        default: throw ProviderError("\(label) is unavailable right now (HTTP \(res.statusCode)).", .network)
        }
    }
}

/// Anthropic Messages API over HTTPS, with structured JSON output. There is no official Anthropic SDK
/// for Swift, so this uses the documented REST interface directly.
public struct AnthropicProvider: AIProvider {
    public let id: AIProviderID = .anthropic
    let transport: HTTPTransport
    let baseURL: String

    public static let apiVersion = "2026-10-21"
    /// Models that accept the server-side refusal fallback.
    static let fallbackModels: Set<String> = ["claude-opus-5", "claude-opus-5-5", "claude-sonnet-5-5", "claude-fable-5-1"]

    public init(transport: HTTPTransport = URLSessionTransport(), baseURL: String = "https://api.anthropic.com") {
        self.transport = transport
        self.baseURL = baseURL
    }

    /// The request body, exposed for tests.
    func body(for req: AIRequest) -> [String: Any] {
        var body: [String: Any] = [
            "model": req.model,
            "max_tokens": 16000,
            "system": StudyAssistant.systemPrompt,
            "messages": [["role": "user", "content": req.userMessage]],
            "output_config": [
                "effort": "low",
                "format": ["type": "json_schema", "schema": StudyAssistant.answerJSONSchema],
            ] as [String: Any],
        ]
        if Self.fallbackModels.contains(req.model) {
            // Server-side refusal fallback: the API retries a declined request on a suitable model.
            body["fallbacks"] = "default"
        }
        return body
    }

    public func ask(_ req: AIRequest) async throws -> String {
        var r = request(path: "/v1/messages", apiKey: req.apiKey)
        r.httpMethod = "POST"
        r.httpBody = try jsonBody(body(for: req))
        r.setValue("application/json", forHTTPHeaderField: "Content-Type")
        if Self.fallbackModels.contains(req.model) {
            r.setValue("server-side-fallback-2026-07-01", forHTTPHeaderField: "anthropic-beta")
        }
        let data = try await execute(r)
        guard let root = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] else {
            throw ProviderError("The AI provider returned an unreadable response.", .invalidResponse)
        }
        switch root["stop_reason"] as? String {
        case "refusal": throw ProviderError("The AI provider declined to answer this request.", .refused)
        case "max_tokens": throw ProviderError("The AI response was cut off before it finished.", .invalidResponse)
        default: break
        }
        let blocks = root["content"] as? [[String: Any]] ?? []
        let text = blocks.compactMap { $0["type"] as? String == "text" ? $0["text"] as? String : nil }.joined()
        if text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            throw ProviderError("The AI provider returned an empty response.", .invalidResponse)
        }
        return text
    }

    public func test(apiKey: String, model: String, baseURL: String?) async throws {
        let id = model.addingPercentEncoding(withAllowedCharacters: .alphanumerics.union(CharacterSet(charactersIn: "-._"))) ?? model
        _ = try await execute(request(path: "/v1/models/" + id, apiKey: apiKey))
    }

    private func request(path: String, apiKey: String) -> URLRequest {
        var r = URLRequest(url: URL(string: baseURL + path)!)
        r.setValue(apiKey, forHTTPHeaderField: "x-api-key")
        r.setValue(Self.apiVersion, forHTTPHeaderField: "anthropic-version")
        return r
    }

    private func execute(_ r: URLRequest) async throws -> Data {
        let (data, res): (Data, HTTPURLResponse)
        do { (data, res) = try await transport.send(r) } catch { throw mapTransportError(error, label: "Anthropic") }
        switch res.statusCode {
        case 200..<300: return data
        case 401, 403: throw ProviderError("The Anthropic API key was rejected. Check it in Settings.", .auth)
        case 404: throw ProviderError("The selected Claude model was not found. Check the model name in Settings.", .auth)
        case 429: throw ProviderError("Anthropic rate limit or spending limit reached. Try again later.", .network)
        case 400: throw ProviderError("Anthropic rejected the request. The model may not support this feature.", .unknown)
        default: throw ProviderError("Anthropic is unavailable right now (HTTP \(res.statusCode)).", .network)
        }
    }
}

/// Offline provider for trying the assistant and for automated tests. Its answers are canned and
/// clearly labelled as a demo; it never contacts the network.
public struct DemoProvider: AIProvider {
    public let id: AIProviderID = .demo
    public init() {}

    public func ask(_ req: AIRequest) async throws -> String {
        try await Task.sleep(nanoseconds: 300_000_000)
        var material = req.userMessage
        if let start = material.range(of: "<student_material>\n"), let end = material.range(of: "\n</student_material>", options: .backwards) {
            material = String(material[start.upperBound..<end.lowerBound])
        }
        let firstLine = material.split(separator: "\n").first { !$0.trimmingCharacters(in: .whitespaces).isEmpty }
            .map { String($0.trimmingCharacters(in: .whitespaces).prefix(120)) } ?? "Your material"
        let answer = StudyAnswer(
            title: firstLine,
            explanation: "This is the offline demo provider. It shows how Sophia presents an answer, but it does not analyse your material. Choose OpenAI, Anthropic or a compatible service in Settings and add your own API key to get real explanations.",
            keyPoints: ["No analysis was performed in demo mode."],
            flashcards: [Flashcard(front: "What does demo mode do?", back: "It shows the answer layout without contacting any AI service.")],
            practiceQuestions: [],
            missingInformation: ["A configured AI provider"],
            confidence: .low,
            warning: "Demo answer only. Do not rely on it."
        )
        return String(decoding: try JSONEncoder().encode(answer), as: UTF8.self)
    }

    public func test(apiKey: String, model: String, baseURL: String?) async throws {}
}

public enum AIProviders {
    public static func make(_ id: AIProviderID, transport: HTTPTransport = URLSessionTransport()) -> AIProvider? {
        switch id {
        case .off: return nil
        case .openai: return OpenAIProvider(id: .openai, transport: transport)
        case .compatible: return OpenAIProvider(id: .compatible, transport: transport)
        case .anthropic: return AnthropicProvider(transport: transport)
        case .demo: return DemoProvider()
        }
    }
}
