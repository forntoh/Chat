package dev.forntoh.chat.material3

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.height
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.width
import androidx.compose.ui.unit.dp
import dev.forntoh.chat.core.Delivery
import dev.forntoh.chat.core.Message
import dev.forntoh.chat.core.Participant
import dev.forntoh.chat.ui.ConversationSlots
import dev.forntoh.chat.ui.HistoryContent
import dev.forntoh.chat.ui.HistoryState
import dev.forntoh.chat.ui.HistoryStatus
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StatusMotionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun historyLoadingRetainsItsPresentationUntilExitCompletes() {
        val status = mutableStateOf(HistoryStatus.Loading)
        compose.setContent {
            Box(Modifier.size(200.dp, 60.dp).background(Color.White)) {
                HistoryStatusContent(
                    HistoryContent(HistoryState(status.value), false, {}),
                    false,
                    ConversationSlots(historyFeedback = {
                        if (it.history.state.status == HistoryStatus.Loading) {
                            Box(Modifier.size(80.dp, 32.dp).background(Color.Red))
                        }
                    }),
                )
            }
        }
        fun green() = compose.onAllNodes(isRoot()).onFirst().captureToImage().toPixelMap()[100, 16].green
        assertTrue(green() < 0.02f)
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { status.value = HistoryStatus.Exhausted }
        repeat(3) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        compose.mainClock.advanceTimeBy(48)
        assertTrue(green() > 0.02f && green() < 0.98f, "Loading pixels must survive through exit: ${green()}")
        compose.mainClock.advanceTimeBy(300)
        assertTrue(green() > 0.98f)
        compose.mainClock.autoAdvance = true
    }

    @Test fun deliveryCrossfadesWithoutChangingOutgoingBubbleWidth() {
        val delivery = mutableStateOf(Delivery.Sending)
        compose.setContent {
            ChatTheme {
                Box(Modifier.width(360.dp)) {
                    MessageBubble(
                        Message("a", Participant("me", "You"), 0, "09:41", "OK", delivery.value),
                        true,
                        null,
                        chatConversationSlots(),
                        Modifier.testTag("bubble"),
                    )
                }
            }
        }
        val width = compose.onNodeWithTag("bubble").fetchSemanticsNode().boundsInRoot.width
        fun pixels(): List<Int> {
            val image = compose.onNodeWithTag("bubble").captureToImage().toPixelMap()
            return (0 until image.height).flatMap { y -> (0 until image.width).map { x -> image[x, y].toArgb() } }
        }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { delivery.value = Delivery.Failed }
        repeat(3) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        val intermediate = pixels()
        compose.mainClock.advanceTimeBy(300)
        assertTrue(intermediate != pixels(), "Delivery status needs an intermediate opacity frame")
        assertEquals(width, compose.onNodeWithTag("bubble").fetchSemanticsNode().boundsInRoot.width, 1f)
        compose.runOnIdle { delivery.value = Delivery.Sent }
        repeat(3) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        val exiting = pixels()
        compose.onNodeWithText("Failed").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(300)
        assertTrue(exiting != pixels(), "Outgoing status must retain pixels through removal")
        assertEquals(width, compose.onNodeWithTag("bubble").fetchSemanticsNode().boundsInRoot.width, 1f)
        compose.mainClock.autoAdvance = true
    }
}
