import XCTest
import SwiftData
import SophiaKit
@testable import Sophia

@MainActor
final class SettingsStoreTests: XCTestCase {
    private var defaults: UserDefaults!

    override func setUp() {
        defaults = UserDefaults(suiteName: "SophiaTests.settings")!
        defaults.removePersistentDomain(forName: "SophiaTests.settings")
    }

    func testDefaultsMatchAndroid() {
        let s = SettingsStore(defaults: defaults)
        XCTAssertEqual(s.homepage, "https://discipulusv2.amasystem.net/")
        XCTAssertEqual(s.searchEngine, .google)
        XCTAssertTrue(s.javaScriptEnabled)
        XCTAssertTrue(s.historyEnabled)
        XCTAssertFalse(s.showPanelHandles, "the normal browser state shows nothing over the page")
        XCTAssertEqual(s.aiProvider, .off)
        XCTAssertEqual(s.anthropicModel, "claude-opus-5-5")
        XCTAssertNil(s.url(for: .lms), "LMS address is not invented")
        XCTAssertNil(s.url(for: .aurora), "Aurora address is not invented")
        XCTAssertEqual(s.url(for: .discipulus), SophiaConstants.defaultPortalURL)
    }

    func testValuesPersist() {
        let s = SettingsStore(defaults: defaults)
        s.searchEngine = .duckduckgo
        s.appearance = .dark
        s.lmsURL = "https://lms.example.edu"
        s.setPermission(origin: "https://a.edu", kind: .camera, allowed: true)
        let again = SettingsStore(defaults: defaults)
        XCTAssertEqual(again.searchEngine, .duckduckgo)
        XCTAssertEqual(again.appearance, .dark)
        XCTAssertEqual(again.url(for: .lms), "https://lms.example.edu")
        XCTAssertEqual(again.permission(origin: "https://a.edu", kind: .camera), true)
        XCTAssertNil(again.permission(origin: "https://a.edu", kind: .microphone))
    }

    func testResetRestoresDefaults() {
        let s = SettingsStore(defaults: defaults)
        s.homepage = "https://example.edu"
        s.aiProvider = .demo
        s.resetAll()
        XCTAssertEqual(s.homepage, SophiaConstants.defaultPortalURL)
        XCTAssertEqual(s.aiProvider, .off)
        XCTAssertEqual(SettingsStore(defaults: defaults).homepage, SophiaConstants.defaultPortalURL)
    }
}

@MainActor
final class HistoryTests: XCTestCase {
    func testRecordsDeduplicatesAndClears() throws {
        let container = SophiaStore.makeContainer(inMemory: true)
        let context = container.mainContext
        let h = HistoryRecorder(context: context)
        h.record(url: "https://discipulusv2.amasystem.net/", title: "Discipulus", now: Date(timeIntervalSince1970: 100))
        h.record(url: "https://discipulusv2.amasystem.net/", title: "Discipulus", now: Date(timeIntervalSince1970: 200))
        h.record(url: "https://example.edu/a", title: "A", now: Date(timeIntervalSince1970: 300))
        h.record(url: "about:blank", title: "", now: Date(timeIntervalSince1970: 400))
        var all = try context.fetch(FetchDescriptor<HistoryEntry>(sortBy: [SortDescriptor(\.visitedAt)]))
        XCTAssertEqual(all.map(\.url), ["https://discipulusv2.amasystem.net/", "https://example.edu/a"])
        XCTAssertEqual(all.first?.visitedAt, Date(timeIntervalSince1970: 200))

        h.updateTitle(url: "https://example.edu/a", title: "Lesson A")
        all = try context.fetch(FetchDescriptor<HistoryEntry>(sortBy: [SortDescriptor(\.visitedAt)]))
        XCTAssertEqual(all.last?.title, "Lesson A")

        h.clear(since: Date(timeIntervalSince1970: 250))
        XCTAssertEqual(try context.fetchCount(FetchDescriptor<HistoryEntry>()), 1)
        h.clear()
        XCTAssertEqual(try context.fetchCount(FetchDescriptor<HistoryEntry>()), 0)
    }

    func testPrunesOldestBeyondLimit() throws {
        // Keep the container alive: a ModelContext doesn't retain it.
        let container = SophiaStore.makeContainer(inMemory: true)
        let context = container.mainContext
        let h = HistoryRecorder(context: context)
        for i in 0..<12 { h.record(url: "https://e.edu/\(i)", title: "\(i)", now: Date(timeIntervalSince1970: Double(i))) }
        h.prune(limit: 5)
        let left = try context.fetch(FetchDescriptor<HistoryEntry>(sortBy: [SortDescriptor(\.visitedAt)]))
        XCTAssertEqual(left.map(\.title), ["7", "8", "9", "10", "11"])
    }
}

@MainActor
final class BrowserModelTests: XCTestCase {
    private func makeModel() -> BrowserModel {
        let defaults = UserDefaults(suiteName: "SophiaTests.browser")!
        defaults.removePersistentDomain(forName: "SophiaTests.browser")
        return BrowserModel(settings: SettingsStore(defaults: defaults), downloads: DownloadManager(directory: FileManager.default.temporaryDirectory.appendingPathComponent("dl-tests")))
    }

    func testNormalStateHasNoPanel() {
        let m = makeModel()
        XCTAssertEqual(m.panel, .none)
        XCTAssertFalse(m.anyPanelVisible)
        XCTAssertEqual(m.reveal(.address), 0)
        XCTAssertEqual(m.reveal(.controls), 0)
    }

