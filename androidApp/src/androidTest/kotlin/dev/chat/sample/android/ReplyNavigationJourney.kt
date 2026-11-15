package dev.chat.sample.android

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.bottom
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.height
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.top
import androidx.compose.ui.test.width
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import java.io.File

class ReplyNavigationJourney {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val window = compose.activity.window
        val bitmap = Bitmap.createBitmap(window.decorView.width, window.decorView.height, Bitmap.Config.ARGB_8888)
        val completed = java.util.concurrent.CountDownLatch(1)
        var result = -1
        android.view.PixelCopy.request(
            window,
            bitmap,
            { status ->
                result = status
                completed.countDown()
            },
            android.os.Handler(android.os.Looper.getMainLooper()),
        )
        check(completed.await(5, java.util.concurrent.TimeUnit.SECONDS) && result == android.view.PixelCopy.SUCCESS)
        File(instrumentation.targetContext.filesDir, "$name.png").outputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        bitmap.recycle()
    }

    private fun waitForClosedKeyboardComposer() {
        val field = compose.onNodeWithTag("composer")
        val density = compose.activity.resources.displayMetrics.density
        compose.waitUntil(5000) {
            val decor = compose.activity.window.decorView
            val systemBottom = decor.rootWindowInsets.getInsets(android.view.WindowInsets.Type.systemBars()).bottom
            val expectedBottom = decor.height - maxOf(systemBottom.toFloat(), 8f * density) - 4f * density
            kotlin.math.abs(field.fetchSemanticsNode().boundsInRoot.bottom - expectedBottom) <= 1f
        }
    }

    @Test fun floatingComposerShowsHistoryBehindItAndRetainsMultilineDraft() {
        val field = compose.onNodeWithTag("composer")
        for (n in 1..6) {
            field.performTextInput("Composer journey $n. More messages to demonstrate history behind the floating field.")
            compose.onNodeWithContentDescription("Send").performClick()
            compose.waitUntil(10000) {
                field.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text.isEmpty()
            }
        }
        field.performTouchInput { click() }
        compose.waitUntil(5000) {
            compose.activity.window.decorView.rootWindowInsets.isVisible(android.view.WindowInsets.Type.ime())
        }
        androidx.test.espresso.Espresso.pressBack()
        compose.waitUntil(5000) {
            !compose.activity.window.decorView.rootWindowInsets.isVisible(android.view.WindowInsets.Type.ime())
        }
        capture("composer-default")
        compose.onNodeWithTag("timeline").performTouchInput { swipeDown(startY = height * 0.25f, endY = height * 0.55f) }
        compose.onNodeWithTag("timeline").performScrollToIndex(2)
        val viewport = compose.onNodeWithTag("timeline").fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertTrue(
            "Timeline continues behind the floating input",
            viewport.bottom > field.fetchSemanticsNode().boundsInRoot.bottom,
        )
        capture("composer-history-behind")
        field.performClick().performTextInput("One\nTwo\nThree\nFour\nFive\nSix")
        compose.onNodeWithContentDescription("Send").assertIsDisplayed().assertIsEnabled()
        capture("composer-multiline-keyboard")
        compose.onNodeWithTag("demo-menu").performClick()
        compose.onNodeWithText("Custom UI").performClick()
        field.assertTextContains("Six", substring = true)
        capture("composer-custom-multiline")
    }

    @Test fun longSimulationReturnsFromTopWithoutStalling() {
        compose.onNodeWithTag("demo-menu").performClick()
        compose.onNodeWithText("Simulation debug panel").performClick()
        compose.onNodeWithText("Normal exchange").performClick()
        compose.onNodeWithText("Long conversation").performScrollTo().performClick()
        compose.onNodeWithText("Start selected scenario").performScrollTo().performClick()
        compose.onNodeWithText("Close").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Message 999").fetchSemanticsNodes().isNotEmpty() }
        // Establish real reader intent before the test accelerates the long manual scroll.
        compose.onNodeWithTag("timeline").performTouchInput { swipeDown(startY = height * 0.25f, endY = height * 0.55f) }
        compose.onNodeWithTag("timeline").performScrollToIndex(0)
        compose.onNodeWithText("Message 0").assertIsDisplayed()
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("Latest", substring = true).fetchSemanticsNodes().isNotEmpty() }
        capture("long-top")
        compose.onNodeWithContentDescription("Latest", substring = true).performTouchInput { click() }
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText("Message 999").fetchSemanticsNodes().any { it.boundsInRoot.height > 0 }
        }
        compose.onNodeWithText("Message 999").assertIsDisplayed()
        capture("long-latest")
    }

    @Test fun replyFocusAndKeyboardSurviveSendUntilTimelineTap() {
        val field = compose.onNodeWithTag("composer")
        compose.onNodeWithTag("bubble-morgan", useUnmergedTree = true).performTouchInput { longClick() }
        compose.onNodeWithText("Reply").performClick()
        field.assertIsFocused()
        compose.waitUntil(5000) {
            compose.activity.window.decorView.rootWindowInsets.isVisible(android.view.WindowInsets.Type.ime())
        }
        field.performTextInput("Reply without closing keyboard")
        compose.onNodeWithContentDescription("Send").performTouchInput { click() }
        field.assertIsFocused()
        compose.waitUntil(10000) {
            field.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text.isEmpty()
        }
        field.assertIsFocused()
        org.junit.Assert.assertTrue(compose.activity.window.decorView.rootWindowInsets.isVisible(android.view.WindowInsets.Type.ime()))
        capture("focus-after-send")
        compose.onNodeWithTag("timeline").performTouchInput { click(androidx.compose.ui.geometry.Offset(4f, 250f)) }
        field.assertIsNotFocused()
        compose.waitUntil(5000) {
            !compose.activity.window.decorView.rootWindowInsets.isVisible(android.view.WindowInsets.Type.ime())
        }
        capture("focus-outside-dismissed")
    }

    @Test fun composerKeepsGapAboveVisibleKeyboard() {
        capture("composer-default")
        val field = compose.onNodeWithTag("composer")
        field.performTouchInput { click() }
        compose.waitUntil(5000) {
            compose.activity.window.decorView.rootWindowInsets.isVisible(android.view.WindowInsets.Type.ime())
        }
        val density = compose.activity.resources.displayMetrics.density
        compose.waitUntil(5000) {
            val decor = compose.activity.window.decorView
            val keyboardTop = decor.height - decor.rootWindowInsets.getInsets(android.view.WindowInsets.Type.ime()).bottom
            keyboardTop - field.fetchSemanticsNode().boundsInRoot.bottom >= 12f * density - 1f
        }
        // 8dp outside the surface plus its 4dp inner field inset.
        capture("composer-keyboard-gap")
    }

    @Test fun keyboardKeepsLatestVisibleAndSendingLeavesHistory() {
        val field = compose.onNodeWithTag("composer")
        for (n in 1..8) {
            field.performTextInput("Keyboard journey message $n. Building enough history to check bottom following.")
            compose.onNodeWithContentDescription("Send").performClick()
            compose.waitUntil(10000) {
                field.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text.isEmpty()
            }
        }
        field.performTouchInput { click() }
        compose.waitUntil(5000) {
            compose.activity.window.decorView.rootWindowInsets.isVisible(android.view.WindowInsets.Type.ime())
        }
        val newest = compose.onNodeWithText("Keyboard journey message 8. Building enough history to check bottom following.")
        newest.assertIsDisplayed()
        compose.waitUntil(5000) {
            newest.fetchSemanticsNode().boundsInRoot.bottom <= field.fetchSemanticsNode().boundsInRoot.top + 1
        }
        capture("keyboard-open-latest")
        androidx.test.espresso.Espresso.pressBack()
        compose.waitUntil(5000) {
            !compose.activity.window.decorView.rootWindowInsets.isVisible(android.view.WindowInsets.Type.ime())
        }
        newest.assertIsDisplayed()
        waitForClosedKeyboardComposer()
        field.performTouchInput { click() }
        compose.waitUntil(5000) {
            compose.activity.window.decorView.rootWindowInsets.isVisible(android.view.WindowInsets.Type.ime())
        }
        newest.assertIsDisplayed()
        compose.waitUntil(5000) {
            newest.fetchSemanticsNode().boundsInRoot.bottom <= field.fetchSemanticsNode().boundsInRoot.top + 1
        }
        compose.onNodeWithTag("timeline").performScrollToIndex(1)
        field.performTextInput("Sent while viewing history")
        compose.onNodeWithContentDescription("Send").performClick()
        compose.waitUntil(10000) {
            field.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text.isEmpty()
        }
        compose.onNodeWithText("Sent while viewing history").assertIsDisplayed()
        capture("send-from-history")
    }

    @Test fun embeddedReplyAndAnimatedLatest() {
        compose.onNodeWithTag("bubble-morgan", useUnmergedTree = true).performScrollTo().performTouchInput { longClick() }
        compose.onNodeWithText("Reply").performClick()
        compose.onNodeWithTag("composer").performTextInput("A reply with an embedded preview")
        capture("composer-reply")
        compose.onNodeWithContentDescription("Send").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("bubble-studio-1", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        if (compose.onAllNodesWithContentDescription("Latest", substring = true).fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithContentDescription("Latest", substring = true).performClick()
        }
        for (n in 1..2) {
            compose.waitUntil(10000) {
                compose.onNodeWithTag(
                    "composer",
                ).fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text.isEmpty()
            }
            compose.onNodeWithTag(
                "composer",
            ).performTextInput("Native follow message $n. This keeps the conversation readable while new messages arrive.")
            compose.onNodeWithContentDescription("Send").performClick()
            compose.waitUntil(10000) {
                compose.onNodeWithTag(
                    "composer",
                ).fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text.isEmpty()
            }
            compose.onNodeWithText(
                "Native follow message $n. This keeps the conversation readable while new messages arrive.",
            ).assertIsDisplayed()
        }
        compose.onNodeWithTag("timeline").performTouchInput { swipeDown(startY = height * 0.25f, endY = height * 0.55f) }
        compose.onNodeWithTag("timeline").performScrollToKey("studio-1")
        compose.onNodeWithTag("bubble-studio-1", useUnmergedTree = true).assertIsDisplayed()
        capture("reply-default")
        compose.onNode(
            hasContentDescription("Reply to Morgan Lee", substring = true) and hasAnyAncestor(hasTestTag("message-studio-1")),
            useUnmergedTree = true,
        ).performClick()
        val viewport = compose.onNodeWithTag("timeline").fetchSemanticsNode().boundsInRoot
        val target = compose.onNodeWithTag("message-morgan").fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertTrue(target.top >= viewport.top && target.bottom <= viewport.bottom)
        capture("reply-centered")
        compose.onNodeWithTag("demo-menu").performClick()
        compose.onNodeWithText("Custom UI").performClick()
        capture("reply-custom-centered")
        compose.onNodeWithContentDescription("Latest", substring = true).performClick()
        capture("reply-latest")
        compose.onNodeWithTag("timeline").performTouchInput { swipeDown(startY = height * 0.25f, endY = height * 0.55f) }
        compose.onNodeWithTag("timeline").performScrollToIndex(0)
        capture("reply-history-start")
        compose.onNodeWithText("Start of conversation").assertIsDisplayed()
        compose.onNodeWithTag("timeline").performScrollToIndex(3)
        compose.onNodeWithText("Start of conversation").assertIsNotDisplayed()
        capture("reply-floating-latest")
    }
}
