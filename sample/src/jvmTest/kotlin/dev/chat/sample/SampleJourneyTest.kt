package dev.chat.sample

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class SampleJourneyTest {
    @get:Rule val compose = createComposeRule()

    @Test fun acceptedSendFailsAndRetriesWithoutDuplicatingTheTimelineRow() {
        val host = FakeHost()
        compose.setContent { SampleApp(host) }
        compose.onNodeWithTag("composer").performTextInput("Hello\n🌍")
        compose.onNodeWithText("Send").performClick()
        compose.onNodeWithText("Send").assertIsNotEnabled()
        compose.onNodeWithText("Accept").performClick()
        compose.onNodeWithTag("composer").assertTextEquals("Message", "")
        compose.onNodeWithText("Fail delivery").performClick()
        compose.onNodeWithText("Failed").assertExists()
        compose.onNodeWithText("Retry").performClick()
        compose.onNodeWithText("Sending").assertExists()
        compose.onNodeWithText("Deliver").performClick()
        compose.onAllNodesWithText("Hello\n🌍").assertCountEquals(1)
        compose.runOnIdle {
            assertEquals("studio-1", host.state.messages.last().id)
            assertEquals(2, host.state.messages.size)
        }
    }

    @Test fun refusalAndNavigationPreserveTheHostDraft() {
        compose.setContent { SampleApp() }
        compose.onNodeWithTag("composer").performTextInput("Keep\nthis draft")
        compose.onNodeWithText("Send").performClick()
        compose.onNodeWithText("Keep draft").performClick()
        compose.onNodeWithText("Send").assertIsEnabled()
        compose.onNodeWithText("Leave").performClick()
        compose.onNodeWithTag("composer").assertDoesNotExist()
        compose.onNodeWithText("Open chat").performClick()
        compose.onNodeWithTag("composer").assertTextContains("Keep\nthis draft")
        compose.onAllNodesWithText("Keep\nthis draft").assertCountEquals(1)
    }
}
