import XCTest

final class BotConversationJourney: XCTestCase {
    func testPauseThemeAndResumeKeepDraft() {
        let app = XCUIApplication()
        app.launch()
        let composer = app.textViews.firstMatch
        XCTAssertTrue(composer.waitForExistence(timeout: 10))
        composer.tap()
        composer.typeText("Meet by the lake")
        app.buttons["Chat options"].tap()
        app.buttons["Pause bot"].tap()
        app.buttons["Chat options"].tap()
        app.buttons["Forest theme"].tap()
        XCTAssertEqual(composer.value as? String, "Meet by the lake")
        app.buttons["Chat options"].tap()
        app.buttons["Resume bot"].tap()
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = "bot-resumed-forest-draft"
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
