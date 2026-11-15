package dev.chat.sample.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test

class BotConversationJourney {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun overflowPausesAndResumesWithoutLosingDraftAcrossThemeChange() {
        compose.onNodeWithTag("composer").performTextInput("Meet by the lake")
        compose.onNodeWithContentDescription("Chat options").performClick()
        compose.onNodeWithText("Pause bot").performClick()
        compose.onNodeWithContentDescription("Chat options").performClick()
        compose.onNodeWithText("Forest theme").performClick()
        compose.onNodeWithTag("composer").assertTextContains("Meet by the lake")
        compose.onNodeWithContentDescription("Chat options").performClick()
        compose.onNodeWithText("Resume bot").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Send").performClick()
    }
}
