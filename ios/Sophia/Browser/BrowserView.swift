import SwiftUI
import QuickLook
import SophiaKit

/// The whole browser window: the page edge to edge, and Sophia's interface only when asked for.
struct BrowserView: View {
    @Bindable var model: BrowserModel
    @Environment(\.horizontalSizeClass) private var hSize
    @Environment(\.modelContext) private var modelContext
    @Environment(NetworkMonitor.self) private var network

    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .top) {
                WebContainer(model: model)
                    .ignoresSafeArea()

                if !model.hasCommitted && model.loadError == nil {
                    StartupView()
                        .transition(.opacity)
                }

                if let error = model.loadError {
                    LoadErrorView(error: error, offline: !network.isConnected, model: model)
                        .transition(.opacity)
                }

                if model.isLoading && model.hasCommitted && model.loadError == nil {
                    LoadingBar(progress: model.progress)
                }

                if model.settings.showPanelHandles && !model.anyPanelVisible {
                    PanelHandles(model: model)
                        .frame(maxHeight: .infinity)
                }

                PanelOverlay(model: model)

                if let notice = model.notice {
                    NoticeBanner(text: notice)
                        .frame(maxHeight: .infinity, alignment: .bottom)
                        .padding(.bottom, 24)
                        .transition(.move(edge: .bottom).combined(with: .opacity))
                        .allowsHitTesting(false)
                }
            }
            .onAppear {
                model.windowWidth = geo.size.width
                model.regularWidth = hSize == .regular
            }
            .onChange(of: geo.size.width) { _, w in model.windowWidth = w }
        }
        .animation(.easeInOut(duration: 0.2), value: model.hasCommitted)
        .animation(.easeInOut(duration: 0.2), value: model.loadError)
        .animation(.easeInOut(duration: 0.25), value: model.notice)
        .onChange(of: hSize) { _, s in model.regularWidth = s == .regular }
        .onChange(of: network.isConnected) { _, connected in
            if connected, model.loadError != nil { model.reload() }
        }
        .statusBarHidden(model.settings.hideStatusBar && !model.anyPanelVisible)
        .persistentSystemOverlays(model.settings.hideStatusBar ? .hidden : .automatic)
        .focusedSceneValue(\.browser, model)
        .task {
            model.history = HistoryRecorder(context: modelContext)
            model.downloads.openWhenDone = { [settings = model.settings] in settings.openDownloadsWhenDone }
            model.start()
        }
        .sheet(item: $model.sheet) { sheet in
            SheetContent(sheet: sheet, model: model)
        }
        .sheet(item: $model.popup, onDismiss: { model.closePopup() }) { popup in
            PopupSheet(popup: popup, model: model)
        }
        .webDialogs(model: model)
        .quickLookPreview(Binding(get: { model.downloads.previewURL }, set: { model.downloads.previewURL = $0 }))
    }
}

/// What a sheet shows.
private struct SheetContent: View {
    let sheet: BrowserSheet
    let model: BrowserModel

    var body: some View {
        switch sheet {
        case .settings:
            SettingsView(model: model)
        case .downloads:
            NavigationStack { DownloadsView(manager: model.downloads) }
        case .history:
            NavigationStack {
                HistoryView { url in
                    model.sheet = nil
                    model.open(url)
                }
            }
        case .notes:
            NavigationStack { NotesView() }
        case .assistant(let text):
            AssistantView(settings: model.settings, initialText: text)
        }
    }
}

/// Shown under the launch screen's mark until the first page appears.
struct StartupView: View {
    var body: some View {
        ZStack {
            Color("LaunchBackground").ignoresSafeArea()
            VStack(spacing: 20) {
                SophiaMark(size: 112)
                ProgressView()
                    .controlSize(.regular)
                    .accessibilityLabel("Opening Discipulus")
            }
        }
        .accessibilityIdentifier("startup_view")
    }
}

struct LoadingBar: View {
    let progress: Double

    var body: some View {
        GeometryReader { geo in
            Rectangle()
                .fill(SophiaColor.maroon)
                .frame(width: max(24, geo.size.width * progress), height: 3)
                .animation(.linear(duration: 0.15), value: progress)
        }
        .frame(height: 3)
        .ignoresSafeArea(edges: .horizontal)
        .allowsHitTesting(false)
        .accessibilityElement()
        .accessibilityLabel("Loading")
        .accessibilityValue("\(Int(progress * 100)) percent")
    }
}

struct NoticeBanner: View {
    let text: String

    var body: some View {
        Text(text)
            .font(.subheadline)
            .multilineTextAlignment(.center)
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            .background(.regularMaterial, in: Capsule())
            .padding(.horizontal, 24)
            .accessibilityIdentifier("notice_banner")
    }
}

struct LoadErrorView: View {
    let error: LoadError
    let offline: Bool
    let model: BrowserModel

