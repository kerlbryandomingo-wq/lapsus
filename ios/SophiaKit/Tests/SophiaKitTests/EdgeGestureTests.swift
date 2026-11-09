import XCTest
@testable import SophiaKit

final class EdgeGestureTests: XCTestCase {
    func testOnlyEdgeStartsOpenPanels() {
        XCTAssertEqual(EdgeGesture.beginning(startX: 5, width: 390, translationX: 20, translationY: 2, regular: false), .left)
        XCTAssertEqual(EdgeGesture.beginning(startX: 385, width: 390, translationX: -20, translationY: 2, regular: false), .right)
        XCTAssertNil(EdgeGesture.beginning(startX: 150, width: 390, translationX: 40, translationY: 0, regular: false), "middle of page scrolls the page")
    }

    func testVerticalOrWrongDirectionIsIgnored() {
        XCTAssertNil(EdgeGesture.beginning(startX: 5, width: 390, translationX: 10, translationY: 30, regular: false))
        XCTAssertNil(EdgeGesture.beginning(startX: 5, width: 390, translationX: -20, translationY: 0, regular: false))
        XCTAssertNil(EdgeGesture.beginning(startX: 385, width: 390, translationX: 20, translationY: 0, regular: false))
        XCTAssertNil(EdgeGesture.beginning(startX: 5, width: 0, translationX: 20, translationY: 0, regular: false))
    }

    func testEdgeZoneScalesWithWindow() {
        XCTAssertEqual(EdgeGesture.edgeZone(width: 390, regular: false), 24)
        XCTAssertEqual(EdgeGesture.edgeZone(width: 1024, regular: true), 32)
        XCTAssertEqual(EdgeGesture.edgeZone(width: 200, regular: false), 16, accuracy: 0.001)
    }

    func testProgressAndSettling() {
        XCTAssertEqual(EdgeGesture.progress(side: .left, translationX: 150, panelWidth: 300, startingOpen: false), 0.5)
        XCTAssertEqual(EdgeGesture.progress(side: .right, translationX: -52, panelWidth: 104, startingOpen: false), 0.5)
        XCTAssertEqual(EdgeGesture.progress(side: .left, translationX: -150, panelWidth: 300, startingOpen: true), 0.5)
        XCTAssertEqual(EdgeGesture.progress(side: .left, translationX: 900, panelWidth: 300, startingOpen: false), 1)
        XCTAssertTrue(EdgeGesture.settlesOpen(side: .left, progress: 0.5, velocityX: 0))
        XCTAssertFalse(EdgeGesture.settlesOpen(side: .left, progress: 0.2, velocityX: 0))
        XCTAssertTrue(EdgeGesture.settlesOpen(side: .left, progress: 0.1, velocityX: 900), "a flick opens")
        XCTAssertFalse(EdgeGesture.settlesOpen(side: .right, progress: 0.9, velocityX: 900), "a flick back toward the edge closes")
    }

    func testPanelWidthsDifferForIPad() {
        XCTAssertEqual(EdgeGesture.panelWidth(.address, windowWidth: 390, regular: false), 300)
        XCTAssertEqual(EdgeGesture.panelWidth(.address, windowWidth: 1180, regular: true), 380)
        XCTAssertEqual(EdgeGesture.panelWidth(.address, windowWidth: 320, regular: false), 262.4, accuracy: 0.01)
        XCTAssertGreaterThan(EdgeGesture.panelWidth(.controls, windowWidth: 1180, regular: true), EdgeGesture.panelWidth(.controls, windowWidth: 390, regular: false))
        XCTAssertEqual(EdgeGesture.panelWidth(.none, windowWidth: 390, regular: false), 0)
    }
}
