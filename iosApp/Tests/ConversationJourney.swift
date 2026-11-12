import XCTest

final class ConversationJourney: XCTestCase {
    func testRTLComposerAndFailureRetryRemainReachable() {
        let app = XCUIApplication()
        app.launchArguments = ["-AppleLanguages", "(ar)", "-AppleLocale", "ar", "-NSForceRightToLeftWritingDirection", "YES"]
        app.launch()
        let composer = app.textViews.firstMatch
        XCTAssertTrue(composer.waitForExistence(timeout: 15))
        XCTAssertLessThan(app.buttons["Send"].frame.maxX, composer.frame.minX)
        composer.tap()
        composer.typeText("مرحبا\nRTL")
        XCTAssertTrue(app.buttons["Send"].isHittable)
        app.buttons["Send"].tap()
        app.buttons["Accept"].tap()
        app.buttons["Fail delivery"].tap()
        XCTAssertTrue(app.buttons["Retry"].isHittable)
        capture("iOS RTL failed delivery and reachable Retry")
        app.buttons["Retry"].tap()
        app.buttons["Deliver"].tap()
        XCTAssertFalse(app.buttons["Retry"].exists)
    }

    func testSendFailureRetryAndRetainedDraft() {
        let app = XCUIApplication()
        app.launch()
        let composer = app.textViews.firstMatch
        XCTAssertTrue(composer.waitForExistence(timeout: 15), app.debugDescription)
        let closedBottom = composer.frame.maxY
        composer.tap()
        composer.typeText("Hello\nfrom iOS")
        XCTAssertTrue(app.keyboards.firstMatch.waitForExistence(timeout: 5))
        let openBottom = composer.frame.maxY
        XCTAssertLessThan(openBottom, closedBottom)
        XCTAssertLessThanOrEqual(openBottom, app.keyboards.firstMatch.frame.minY)
        XCTAssertTrue(app.buttons["Send"].isHittable)
        capture("iOS keyboard open and composer above keyboard")
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
        XCTAssertTrue(app.keyboards.firstMatch.waitForNonExistence(timeout: 5))
        app.buttons["Open chat"].tap()
        XCTAssertTrue(composer.waitForExistence(timeout: 5))
        XCTAssertEqual(composer.value as? String, "Keep this draft")
        XCTAssertGreaterThan(composer.frame.maxY, openBottom)
        XCTAssertTrue(app.buttons["Send"].isHittable)
        capture("iOS retained draft and delivered message")

    }

    private func capture(_ name: String) {
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
