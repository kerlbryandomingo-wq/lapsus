import SwiftUI
import SwiftData
import UIKit
import SophiaKit

struct AssistantView: View {
    @State private var model: AssistantModel
    let embedded: Bool
    @Environment(\.dismiss) private var dismiss
    @Environment(\.horizontalSizeClass) private var hSize

    init(settings: SettingsStore, initialText: String, embedded: Bool = false) {
        _model = State(initialValue: AssistantModel(settings: settings, material: initialText))
        self.embedded = embedded
    }

    var body: some View {
        if embedded {
            content
        } else {
            NavigationStack {
                content
                    .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { model.cancel(); dismiss() } } }
            }
        }
    }

    @ViewBuilder private var content: some View {
        Group {
            if hSize == .regular {
                // iPad: material on the left, the answer on the right.
                HStack(spacing: 0) {
                    InputPane(model: model).frame(maxWidth: 460)
                    Divider()
                    ResultPane(model: model).frame(maxWidth: .infinity)
                }
            } else {
                switch model.phase {
                case .editing: InputPane(model: model)
                default: ResultPane(model: model)
                }
            }
        }
        .navigationTitle("Study Assistant")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                NavigationLink { AssistantSettings(settings: model.settings).navigationTitle("Study Assistant settings") } label: {
                    Image(systemName: "slider.horizontal.3")
                }
                .accessibilityLabel("Study Assistant settings")
            }
        }
    }
}

private struct InputPane: View {
    @Bindable var model: AssistantModel
    @FocusState private var editing: Bool

    var body: some View {
        Form {
            if let problem = model.setupProblem {
                Section {
                    Label(problem, systemImage: "exclamationmark.circle").foregroundStyle(.secondary)
                    NavigationLink("Set up the Study Assistant") { AssistantSettings(settings: model.settings).navigationTitle("Study Assistant settings") }
                    if model.provider == .off {
                        Button("Try the offline demo") { model.settings.aiProvider = .demo }
                    }
                }
            }
            Section("What should Sophia do?") {
                Picker("Mode", selection: $model.mode) {
                    ForEach(StudyMode.allCases) { Label($0.label, systemImage: $0.symbol).tag($0) }
                }
                .pickerStyle(.inline)
                .labelsHidden()
            }
            Section {
                TextEditor(text: $model.material)
                    .focused($editing)
                    .frame(minHeight: 160)
                    .accessibilityLabel("Study material")
                    .accessibilityIdentifier("assistant_material")
                HStack {
                    Button {
                        if let text = UIPasteboard.general.string { model.material = String(text.prefix(SophiaConstants.maxAIInputChars * 2)) }
                    } label: { Label("Paste", systemImage: "doc.on.clipboard") }
                    Spacer()
                    Text("\(model.material.count) / \(SophiaConstants.maxAIInputChars)")
                        .font(.caption.monospacedDigit())
                        .foregroundStyle(model.material.count > SophiaConstants.maxAIInputChars ? .orange : .secondary)
                }
                .buttonStyle(.borderless)
            } header: { Text("Your material") } footer: {
                Text("Paste notes or text from a lesson, or select text on a page and choose Ask Sophia.")
            }
            Section("Anything to add? (optional)") {
                TextField("For example: focus on the definitions", text: $model.note, axis: .vertical)
                    .lineLimit(1...3)
            }
            Section {
                Button {
                    editing = false
                    model.review()
                } label: {
                    Label("Review before sending", systemImage: "eye").frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .disabled(!model.canReview)
                .accessibilityIdentifier("assistant_review")
            } footer: {
                Text("Use the Study Assistant to understand your lessons. Don't use it during graded or monitored assessments; follow your course's rules.")
            }
        }
    }
}

private struct ResultPane: View {
    @Bindable var model: AssistantModel
    @Environment(\.modelContext) private var context