    func testEdgeDragOpensAndClosesPanels() {
        let m = makeModel()
        m.windowWidth = 390
        m.beginDrag(side: .left, startingOpen: false)
        m.updateDrag(translationX: 150)
        XCTAssertEqual(m.reveal(.address), 0.5, accuracy: 0.001)
        m.endDrag(velocityX: 0)
        XCTAssertEqual(m.panel, .address)
        XCTAssertNil(m.drag)

        // Dragging the open panel back toward its edge closes it.
        m.beginDrag(side: .left, startingOpen: true)
        m.updateDrag(translationX: -250)
        m.endDrag(velocityX: -100)
        XCTAssertEqual(m.panel, .none)

        // A short, slow pull from the right edge snaps back closed.
        m.beginDrag(side: .right, startingOpen: false)
        m.updateDrag(translationX: -20)
        m.endDrag(velocityX: 0)
        XCTAssertEqual(m.panel, .none)

        m.beginDrag(side: .right, startingOpen: false)
        m.updateDrag(translationX: -10)
        m.endDrag(velocityX: -1200)
        XCTAssertEqual(m.panel, .controls, "a flick opens the panel")
    }

    func testOnlyOnePanelAtATime() {
        let m = makeModel()
        m.show(.address)
        m.show(.controls)
        XCTAssertEqual(m.reveal(.address), 0)
        XCTAssertEqual(m.reveal(.controls), 1)
        m.close()
        XCTAssertFalse(m.anyPanelVisible)
    }

    func testUnsetBookmarkOpensSettings() {
        let m = makeModel()
        m.openBookmark(.lms)
        XCTAssertEqual(m.sheet, .settings)
        XCTAssertNotNil(m.notice)
    }

    func testBlockedAddressShowsNotice() {
        let m = makeModel()
        m.open("javascript:alert(1)")
        XCTAssertEqual(m.notice, BlockReason.scheme.message)
    }

    func testErrorDescriptions() {
        let offline = BrowserModel.describe(NSError(domain: NSURLErrorDomain, code: NSURLErrorNotConnectedToInternet), url: "https://discipulusv2.amasystem.net/")
        XCTAssertEqual(offline.title, "You're offline")
        let cert = BrowserModel.describe(NSError(domain: NSURLErrorDomain, code: NSURLErrorServerCertificateUntrusted), url: "https://bad.example/")
        XCTAssertEqual(cert.title, "Connection isn't secure")
        XCTAssertTrue(cert.message.contains("bad.example"))
    }

    func testIgnoresCancelledLoads() {
        let m = makeModel()
        m.didFail(NSError(domain: NSURLErrorDomain, code: NSURLErrorCancelled), url: "https://a.edu")
        XCTAssertNil(m.loadError)
        m.didFail(NSError(domain: NSURLErrorDomain, code: NSURLErrorTimedOut), url: "https://a.edu")
        XCTAssertEqual(m.loadError?.title, "The page took too long")
    }
}

@MainActor
final class AssistantModelTests: XCTestCase {
    private func settings() -> SettingsStore {
        let d = UserDefaults(suiteName: "SophiaTests.assistant")!
        d.removePersistentDomain(forName: "SophiaTests.assistant")
        return SettingsStore(defaults: d)
    }

    func testNeedsSetupWhenOff() {
        let m = AssistantModel(settings: settings(), material: "Cells", keychain: KeychainStore(service: "SophiaTests.keys"))
        XCTAssertNotNil(m.setupProblem)
        XCTAssertFalse(m.canReview)
    }

    func testNeedsKeyForRealProviders() {
        let keychain = KeychainStore(service: "SophiaTests.keys")
        keychain.deleteAll()
        let s = settings()
        s.aiProvider = .anthropic
        let m = AssistantModel(settings: s, material: "Cells", keychain: keychain)
        XCTAssertEqual(m.setupProblem, "Add your Anthropic Claude API key in Study Assistant settings.")
        XCTAssertTrue(keychain.setKey("sk-ant-test", for: .anthropic))
        XCTAssertNil(m.setupProblem)
        XCTAssertEqual(m.destination, "api.anthropic.com")
        keychain.deleteAll()
        XCTAssertFalse(keychain.hasKey(for: .anthropic))
    }

    func testReviewRedactsThenDemoAnswers() async throws {
        let s = settings()
        s.aiProvider = .demo
        let m = AssistantModel(settings: s, material: "Mitosis\nmy password: secret123", keychain: KeychainStore(service: "SophiaTests.keys"))
        m.review()
        guard case .reviewing(let prepared) = m.phase else { return XCTFail("expected review") }
        XCTAssertFalse(prepared.text.contains("secret123"))
        XCTAssertEqual(prepared.redactions, 1)
        m.send()
        XCTAssertEqual(m.phase, .sending)
        for _ in 0..<50 where m.phase == .sending { try await Task.sleep(nanoseconds: 100_000_000) }
        guard case .answer(let answer) = m.phase else { return XCTFail("expected an answer, got \(m.phase)") }
        XCTAssertEqual(answer.title, "Mitosis")

        let container = SophiaStore.makeContainer(inMemory: true)
        let context = container.mainContext
        m.save(answer, in: context)
        XCTAssertEqual(try context.fetchCount(FetchDescriptor<SavedNote>()), 1)
    }

    func testCancelReturnsToEditing() {
        let s = settings()
        s.aiProvider = .demo
        let m = AssistantModel(settings: s, material: "x", keychain: KeychainStore(service: "SophiaTests.keys"))
        m.review()
        m.send()
        m.cancel()
        XCTAssertEqual(m.phase, .editing)
    }
}
