import WebKit
import SophiaKit

/// WKNavigationDelegate and WKUIDelegate for the main browser and for pop-up windows.
/// It enforces Sophia's URL rules, routes downloads, answers page dialogs natively and asks the
/// student before a site may use the camera or microphone. TLS is always validated by WebKit:
/// Sophia never answers server-trust challenges itself.
final class WebCoordinator: NSObject, WKNavigationDelegate, WKUIDelegate {
    enum Role { case main, popup }

    weak var model: BrowserModel?
    let role: Role

    init(model: BrowserModel, role: Role) {
        self.model = model
        self.role = role
    }

    // MARK: Navigation policy

    func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction, preferences: WKWebpagePreferences,
                 decisionHandler: @escaping (WKNavigationActionPolicy, WKWebpagePreferences) -> Void) {
        MainActor.assumeIsolated {
            guard let model else { return decisionHandler(.cancel, preferences) }
            preferences.allowsContentJavaScript = model.settings.javaScriptEnabled
            if navigationAction.shouldPerformDownload { return decisionHandler(.download, preferences) }
            guard let url = navigationAction.request.url else { return decisionHandler(.cancel, preferences) }
            let isMainFrame = navigationAction.targetFrame?.isMainFrame ?? true
            if !isMainFrame {
                // Sub-frames load through WebKit's own rules (and App Transport Security), except
                // links that belong to another app.
                if let scheme = UrlPolicy.scheme(of: url.absoluteString), UrlPolicy.externalSchemes.contains(scheme) {
                    return decisionHandler(.cancel, preferences)
                }
                return decisionHandler(.allow, preferences)
            }
            if url.host == "sophia.test" { return decisionHandler(.allow, preferences) } // UI-test fixture
            switch UrlPolicy.evaluateNavigation(url.absoluteString) {
            case .allow:
                decisionHandler(.allow, preferences)
            case .upgrade(let https):
                decisionHandler(.cancel, preferences)
                if let u = URL(string: https) { webView.load(URLRequest(url: u)) }
            case .external(let ext):
                decisionHandler(.cancel, preferences)
                if navigationAction.navigationType == .linkActivated || navigationAction.navigationType == .other {
                    model.openExternally(ext)
                }
            case .block(let reason):
                decisionHandler(.cancel, preferences)
                model.showNotice(reason.message)
            }
        }
    }

    func webView(_ webView: WKWebView, decidePolicyFor navigationResponse: WKNavigationResponse,
                 decisionHandler: @escaping (WKNavigationResponsePolicy) -> Void) {
        MainActor.assumeIsolated {
            guard let model else { return decisionHandler(.cancel) }
            let response = navigationResponse.response
            let disposition = (response as? HTTPURLResponse)?.value(forHTTPHeaderField: "Content-Disposition")?.lowercased() ?? ""
            let isAttachment = disposition.hasPrefix("attachment")
            guard isAttachment || !navigationResponse.canShowMIMEType else { return decisionHandler(.allow) }
            guard model.settings.askBeforeDownloading else { return decisionHandler(.download) }
            let name = FileNames.sanitize(response.suggestedFilename)
            model.pendingDownload = PendingDownload(
                fileName: name,
                typeLabel: FileNames.typeLabel(name, mimeType: response.mimeType),
                sizeLabel: Formatting.bytes(response.expectedContentLength),
                host: response.url?.host ?? "",
                decide: { allow in decisionHandler(allow ? .download : .cancel) }
            )
        }
    }

    func webView(_ webView: WKWebView, navigationAction: WKNavigationAction, didBecome download: WKDownload) {
        MainActor.assumeIsolated {
            model?.downloads.track(download, sourceURL: navigationAction.request.url?.absoluteString)
            model?.showNotice("Downloading. Open Downloads in Settings to follow it.")
        }
    }

    func webView(_ webView: WKWebView, navigationResponse: WKNavigationResponse, didBecome download: WKDownload) {
        MainActor.assumeIsolated {
            model?.downloads.track(download, sourceURL: navigationResponse.response.url?.absoluteString)
            model?.showNotice("Downloading \(FileNames.sanitize(navigationResponse.response.suggestedFilename)).")
        }
    }

    // MARK: Navigation progress

    func webView(_ webView: WKWebView, didStartProvisionalNavigation navigation: WKNavigation!) {
        MainActor.assumeIsolated { if role == .main { model?.didStartNavigation() } }
    }

    func webView(_ webView: WKWebView, didCommit navigation: WKNavigation!) {
        MainActor.assumeIsolated { if role == .main { model?.didCommit() } }
    }

    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        MainActor.assumeIsolated { if role == .main { model?.didFinish() } }
    }

    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        MainActor.assumeIsolated { if role == .main { model?.didFail(error, url: nil) } }
    }

    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        MainActor.assumeIsolated { if role == .main { model?.didFail(error, url: webView.url?.absoluteString) } }
    }

    func webViewWebContentProcessDidTerminate(_ webView: WKWebView) {
        // The page's process was stopped by iOS (usually memory pressure). Reload it.
        webView.reload()
    }

    // MARK: Windows

    func webView(_ webView: WKWebView, createWebViewWith configuration: WKWebViewConfiguration,
                 for navigationAction: WKNavigationAction, windowFeatures: WKWindowFeatures) -> WKWebView? {
        MainActor.assumeIsolated {
            guard let model else { return nil }
            if role == .popup || model.settings.newWindowMode == .sameWindow || model.popup != nil {
                if let url = navigationAction.request.url { model.open(url.absoluteString) }
                if role == .popup { model.closePopup() }
                return nil
            }
            let popup = WKWebView(frame: .zero, configuration: configuration)
            popup.allowsBackForwardNavigationGestures = true
            return model.presentPopup(popup)
        }
    }

    func webViewDidClose(_ webView: WKWebView) {
        MainActor.assumeIsolated { if role == .popup { model?.closePopup() } }
    }

    // MARK: Page dialogs

    private func host(_ frame: WKFrameInfo) -> String {
        frame.securityOrigin.host.isEmpty ? "This page" : frame.securityOrigin.host
    }

    func webView(_ webView: WKWebView, runJavaScriptAlertPanelWithMessage message: String, initiatedByFrame frame: WKFrameInfo,
                 completionHandler: @escaping () -> Void) {
        MainActor.assumeIsolated {
            guard let model, model.webDialog == nil else { return completionHandler() }
            model.webDialog = WebDialog(kind: .alert, host: host(frame), message: message) { _, _ in completionHandler() }
        }
    }

    func webView(_ webView: WKWebView, runJavaScriptConfirmPanelWithMessage message: String, initiatedByFrame frame: WKFrameInfo,
                 completionHandler: @escaping (Bool) -> Void) {
        MainActor.assumeIsolated {
            guard let model, model.webDialog == nil else { return completionHandler(false) }
            model.webDialog = WebDialog(kind: .confirm, host: host(frame), message: message) { ok, _ in completionHandler(ok) }
        }
    }

    func webView(_ webView: WKWebView, runJavaScriptTextInputPanelWithPrompt prompt: String, defaultText: String?,
                 initiatedByFrame frame: WKFrameInfo, completionHandler: @escaping (String?) -> Void) {
        MainActor.assumeIsolated {
            guard let model, model.webDialog == nil else { return completionHandler(nil) }
            model.webDialog = WebDialog(kind: .prompt, host: host(frame), message: prompt, defaultText: defaultText ?? "") { _, text in completionHandler(text) }
        }
    }

    // MARK: Camera and microphone

    func webView(_ webView: WKWebView, requestMediaCapturePermissionFor origin: WKSecurityOrigin, initiatedByFrame frame: WKFrameInfo,
                 type: WKMediaCaptureType, decisionHandler: @escaping (WKPermissionDecision) -> Void) {
        MainActor.assumeIsolated {
            guard let model else { return decisionHandler(.deny) }
            let originString = origin.port > 0 && origin.port != 443
                ? "\(origin.protocol)://\(origin.host):\(origin.port)" : "\(origin.protocol)://\(origin.host)"
            let kind: PermissionKind
            switch type {
            case .camera: kind = .camera
            case .microphone: kind = .microphone
            default: kind = .cameraAndMicrophone
            }
            if let remembered = model.settings.permission(origin: originString, kind: kind) {
                return decisionHandler(remembered ? .grant : .deny)
            }
            guard origin.protocol == "https" else { return decisionHandler(.deny) }
            model.permissionRequest = PermissionRequest(origin: originString, kind: kind) { [weak model] allow, remember in
                if remember { model?.settings.setPermission(origin: originString, kind: kind, allowed: allow) }
                decisionHandler(allow ? .grant : .deny)
            }
        }
    }
}
