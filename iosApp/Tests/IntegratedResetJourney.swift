import XCTest

/// Ordinary sample controls and real-time rendering; no private state or animation clock.
final class IntegratedResetJourney: XCTestCase {
    func testDefaultAndCustomResetReturnsToInitialLatest() {
        let app = XCUIApplication()
        app.launchArguments += ["--diagnostics"]
        app.launch()
        XCTAssertTrue(app.buttons["Conversation options"].waitForExistence(timeout: 15))
        app.buttons["Conversation options"].tap()
        app.buttons["Simulation debug panel"].tap()
        app.buttons["Normal exchange"].tap()
        let long = app.buttons["Long conversation"]
        for _ in 0..<5 {
            if long.isHittable { break }
            app.swipeUp()
        }
        long.tap()
        app.buttons["Start selected scenario"].tap()
        app.buttons["Pause"].tap()
        app.buttons["Close"].tap()
        for custom in [false, true] {
            if custom {
                app.buttons["Conversation options"].tap()
                app.buttons["Custom UI"].tap()
            }
            let latestRow = app.otherElements["message-history-999"]
            XCTAssertTrue(latestRow.waitForExistence(timeout: 5))
            app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.35))
                .press(forDuration: 0.05, thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.75)))
            let latest = app.buttons.matching(NSPredicate(format: "label BEGINSWITH 'Latest'")).firstMatch
            XCTAssertTrue(latest.waitForExistence(timeout: 5))
            capture(custom ? "custom-away" : "default-away")
            app.buttons["Conversation options"].tap()
            app.buttons["Simulation debug panel"].tap()
            app.buttons["Reset"].tap()
            app.buttons["Pause"].tap()
            app.buttons["Close"].tap()
            XCTAssertTrue(latestRow.waitForExistence(timeout: 5))
            XCTAssertTrue(latest.waitForNonExistence(timeout: 5))
            capture(custom ? "custom-reset-latest" : "default-reset-latest")
        }
    }
    private func capture(_ name: String) {
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = "pat272-\(name)"
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
