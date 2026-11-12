package dev.chat.sample

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import dev.chat.ui.*
import dev.chat.core.ConversationEvent

class SampleJourneyTest {
    @get:Rule val compose = createComposeRule()

    @Test fun historyHostMergesOverlapRejectsLateResultsAndRetainsRoomDrafts() {
        val host = FakeHost()
        compose.setContent { SampleApp(host) }
        compose.onNodeWithText("History fixture").performClick()
        compose.runOnIdle {
            host.onEvent(ConversationEvent.DraftChanged(host.state.conversationId, "Keep studio"))
            host.loadEarlier(HistoryRequest(host.state.conversationId, host.history.revision))
        }
        compose.onNodeWithText("Loading messages…").assertExists()
        val old = host.pendingHistory!!
        compose.runOnIdle { host.failHistory() }
        compose.onNodeWithText("Couldn't load messages · Retry").performClick()
        compose.runOnIdle { host.completeHistory() }
        compose.runOnIdle {
            assertEquals(100, host.state.messages.size)
            assertEquals(100, host.state.messages.map { it.id }.distinct().size)
            assertEquals("Message 950 · refreshed", host.state.messages.first { it.id == "history-950" }.text)
            host.loadEarlier(HistoryRequest(host.state.conversationId, host.history.revision))
        }
        val cancelled = host.pendingHistory!!
        compose.runOnIdle {
            host.switchRoom()
            host.completeHistory(cancelled)
            assertEquals("other", host.state.conversationId)
            assertEquals("", host.state.draft.text)
            host.switchRoom()
            assertEquals("Keep studio", host.state.draft.text)
            host.completeHistory(cancelled)
            host.completeHistory(old)
            assertEquals(100, host.state.messages.size)
        }
    }

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
