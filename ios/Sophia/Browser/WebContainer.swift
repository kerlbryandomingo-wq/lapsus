import SwiftUI
import WebKit
import SophiaKit

/// WKWebView with an "Ask Sophia" item in the text-selection menu.
final class SophiaWebView: WKWebView {
    var onAskSophia: ((String) -> Void)?

    override func buildMenu(with builder: UIMenuBuilder) {
        super.buildMenu(with: builder)
        guard onAskSophia != nil, builder.menu(for: .standardEdit) != nil else { return }
        let ask = UIAction(title: "Ask Sophia", image: UIImage(systemName: "sparkles")) { [weak self] _ in
            self?.evaluateJavaScript("window.getSelection().toString()") { result, _ in
                let text = (result as? String) ?? ""
                self?.onAskSophia?(String(text.prefix(SophiaConstants.maxAIInputChars)))
            }
        }
        builder.insertSibling(UIMenu(title: "", options: .displayInline, children: [ask]), afterMenu: .standardEdit)
    }
}

/// Hosts the shared WKWebView and recognises Sophia's edge swipes.
///
/// The swipe recogniser sits on this host view, not on the page. It only begins when a touch starts
/// in the narrow edge strip and moves clearly sideways, away from the edge (see `EdgeGesture`).
/// Every other touch fails it at once and goes to the page, so scrolling, zooming, text selection and
/// forms work exactly as in Safari.
final class EdgeSwipeHostView: UIView {
    let webView: SophiaWebView
    weak var model: BrowserModel?
    private let pan = UIPanGestureRecognizer()
    private var side: PanelSide?
    // A separate delegate object: UIView already has a method named gestureRecognizerShouldBegin(_:).
    private lazy var panDelegate = PanDelegate(host: self)

    init(webView: SophiaWebView, model: BrowserModel) {
        self.webView = webView
        self.model = model
        super.init(frame: .zero)
        backgroundColor = .systemBackground
        webView.translatesAutoresizingMaskIntoConstraints = false
        addSubview(webView)
        NSLayoutConstraint.activate([
            webView.leadingAnchor.constraint(equalTo: leadingAnchor),
            webView.trailingAnchor.constraint(equalTo: trailingAnchor),
            webView.topAnchor.constraint(equalTo: topAnchor),
            webView.bottomAnchor.constraint(equalTo: bottomAnchor),
        ])
        pan.addTarget(self, action: #selector(handlePan(_:)))
        pan.delegate = panDelegate
        pan.maximumNumberOfTouches = 1
        // Two-finger trackpad scrolls that start with the pointer at an edge also open the panels.
        pan.allowedScrollTypesMask = .continuous
        addGestureRecognizer(pan)
        // The page's own scrolling waits only for touches that start in the edge strip.
        webView.scrollView.panGestureRecognizer.require(toFail: pan)
        webView.accessibilityIdentifier = "browser_web_view"
        installAccessibilityActions()
    }

    required init?(coder: NSCoder) { fatalError("not used") }

    /// VoiceOver and Switch Control users get the panels as actions on the page.
    private func installAccessibilityActions() {
        webView.accessibilityCustomActions = [
            UIAccessibilityCustomAction(name: "Show address and bookmarks") { [weak self] _ in
                self?.model?.show(.address, focusAddress: true); return true
            },
            UIAccessibilityCustomAction(name: "Show browser controls") { [weak self] _ in
                self?.model?.show(.controls); return true
            },
        ]
    }

    private var regular: Bool { traitCollection.horizontalSizeClass == .regular }

    fileprivate func shouldBeginEdgeSwipe() -> Bool {
        guard let model, model.panel == .none, model.sheet == nil else { return false }
        let t = pan.translation(in: self)
        let now = pan.location(in: self)
        let startX = Double(now.x - t.x)
        side = EdgeGesture.beginning(startX: startX, width: Double(bounds.width), translationX: Double(t.x), translationY: Double(t.y), regular: regular)
        return side != nil
    }

    @objc private func handlePan(_ g: UIPanGestureRecognizer) {
        guard let model else { return }
        let tx = Double(g.translation(in: self).x)
        switch g.state {
        case .began:
            guard let side else { return }
            model.windowWidth = Double(bounds.width)
            model.beginDrag(side: side, startingOpen: false)
            model.updateDrag(translationX: tx)
        case .changed:
            model.updateDrag(translationX: tx)
        case .ended:
            model.endDrag(velocityX: Double(g.velocity(in: self).x))
        case .cancelled, .failed:
            model.endDrag(velocityX: 0)
        default:
            break
        }
    }
}

@MainActor
private final class PanDelegate: NSObject, UIGestureRecognizerDelegate {
    weak var host: EdgeSwipeHostView?
    init(host: EdgeSwipeHostView) { self.host = host }

    func gestureRecognizerShouldBegin(_ g: UIGestureRecognizer) -> Bool {
        host?.shouldBeginEdgeSwipe() ?? false
    }
}

struct WebContainer: UIViewRepresentable {
    let model: BrowserModel

    func makeUIView(context: Context) -> EdgeSwipeHostView {
        EdgeSwipeHostView(webView: model.webView, model: model)
    }

    func updateUIView(_ uiView: EdgeSwipeHostView, context: Context) {}
}

/// Hosts a pop-up window's own WKWebView.
struct PopupWebView: UIViewRepresentable {
    let webView: WKWebView
    func makeUIView(context: Context) -> WKWebView { webView }
    func updateUIView(_ uiView: WKWebView, context: Context) {}
}
