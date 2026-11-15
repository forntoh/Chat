package dev.forntoh.chat.material3

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import dev.forntoh.chat.core.*
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StatusMotionTest {
    @get:Rule val compose = createComposeRule()
    @Test fun deliveryCrossfadesWithoutChangingOutgoingBubbleWidth() {
        val delivery = mutableStateOf(Delivery.Sending)
        compose.setContent {
            ChatTheme {
                Box(Modifier.width(360.dp)) {
                    MessageBubble(Message("a", Participant("me", "You"), 0, "09:41", "OK", delivery.value),
                        true, null, chatConversationSlots(), Modifier.testTag("bubble"))
                }
            }
        }
        val width = compose.onNodeWithTag("bubble").fetchSemanticsNode().boundsInRoot.width
        fun pixels(): List<Int> {
            val image = compose.onNodeWithTag("bubble").captureToImage().toPixelMap()
            return (0 until image.height).flatMap { y -> (0 until image.width).map { x -> image[x,y].toArgb() } }
        }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { delivery.value = Delivery.Failed }
        repeat(3) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
        val intermediate = pixels()
        compose.mainClock.advanceTimeBy(300)
        assertTrue(intermediate != pixels(), "Delivery status needs an intermediate opacity frame")
        assertEquals(width, compose.onNodeWithTag("bubble").fetchSemanticsNode().boundsInRoot.width, 1f)
        compose.runOnIdle { delivery.value = Delivery.Sent }
        compose.mainClock.advanceTimeBy(300)
        assertEquals(width, compose.onNodeWithTag("bubble").fetchSemanticsNode().boundsInRoot.width, 1f)
        compose.mainClock.autoAdvance = true
    }
}
