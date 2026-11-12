package dev.chat.sample.android

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import java.io.File

class ConversationJourney {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

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
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            compose.activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        compose.waitForIdle()
        compose.onNodeWithTag("composer").assertTextContains("Retained draft")
        compose.onNodeWithText("Send").assertIsDisplayed()
        capture("android-landscape")
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        File(instrumentation.targetContext.filesDir, "$name.png").outputStream().use {
            check(instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }
}
