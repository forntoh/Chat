package dev.chat.sample.android

import android.graphics.Bitmap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.then
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
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
    }

    @Test fun copyActionUsesAndroidClipboard() {
        compose.onNodeWithText("Actions").performClick()
        compose.onNodeWithText("Copy").performClick()
        compose.runOnIdle {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            org.junit.Assert.assertEquals(
                "Welcome! Send a message, then choose its delivery outcome.",
                clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString(),
            )
        }
        capture("android-message-actions-copy")
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        File(instrumentation.targetContext.filesDir, "$name.png").outputStream().use {
            check(instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }
}
