import SwiftUI
import WebKit
import SwiftData
import SophiaKit

enum SettingsPage: String, CaseIterable, Identifiable, Hashable {
    case assistant, notes, downloadsList, history
    case appearance, browser, bookmarks, downloads, privacy, studyAssistant, accessibility, about

    var id: String { rawValue }

    var title: String {
        switch self {
        case .assistant: return "Study Assistant"
        case .notes: return "Saved notes"
        case .downloadsList: return "Downloads"
        case .history: return "History"
        case .appearance: return "Appearance"
        case .browser: return "Browser"
        case .bookmarks: return "Bookmarks"
        case .downloads: return "Download preferences"
        case .privacy: return "Privacy and Security"
        case .studyAssistant: return "Study Assistant settings"
        case .accessibility: return "Accessibility"
        case .about: return "About Sophia"
        }
    }

    var symbol: String {
        switch self {
        case .assistant: return "sparkles"
        case .notes: return "note.text"
        case .downloadsList: return "arrow.down.circle"
        case .history: return "clock"
        case .appearance: return "circle.lefthalf.filled"
        case .browser: return "globe"
        case .bookmarks: return "bookmark"
        case .downloads: return "tray.and.arrow.down"
        case .privacy: return "hand.raised"
        case .studyAssistant: return "brain.head.profile"
        case .accessibility: return "accessibility"
        case .about: return "info.circle"
        }
    }

    static let tools: [SettingsPage] = [.assistant, .notes, .downloadsList, .history]
    static let preferences: [SettingsPage] = [.appearance, .browser, .bookmarks, .downloads, .privacy, .studyAssistant, .accessibility, .about]
}

/// Sophia's settings. iPhone: a list that pushes each page. iPad (regular width): a sidebar with the
/// page beside it, so the larger screen shows both at once.
struct SettingsView: View {
    let model: BrowserModel
    @Environment(\.horizontalSizeClass) private var hSize
    @Environment(\.dismiss) private var dismiss
    @State private var selection: SettingsPage? = .browser
    @State private var path: [SettingsPage] = []

    var body: some View {
        if hSize == .regular {
            NavigationSplitView {
                List(selection: $selection) {
                    Section { header }
                    Section("Sophia") { ForEach(SettingsPage.tools) { row($0) } }
                    Section("Settings") { ForEach(SettingsPage.preferences) { row($0) } }
                }
                .navigationTitle("Settings")
                .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } } }
            } detail: {
                NavigationStack {
                    SettingsPageView(page: selection ?? .browser, model: model)
                }
            }
            .navigationSplitViewStyle(.balanced)
        } else {
            NavigationStack(path: $path) {
                List {
                    Section { header }
                    Section("Sophia") { ForEach(SettingsPage.tools) { link($0) } }
                    Section("Settings") { ForEach(SettingsPage.preferences) { link($0) } }
                }
                .navigationTitle("Settings")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } } }
                .navigationDestination(for: SettingsPage.self) { SettingsPageView(page: $0, model: model) }
            }
        }
    }

    private var header: some View {
        HStack(spacing: 14) {
            SophiaMark(size: 48)
            VStack(alignment: .leading, spacing: 2) {
                Text(SophiaConstants.appName).font(.title3.bold())
                Text(SophiaConstants.tagline).font(.subheadline).foregroundStyle(.secondary)
            }
        }
        .padding(.vertical, 4)
        .accessibilityElement(children: .combine)
    }

    private func row(_ p: SettingsPage) -> some View {
        Label(p.title, systemImage: p.symbol).tag(p).accessibilityIdentifier("settings_\(p.rawValue)")
    }

    private func link(_ p: SettingsPage) -> some View {
        NavigationLink(value: p) { Label(p.title, systemImage: p.symbol) }
            .accessibilityIdentifier("settings_\(p.rawValue)")
    }
}

struct SettingsPageView: View {
    let page: SettingsPage
    let model: BrowserModel

    var body: some View {
        Group {
            switch page {
            case .assistant: AssistantView(settings: model.settings, initialText: "", embedded: true)
            case .notes: NotesView()
            case .downloadsList: DownloadsView(manager: model.downloads)
            case .history: HistoryView { url in model.sheet = nil; model.open(url) }
            case .appearance: AppearanceSettings(settings: model.settings)
            case .browser: BrowserSettings(settings: model.settings)
            case .bookmarks: BookmarkSettings(settings: model.settings)
            case .downloads: DownloadSettings(settings: model.settings)
            case .privacy: PrivacySettings(settings: model.settings)
            case .studyAssistant: AssistantSettings(settings: model.settings)
            case .accessibility: AccessibilitySettings(settings: model.settings)
            case .about: AboutView()
            }
        }
        .navigationTitle(page.title)
        .navigationBarTitleDisplayMode(.inline)
    }
}

