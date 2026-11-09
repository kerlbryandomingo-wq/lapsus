import Foundation
import Observation
import SwiftData
import SophiaKit

/// The Study Assistant's flow: write or paste material, review exactly what will be sent, send it to
/// the provider the student configured, then read, copy or save the structured answer.
@MainActor
@Observable
final class AssistantModel {
    enum Phase: Equatable {
        case editing
        case reviewing(PreparedInput)
        case sending
        case answer(StudyAnswer)
        case failed(String)
    }

    let settings: SettingsStore
    @ObservationIgnored let keychain: KeychainStore
    @ObservationIgnored private let makeProvider: (AIProviderID) -> AIProvider?
    @ObservationIgnored private var task: Task<Void, Never>?

    var mode: StudyMode = .explain
    var material: String
    var note = ""
    var phase: Phase = .editing
    var savedNotice: String?

    init(settings: SettingsStore, material: String = "", keychain: KeychainStore = KeychainStore(),
         makeProvider: @escaping (AIProviderID) -> AIProvider? = { AIProviders.make($0) }) {
        self.settings = settings
        self.material = material
        self.keychain = keychain
        self.makeProvider = makeProvider
    }

    var provider: AIProviderID { settings.aiProvider }

    /// Why sending isn't possible yet, or nil when it is.
    var setupProblem: String? {
        switch provider {
        case .off:
            return "Choose an AI provider in Study Assistant settings, or try the offline demo."
        case .demo:
            return nil
        case .openai, .anthropic, .compatible:
            if !keychain.hasKey(for: provider) { return "Add your \(provider.label) API key in Study Assistant settings." }
            if !AIConfigValidation.isValidModel(settings.model(for: provider)) { return "Enter a model name in Study Assistant settings." }
            if provider == .compatible && AIConfigValidation.normalizeBaseURL(settings.compatibleBaseURL) == nil {
                return "Enter the https address of your AI service in Study Assistant settings."
            }
            return nil
        }
    }

    var destination: String {
        provider.destinationHost(baseURL: settings.compatibleBaseURL) ?? "nowhere (demo, works offline)"
    }

    var canReview: Bool { !material.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && setupProblem == nil }

    func review() {
        guard canReview else { return }
        phase = .reviewing(StudyAssistant.prepareInput(material))
    }

    func edit() {
        cancel()
        phase = .editing
    }

    func send() {
        guard case .reviewing(let prepared) = phase, let p = makeProvider(provider) else { return }
        let request = AIRequest(
            apiKey: keychain.key(for: provider) ?? "",
            model: settings.model(for: provider).trimmingCharacters(in: .whitespaces),
            userMessage: StudyAssistant.buildUserMessage(mode: mode, material: prepared.text, note: note, detail: settings.responseDetail),
            baseURL: provider == .compatible ? AIConfigValidation.normalizeBaseURL(settings.compatibleBaseURL) : nil
        )
        phase = .sending
        task = Task { [weak self] in
            do {
                let raw = try await p.ask(request)
                guard let self, !Task.isCancelled else { return }
                switch StudyAssistant.parseAnswer(raw) {
                case .ok(let answer): self.phase = .answer(answer)
                case .error(let message): self.phase = .failed(message)
                }
            } catch let e as ProviderError {
                if e.code != .cancelled { self?.phase = .failed(e.message) }
            } catch {
                if !Task.isCancelled { self?.phase = .failed("The AI request failed.") }
            }
        }
    }

    func cancel() {
        task?.cancel()
        task = nil
        if phase == .sending { phase = .editing }
    }

    func startOver() {
        cancel()
        material = ""
        note = ""
        phase = .editing
    }

    func save(_ answer: StudyAnswer, in context: ModelContext) {
        context.insert(SavedNote(title: answer.title, body: answer.plainText(), mode: mode.label))
        try? context.save()
        savedNotice = "Saved to Saved notes."
    }
}
