import XCTest

final class ConversationJourney: XCTestCase {
    func testSendFailureRetryAndRetainedDraft() {
        let app = XCUIApplication()
        app.launch()
        let composer = app.textViews.firstMatch
        XCTAssertTrue(composer.waitForExistence(timeout: 15), app.debugDescription)
        composer.tap()
        composer.typeText("Hello\nfrom iOS")
        app.buttons["Send"].tap()
        XCTAssertFalse(app.buttons["Send"].isEnabled)
        app.buttons["Accept"].tap()
        app.buttons["Fail delivery"].tap()
        XCTAssertTrue(app.staticTexts["Failed"].waitForExistence(timeout: 5))
        capture("iOS failed delivery")
        app.buttons["Retry"].tap()
        app.buttons["Deliver"].tap()
        XCTAssertFalse(app.buttons["Retry"].exists)
        XCTAssertEqual(app.staticTexts.matching(NSPredicate(format: "label == %@", "Hello\nfrom iOS")).count, 1)
        composer.tap()
        composer.typeText("Keep this draft")
        app.buttons["Leave"].tap()
        app.buttons["Open chat"].tap()
        XCTAssertTrue(composer.waitForExistence(timeout: 5))
        XCTAssertEqual(composer.value as? String, "Keep this draft")
        capture("iOS retained draft and delivered message")

    }

    private func capture(_ name: String) {
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
