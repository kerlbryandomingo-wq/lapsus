import Foundation
import WebKit

/// A self-contained page for UI tests, so they never depend on the live portal or the network.
/// It is served from a private URL scheme, page by page (no JavaScript needed), so Back and Forward follow real navigations
/// just as they do on the portal. Only UI-test launches register the scheme.
enum FixturePage {
    static let scheme = "sophia-fixture"
    static let startURL = URL(string: "\(scheme)://sophia.test/page1")!

    /// The page for a fixture URL such as sophia-fixture://sophia.test/page2.
    static func page(for url: URL) -> String {
        let digits = url.lastPathComponent.drop { !$0.isNumber }
        let n = max(1, Int(digits) ?? 1)
        return html.replacingOccurrences(of: "{{N}}", with: String(n))
            .replacingOccurrences(of: "{{NEXT}}", with: String(n + 1))
    }

    static let html = """
    <!doctype html>
    <html><head><meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
    <title>Sophia test page {{N}}</title>
    <style>
      body { margin: 0; font: 17px -apple-system, sans-serif; background: #7A1B1B; color: #fff; }
      header { padding: 48px 24px 24px; background: #5e1414; }
      h1 { margin: 0 0 8px; font-size: 32px; }
      main { padding: 24px; }
      p { line-height: 1.5; }
      .next { display: inline-block; background: #eee; color: #0a64d8; text-decoration: none; }
      button, input, .next { font: inherit; padding: 12px 16px; border-radius: 10px; border: 0; margin: 6px 0; }
      .card { background: rgba(255,255,255,.12); border-radius: 16px; padding: 16px; margin: 16px 0; }
    </style></head>
    <body>
      <header><h1 id="title">Discipulus test page</h1><div id="state">Page {{N}}</div></header>
      <main>
        <div class="card">
          <p>This page stands in for the student portal during automated tests. Its colours must look
          exactly like this in the normal browser state: no grey, no dimming.</p>
          <a id="next" class="next" href="/page{{NEXT}}">Next page</a>
        </div>
        <div class="card"><label>Student number <input id="field" placeholder="Type here"></label></div>
        <div class="card"><p>Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed do eiusmod tempor
          incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation.</p></div>
        <div class="card"><p>Scroll content.</p></div>
        <div class="card"><p>More scroll content.</p></div>
      </main>
    </body></html>
    """
}

/// Serves FixturePage for every path under sophia-fixture://sophia.test/.
final class FixtureSchemeHandler: NSObject, WKURLSchemeHandler {
    func webView(_ webView: WKWebView, start task: any WKURLSchemeTask) {
        let url = task.request.url ?? FixturePage.startURL
        let data = Data(FixturePage.page(for: url).utf8)
        let response = HTTPURLResponse(url: url, statusCode: 200, httpVersion: "HTTP/1.1",
                                       headerFields: ["Content-Type": "text/html; charset=utf-8",
                                                      "Content-Length": String(data.count)])!
        task.didReceive(response)
        task.didReceive(data)
        task.didFinish()
    }

    func webView(_ webView: WKWebView, stop task: any WKURLSchemeTask) {}
}
