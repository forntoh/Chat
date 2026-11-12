package dev.chat.sample

import androidx.compose.ui.test.*
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.toArgb
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import dev.chat.ui.*
import dev.chat.core.ConversationEvent

class SampleJourneyTest {
    @get:Rule val compose = createComposeRule()

    private fun capture(name: String) {
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val image = BufferedImage(pixels.width, pixels.height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) image.setRGB(x, y, pixels[x, y].toArgb())
        val directory = File("build/pat243-evidence").apply { mkdirs() }
        ImageIO.write(image, "png", File(directory, "$name.png"))
    }

    @Test fun themeAndMixedRenderersPreserveLongDraftAndPendingSend() {
        val host = FakeHost()
        val draft = "Hello 🌍\n" + "unbroken".repeat(40)
        compose.setContent {
            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier
                .width(360.dp).height(720.dp)) { SampleApp(host) }
        }
        capture("01-jetchat")
        compose.onNodeWithTag("composer").performTextInput(draft)
        compose.onNodeWithText("Send").assertIsDisplayed().performClick()
        val submission = host.pendingDraft
        compose.onNodeWithText("Host theme").performClick()
        compose.onNodeWithText("Custom UI").performClick()
        compose.onNodeWithText("Custom composer · host draft").assertExists()
        compose.onNodeWithTag("custom-incoming").assertExists()
        compose.onNodeWithTag("composer").assertTextContains(draft)
        compose.onNodeWithText("Send").assertIsDisplayed().assertIsNotEnabled().performClick()
        compose.runOnIdle {
            assertEquals(submission, host.pendingDraft)
            assertEquals(1, host.state.messages.size)
        }
        capture("02-host-theme-pending")
        compose.onNodeWithText("Accept").performClick()
        compose.onNodeWithTag("message-studio-1").assertExists()
        compose.onNodeWithTag("custom-incoming").assertExists()
        compose.onNodeWithText("Fail delivery").performClick()
        compose.onNodeWithText("Retry").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(2, host.state.messages.size) }
        capture("03-mixed-content")
        compose.onNodeWithTag("composer").performTextInput("Retained draft")
        compose.onNodeWithText("Material3 UI").performClick()
        compose.onNodeWithText("Jetchat theme").performClick()
        compose.onNodeWithTag("composer").assertTextContains("Retained draft")
        compose.onNodeWithText("Send").assertIsEnabled()
    }

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
        compose.onNodeWithText("Failed", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Retry").performClick()
        compose.onNodeWithText("Sending", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Deliver").performClick()
        compose.onAllNodesWithText("Hello\n🌍", useUnmergedTree = true).assertCountEquals(1)
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
