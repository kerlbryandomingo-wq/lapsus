import Foundation

public enum SearchEngine: String, CaseIterable, Identifiable, Sendable {
    case google, bing, duckduckgo

    public var id: String { rawValue }

    public var label: String {
        switch self {
        case .google: return "Google"
        case .bing: return "Bing"
        case .duckduckgo: return "DuckDuckGo"
        }
    }

    public var template: String {
        switch self {
        case .google: return "https://www.google.com/search?q=%s"
        case .bing: return "https://www.bing.com/search?q=%s"
        case .duckduckgo: return "https://duckduckgo.com/?q=%s"
        }
    }
}

/// What the browser does with a top-level navigation.
public enum NavigationDecision: Equatable, Sendable {
    case allow
    /// Load this https address instead (HTTPS-first: plain http is upgraded, never loaded).
    case upgrade(String)
    /// Hand the URL to another app (Mail, Phone, Maps, the App Store).
    case external(String)
    case block(BlockReason)
}

public enum BlockReason: String, Sendable {
    case invalid, scheme, credentials

    public var message: String {
        switch self {
        case .invalid: return "This address isn't valid."
        case .scheme: return "Sophia can't open this kind of link."
        case .credentials: return "Sophia doesn't open addresses with a user name or password in them."
        }
    }
}

public enum SecurityLevel: Sendable { case secure, local, insecure, none }

/// URL rules for the Sophia browser, ported from Sophia for Android's UrlPolicy.kt.
/// Pure Foundation, so it is unit-tested on any platform.
public enum UrlPolicy {
    public static let externalSchemes: Set<String> = ["mailto", "tel", "sms", "facetime", "facetime-audio", "maps", "itms-apps", "itms-appss"]

    private static let hostLike = try! NSRegularExpression(
        pattern: "^(localhost|\\[[0-9a-fA-F:]+\\]|\\d{1,3}(?:\\.\\d{1,3}){3}|(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,63})(:\\d{1,5})?([/?#].*)?$"
    )
    private static let schemeRe = try! NSRegularExpression(pattern: "^[a-zA-Z][a-zA-Z0-9+.-]*:")
    private static let hostnameRe = try! NSRegularExpression(pattern: "^(?:[a-z0-9-]+\\.)+[a-z]{2,63}$")

    public static func isLoopbackHost(_ hostname: String) -> Bool {
        var h = hostname.lowercased()
        if h.hasPrefix("[") { h.removeFirst() }
        if h.hasSuffix("]") { h.removeLast() }
        if h == "localhost" || h == "::1" { return true }
        let parts = h.split(separator: ".", omittingEmptySubsequences: false)
        return parts.count == 4 && parts[0] == "127" && parts.allSatisfy { !$0.isEmpty && $0.count <= 3 && $0.allSatisfy(\.isNumber) }
    }

    /// Lower-cased scheme, or nil when the string has none.
    public static func scheme(of raw: String) -> String? {
        let s = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let m = schemeRe.firstMatch(in: s, range: NSRange(s.startIndex..., in: s)),
              let r = Range(m.range, in: s) else { return nil }
        return String(s[r].dropLast()).lowercased()
    }

    /// A parsed http(s) URL with a host, or nil.
    public static func webURL(_ raw: String) -> URLComponents? {
        guard let c = URLComponents(string: raw.trimmingCharacters(in: .whitespacesAndNewlines)),
              let scheme = c.scheme?.lowercased(), scheme == "http" || scheme == "https",
              let host = c.host, !host.isEmpty else { return nil }
        return c
    }

