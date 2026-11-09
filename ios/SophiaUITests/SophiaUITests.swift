import XCTest

/// End-to-end tests of Sophia's browser on a self-contained test page (no network needed).
/// They also save screenshots of the three browser states for design review.
final class SophiaUITests: XCTestCase {
    private var app: XCUIApplication!

    override func setUpWithError() throws {
        continueAfterFailure = false
        XCUIDevice.shared.orientation = .portrait
        app = XCUIApplication()
        app.launchArguments = ["-ui-testing"]
    }

    override func tearDown() {
        // A picture of the screen at the moment a test failed makes CI failures readable.
        if let run = testRun, run.failureCount > 0, app != nil, app.state == .runningForeground {
            let test = name.components(separatedBy: " ").last?.trimmingCharacters(in: CharacterSet(charactersIn: "]")) ?? "test"
            Screenshots.save(app, "failed-\(test)")
        }
        XCUIDevice.shared.orientation = .portrait
        super.tearDown()
    }

    /// Waits until the element exists and its enabled state matches.
    @discardableResult
    private func waitUntil(_ element: XCUIElement, enabled: Bool, timeout: TimeInterval = 5) -> Bool {
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            if element.exists && element.isEnabled == enabled { return true }
            usleep(200_000)
        }
        return element.exists && element.isEnabled == enabled
    }

    /// Taps the page area beside an open panel, which closes it.
    private func tapBesidePanel(_ side: CGFloat) {
        app.coordinate(withNormalizedOffset: CGVector(dx: side, dy: 0.5)).tap()
    }

    private func launchToPage() {
        app.launch()
        XCTAssertTrue(app.webViews.staticTexts["Discipulus test page"].waitForExistence(timeout: 60), "the start page should load")
    }

    private var addressPanel: XCUIElement { app.descendants(matching: .any)["address_panel"] }
    private var controlsPanel: XCUIElement { app.descendants(matching: .any)["controls_panel"] }

    private func swipeFromLeftEdge() {
        let start = app.coordinate(withNormalizedOffset: CGVector(dx: 0.004, dy: 0.5))
        let end = app.coordinate(withNormalizedOffset: CGVector(dx: 0.75, dy: 0.5))
        start.press(forDuration: 0.05, thenDragTo: end, withVelocity: .fast, thenHoldForDuration: 0)
    }

    private func swipeFromRightEdge() {
        let start = app.coordinate(withNormalizedOffset: CGVector(dx: 0.996, dy: 0.5))
        let end = app.coordinate(withNormalizedOffset: CGVector(dx: 0.25, dy: 0.5))
        start.press(forDuration: 0.05, thenDragTo: end, withVelocity: .fast, thenHoldForDuration: 0)
    }

    func testLaunchesStraightIntoTheBrowserWithNothingOverThePage() {
        launchToPage()
        XCTAssertFalse(addressPanel.exists)
        XCTAssertFalse(controlsPanel.exists)
        XCTAssertFalse(app.descendants(matching: .any)["panel_dismiss"].exists, "no overlay in the normal state")
        Screenshots.save(app, "1-normal")
    }

    func testLeftEdgeSwipeShowsAddressBarAndBookmarks() {
        launchToPage()
        swipeFromLeftEdge()
        XCTAssertTrue(addressPanel.waitForExistence(timeout: 5))
        XCTAssertTrue(app.textFields["panel_address"].exists)
        XCTAssertTrue(app.buttons["panel_bookmark_discipulus"].exists)
        XCTAssertTrue(app.buttons["panel_bookmark_lms"].exists)
        XCTAssertTrue(app.buttons["panel_bookmark_aurora"].exists)
        XCTAssertTrue(app.staticTexts["Not set up yet"].exists, "LMS and Aurora have no invented address")
        Screenshots.save(app, "2-left-panel")

        // Tapping the page beside the panel closes it.
        app.coordinate(withNormalizedOffset: CGVector(dx: 0.95, dy: 0.5)).tap()
        XCTAssertTrue(addressPanel.waitToDisappear(timeout: 5))
    }

    func testRightEdgeSwipeShowsBrowserControls() {
        launchToPage()
        swipeFromRightEdge()
        XCTAssertTrue(controlsPanel.waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["control_back"].exists)
        XCTAssertFalse(app.buttons["control_back"].isEnabled, "nothing to go back to yet")
        XCTAssertFalse(app.buttons["control_forward"].isEnabled)
        XCTAssertTrue(app.buttons["control_reload"].isEnabled)
        Screenshots.save(app, "3-right-panel")

        // Swiping the panel back toward its edge closes it. The drag runs across the panel's empty lower
        // part, from its inner edge to the screen edge.
        let panel = controlsPanel.frame
        let origin = app.coordinate(withNormalizedOffset: .zero)
        let y = app.frame.height * 0.88
        let from = origin.withOffset(CGVector(dx: panel.minX + 8, dy: y))
        let to = origin.withOffset(CGVector(dx: app.frame.width - 2, dy: y))
        from.press(forDuration: 0.05, thenDragTo: to, withVelocity: .fast, thenHoldForDuration: 0)
        XCTAssertTrue(controlsPanel.waitToDisappear(timeout: 5), "dragging the panel toward its edge closes it")
    }

    func testBackAndForwardFollowPageHistory() {
        launchToPage()
        let next = app.webViews.links["Next page"]
        XCTAssertTrue(next.waitForExistence(timeout: 5), "the test page has a Next page link")
        next.tap()
        XCTAssertTrue(app.webViews.staticTexts["Page 2"].waitForExistence(timeout: 10), "the second page loads")

        swipeFromRightEdge()
        let back = app.buttons["control_back"]
        XCTAssertTrue(back.waitForExistence(timeout: 5), "the controls panel opens")
        XCTAssertTrue(waitUntil(back, enabled: true), "Back is available after a page change")
        back.tap()
        let forward = app.buttons["control_forward"]
        XCTAssertTrue(waitUntil(forward, enabled: true), "Forward is available after going back")
        XCTAssertTrue(waitUntil(back, enabled: false), "Back is unavailable on the first page")
        XCTAssertTrue(app.webViews.staticTexts["Page 1"].waitForExistence(timeout: 10), "the page shows the first entry")
        forward.tap()
        XCTAssertTrue(waitUntil(back, enabled: true), "Back is available again after going forward")
        XCTAssertTrue(waitUntil(forward, enabled: false), "Forward is unavailable on the newest entry")
        // A tap can land while the page is still settling after the navigation; tap again if needed.
        tapBesidePanel(0.2)
        if !controlsPanel.waitToDisappear(timeout: 3) { tapBesidePanel(0.2) }
        XCTAssertTrue(controlsPanel.waitToDisappear(timeout: 5), "tapping beside the controls closes them")
    }

    func testSettingsOpenFromControls() {
        launchToPage()
        swipeFromRightEdge()
        app.buttons["control_settings"].tap()
        XCTAssertTrue(app.navigationBars["Settings"].waitForExistence(timeout: 5))
        Screenshots.save(app, "4-settings")
        app.buttons["Done"].firstMatch.tap()
        XCTAssertTrue(app.navigationBars["Settings"].waitToDisappear(timeout: 5))
    }

    func testUnsetBookmarkLeadsToSettings() {
        launchToPage()
        swipeFromLeftEdge()
        XCTAssertTrue(app.buttons["panel_bookmark_lms"].waitForExistence(timeout: 5))
        app.buttons["panel_bookmark_lms"].tap()
        XCTAssertTrue(app.navigationBars["Settings"].waitForExistence(timeout: 5))
    }

    func testLandscapePanels() {
        launchToPage()
        XCUIDevice.shared.orientation = .landscapeLeft
        sleep(1)
        swipeFromLeftEdge()
        XCTAssertTrue(addressPanel.waitForExistence(timeout: 5))
        Screenshots.save(app, "5-landscape-left-panel")
        tapBesidePanel(0.9)
        XCTAssertTrue(addressPanel.waitToDisappear(timeout: 5), "tapping beside the panel closes it in landscape")
        sleep(1)
        swipeFromRightEdge()
        XCTAssertTrue(controlsPanel.waitForExistence(timeout: 5), "the right edge swipe works in landscape")
        Screenshots.save(app, "6-landscape-right-panel")
    }

    func testStudyAssistantDemoFlow() {
        launchToPage()
        swipeFromLeftEdge()
        app.buttons["panel_assistant"].tap()
        let demo = app.buttons["Try the offline demo"]
        XCTAssertTrue(demo.waitForExistence(timeout: 5))
        demo.tap()
        let editor = app.textViews["assistant_material"]
        XCTAssertTrue(editor.waitForExistence(timeout: 5))
        editor.tap()
        editor.typeText("Photosynthesis turns light into chemical energy. Contact me at test@example.com")
        // The review button stays above the keyboard.
        let review = app.buttons["assistant_review"]
        XCTAssertTrue(review.waitForExistence(timeout: 5) && review.isHittable, "the review button can be reached")
        review.tap()
        let reviewText = app.staticTexts["assistant_review_text"]
        XCTAssertTrue(reviewText.waitForExistence(timeout: 5))
        XCTAssertFalse(reviewText.label.contains("test@example.com"), "contact details are removed before sending")
        let send = app.buttons["assistant_send"]
        var tries = 0
        XCTAssertTrue(send.waitForExistence(timeout: 5), "the review screen shows a Send button")
        while !send.isHittable && tries < 4 {
            app.swipeUp()
            tries += 1
        }
        send.tap()
        XCTAssertTrue(app.staticTexts["answer_title"].waitForExistence(timeout: 10))
        Screenshots.save(app, "7-assistant-answer")
    }

    /// Loads the real Discipulus portal. Skipped when the test machine is offline.
    func testLivePortalLoads() throws {
        app.launchArguments = ["-ui-testing", "-live-portal"]
        app.launch()
        let start = app.descendants(matching: .any)["startup_view"]
        let error = app.descendants(matching: .any)["load_error"]
        let deadline = Date().addingTimeInterval(40)
        while Date() < deadline && start.exists && !error.exists { usleep(500_000) }
        if error.exists { throw XCTSkip("The portal could not be reached from this machine.") }
        XCTAssertFalse(start.exists, "the portal should replace the startup screen")
        sleep(4) // let the portal finish drawing
        Screenshots.save(app, "8-live-portal")
        swipeFromLeftEdge()
        if addressPanel.waitForExistence(timeout: 5) { Screenshots.save(app, "9-live-portal-left-panel") }
        app.coordinate(withNormalizedOffset: CGVector(dx: 0.95, dy: 0.5)).tap()
        _ = addressPanel.waitToDisappear(timeout: 5)
        swipeFromRightEdge()
        if controlsPanel.waitForExistence(timeout: 5) { Screenshots.save(app, "10-live-portal-right-panel") }
    }
}

