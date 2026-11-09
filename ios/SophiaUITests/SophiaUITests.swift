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
        XCUIDevice.shared.orientation = .portrait
        super.tearDown()
    }

    private func launchToPage() {
        app.launch()
        XCTAssertTrue(app.webViews.staticTexts["Discipulus test page"].waitForExistence(timeout: 20), "the start page should load")
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

        // Swiping the panel back toward its edge closes it.
        let from = app.coordinate(withNormalizedOffset: CGVector(dx: 0.9, dy: 0.5))
        let to = app.coordinate(withNormalizedOffset: CGVector(dx: 1.0, dy: 0.5)).withOffset(CGVector(dx: 40, dy: 0))
        from.press(forDuration: 0.05, thenDragTo: to, withVelocity: .fast, thenHoldForDuration: 0)
        XCTAssertTrue(controlsPanel.waitToDisappear(timeout: 5))
    }

    func testBackAndForwardFollowPageHistory() {
        launchToPage()
        app.webViews.buttons["Next page"].tap()
        XCTAssertTrue(app.webViews.staticTexts["Page 2"].waitForExistence(timeout: 5))

        swipeFromRightEdge()
        let back = app.buttons["control_back"]
        XCTAssertTrue(back.waitForExistence(timeout: 5))
        XCTAssertTrue(back.isEnabled)
        back.tap()
        XCTAssertTrue(app.webViews.staticTexts["Page 1"].waitForExistence(timeout: 5))
        let forward = app.buttons["control_forward"]
        XCTAssertTrue(forward.isEnabled)
        forward.tap()
        XCTAssertTrue(app.webViews.staticTexts["Page 2"].waitForExistence(timeout: 5))
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
        app.coordinate(withNormalizedOffset: CGVector(dx: 0.95, dy: 0.5)).tap()
        XCTAssertTrue(addressPanel.waitToDisappear(timeout: 5))
        swipeFromRightEdge()
        XCTAssertTrue(controlsPanel.waitForExistence(timeout: 5))
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
        app.buttons["assistant_review"].tap()
        let reviewText = app.staticTexts["assistant_review_text"]
        XCTAssertTrue(reviewText.waitForExistence(timeout: 5))
        XCTAssertFalse(reviewText.label.contains("test@example.com"), "contact details are removed before sending")
        app.buttons["assistant_send"].tap()
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
