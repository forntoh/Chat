package dev.chat.sample.android

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import java.io.File

class ReplyNavigationJourney {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        File(instrumentation.targetContext.filesDir, "$name.png").outputStream().use {
            check(instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it))
        }
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
        field.performClick()
        compose.waitUntil(5000) {
            compose.activity.window.decorView.rootWindowInsets.isVisible(android.view.WindowInsets.Type.ime())
        }
        val newest = compose.onNodeWithText("Keyboard journey message 8. Building enough history to check bottom following.")
        newest.assertIsDisplayed()
        var viewport = compose.onNodeWithTag("timeline").fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertTrue(newest.fetchSemanticsNode().boundsInRoot.bottom <= viewport.bottom + 1)
        capture("keyboard-open-latest")
        androidx.test.espresso.Espresso.pressBack()
        compose.waitUntil(5000) {
            !compose.activity.window.decorView.rootWindowInsets.isVisible(android.view.WindowInsets.Type.ime())
        }
        newest.assertIsDisplayed()
        field.performClick()
        compose.waitUntil(5000) {
            compose.activity.window.decorView.rootWindowInsets.isVisible(android.view.WindowInsets.Type.ime())
        }
        newest.assertIsDisplayed()
        viewport = compose.onNodeWithTag("timeline").fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertTrue(newest.fetchSemanticsNode().boundsInRoot.bottom <= viewport.bottom + 1)
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
        compose.onNodeWithContentDescription("Send").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("bubble-studio-1", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        if (compose.onAllNodesWithContentDescription("Latest", substring = true).fetchSemanticsNodes().isNotEmpty())
            compose.onNodeWithContentDescription("Latest", substring = true).performClick()
        for (n in 1..5) {
            compose.waitUntil(10000) {
                compose.onNodeWithTag("composer").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text.isEmpty()
            }
            compose.onNodeWithTag("composer").performTextInput("Native follow message $n. This keeps the conversation readable while new messages arrive.")
            compose.onNodeWithContentDescription("Send").performClick()
            compose.waitUntil(10000) {
                compose.onNodeWithTag("composer").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text.isEmpty()
            }
            compose.onNodeWithText("Native follow message $n. This keeps the conversation readable while new messages arrive.").assertIsDisplayed()
        }
        compose.onNodeWithTag("timeline").performScrollToNode(hasTestTag("message-studio-1"))
        capture("reply-default")
        compose.onNodeWithContentDescription("Reply to Morgan Lee", useUnmergedTree = true).performClick()
        val viewport = compose.onNodeWithTag("timeline").fetchSemanticsNode().boundsInRoot
        val target = compose.onNodeWithTag("message-morgan").fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertTrue(target.top >= viewport.top && target.bottom <= viewport.bottom)
        capture("reply-centered")
        compose.onNodeWithTag("demo-menu").performClick()
        compose.onNodeWithText("Custom UI").performClick()
        capture("reply-custom-centered")
        compose.onNodeWithContentDescription("Latest", substring = true).performClick()
        capture("reply-latest")
        compose.onNodeWithTag("timeline").performScrollToIndex(0)
        compose.onNodeWithText("Start of conversation").assertIsDisplayed()
        compose.onNodeWithTag("timeline").performScrollToIndex(3)
        compose.onNodeWithText("Start of conversation").assertIsNotDisplayed()
        capture("reply-floating-latest")
    }
}
