import XCTest
import Foundation
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif
@testable import SophiaKit

final class StubTransport: HTTPTransport, @unchecked Sendable {
    var status = 200
    var body = Data()
    var error: Error?
    private(set) var requests: [URLRequest] = []

    func send(_ request: URLRequest) async throws -> (Data, HTTPURLResponse) {
        requests.append(request)
        if let error { throw error }
        return (body, HTTPURLResponse(url: request.url!, statusCode: status, httpVersion: nil, headerFields: nil)!)
    }

    func json(_ object: Any) { body = try! JSONSerialization.data(withJSONObject: object) }
}

final class ProviderTests: XCTestCase {
    func testAnthropicRequestShape() async throws {
        let t = StubTransport()
        t.json(["stop_reason": "end_turn", "content": [["type": "text", "text": StudyAssistantTests.validJSON]]])
        let p = AnthropicProvider(transport: t)
        let text = try await p.ask(AIRequest(apiKey: "test-key", model: "claude-opus-5-5", userMessage: "hi"))
        XCTAssertEqual(text, StudyAssistantTests.validJSON)
        let r = try XCTUnwrap(t.requests.first)
        XCTAssertEqual(r.url?.absoluteString, "https://api.anthropic.com/v1/messages")
        XCTAssertEqual(r.value(forHTTPHeaderField: "x-api-key"), "test-key")
        XCTAssertEqual(r.value(forHTTPHeaderField: "anthropic-version"), AnthropicProvider.apiVersion)
        XCTAssertEqual(r.value(forHTTPHeaderField: "anthropic-beta"), "server-side-fallback-2026-07-01")
        let body = try XCTUnwrap(JSONSerialization.jsonObject(with: r.httpBody!) as? [String: Any])
        XCTAssertEqual(body["model"] as? String, "claude-opus-5-5")
        XCTAssertEqual(body["fallbacks"] as? String, "default")
        XCTAssertNil(body["temperature"])
        let config = try XCTUnwrap(body["output_config"] as? [String: Any])
        XCTAssertEqual((config["format"] as? [String: Any])?["type"] as? String, "json_schema")
    }

    func testAnthropicOtherModelsSkipFallback() async throws {
        let t = StubTransport()
        t.json(["stop_reason": "end_turn", "content": [["type": "text", "text": "{}"]]])
        _ = try await AnthropicProvider(transport: t).ask(AIRequest(apiKey: "k", model: "claude-haiku-5-5", userMessage: "hi"))
        XCTAssertNil(t.requests[0].value(forHTTPHeaderField: "anthropic-beta"))
    }

    func testAnthropicRefusalAndErrors() async {
        let t = StubTransport()
        t.json(["stop_reason": "refusal", "content": []])
        await assertThrows(.refused) { try await AnthropicProvider(transport: t).ask(AIRequest(apiKey: "k", model: "m", userMessage: "x")) }
        t.status = 401
        await assertThrows(.auth) { try await AnthropicProvider(transport: t).ask(AIRequest(apiKey: "k", model: "m", userMessage: "x")) }
        t.status = 200
        t.error = URLError(.notConnectedToInternet)
        await assertThrows(.network) { try await AnthropicProvider(transport: t).ask(AIRequest(apiKey: "k", model: "m", userMessage: "x")) }
    }

    func testOpenAIStrictSchemaAndRefusal() async throws {
        let t = StubTransport()
        t.json(["choices": [["message": ["content": "{\"a\":1}"], "finish_reason": "stop"]]])
        let p = OpenAIProvider(transport: t)
        let out = try await p.ask(AIRequest(apiKey: "k", model: "gpt-5-mini", userMessage: "x"))
        XCTAssertEqual(out, "{\"a\":1}")
        let r = t.requests[0]
        XCTAssertEqual(r.url?.absoluteString, "https://api.openai.com/v1/chat/completions")
        XCTAssertEqual(r.value(forHTTPHeaderField: "Authorization"), "Bearer k")
        let body = try XCTUnwrap(JSONSerialization.jsonObject(with: r.httpBody!) as? [String: Any])
        XCTAssertEqual((body["response_format"] as? [String: Any])?["type"] as? String, "json_schema")

        t.json(["choices": [["message": ["refusal": "no"], "finish_reason": "stop"]]])
        await assertThrows(.refused) { try await p.ask(AIRequest(apiKey: "k", model: "m", userMessage: "x")) }
    }

    func testCompatibleNeedsHttpsBaseURL() async throws {
        let t = StubTransport()
        let p = OpenAIProvider(id: .compatible, transport: t)
        await assertThrows(.config) { try await p.ask(AIRequest(apiKey: "k", model: "m", userMessage: "x", baseURL: "http://example.com/v1")) }
        t.json(["choices": [["message": ["content": "{}"], "finish_reason": "stop"]]])
        _ = try await p.ask(AIRequest(apiKey: "k", model: "m", userMessage: "x", baseURL: "https://llm.example.com/v1/"))
        XCTAssertEqual(t.requests.last?.url?.absoluteString, "https://llm.example.com/v1/chat/completions")
        let body = try XCTUnwrap(JSONSerialization.jsonObject(with: t.requests.last!.httpBody!) as? [String: Any])
        XCTAssertEqual((body["response_format"] as? [String: Any])?["type"] as? String, "json_object")
    }

    func testValidation() {
        XCTAssertTrue(AIConfigValidation.isValidModel("claude-opus-5-5"))
        XCTAssertFalse(AIConfigValidation.isValidModel("bad model"))
        XCTAssertNil(AIConfigValidation.normalizeBaseURL("https://u:p@x.com/v1"))
        XCTAssertNil(AIConfigValidation.normalizeBaseURL("https://x.com/v1?a=1"))
        XCTAssertEqual(AIConfigValidation.normalizeBaseURL("http://localhost:11434/v1"), "http://localhost:11434/v1")
    }

    func testDemoProviderProducesParsableAnswer() async throws {
        let msg = StudyAssistant.buildUserMessage(mode: .explain, material: "Mitosis\nmore", note: "")
        let raw = try await DemoProvider().ask(AIRequest(apiKey: "", model: "demo", userMessage: msg))
        guard case .ok(let a) = StudyAssistant.parseAnswer(raw) else { return XCTFail("demo answer must parse") }
        XCTAssertEqual(a.title, "Mitosis")
        XCTAssertEqual(a.confidence, .low)
    }

    private func assertThrows(_ code: ProviderError.Code, file: StaticString = #filePath, line: UInt = #line, _ body: () async throws -> Void) async {
        do {
            try await body()
            XCTFail("expected \(code)", file: file, line: line)
        } catch let e as ProviderError {
            XCTAssertEqual(e.code, code, e.message, file: file, line: line)
        } catch {
            XCTFail("unexpected \(error)", file: file, line: line)
        }
    }
}
