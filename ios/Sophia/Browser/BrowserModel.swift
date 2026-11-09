import SwiftUI
import WebKit
import Observation
import SwiftData
import SophiaKit

struct PanelDrag: Equatable {
    var panel: PanelState
    var side: PanelSide
    var startingOpen: Bool
    var progress: Double
}

struct LoadError: Equatable {
    var url: String
    var title: String
    var message: String
    var symbol: String
}

enum BrowserSheet: Identifiable, Equatable {
    case settings
    case downloads
    case history
    case notes
    case assistant(String)

    var id: String {
        switch self {
        case .settings: return "settings"
        case .downloads: return "downloads"
        case .history: return "history"
        case .notes: return "notes"
        case .assistant: return "assistant"
        }
    }
}

/// A JavaScript alert, confirm or prompt from the page, answered natively.
final class WebDialog: Identifiable {
    enum Kind { case alert, confirm, prompt }
    let id = UUID()
    let kind: Kind
    let host: String
    let message: String
    var text: String
    private var completion: ((Bool, String?) -> Void)?

    init(kind: Kind, host: String, message: String, defaultText: String = "", completion: @escaping (Bool, String?) -> Void) {
        self.kind = kind
        self.host = host
        self.message = String(message.prefix(1000))
        self.text = defaultText
        self.completion = completion
    }

    /// Answers the page once; later calls do nothing.
    func finish(ok: Bool) {
        completion?(ok, ok ? text : nil)
        completion = nil
    }

    deinit { completion?(false, nil) }
}

/// A file the page wants to download, waiting for the student's go-ahead.
struct PendingDownload: Identifiable {
    let id = UUID()
    let fileName: String
    let typeLabel: String
    let sizeLabel: String
    let host: String
    let decide: (Bool) -> Void
}

/// A site asking for the camera or microphone.
struct PermissionRequest: Identifiable {
    let id = UUID()
    let origin: String
    let kind: PermissionKind
    let decide: (_ allow: Bool, _ remember: Bool) -> Void
}

/// A pop-up window opened by the page (sign-in windows and the like).
struct PopupRequest: Identifiable {
    let id = UUID()
    let webView: WKWebView
}

/// Owns one browser window's WKWebView and everything the interface shows about it.
@MainActor
@Observable
final class BrowserModel {
    let settings: SettingsStore
    let downloads: DownloadManager
    @ObservationIgnored let webView: SophiaWebView
    @ObservationIgnored var history: HistoryRecorder?
    @ObservationIgnored private var coordinator: WebCoordinator?
    @ObservationIgnored private var popupCoordinator: WebCoordinator?
    @ObservationIgnored private var observations: [NSKeyValueObservation] = []
    @ObservationIgnored private var noticeTask: Task<Void, Never>?
    @ObservationIgnored private let fixtureHTML: String?

    // Panels
    var panel: PanelState = .none
    var drag: PanelDrag?
    var focusAddress = false
    var windowWidth: Double = 390
    var regularWidth = false

    // Page state
    var url = ""
    var title = ""
    var progress: Double = 0
    var isLoading = false
    var canGoBack = false
    var canGoForward = false
    var hasCommitted = false
    var loadError: LoadError?
    var notice: String?

    // Prompts and sheets
    var webDialog: WebDialog?
    var pendingDownload: PendingDownload?
    var permissionRequest: PermissionRequest?
    var popup: PopupRequest?
    var sheet: BrowserSheet?

    init(settings: SettingsStore, downloads: DownloadManager, fixtureHTML: String? = nil) {
        self.settings = settings
        self.downloads = downloads
        self.fixtureHTML = fixtureHTML
        webView = SophiaWebView(frame: .zero, configuration: Self.makeConfiguration())
        let c = WebCoordinator(model: self, role: .main)
        coordinator = c
        webView.navigationDelegate = c
        webView.uiDelegate = c
        webView.allowsBackForwardNavigationGestures = false // the screen edges belong to Sophia's panels
        webView.allowsLinkPreview = true
        webView.backgroundColor = .systemBackground
        webView.scrollView.contentInsetAdjustmentBehavior = .automatic
        #if DEBUG
        webView.isInspectable = true
        #endif
        webView.onAskSophia = { [weak self] text in self?.openAssistant(with: text) }
        observe()
    }

