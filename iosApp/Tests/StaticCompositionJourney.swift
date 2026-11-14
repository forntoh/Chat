import XCTest

final class StaticCompositionJourney: XCTestCase {
    func testDefaultAndCustomComposerRetainsDraftAndUsesSingleSafeArea() {
        let app = XCUIApplication()
        app.launch()
        let composer = app.textViews.firstMatch
        XCTAssertTrue(composer.waitForExistence(timeout: 15))
        XCTAssertLessThan(app.buttons["Leave"].frame.minY, 100, "The host must not reserve the top safe area twice")
        let closedBottom = composer.frame.maxY
        capture("default-conversation")
        composer.tap()
        composer.typeText("First line\nSecond line\nThird line\nFourth line\nFifth line\nSixth line")
        print("PAT267 input geometry: closed=\(closedBottom), composer=\(composer.frame), keyboard=\(app.keyboards.firstMatch.frame)")
        XCTAssertTrue(app.buttons["Send"].isHittable)
        capture("default-focused-multiline")
        app.buttons["Conversation options"].tap()
        app.buttons["Custom UI"].tap()
        XCTAssertTrue(app.staticTexts["Custom composer · host draft"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["Send"].isHittable)
        capture("custom-focused-multiline")
        app.buttons["Leave"].tap()
        app.buttons["Open chat"].tap()
        XCTAssertTrue(composer.waitForExistence(timeout: 5))
        XCTAssertTrue((composer.value as? String)?.contains("Sixth line") == true)
        capture("custom-retained-draft")
    }

    private func capture(_ name: String) {
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = "pat267-\(name)"
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