// MARK: - Pages

struct AppearanceSettings: View {
    @Bindable var settings: SettingsStore

    var body: some View {
        Form {
            Section {
                Picker("Appearance", selection: $settings.appearance) {
                    ForEach(AppearanceMode.allCases) { Text($0.label).tag($0) }
                }
                .pickerStyle(.inline)
                .labelsHidden()
            } footer: {
                Text("Changes Sophia's own screens. Websites keep their original colours and follow their own light or dark design.")
            }
            Section {
                Toggle("Full-screen browsing", isOn: $settings.hideStatusBar)
            } footer: {
                Text("Hides the status bar and dims the Home indicator while you browse, so the page uses the whole screen.")
            }
        }
    }
}

struct BrowserSettings: View {
    @Bindable var settings: SettingsStore
    @State private var homepageDraft = ""
    @State private var homepageError: String?

    var body: some View {
        Form {
            Section {
                TextField("https://", text: $homepageDraft)
                    .keyboardType(.URL)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .onSubmit(saveHomepage)
                    .accessibilityIdentifier("settings_homepage")
                if let homepageError { Text(homepageError).font(.footnote).foregroundStyle(.red) }
                Button("Save homepage", action: saveHomepage)
                Button("Use Discipulus") {
                    settings.homepage = SophiaConstants.defaultPortalURL
                    homepageDraft = settings.homepage
                    homepageError = nil
                }
            } header: { Text("Homepage") } footer: { Text("Sophia opens this page when it starts. Only secure (https) addresses are accepted.") }

            Section("Search") {
                Picker("Search engine", selection: $settings.searchEngine) {
                    ForEach(SearchEngine.allCases) { Text($0.label).tag($0) }
                }
            }

            Section {
                Toggle("JavaScript", isOn: $settings.javaScriptEnabled)
                Picker("Open new windows in", selection: $settings.newWindowMode) {
                    ForEach(NewWindowMode.allCases) { Text($0.label).tag($0) }
                }
                Toggle("Save browsing history", isOn: $settings.historyEnabled)
            } header: { Text("Pages") } footer: {
                Text("Discipulus needs JavaScript. Pop-up windows, such as sign-in windows, open only after a tap; choose Same page if a site's pop-ups don't work for you.")
            }
        }
        .onAppear { homepageDraft = settings.homepage }
    }

    private func saveHomepage() {
        if let valid = UrlPolicy.validateUserURL(homepageDraft) {
            settings.homepage = valid
            homepageDraft = valid
            homepageError = nil
        } else {
            homepageError = "Enter a secure web address, such as https://discipulusv2.amasystem.net/"
        }
    }
}

struct BookmarkSettings: View {
    @Bindable var settings: SettingsStore