    static func makeConfiguration() -> WKWebViewConfiguration {
        let config = WKWebViewConfiguration()
        config.websiteDataStore = .default()
        config.allowsInlineMediaPlayback = true
        config.mediaTypesRequiringUserActionForPlayback = .all
        config.dataDetectorTypes = []
        config.preferences.javaScriptCanOpenWindowsAutomatically = false // pop-ups need a tap
        config.preferences.isFraudulentWebsiteWarningEnabled = true
        config.defaultWebpagePreferences.preferredContentMode = .recommended
        config.upgradeKnownHostsToHTTPS = true
        return config
    }

    private func observe() {
        func on<V>(_ kp: KeyPath<SophiaWebView, V>, _ apply: @escaping @MainActor (BrowserModel, V) -> Void) {
            observations.append(webView.observe(kp, options: [.initial, .new]) { [weak self] wv, _ in
                MainActor.assumeIsolated {
                    guard let self else { return }
                    apply(self, wv[keyPath: kp])
                }
            })
        }
        on(\.estimatedProgress) { m, v in m.progress = v }
        on(\.isLoading) { m, v in m.isLoading = v }
        on(\.canGoBack) { m, v in m.canGoBack = v }
        on(\.canGoForward) { m, v in m.canGoForward = v }
        on(\.title) { m, v in
            m.title = v ?? ""
            m.history?.updateTitle(url: m.url, title: m.title)
        }
        on(\.url) { m, v in if let s = v?.absoluteString { m.url = s } }
    }

    // MARK: Navigation

    func start() {
        guard webView.url == nil else { return }
        if let fixtureHTML {
            webView.loadHTMLString(fixtureHTML, baseURL: URL(string: "https://sophia.test/"))
        } else {
            open(settings.homepage.isEmpty ? SophiaConstants.defaultPortalURL : settings.homepage)
        }
    }

    /// Address-bar input: a web address or a search.
    func submitAddress(_ input: String) {
        guard let target = UrlPolicy.normalizeAddressInput(input, engine: settings.searchEngine) else { return }
        open(target)
        close()
    }

    func open(_ raw: String) {
        switch UrlPolicy.evaluateNavigation(raw) {
        case .allow:
            load(raw)
        case .upgrade(let https):
            load(https)
        case .external(let ext):
            openExternally(ext)
        case .block(let reason):
            showNotice(reason.message)
        }
    }

    private func load(_ raw: String) {
        guard let u = URL(string: raw) else { return showNotice(BlockReason.invalid.message) }
        loadError = nil
        webView.load(URLRequest(url: u))
    }

    func openExternally(_ raw: String) {
        guard let u = URL(string: raw) else { return }
        UIApplication.shared.open(u) { [weak self] ok in
            if !ok { Task { @MainActor in self?.showNotice("No app on this device can open that link.") } }
        }
    }

    func openBookmark(_ b: PortalBookmark) {
        if let target = settings.url(for: b) {
            open(target)
            close()
        } else {
            close()
            sheet = .settings
            showNotice("Add the \(b.title) address in Settings > Bookmarks.")
        }
    }

    func goBack() { if webView.canGoBack { webView.goBack() } }
    func goForward() { if webView.canGoForward { webView.goForward() } }

    func reload() {
        if let e = loadError, webView.url == nil || webView.url?.absoluteString != e.url {
            open(e.url)
        } else if webView.url == nil {
            start()
        } else {
            loadError = nil
            webView.reload()
        }
    }

    func stopLoading() { webView.stopLoading() }

    func showNotice(_ text: String) {
        notice = text
        noticeTask?.cancel()
        noticeTask = Task { @MainActor [weak self] in
            try? await Task.sleep(nanoseconds: 3_500_000_000)
            if !Task.isCancelled { withAnimation { self?.notice = nil } }
        }
    }

    func openAssistant(with text: String) {
        close()
        sheet = .assistant(text)
    }

    // MARK: Panels

    func reveal(_ state: PanelState) -> Double {
        if let d = drag, d.panel == state { return d.progress }
        return panel == state ? 1 : 0
    }

    var anyPanelVisible: Bool { reveal(.address) > 0 || reveal(.controls) > 0 }

    func panelWidth(_ state: PanelState) -> Double {
        EdgeGesture.panelWidth(state, windowWidth: windowWidth, regular: regularWidth)
    }

    func show(_ state: PanelState, focusAddress: Bool = false) {
        withAnimation(.snappy(duration: 0.28)) {
            drag = nil
            panel = state
        }
        self.focusAddress = focusAddress && state == .address
    }

    func toggle(_ state: PanelState) { show(panel == state ? .none : state, focusAddress: state == .address) }

    func close() { if panel != .none || drag != nil { show(.none) } }

