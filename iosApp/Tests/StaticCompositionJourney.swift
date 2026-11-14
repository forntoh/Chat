import XCTest

final class StaticCompositionJourney: XCTestCase {
    func testDefaultAndCustomComposerRetainsDraftAndUsesSingleSafeArea() {
        let app = XCUIApplication()
        app.launch()
        let composer = app.textViews.firstMatch
        XCTAssertTrue(composer.waitForExistence(timeout: 15))
        XCTAssertLessThan(app.buttons["Leave"].frame.minY, 100, "The host must not reserve the top safe area twice")
        let closedBottom = composer.frame.maxY
        XCTAssertEqual(app.frame.maxY - closedBottom, composer.frame.minX, accuracy: 1, "The bottom gap must match the side gutter")
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

    func testLongPressFocusAndCustomSurfaceDismissal() {
        let app = XCUIApplication()
        app.launch()
        let bubble = app.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS %@", "Morning! I’ve added")).firstMatch
        XCTAssertTrue(bubble.waitForExistence(timeout: 15))
        bubble.tap()
        XCTAssertFalse(app.buttons["Copy"].exists)
        let originalFrame = bubble.frame
        bubble.press(forDuration: 0.7)
        XCTAssertTrue(app.buttons["Copy"].waitForExistence(timeout: 5))
        let liftedBubble = app.descendants(matching: .any).matching(NSPredicate(format: "label == %@",
            "Avery Chen, Morning! I’ve added the latest sketches for the shared space., 09:41")).firstMatch
        XCTAssertTrue(liftedBubble.waitForExistence(timeout: 5))
        XCTAssertEqual(liftedBubble.frame.minX, originalFrame.minX, accuracy: 2, "Incoming growth anchors to its left edge")
        XCTAssertEqual(liftedBubble.frame.width, originalFrame.width * 1.05, accuracy: 3, "Focus grows the bubble by 5%")
        XCTAssertEqual(liftedBubble.frame.midY, originalFrame.midY, accuracy: 3, "A bubble with room for its menu must stay anchored")
        let scrim = app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", "Dismiss message actions")).firstMatch
        XCTAssertTrue(scrim.exists)
        XCTAssertEqual(scrim.frame.minY, app.frame.minY, accuracy: 1, "Scrim must cover the top safe area")
        XCTAssertEqual(scrim.frame.maxY, app.frame.maxY, accuracy: 1, "Scrim must cover the bottom safe area")
        XCTAssertEqual(scrim.frame.width, app.frame.width, accuracy: 1)
        capture("default-action-focus")
        app.buttons["Close actions"].tap()
        XCTAssertTrue(bubble.waitForExistence(timeout: 5))
        app.buttons["Conversation options"].tap()
        app.buttons["Custom UI"].tap()
        let customBubble = app.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS %@", "Morning! I’ve added")).firstMatch
        XCTAssertTrue(customBubble.waitForExistence(timeout: 5))
        customBubble.press(forDuration: 0.7)
        XCTAssertTrue(app.buttons["Copy"].waitForExistence(timeout: 5))
        capture("custom-action-focus")
        app.buttons["Close actions"].tap()
    }

    func testEmbeddedReplyAndLatestNavigation() {
        let app = XCUIApplication()
        app.launch()
        let composer = app.textViews.firstMatch
        XCTAssertTrue(composer.waitForExistence(timeout: 15))
        let target = app.descendants(matching: .any).matching(NSPredicate(format: "label BEGINSWITH %@", "Morgan Lee, I’ll bring")).firstMatch
        XCTAssertTrue(target.waitForExistence(timeout: 5))
        target.press(forDuration: 0.7)
        app.buttons["Reply"].tap()
        composer.tap()
        composer.typeText("A reply with an embedded preview")
        capture("composer-reply")
        app.buttons["Send"].tap()
        let preview = app.descendants(matching: .any).matching(NSPredicate(format: "label BEGINSWITH %@", "Reply to Morgan Lee")).firstMatch
        XCTAssertTrue(preview.waitForExistence(timeout: 10))
        capture("reply-default")
        preview.tap()
        capture("reply-centered")
        let returnToBottom = app.buttons.matching(NSPredicate(format: "label CONTAINS %@", "Latest")).firstMatch
        if returnToBottom.exists { returnToBottom.tap() }
        for n in 1...4 {
            composer.tap()
            composer.typeText("Native follow message \(n). More conversation content for the scrolling journey.")
            app.buttons["Send"].tap()
            let message = app.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS %@", "Native follow message \(n)")).firstMatch
            XCTAssertTrue(message.waitForExistence(timeout: 10))
        }
        app.swipeDown()
        let latest = app.buttons.matching(NSPredicate(format: "label CONTAINS %@", "Latest")).firstMatch
        XCTAssertTrue(latest.waitForExistence(timeout: 5))
        capture("reply-floating-latest")
        latest.tap()
        app.buttons["Conversation options"].tap()
        app.buttons["Custom UI"].tap()
        capture("reply-custom-latest")
    }

    func testKeyboardKeepsLatestAndSendReturnsFromHistory() throws {
        let app = XCUIApplication()
        app.launch()
        let composer = app.textViews.firstMatch
        XCTAssertTrue(composer.waitForExistence(timeout: 15))
        for n in 1...8 {
            composer.tap()
            composer.typeText("Keyboard journey message \(n). Building enough history for bottom following.")
            app.buttons["Send"].tap()
            let accepted = app.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS %@", "Keyboard journey message \(n)")).firstMatch
            XCTAssertTrue(accepted.waitForExistence(timeout: 10))
        }
        let latest = app.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS %@", "Keyboard journey message 8")).firstMatch
        try XCTSkipUnless(app.keyboards.firstMatch.waitForExistence(timeout: 5), "This simulator does not expose a software keyboard; keyboard overlap is unverified")
        XCTAssertLessThanOrEqual(latest.frame.maxY, composer.frame.minY + 1)
        XCTAssertLessThanOrEqual(composer.frame.maxY, app.keyboards.firstMatch.frame.minY + 1)
        capture("keyboard-open-latest")
        // Dismiss and reopen without leaving the conversation.
        app.swipeDown()
        composer.tap()
        XCTAssertTrue(app.keyboards.firstMatch.waitForExistence(timeout: 5))
        let options = app.buttons["Conversation options"]
        options.tap()
        app.buttons["Custom UI"].tap()
        // Scroll backward within the timeline while the composer remains editable.
        app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.3)).press(forDuration: 0.05,
            thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.55)))
        composer.tap()
        composer.typeText("Sent while viewing history")
        app.buttons["Send"].tap()
        let sent = app.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS %@", "Sent while viewing history")).firstMatch
        XCTAssertTrue(sent.waitForExistence(timeout: 10))
        XCTAssertLessThanOrEqual(sent.frame.maxY, composer.frame.minY + 1)
        capture("send-from-history-custom")
    }

    private func capture(_ name: String) {
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = "pat267-\(name)"
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
