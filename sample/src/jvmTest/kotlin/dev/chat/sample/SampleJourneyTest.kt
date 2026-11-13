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
import com.forntoh.chat.ui.*
import com.forntoh.chat.core.ConversationEvent

class SampleJourneyTest {
    private fun demo(label: String) {
        compose.onNodeWithTag("demo-menu").performClick()
        compose.onNodeWithText(label).performClick()
    }

    @get:Rule val compose = createComposeRule()

    @Test fun polishedConversationDemonstratesDefaultGroupsTypingSendingAndContextMenu() {
        val host = FakeHost().apply { useChatFixture() }
        val width = androidx.compose.runtime.mutableStateOf(360.dp)
        compose.setContent { androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier
            .width(width.value).height(720.dp)) { SampleApp(host) } }
        capture("pat254-01-default-groups")
        compose.onNodeWithText("History fixture").assertDoesNotExist()
        compose.onNodeWithText("Actions").assertDoesNotExist()
        compose.onNodeWithTag("composer").performTextInput("See you soon!\nI’ll bring the sketches.")
        capture("pat254-02-typing")
        compose.onNodeWithContentDescription("Send").performClick()
        compose.runOnIdle {
            assertEquals("See you soon!\nI’ll bring the sketches.", host.state.messages.last().text)
            assertEquals(com.forntoh.chat.core.Delivery.Sent, host.state.messages.last().delivery)
            assertEquals("", host.state.draft.text)
        }
        capture("pat254-03-sent")
        compose.runOnIdle { width.value = 320.dp }
        capture("pat254-04-narrow")
        compose.onNodeWithTag("timeline").performScrollToIndex(host.state.messages.lastIndex)
        compose.onNodeWithTag("message-${host.state.messages.last().id}").performClick()
        compose.onNodeWithText("Reply").performClick()
        capture("pat254-05-quoted-draft")
        demo("Manual service")
        compose.onNodeWithTag("composer").performTextInput("Keep this draft")
        compose.onNodeWithContentDescription("Send").performClick()
        compose.runOnIdle { assertEquals("Keep this draft", host.pendingDraft?.text) }
        compose.onNodeWithContentDescription("Send").assertIsNotEnabled()
    }

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
                .width(360.dp).height(720.dp)) { SampleApp(host, manualService = true) }
        }
        capture("01-jetchat")
        compose.onNodeWithTag("composer").performTextInput(draft)
        compose.onNodeWithContentDescription("Send").assertIsDisplayed().performClick()
        val submission = host.pendingDraft
        demo("Host theme")
        demo("Custom UI")
        compose.onNodeWithText("Custom composer · host draft").assertExists()
        compose.onNodeWithTag("custom-incoming", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("composer").assertTextContains(draft)
        compose.onNodeWithContentDescription("Send").assertIsDisplayed().assertIsNotEnabled().performClick()
        compose.runOnIdle {
            assertEquals(submission, host.pendingDraft)
            assertEquals(1, host.state.messages.size)
        }
        capture("02-host-theme-pending")
        demo("Accept")
        compose.onNodeWithTag("message-studio-1").assertExists()
        compose.onNodeWithTag("custom-incoming", useUnmergedTree = true).assertExists()
        demo("Fail delivery")
        compose.onNodeWithText("Retry").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(2, host.state.messages.size) }
        capture("03-mixed-content")
        compose.onNodeWithTag("composer").performTextInput("Retained draft")
        demo("Material3 UI")
        demo("Default chat theme")
        compose.onNodeWithTag("composer").assertTextContains("Retained draft")
        compose.onNodeWithContentDescription("Send").assertIsEnabled()
    }

    @Test fun historyHostMergesOverlapRejectsLateResultsAndRetainsRoomDrafts() {
        val host = FakeHost()
        compose.setContent { SampleApp(host, manualService = true) }
        demo("History fixture")
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
        compose.setContent { SampleApp(host, manualService = true) }
        compose.onNodeWithTag("composer").performTextInput("Hello\n🌍")
        compose.onNodeWithContentDescription("Send").performClick()
        compose.onNodeWithContentDescription("Send").assertIsNotEnabled()
        demo("Accept")
        compose.onNodeWithTag("composer").assertTextEquals("Type a message…", "")
        demo("Fail delivery")
        compose.onNodeWithText("Failed", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Retry").performClick()
        compose.onNodeWithText("Sending", useUnmergedTree = true).assertExists()
        demo("Deliver")
        compose.onAllNodesWithText("Hello\n🌍", useUnmergedTree = true).assertCountEquals(1)
        compose.runOnIdle {
            assertEquals("studio-1", host.state.messages.last().id)
            assertEquals(2, host.state.messages.size)
        }
    }

    @Test fun refusalAndNavigationPreserveTheHostDraft() {
        compose.setContent { SampleApp(manualService = true) }
        compose.onNodeWithTag("composer").performTextInput("Keep\nthis draft")
        compose.onNodeWithContentDescription("Send").performClick()
        demo("Keep draft")
        compose.onNodeWithContentDescription("Send").assertIsEnabled()
        compose.onNodeWithContentDescription("Leave").performClick()
        compose.onNodeWithTag("composer").assertDoesNotExist()
        compose.onNodeWithContentDescription("Open chat").performClick()
        compose.onNodeWithTag("composer").assertTextContains("Keep\nthis draft")
        compose.onAllNodesWithText("Keep\nthis draft").assertCountEquals(1)
    }
}
