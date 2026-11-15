import XCTest

/// Focused ordinary-app evidence: XCTest drives native input, never the Compose animation clock.
final class MotionJourney: XCTestCase {
    func testDefaultAndCustomSendComposerAndPreviewMotion() {
        let app = XCUIApplication()
        app.launchArguments += ["--diagnostics"]
        app.launch()
        let composer = app.textViews.firstMatch
        XCTAssertTrue(composer.waitForExistence(timeout: 15))
        for custom in [false, true] {
            if custom {
                app.buttons["Conversation options"].tap()
                app.buttons["Custom UI"].tap()
            }
            let text = custom ? "Custom motion message" : "Default motion message"
            composer.tap()
            composer.typeText(text + "\nSecond line\nThird line")
            capture(custom ? "custom-multiline" : "default-multiline")
            app.buttons["Send"].tap()
            let sent = app.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS %@", text)).firstMatch
            XCTAssertTrue(sent.waitForExistence(timeout: 10))
            // Seeded Exchange IDs are public sample row tags; visible counts change under virtualization.
            let incomingRow = app.otherElements["message-arrival-\(custom ? 6 : 2)"]
            XCTAssertTrue(incomingRow.waitForExistence(timeout: 10))
            capture(custom ? "custom-send-incoming" : "default-send-incoming")
            incomingRow.coordinate(withNormalizedOffset: CGVector(dx: 0.35, dy: 0.5)).press(forDuration: 0.7)
            XCTAssertTrue(app.buttons["Reply"].waitForExistence(timeout: 5))
            app.buttons["Reply"].tap()
            XCTAssertTrue(app.buttons["Cancel reply"].waitForExistence(timeout: 5))
            composer.typeText("Unrelated draft retained")
            capture(custom ? "custom-reply-preview" : "default-reply-preview")
            app.buttons["Cancel reply"].tap()
            XCTAssertTrue((composer.value as? String)?.contains("Unrelated draft retained") == true)
            app.buttons["Send"].tap()
            XCTAssertTrue(app.otherElements["message-arrival-\(custom ? 8 : 4)"].waitForExistence(timeout: 10))
        }
        app.buttons["Leave"].tap()
        app.buttons["Open chat"].tap()
        XCTAssertTrue(composer.waitForExistence(timeout: 5))
        capture("remounted-latest")
    }

    private func capture(_ name: String) {
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = "pat268-\(name)"
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
