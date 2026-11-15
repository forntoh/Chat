import XCTest

/// Ordinary sample UI evidence for contextual attachment states; no accelerated Compose clock.
final class ContextualJourney: XCTestCase {
    func testImagesFailureRetryRevocationAndCustomFallbacks() {
        let app = XCUIApplication()
        app.launchArguments += ["--diagnostics"]
        app.launch()
        XCTAssertTrue(app.buttons["Conversation options"].waitForExistence(timeout: 15))
        app.buttons["Conversation options"].tap()
        app.buttons["Image sample"].tap()
        let composer = app.textViews.firstMatch
        XCTAssertTrue(composer.waitForExistence(timeout: 10))
        composer.tap()
        composer.typeText("Retained image caption")
        menu(app, "Manual service")
        for _ in 0..<2 {
            app.buttons["Attach image"].tap()
            app.buttons["Choose bundled image"].tap()
        }
        capture("two-image-preview")
        app.buttons["Send"].tap()
        menu(app, "Keep draft")
        XCTAssertTrue((composer.value as? String)?.contains("Retained image caption") == true)
        app.buttons["Send"].tap()
        menu(app, "Accept image draft")
        menu(app, "Complete upload")
        menu(app, "Fail upload")
        XCTAssertTrue(app.buttons["Retry upload"].waitForExistence(timeout: 5))
        capture("image-failure")
        app.buttons["Retry upload"].tap()
        menu(app, "Complete upload")
        XCTAssertFalse(app.buttons["Retry upload"].exists)
        capture("images-resolved")
        app.buttons["View image"].firstMatch.tap()
        XCTAssertTrue(app.buttons["Close viewer"].waitForExistence(timeout: 5))
        app.buttons["Close viewer"].tap()
        menu(app, "Revoke first image")
        XCTAssertTrue(app.staticTexts["Image unavailable"].waitForExistence(timeout: 5))
        capture("revoked-image")
        menu(app, "Reset image simulator")
        menu(app, "Custom image UI")
        XCTAssertTrue(app.staticTexts["Unsupported attachment"].waitForExistence(timeout: 5))
        capture("custom-fallbacks")
        menu(app, "Finish image loading")
        XCTAssertFalse(app.staticTexts["Loading image…"].exists)
        app.buttons["Back to conversation"].tap()
        XCTAssertTrue(app.buttons["Conversation options"].waitForExistence(timeout: 5))
    }

    private func menu(_ app: XCUIApplication, _ label: String) {
        app.buttons["Image conversation options"].tap()
        app.buttons[label].tap()
    }

    private func capture(_ name: String) {
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = "pat269-\(name)"
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