    func beginDrag(side: PanelSide, startingOpen: Bool) {
        let state: PanelState = side == .left ? .address : .controls
        if startingOpen && panel != state { return }
        drag = PanelDrag(panel: state, side: side, startingOpen: startingOpen, progress: startingOpen ? 1 : 0)
    }

    func updateDrag(translationX: Double) {
        guard var d = drag else { return }
        d.progress = EdgeGesture.progress(side: d.side, translationX: translationX, panelWidth: panelWidth(d.panel), startingOpen: d.startingOpen)
        drag = d
    }

    func endDrag(velocityX: Double) {
        guard let d = drag else { return }
        let open = EdgeGesture.settlesOpen(side: d.side, progress: d.progress, velocityX: velocityX)
        show(open ? d.panel : .none)
    }

    // MARK: Called by the web coordinator

    func didStartNavigation() { loadError = nil }

    func didCommit() { hasCommitted = true }

    func didFinish() {
        hasCommitted = true
        guard settings.historyEnabled, let current = webView.url?.absoluteString, current.hasPrefix("https://") || current.hasPrefix("http://") else { return }
        if webView.url?.host == "sophia.test" { return }
        history?.record(url: current, title: webView.title ?? "")
    }

    func didFail(_ error: Error, url failingURL: String?) {
        let ns = error as NSError
        if ns.domain == NSURLErrorDomain && ns.code == NSURLErrorCancelled { return }
        // "Frame load interrupted": the response became a download or was handed elsewhere.
        if ns.domain == WKError.errorDomain && ns.code == 102 { return }
        let target = failingURL ?? (ns.userInfo[NSURLErrorFailingURLStringErrorKey] as? String) ?? url
        loadError = Self.describe(ns, url: target)
    }

    static func describe(_ e: NSError, url: String) -> LoadError {
        let host = UrlPolicy.host(of: url)
        let site = host.isEmpty ? "the page" : host
        switch (e.domain, e.code) {
        case (NSURLErrorDomain, NSURLErrorNotConnectedToInternet), (NSURLErrorDomain, NSURLErrorNetworkConnectionLost), (NSURLErrorDomain, NSURLErrorDataNotAllowed):
            return LoadError(url: url, title: "You're offline", message: "Sophia couldn't reach \(site). Check Wi-Fi or mobile data, then try again.", symbol: "wifi.slash")
        case (NSURLErrorDomain, NSURLErrorTimedOut):
            return LoadError(url: url, title: "The page took too long", message: "\(site) didn't respond in time. It may be busy; try again in a moment.", symbol: "clock.badge.exclamationmark")
        case (NSURLErrorDomain, NSURLErrorCannotFindHost), (NSURLErrorDomain, NSURLErrorDNSLookupFailed), (NSURLErrorDomain, NSURLErrorCannotConnectToHost):
            return LoadError(url: url, title: "Can't find the site", message: "Sophia couldn't connect to \(site). Check the address and your connection.", symbol: "globe.badge.chevron.backward")
        case (NSURLErrorDomain, NSURLErrorServerCertificateUntrusted), (NSURLErrorDomain, NSURLErrorServerCertificateHasBadDate),
             (NSURLErrorDomain, NSURLErrorServerCertificateHasUnknownRoot), (NSURLErrorDomain, NSURLErrorServerCertificateNotYetValid),
             (NSURLErrorDomain, NSURLErrorSecureConnectionFailed), (NSURLErrorDomain, NSURLErrorClientCertificateRejected):
            return LoadError(url: url, title: "Connection isn't secure", message: "Sophia couldn't verify the security certificate of \(site), so the page was not opened. This protects your account.", symbol: "lock.trianglebadge.exclamationmark")
        case (NSURLErrorDomain, NSURLErrorAppTransportSecurityRequiresSecureConnection):
            return LoadError(url: url, title: "Secure pages only", message: "Sophia only opens pages over a secure (https) connection.", symbol: "lock.slash")
        default:
            return LoadError(url: url, title: "The page didn't load", message: e.localizedDescription, symbol: "exclamationmark.triangle")
        }
    }

    func presentPopup(_ wv: WKWebView) -> WKWebView? {
        if popup != nil { return nil }
        let c = WebCoordinator(model: self, role: .popup)
        popupCoordinator = c
        wv.navigationDelegate = c
        wv.uiDelegate = c
        popup = PopupRequest(webView: wv)
        return wv
    }

    func closePopup() {
        popup?.webView.stopLoading()
        popup = nil
        popupCoordinator = nil
    }

    /// Moves the pop-up's page into the main browser.
    func openPopupHere() {
        guard let u = popup?.webView.url?.absoluteString else { return closePopup() }
        closePopup()
        open(u)
    }
}