    var body: some View {
        switch model.phase {
        case .editing:
            ContentUnavailableView("Your answer appears here", systemImage: "sparkles",
                                   description: Text("Choose a mode, add your material, then review it before sending."))
        case .reviewing(let prepared):
            ReviewView(model: model, prepared: prepared)
        case .sending:
            VStack(spacing: 16) {
                ProgressView().controlSize(.large)
                Text("Asking \(model.provider.label)…").foregroundStyle(.secondary)
                Button("Cancel", role: .cancel) { model.cancel() }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        case .failed(let message):
            ContentUnavailableView {
                Label("No answer", systemImage: "exclamationmark.triangle")
            } description: {
                Text(message)
            } actions: {
                Button("Back to my material") { model.edit() }.buttonStyle(.borderedProminent)
            }
        case .answer(let answer):
            AnswerView(answer: answer, model: model)
                .toolbar {
                    ToolbarItemGroup(placement: .bottomBar) {
                        Button { model.edit() } label: { Label("Edit", systemImage: "pencil") }
                        Spacer()
                        Button { UIPasteboard.general.string = answer.plainText(); model.savedNotice = "Copied." } label: {
                            Label("Copy", systemImage: "doc.on.doc")
                        }
                        ShareLink(item: answer.plainText()) { Label("Share", systemImage: "square.and.arrow.up") }
                        Button { model.save(answer, in: context) } label: { Label("Save", systemImage: "square.and.arrow.down") }
                            .accessibilityIdentifier("assistant_save")
                    }
                }
        }
    }
}

/// Shows exactly what will leave the device, and where it goes, before anything is sent.
private struct ReviewView: View {
    let model: AssistantModel
    let prepared: PreparedInput

    var body: some View {
        Form {
            Section {
                LabeledContent("Mode", value: model.mode.label)
                LabeledContent("Sent to", value: model.destination)
                if model.provider != .demo {
                    LabeledContent("Model", value: model.settings.model(for: model.provider))
                }
            }
            if prepared.redactions > 0 || prepared.truncated {
                Section {
                    if prepared.redactions > 0 {
                        Label("\(prepared.redactions) item\(prepared.redactions == 1 ? "" : "s") that looked like passwords, keys, email addresses or ID numbers were removed.",
                              systemImage: "eye.slash")
                    }
                    if prepared.truncated {
                        Label("Only the first \(SophiaConstants.maxAIInputChars) characters will be sent.", systemImage: "scissors")
                    }
                }
                .font(.subheadline)
            }
            Section("Text that will be sent") {
                Text(prepared.text)
                    .font(.callout)
                    .textSelection(.enabled)
                    .accessibilityIdentifier("assistant_review_text")
                if !model.note.trimmingCharacters(in: .whitespaces).isEmpty {
                    Text("Your note: \(model.note)").font(.callout).foregroundStyle(.secondary)
                }
            }
            Section {
                Button { model.send() } label: { Label("Send", systemImage: "paperplane.fill").frame(maxWidth: .infinity) }
                    .buttonStyle(.borderedProminent)
                    .disabled(prepared.text.isEmpty)
                    .accessibilityIdentifier("assistant_send")
                Button("Edit") { model.edit() }.frame(maxWidth: .infinity)
            } footer: {
                Text("The provider's own privacy terms apply to what you send. Sophia doesn't keep a copy unless you save the answer.")
            }
        }
    }
}

struct AnswerView: View {
    let answer: StudyAnswer
    @Bindable var model: AssistantModel

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                Text(answer.title).font(.title2.bold()).accessibilityIdentifier("answer_title")
                if let warning = answer.warning {
                    Label(warning, systemImage: "exclamationmark.triangle.fill")
                        .font(.subheadline)
                        .padding(12)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color.orange.opacity(0.15), in: RoundedRectangle(cornerRadius: 12))
                }
                Text(answer.explanation).font(.body).textSelection(.enabled)
                if !answer.keyPoints.isEmpty {
                    AnswerSection("Key points") {
                        ForEach(Array(answer.keyPoints.enumerated()), id: \.offset) { _, p in
                            Label { Text(p).textSelection(.enabled) } icon: { Image(systemName: "circle.fill").font(.system(size: 6)) }
                        }
                    }
                }
                if !answer.flashcards.isEmpty {
                    AnswerSection("Flashcards") {
                        ForEach(Array(answer.flashcards.enumerated()), id: \.offset) { _, c in FlashcardView(card: c) }
                    }
                }
                if !answer.practiceQuestions.isEmpty {
                    AnswerSection("Practice questions") {
                        ForEach(Array(answer.practiceQuestions.enumerated()), id: \.offset) { i, q in PracticeView(number: i + 1, question: q) }
                    }
                }
                if !answer.missingInformation.isEmpty {
                    AnswerSection("Missing information") {
                        ForEach(Array(answer.missingInformation.enumerated()), id: \.offset) { _, m in Text("• \(m)").foregroundStyle(.secondary) }
                    }
                }
                Label("Confidence: \(answer.confidence.rawValue.capitalized)", systemImage: "gauge.with.dots.needle.33percent")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                Text("AI answers can be wrong. Check important facts against your course materials.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                if let notice = model.savedNotice {
                    Text(notice).font(.footnote.bold()).foregroundStyle(SophiaColor.maroon)
                }
            }
            .padding(20)
            .frame(maxWidth: 720, alignment: .leading)
            .frame(maxWidth: .infinity)
        }
    }
}