enum Screenshots {
    /// Attaches a screenshot to the test result and, when SOPHIA_SCREENSHOT_DIR is set (CI), writes it
    /// there as "<device>-<name>.png".
    static func save(_ app: XCUIApplication, _ name: String) {
        let shot = app.screenshot()
        let attachment = XCTAttachment(screenshot: shot)
        attachment.name = name
        attachment.lifetime = .keepAlways
        XCTContext.runActivity(named: "Screenshot \(name)") { $0.add(attachment) }
        let env = ProcessInfo.processInfo.environment
        guard let dir = env["SOPHIA_SCREENSHOT_DIR"], !dir.isEmpty else { return }
        let device = (env["SIMULATOR_DEVICE_NAME"] ?? "device").replacingOccurrences(of: " ", with: "_")
        let url = URL(fileURLWithPath: dir).appendingPathComponent("\(device)-\(name).png")
        try? FileManager.default.createDirectory(at: URL(fileURLWithPath: dir), withIntermediateDirectories: true)
        try? shot.pngRepresentation.write(to: url)
    }
}

extension XCUIElement {
    /// Waits until the element is gone. Returns true when it disappeared in time.
    @discardableResult
    func waitToDisappear(timeout: TimeInterval) -> Bool {
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            if !exists { return true }
            usleep(200_000)
        }
        return !exists
    }
}
