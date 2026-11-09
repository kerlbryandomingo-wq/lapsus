import XCTest
@testable import SophiaKit

final class UrlPolicyTests: XCTestCase {
    func testHostsBecomeHttps() {
        XCTAssertEqual(UrlPolicy.normalizeAddressInput("discipulusv2.amasystem.net", engine: .google), "https://discipulusv2.amasystem.net")
        XCTAssertEqual(UrlPolicy.normalizeAddressInput("example.edu/path?q=1", engine: .google), "https://example.edu/path?q=1")
        XCTAssertEqual(UrlPolicy.normalizeAddressInput("localhost:8080/x", engine: .google), "http://localhost:8080/x")
    }

    func testPlainHttpIsUpgraded() {
        XCTAssertEqual(UrlPolicy.normalizeAddressInput("http://example.com/a", engine: .google), "https://example.com/a")
        XCTAssertEqual(UrlPolicy.evaluateNavigation("http://example.com/a"), .upgrade("https://example.com/a"))
        XCTAssertEqual(UrlPolicy.evaluateNavigation("http://127.0.0.1:4000/"), .allow)
    }

    func testSearchQueries() {
        XCTAssertEqual(UrlPolicy.normalizeAddressInput("what is osmosis", engine: .google), "https://www.google.com/search?q=what%20is%20osmosis")
        XCTAssertEqual(UrlPolicy.normalizeAddressInput("c++ & java", engine: .duckduckgo), "https://duckduckgo.com/?q=c%2B%2B%20%26%20java")
        XCTAssertNil(UrlPolicy.normalizeAddressInput("   ", engine: .bing))
    }

    func testNavigationRules() {
        XCTAssertEqual(UrlPolicy.evaluateNavigation("https://discipulusv2.amasystem.net/"), .allow)
        XCTAssertEqual(UrlPolicy.evaluateNavigation("https://user:pw@example.com/"), .block(.credentials))
        XCTAssertEqual(UrlPolicy.evaluateNavigation("javascript:alert(1)"), .block(.scheme))
        XCTAssertEqual(UrlPolicy.evaluateNavigation("file:///etc/passwd"), .block(.scheme))
        XCTAssertEqual(UrlPolicy.evaluateNavigation("data:text/html,hi"), .block(.scheme))
        XCTAssertEqual(UrlPolicy.evaluateNavigation("mailto:a@b.c"), .external("mailto:a@b.c"))
        XCTAssertEqual(UrlPolicy.evaluateNavigation("about:blank"), .allow)
        XCTAssertEqual(UrlPolicy.evaluateNavigation("nonsense"), .block(.invalid))
    }

    func testPortalHosts() {
        XCTAssertTrue(UrlPolicy.isPortalHost("discipulusv2.amasystem.net"))
        XCTAssertTrue(UrlPolicy.isPortalHost("amaes.edu.ph"))
        XCTAssertFalse(UrlPolicy.isPortalHost("amasystem.net.evil.com"))
    }

    func testDisplayAndOrigin() {
        XCTAssertEqual(UrlPolicy.displayURL("https://discipulusv2.amasystem.net/"), "https://discipulusv2.amasystem.net")
        XCTAssertEqual(UrlPolicy.displayURL("https://a.com/x"), "https://a.com/x")
        XCTAssertEqual(UrlPolicy.origin(of: "https://A.com:443/x"), "https://a.com")
        XCTAssertEqual(UrlPolicy.origin(of: "https://a.com:8443/x"), "https://a.com:8443")
        XCTAssertEqual(UrlPolicy.securityLevel(for: "https://a.com"), .secure)
        XCTAssertEqual(UrlPolicy.securityLevel(for: "http://a.com"), .insecure)
    }

    func testUserURLs() {
        XCTAssertEqual(UrlPolicy.validateUserURL("lms.example.edu"), "https://lms.example.edu")
        XCTAssertNil(UrlPolicy.validateUserURL("http://lms.example.edu"))
        XCTAssertNil(UrlPolicy.validateUserURL("https://u:p@lms.example.edu"))
        XCTAssertNil(UrlPolicy.validateUserURL("not a url"))
        XCTAssertNil(UrlPolicy.validateUserURL(""))
    }

    func testBookmarksDoNotInventAddresses() {
        XCTAssertEqual(PortalBookmark.discipulus.builtInURL, "https://discipulusv2.amasystem.net/")
        XCTAssertNil(PortalBookmark.lms.builtInURL)
        XCTAssertNil(PortalBookmark.aurora.builtInURL)
        XCTAssertEqual(PortalBookmark.allCases.map(\.title), ["Discipulus", "LMS", "Aurora Login"])
    }
}