private struct AnswerSection<Content: View>: View {
    let title: String
    let content: Content
    init(_ title: String, @ViewBuilder content: () -> Content) { self.title = title; self.content = content() }
    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(title).font(.headline).accessibilityAddTraits(.isHeader)
            content
        }
    }
}

private struct FlashcardView: View {
    let card: Flashcard
    @State private var flipped = false

    var body: some View {
        Button { withAnimation(.snappy) { flipped.toggle() } } label: {
            VStack(alignment: .leading, spacing: 6) {
                Text(flipped ? "Answer" : "Question").font(.caption.bold()).foregroundStyle(.secondary)
                Text(flipped ? card.back : card.front).font(.body).frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(14)
            .background(RoundedRectangle(cornerRadius: 14).fill(flipped ? SophiaColor.maroon.opacity(0.12) : Color(.secondarySystemBackground)))
        }
        .buttonStyle(.plain)
        .accessibilityHint(flipped ? "Shows the question" : "Shows the answer")
    }
}

private struct PracticeView: View {
    let number: Int
    let question: PracticeQuestion
    @State private var revealed = false

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("\(number). \(question.question)").font(.body.weight(.medium))
            ForEach(Array(question.choices.enumerated()), id: \.offset) { j, c in
                Text("\(String(Character(UnicodeScalar(UInt8(65 + min(j, 25)))))). \(c)").font(.callout)
            }
            if revealed {
                Text("Answer: \(question.answer)").font(.callout.bold())
                Text(question.rationale).font(.callout).foregroundStyle(.secondary)
            } else {
                Button("Show answer") { withAnimation { revealed = true } }.font(.callout)
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 14).fill(Color(.secondarySystemBackground)))
    }
}

// MARK: - Settings

struct AssistantSettings: View {
    @Bindable var settings: SettingsStore
    private let keychain = KeychainStore()
    @State private var keyDraft = ""
    @State private var hasKey = false
    @State private var testing = false
    @State private var testResult: String?