    var body: some View {
        Form {
            Section {
                LabeledContent("Discipulus", value: UrlPolicy.displayURL(SophiaConstants.defaultPortalURL))
            } footer: { Text("Built in.") }
            BookmarkField(title: "LMS", value: $settings.lmsURL, id: "settings_lms_url")
            BookmarkField(title: "Aurora Login", value: $settings.auroraURL, id: "settings_aurora_url")
            Section {
                Text("Sophia doesn't include addresses for the LMS or Aurora Login, because they aren't published yet. Enter the address your school gave you, and the bookmark in the left panel will open it.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
        }
    }
}

private struct BookmarkField: View {
    let title: String
    @Binding var value: String
    let id: String
    @State private var draft = ""
    @State private var error: String?

    var body: some View {
        Section(title) {
            TextField("https://", text: $draft)
                .keyboardType(.URL)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .onSubmit(save)
                .accessibilityIdentifier(id)
            if let error { Text(error).font(.footnote).foregroundStyle(.red) }
            HStack {
                Button("Save", action: save)
                Spacer()
                if !value.isEmpty {
                    Button("Remove", role: .destructive) { value = ""; draft = ""; error = nil }
                }
            }
            .buttonStyle(.borderless)
        }
        .onAppear { draft = value }
    }

    private func save() {
        if draft.trimmingCharacters(in: .whitespaces).isEmpty { value = ""; error = nil; return }
        if let valid = UrlPolicy.validateUserURL(draft) {
            value = valid
            draft = valid
            error = nil
        } else {
            error = "Enter a secure web address that starts with https://"
        }
    }
}

struct DownloadSettings: View {
    @Bindable var settings: SettingsStore

    var body: some View {
        Form {
            Section {
                Toggle("Ask before downloading", isOn: $settings.askBeforeDownloading)
                Toggle("Open files when they finish", isOn: $settings.openDownloadsWhenDone)
            } footer: {
                Text("Downloads are saved in Sophia's Downloads folder, which you can also open in the Files app under On My iPhone or On My iPad > Sophia.")
            }
        }
    }
}

struct PrivacySettings: View {
    @Bindable var settings: SettingsStore
    @Environment(\.modelContext) private var context
    @State private var confirm: ClearAction?
    @State private var done: String?

    enum ClearAction: String, Identifiable {
        case history, cookies, cache, everything, reset
        var id: String { rawValue }
        var title: String {
            switch self {
            case .history: return "Clear browsing history?"
            case .cookies: return "Clear cookies and site data?"
            case .cache: return "Clear cache?"
            case .everything: return "Clear all browsing data?"
            case .reset: return "Reset all settings?"
            }
        }
        var detail: String {
            switch self {
            case .history: return "Removes every page from History on this device."
            case .cookies: return "Signs you out of Discipulus and other sites."
            case .cache: return "Removes stored copies of pages and images. Sites may load more slowly the next time."
            case .everything: return "Removes history, cookies, site data and the cache. You'll be signed out of every site."
            case .reset: return "Restores every setting and removes your saved AI API keys. Your history and downloads stay."
            }
        }
    }

    var body: some View {
        Form {
            Section {
                Button("Clear browsing history") { confirm = .history }
                Button("Clear cookies and site data") { confirm = .cookies }
                Button("Clear cache") { confirm = .cache }
                Button("Clear all browsing data", role: .destructive) { confirm = .everything }
            } header: { Text("Browsing data") } footer: {
                if let done { Text(done) }
            }

            Section {
                NavigationLink("Website permissions") { SitePermissionsView(settings: settings) }
            } footer: { Text("Sites ask before they can use the camera or microphone. Sophia never grants them on its own.") }

            Section("How Sophia protects you") {
                PrivacyPoint(symbol: "lock.shield", text: "Pages open only over secure https connections, and certificates are always checked.")
                PrivacyPoint(symbol: "iphone", text: "History, downloads and saved notes stay on this device. Sophia has no account and no servers.")
                PrivacyPoint(symbol: "key", text: "AI API keys are kept in the iOS Keychain on this device only.")
                PrivacyPoint(symbol: "eye.slash", text: "The Study Assistant sends text only when you review it and tap Send, and only to the provider you chose.")
                PrivacyPoint(symbol: "chart.bar.xaxis", text: "No analytics, ads or tracking.")
                Link("Privacy policy", destination: URL(string: SophiaConstants.privacyPolicyURL)!)
            }

            Section {
                Button("Reset all settings", role: .destructive) { confirm = .reset }
            }
        }
        .confirmationDialog(confirm?.title ?? "", isPresented: Binding(get: { confirm != nil }, set: { if !$0 { confirm = nil } }),
                            titleVisibility: .visible, presenting: confirm) { action in
            Button("Clear", role: .destructive) { Task { await perform(action) } }
        } message: { Text($0.detail) }
    }

    @MainActor
    private func perform(_ action: ClearAction) async {
        let store = WKWebsiteDataStore.default()
        let siteData: Set<String> = [WKWebsiteDataTypeCookies, WKWebsiteDataTypeLocalStorage, WKWebsiteDataTypeSessionStorage,
                                     WKWebsiteDataTypeIndexedDBDatabases, WKWebsiteDataTypeWebSQLDatabases,
                                     WKWebsiteDataTypeServiceWorkerRegistrations]
        let cache: Set<String> = [WKWebsiteDataTypeDiskCache, WKWebsiteDataTypeMemoryCache, WKWebsiteDataTypeFetchCache,
                                  WKWebsiteDataTypeOfflineWebApplicationCache]
        switch action {
        case .history:
            HistoryRecorder(context: context).clear()
            done = "History cleared."
        case .cookies:
            await store.removeData(ofTypes: siteData, modifiedSince: .distantPast)
            done = "Cookies and site data cleared."
        case .cache:
            await store.removeData(ofTypes: cache, modifiedSince: .distantPast)
            done = "Cache cleared."
        case .everything:
            HistoryRecorder(context: context).clear()
            await store.removeData(ofTypes: WKWebsiteDataStore.allWebsiteDataTypes(), modifiedSince: .distantPast)
            done = "All browsing data cleared."
        case .reset:
            settings.resetAll()
            KeychainStore().deleteAll()
            done = "Settings reset."
        }
    }
}

private struct PrivacyPoint: View {
    let symbol: String
    let text: String
    var body: some View {
        Label { Text(text).font(.subheadline) } icon: { Image(systemName: symbol).foregroundStyle(SophiaColor.maroon) }
    }
}

struct SitePermissionsView: View {
    @Bindable var settings: SettingsStore

    var body: some View {
        List {
            if settings.sitePermissions.isEmpty {
                ContentUnavailableView("No website permissions", systemImage: "hand.raised",
                                       description: Text("When you choose Always allow for a site's camera or microphone request, it appears here."))
            }
            ForEach(settings.sitePermissions) { p in
                VStack(alignment: .leading, spacing: 2) {
                    Text(UrlPolicy.host(of: p.origin)).font(.body)
                    Text("\(p.kind.label): \(p.allowed ? "Allowed" : "Blocked")").font(.caption).foregroundStyle(.secondary)
                }
            }
            .onDelete { settings.sitePermissions.remove(atOffsets: $0) }
        }
        .navigationTitle("Website permissions")
        .toolbar { ToolbarItem(placement: .primaryAction) { EditButton().disabled(settings.sitePermissions.isEmpty) } }
    }
}

struct AccessibilitySettings: View {
    @Bindable var settings: SettingsStore

    var body: some View {
        Form {
            Section {
                Toggle("Show panel buttons", isOn: $settings.showPanelHandles)
                    .accessibilityIdentifier("settings_panel_handles")
            } footer: {
                Text("Adds two small tabs at the screen edges. Tap them instead of swiping to open the address bar or the browser controls.")
            }
            Section("Other ways to open the panels") {
                Label("VoiceOver: on the page, swipe up or down to choose Show address and bookmarks or Show browser controls, then double-tap.", systemImage: "voiceover")
                Label("Keyboard: Command-L opens the address bar, Command-Shift-B the browser controls.", systemImage: "keyboard")
                Label("Trackpad: move the pointer to the screen edge and scroll sideways with two fingers.", systemImage: "rectangle.and.hand.point.up.left")
            }
            .font(.subheadline)
            Section("Keyboard shortcuts") {
                Shortcut(keys: "⌘L", action: "Address bar")
                Shortcut(keys: "⌘[  ⌘]", action: "Back, Forward")
                Shortcut(keys: "⌘R", action: "Reload")
                Shortcut(keys: "⇧⌘D", action: "Go to Discipulus")
                Shortcut(keys: "⇧⌘K", action: "Study Assistant")
                Shortcut(keys: "⌘Y", action: "History")
                Shortcut(keys: "⌥⌘J", action: "Downloads")
                Shortcut(keys: "⌘,", action: "Settings")
            }
        }
    }
}

private struct Shortcut: View {
    let keys: String
    let action: String
    var body: some View { LabeledContent(action) { Text(keys).font(.body.monospaced()) } }
}

struct AboutView: View {
    private var version: String {
        let v = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "0"
        let b = Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "0"
        return "\(v) (\(b))"
    }

    var body: some View {
        Form {
            Section {
                VStack(spacing: 12) {
                    SophiaMark(size: 88)
                    Text(SophiaConstants.appName).font(.largeTitle.bold())
                    Text(SophiaConstants.tagline).font(.headline).foregroundStyle(.secondary)
                    Text(SophiaConstants.description).font(.subheadline).foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 12)
            }
            Section {
                LabeledContent("Developer", value: SophiaConstants.publisher)
                LabeledContent("Version", value: version)
                LabeledContent("Name", value: SophiaConstants.nameMeaning)
            }
            Section {
                Link("Privacy policy", destination: URL(string: SophiaConstants.privacyPolicyURL)!)
                Link("Source code on GitHub", destination: URL(string: SophiaConstants.repoURL)!)
            } footer: {
                Text("Developed by KRLBRYN for AMA Computer College students.")
            }
        }
    }
}