    /// Turns address-bar input into a URL (always https unless it is this device), or a search URL.
    /// Returns nil for blank input.
    public static func normalizeAddressInput(_ raw: String, engine: SearchEngine) -> String? {
        let input = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if input.isEmpty { return nil }
        let hasSpace = input.rangeOfCharacter(from: .whitespacesAndNewlines) != nil

        if !hasSpace, let m = hostLike.firstMatch(in: input, range: NSRange(input.startIndex..., in: input)),
           let hostRange = Range(m.range(at: 1), in: input) {
            let host = String(input[hostRange])
            let candidate = (isLoopbackHost(host) ? "http://" : "https://") + input
            if let c = webURL(candidate), let s = c.string { return s }
        }

        if !hasSpace, let scheme = scheme(of: input) {
            if scheme == "http" || scheme == "https" {
                if var c = webURL(input) {
                    if scheme == "http", let host = c.host, !isLoopbackHost(host) { c.scheme = "https" }
                    if let s = c.string { return s }
                }
            } else if input.count > scheme.count + 1 {
                return input
            }
        }

        var allowed = CharacterSet.alphanumerics
        allowed.insert(charactersIn: "-._~")
        let encoded = input.addingPercentEncoding(withAllowedCharacters: allowed) ?? input
        return engine.template.replacingOccurrences(of: "%s", with: encoded)
    }

    public static func isPortalHost(_ hostname: String, extraHosts: [String] = []) -> Bool {
        var h = hostname.lowercased()
        if h.hasSuffix(".") { h.removeLast() }
        return (SophiaConstants.portalDomains + extraHosts).contains { d in
            let domain = d.lowercased()
            return h == domain || h.hasSuffix("." + domain)
        }
    }

    /// Decides a main-frame navigation. Sub-frames are left to WebKit.
    public static func evaluateNavigation(_ raw: String) -> NavigationDecision {
        guard let scheme = scheme(of: raw) else { return .block(.invalid) }
        switch scheme {
        case "http", "https":
            guard var c = webURL(raw) else { return .block(.invalid) }
            if c.user != nil || c.password != nil { return .block(.credentials) }
            if scheme == "http", let host = c.host, !isLoopbackHost(host) {
                c.scheme = "https"
                guard let upgraded = c.string else { return .block(.invalid) }
                return .upgrade(upgraded)
            }
            return .allow
        case "about":
            return raw == "about:blank" || raw == "about:srcdoc" ? .allow : .block(.scheme)
        default:
            if externalSchemes.contains(scheme) { return .external(raw) }
            // file:, data:, javascript:, blob: and anything unknown.
            return .block(.scheme)
        }
    }

    /// "scheme://host[:port]" for http(s) URLs. Default ports are omitted.
    public static func origin(of raw: String) -> String? {
        guard let c = webURL(raw), let scheme = c.scheme?.lowercased(), let host = c.host?.lowercased() else { return nil }
        let h = host.contains(":") ? "[\(host)]" : host
        if let port = c.port, !((scheme == "https" && port == 443) || (scheme == "http" && port == 80)) {
            return "\(scheme)://\(h):\(port)"
        }
        return "\(scheme)://\(h)"
    }

    public static func host(of raw: String) -> String { webURL(raw)?.host ?? "" }

    public static func securityLevel(for raw: String) -> SecurityLevel {
        switch scheme(of: raw) {
        case "https": return .secure
        case "http": return isLoopbackHost(host(of: raw)) ? .local : .insecure
        default: return .none
        }
    }

    /// Address-bar display form: drops the trailing slash on bare origins.
    public static func displayURL(_ raw: String) -> String {
        guard let c = webURL(raw) else { return raw }
        if (c.percentEncodedPath == "/" || c.percentEncodedPath.isEmpty) && c.percentEncodedQuery == nil && c.percentEncodedFragment == nil {
            return origin(of: raw) ?? raw
        }
        return raw
    }

    /// Validates a user-entered homepage or bookmark address. Only https (or this device) is accepted.
    public static func validateUserURL(_ raw: String) -> String? {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty || trimmed.rangeOfCharacter(from: .whitespacesAndNewlines) != nil { return nil }
        let normalized = scheme(of: trimmed) == nil ? "https://" + trimmed : trimmed
        guard let c = webURL(normalized), c.user == nil, c.password == nil, let host = c.host else { return nil }
        if c.scheme?.lowercased() == "http" && !isLoopbackHost(host) { return nil }
        guard host.contains(".") || isLoopbackHost(host) else { return nil }
        return c.string
    }

    /// Normalizes a plain host name ("example.edu"); nil when it is not one.
    public static func normalizeHostname(_ raw: String) -> String? {
        let s = raw.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        return hostnameRe.firstMatch(in: s, range: NSRange(s.startIndex..., in: s)) != nil ? s : nil
    }
}