    var body: some View {
        Form {
            Section {
                Picker("AI provider", selection: $settings.aiProvider) {
                    ForEach(AIProviderID.allCases) { Text($0.label).tag($0) }
                }
                .accessibilityIdentifier("assistant_provider")
            } footer: {
                Text("Sophia doesn't include an AI service. Use your own account with OpenAI, Anthropic or a compatible provider, or try the demo, which works offline and doesn't analyse anything.")
            }

            if settings.aiProvider.needsKey {
                Section("Model") {
                    TextField("Model name", text: modelBinding)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                    if settings.aiProvider == .compatible {
                        TextField("https://api.example.com/v1", text: $settings.compatibleBaseURL)
                            .keyboardType(.URL)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                        if !settings.compatibleBaseURL.isEmpty && AIConfigValidation.normalizeBaseURL(settings.compatibleBaseURL) == nil {
                            Text("Enter an https address without a user name, query or #.").font(.footnote).foregroundStyle(.red)
                        }
                    }
                }

                Section {
                    if hasKey {
                        Label("Key saved in the Keychain on this device", systemImage: "key.fill")
                        Button("Remove key", role: .destructive) {
                            keychain.deleteKey(for: settings.aiProvider)
                            refresh()
                        }
                    } else {
                        SecureField("Paste your API key", text: $keyDraft)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                            .accessibilityIdentifier("assistant_api_key")
                        Button("Save key") {
                            keychain.setKey(keyDraft, for: settings.aiProvider)
                            keyDraft = ""
                            refresh()
                        }
                        .disabled(keyDraft.trimmingCharacters(in: .whitespaces).isEmpty)
                    }
                    Button(testing ? "Testing…" : "Test connection") { Task { await test() } }
                        .disabled(!hasKey || testing)
                    if let testResult { Text(testResult).font(.footnote) }
                } header: { Text("API key") } footer: {
                    Text("Your key is stored only in this device's Keychain, isn't backed up to iCloud and is sent only to \(settings.aiProvider.destinationHost(baseURL: settings.compatibleBaseURL) ?? "the provider").")
                }
            }

            if settings.aiProvider != .off {
                Section("Answers") {
                    Picker("Detail", selection: $settings.responseDetail) {
                        ForEach(ResponseDetail.allCases) { Text($0.label).tag($0) }
                    }
                    .pickerStyle(.segmented)
                }
            }

            Section("Privacy") {
                Text("Before anything is sent you see the exact text, with passwords, keys, email addresses and long ID numbers removed. Nothing is sent in the background, and the assistant never reads pages by itself.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
        }
        .onAppear(perform: refresh)
        .onChange(of: settings.aiProvider) { _, _ in refresh() }
    }

    private var modelBinding: Binding<String> {
        Binding(
            get: { settings.model(for: settings.aiProvider) },
            set: { v in
                switch settings.aiProvider {
                case .openai: settings.openAIModel = v
                case .anthropic: settings.anthropicModel = v
                case .compatible: settings.compatibleModel = v
                default: break
                }
            }
        )
    }

    private func refresh() {
        hasKey = keychain.hasKey(for: settings.aiProvider)
        testResult = nil
    }

    @MainActor
    private func test() async {
        guard let provider = AIProviders.make(settings.aiProvider), let key = keychain.key(for: settings.aiProvider) else { return }
        testing = true
        defer { testing = false }
        do {
            try await provider.test(apiKey: key, model: settings.model(for: settings.aiProvider).trimmingCharacters(in: .whitespaces),
                                    baseURL: settings.aiProvider == .compatible ? AIConfigValidation.normalizeBaseURL(settings.compatibleBaseURL) : nil)
            testResult = "Connected. The key and model work."
        } catch let e as ProviderError {
            testResult = e.message
        } catch {
            testResult = "The test failed."
        }
    }
}

// MARK: - Saved notes

struct NotesView: View {
    @Query(sort: \SavedNote.createdAt, order: .reverse) private var notes: [SavedNote]
    @Environment(\.modelContext) private var context

    var body: some View {
        List {
            if notes.isEmpty {
                ContentUnavailableView("No saved notes", systemImage: "note.text",
                                       description: Text("Answers you save from the Study Assistant appear here."))
            }
            ForEach(notes) { n in
                NavigationLink {
                    ScrollView {
                        Text(n.body).textSelection(.enabled).padding(20).frame(maxWidth: 720, alignment: .leading)
                    }
                    .navigationTitle(n.title)
                    .toolbar {
                        ToolbarItem(placement: .primaryAction) { ShareLink(item: n.body) }
                    }
                } label: {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(n.title).lineLimit(1)
                        Text("\(n.mode) · \(n.createdAt.formatted(date: .abbreviated, time: .shortened))").font(.caption).foregroundStyle(.secondary)
                    }
                }
            }
            .onDelete { idx in
                for i in idx { context.delete(notes[i]) }
                try? context.save()
            }
        }
        .navigationTitle("Saved notes")
    }
}
