package dev.chat.sample.android

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import java.io.File
import android.os.Build
import android.view.WindowInsets
import androidx.test.espresso.Espresso
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertTrue

class ConversationJourney {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun keyboardOpeningAndClosingAdjustsComposerViewportWithoutLosingDraft() {
        assumeTrue(Build.VERSION.SDK_INT >= 30)
        val composer = compose.onNodeWithTag("composer")
        val closedBottom = composer.fetchSemanticsNode().boundsInRoot.bottom
        composer.performClick().performTextInput("Retained\nkeyboard draft")
        compose.waitUntil(5_000) {
            compose.activity.window.decorView.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) == true
        }
        compose.waitForIdle()
        val openBottom = composer.fetchSemanticsNode().boundsInRoot.bottom
        assertTrue("Composer moves above the keyboard", openBottom < closedBottom)
        compose.onNodeWithText("Send").assertIsDisplayed()
        capture("android-keyboard-open")
        Espresso.pressBack()
        compose.waitUntil(5_000) {
            compose.activity.window.decorView.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) == false
        }
        compose.waitForIdle()
        assertTrue("Composer returns to the expanded viewport", composer.fetchSemanticsNode().boundsInRoot.bottom > openBottom)
        composer.assertTextContains("Retained\nkeyboard draft")
        compose.onNodeWithText("Send").assertIsDisplayed()
        capture("android-keyboard-closed")
    }

    @Test fun sendFailureRetryAndNavigationWithKeyboard() {
        compose.onNodeWithTag("composer").performClick().performTextInput("Hello\nfrom Android")
        compose.onNodeWithText("Send").assertIsDisplayed().performClick()
        compose.onNodeWithText("Send").assertIsNotEnabled()
        compose.onNodeWithText("Accept").performClick()
        compose.onNodeWithText("Fail delivery").performClick()
        compose.onNodeWithText("Failed").assertExists()
        capture("android-failed")
        compose.onNodeWithText("Retry").performClick()
        compose.onNodeWithText("Deliver").performClick()
        compose.onAllNodesWithText("Hello\nfrom Android").assertCountEquals(1)
        compose.onNodeWithText("Retry").assertDoesNotExist()
        compose.onNodeWithTag("composer").performTextInput("Retained draft")
        compose.onNodeWithText("Leave").performClick()
        compose.onNodeWithText("Open chat").performClick()
        compose.onNodeWithTag("composer").assertTextContains("Retained draft")
        compose.onNodeWithText("Send").assertIsDisplayed()
        capture("android-retained-draft")

    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        File(instrumentation.targetContext.filesDir, "$name.png").outputStream().use {
            check(instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }
}