    var body: some View {
        ZStack {
            Color(.systemBackground).ignoresSafeArea()
            VStack(spacing: 16) {
                Image(systemName: offline ? "wifi.slash" : error.symbol)
                    .font(.system(size: 44))
                    .foregroundStyle(SophiaColor.maroon)
                Text(offline ? "You're offline" : error.title).font(.title2.bold())
                Text(error.message)
                    .font(.body)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: 420)
                Text(UrlPolicy.displayURL(error.url))
                    .font(.footnote.monospaced())
                    .foregroundStyle(.tertiary)
                    .lineLimit(2)
                    .multilineTextAlignment(.center)
                HStack(spacing: 12) {
                    Button("Try again") { model.reload() }
                        .buttonStyle(.borderedProminent)
                        .accessibilityIdentifier("error_retry")
                    Button("Address bar") { model.show(.address, focusAddress: true) }
                        .buttonStyle(.bordered)
                }
                .padding(.top, 8)
                if offline {
                    Text("Sophia will try again when you're back online.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
            .padding(32)
        }
        .accessibilityIdentifier("load_error")
    }
}

/// A page's pop-up window (sign-in windows and the like) in a sheet with its own toolbar.
struct PopupSheet: View {
    let popup: PopupRequest
    let model: BrowserModel

    var body: some View {
        NavigationStack {
            PopupWebView(webView: popup.webView)
                .ignoresSafeArea(edges: .bottom)
                .navigationTitle(UrlPolicy.host(of: popup.webView.url?.absoluteString ?? ""))
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Done") { model.closePopup() }
                    }
                    ToolbarItem(placement: .primaryAction) {
                        Button("Open in Sophia") { model.openPopupHere() }
                    }
                }
        }
    }
}

// MARK: - Page dialogs, download and permission prompts

private struct WebDialogsModifier: ViewModifier {
    @Bindable var model: BrowserModel

    func body(content: Content) -> some View {
        content
            .alert(model.webDialog?.host ?? "", isPresented: Binding(get: { model.webDialog != nil }, set: { if !$0 { finish(false) } }),
                   presenting: model.webDialog) { dialog in
                switch dialog.kind {
                case .alert:
                    Button("OK") { finish(true) }
                case .confirm:
                    Button("Cancel", role: .cancel) { finish(false) }
                    Button("OK") { finish(true) }
                case .prompt:
                    TextField("", text: Binding(get: { dialog.text }, set: { dialog.text = $0 }))
                    Button("Cancel", role: .cancel) { finish(false) }
                    Button("OK") { finish(true) }
                }
            } message: { dialog in
                Text(dialog.message)
            }
            .confirmationDialog(
                "Download \(model.pendingDownload?.fileName ?? "file")?",
                isPresented: Binding(get: { model.pendingDownload != nil }, set: { if !$0 { decideDownload(false) } }),
                titleVisibility: .visible,
                presenting: model.pendingDownload
            ) { _ in
                Button("Download") { decideDownload(true) }
                Button("Cancel", role: .cancel) { decideDownload(false) }
            } message: { d in
                Text("\(d.typeLabel), \(d.sizeLabel), from \(d.host)")
            }
            .confirmationDialog(
                permissionTitle,
                isPresented: Binding(get: { model.permissionRequest != nil }, set: { if !$0 { decidePermission(false, remember: false) } }),
                titleVisibility: .visible,
                presenting: model.permissionRequest
            ) { _ in
                Button("Allow once") { decidePermission(true, remember: false) }
                Button("Always allow for this site") { decidePermission(true, remember: true) }
                Button("Don't allow", role: .cancel) { decidePermission(false, remember: false) }
            } message: { _ in
                Text("You can change this later in Settings > Privacy and Security > Website permissions.")
            }
    }

    private var permissionTitle: String {
        guard let r = model.permissionRequest else { return "" }
        return "Allow \(UrlPolicy.host(of: r.origin)) to use your \(r.kind.label.lowercased())?"
    }

    private func finish(_ ok: Bool) {
        guard let d = model.webDialog else { return }
        model.webDialog = nil
        d.finish(ok: ok)
    }

    private func decideDownload(_ allow: Bool) {
        guard let d = model.pendingDownload else { return }
        model.pendingDownload = nil
        d.decide(allow)
    }

    private func decidePermission(_ allow: Bool, remember: Bool) {
        guard let r = model.permissionRequest else { return }
        model.permissionRequest = nil
        r.decide(allow, remember)
    }
}

extension View {
    func webDialogs(model: BrowserModel) -> some View { modifier(WebDialogsModifier(model: model)) }
}

// MARK: - Keyboard commands (iPad and hardware keyboards)

struct BrowserFocusKey: FocusedValueKey {
    typealias Value = BrowserModel
}

extension FocusedValues {
    var browser: BrowserModel? {
        get { self[BrowserFocusKey.self] }
        set { self[BrowserFocusKey.self] = newValue }
    }
}

struct BrowserCommands: Commands {
    @FocusedValue(\.browser) private var browser

    var body: some Commands {
        CommandMenu("Browser") {
            Button("Open Address Bar") { browser?.show(.address, focusAddress: true) }
                .keyboardShortcut("l", modifiers: .command)
            Button("Show Browser Controls") { browser?.toggle(.controls) }
                .keyboardShortcut("b", modifiers: [.command, .shift])
            Divider()
            Button("Back") { browser?.goBack() }
                .keyboardShortcut("[", modifiers: .command)
                .disabled(!(browser?.canGoBack ?? false))
            Button("Forward") { browser?.goForward() }
                .keyboardShortcut("]", modifiers: .command)
                .disabled(!(browser?.canGoForward ?? false))
            Button("Reload") { browser?.reload() }
                .keyboardShortcut("r", modifiers: .command)
            Button("Go to Discipulus") { browser?.openBookmark(.discipulus) }
                .keyboardShortcut("d", modifiers: [.command, .shift])
            Divider()
            Button("Study Assistant") { browser?.openAssistant(with: "") }
                .keyboardShortcut("k", modifiers: [.command, .shift])
            Button("Downloads") { browser?.sheet = .downloads }
                .keyboardShortcut("j", modifiers: [.command, .option])
            Button("History") { browser?.sheet = .history }
                .keyboardShortcut("y", modifiers: .command)
        }
        CommandGroup(replacing: .appSettings) {
            Button("Settings…") { browser?.sheet = .settings }
                .keyboardShortcut(",", modifiers: .command)
        }
    }
}
