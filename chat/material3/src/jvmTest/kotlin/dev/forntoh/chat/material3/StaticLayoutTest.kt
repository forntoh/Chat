package dev.forntoh.chat.material3

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import dev.forntoh.chat.core.*
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertTrue

class StaticLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun timeIsBelowBodyAndDeliverySharesItsFooter() {
        val row = Message("one", Participant("avery", "Avery Chen"), 0, "09:41", "Hi", Delivery.Sending)
        compose.setContent {
            ChatTheme {
                Box(Modifier.width(360.dp)) {
                    MessageBubble(row, false, null, chatConversationSlots().copy(
                        messageBody = { Text(it.text, Modifier.testTag("body")) },
                        messageMetadata = { Text(it.displayTime, Modifier.testTag("time")) },
                        messageDelivery = { Text(it.name, Modifier.testTag("delivery")) },
                    ))
                }
            }
        }
        val body = compose.onNodeWithTag("body", true).fetchSemanticsNode().boundsInRoot
        val time = compose.onNodeWithTag("time", true).fetchSemanticsNode().boundsInRoot
        val delivery = compose.onNodeWithTag("delivery", true).fetchSemanticsNode().boundsInRoot
        assertTrue(time.top >= body.bottom, "Time must stay below the body inside the bubble")
        assertTrue(kotlin.math.abs(time.bottom - delivery.bottom) <= 1f, "Time and delivery must align")
    }
    @Test fun absentAvatarAndDeliveryChangesKeepTextGeometryStableAndLongContentFits() {
        val row = mutableStateOf(Message("one", Participant("avery", "Avery Chen"), 0, "09:41", "Hi", Delivery.Sending))
        val avatar = mutableStateOf(true)
        compose.setContent {
            ChatTheme {
                Box(Modifier.width(320.dp)) {
                    MessageBubble(row.value, false, null, chatConversationSlots().copy(
                        messageAvatar = { if (avatar.value) Box(Modifier.size(30.dp)) },
                        messageBody = { Text(it.text, Modifier.testTag("body")) },
                        messageMetadata = { Text(it.displayTime, Modifier.testTag("time")) },
                    ))
                }
            }
        }
        fun body() = compose.onNodeWithTag("body", true).fetchSemanticsNode().boundsInRoot
        val initial = body()
        compose.runOnIdle { avatar.value = false; row.value = row.value.copy(delivery = Delivery.Failed) }
        val changed = body()
        assertTrue(initial.left == changed.left && initial.top == changed.top, "Avatar/status cannot shift the text origin")
        compose.runOnIdle { row.value = row.value.copy(text = "unbroken".repeat(40)) }
        val long = body()
        val time = compose.onNodeWithTag("time", true).fetchSemanticsNode().boundsInRoot
        assertTrue(long.right < 320f && long.height > initial.height, "Long text must wrap with an opposite gutter")
        assertTrue(time.top >= long.bottom, "Long text cannot overlap metadata")
    }

    @Test fun blankContentAndSenderHaveReadableDefaultFallbacks() {
        val row = Message("missing", Participant("other", ""), 0, "09:41", "", Delivery.Sent)
        compose.setContent { ChatTheme { MessageBubble(row, false, null) } }
        compose.onNodeWithContentDescription("Unknown sender, Message unavailable, 09:41, Sent").assertExists()
        compose.onNodeWithText("Unknown sender", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Message unavailable", useUnmergedTree = true).assertExists()
    }

    @Test fun metadataSlotOwnsSenderAndCanReplaceOrOmitVisibleMetadata() {
        val showMetadata = mutableStateOf(true)
        val row = Message("one", Participant("avery", "Avery Chen"), 0, "09:41", "Hi", Delivery.Sent)
        compose.setContent {
            ChatTheme {
                MessageBubble(row, false, null, chatConversationSlots().copy(
                    messageMetadata = {
                        if (showMetadata.value) MessageMetadata(header = { Text("Host sender") }) { Text("Host time") }
                    },
                ))
            }
        }
        compose.onNodeWithText("Host sender", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Host time", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Avery Chen", useUnmergedTree = true).assertDoesNotExist()
        compose.runOnIdle { showMetadata.value = false }
        compose.onNodeWithText("Host sender", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Host time", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Hi", useUnmergedTree = true).assertExists()
    }

    @Test fun senderStaysOutsideWhileTimeIsInsideAndBubblesWrapWithinGutters() {
        val text = mutableStateOf("OK")
        val senderName = mutableStateOf("Avery")
        val outgoing = mutableStateOf(false)
        val viewport = mutableStateOf(360.dp)
        val bubbleColor = Color.Green
        compose.setContent {
            ChatTheme(colorScheme = chatColorScheme().copy(primary = bubbleColor, surfaceContainerHighest = bubbleColor)) {
                Box(Modifier.width(viewport.value).padding(12.dp)) {
                    MessageBubble(Message("one", Participant(if (outgoing.value) "me" else "other", senderName.value), 0, "09:41", text.value, Delivery.Sent), outgoing.value, null)
                }
            }
        }
        data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int)
        fun bubbleBounds(): Bounds {
            val pixels = compose.onRoot().captureToImage().toPixelMap()
            var left = pixels.width; var top = pixels.height; var right = -1; var bottom = -1
            for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                if (pixels[x, y].toArgb() == bubbleColor.toArgb()) {
                    left = minOf(left, x); top = minOf(top, y); right = maxOf(right, x); bottom = maxOf(bottom, y)
                }
            }
            assertTrue(right >= left, "A visible bubble must be present")
            return Bounds(left, top, right + 1, bottom + 1)
        }
        val short = bubbleBounds()
        assertTrue(short.right - short.left < 180, "A short bubble must wrap content rather than expand to the row width")
        val sender = compose.onNodeWithText("Avery", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val time = compose.onNodeWithText("09:41", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val body = compose.onNodeWithText("OK", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(sender.bottom <= short.top, "The sender must be above and outside the colored bubble")
        assertTrue(time.top >= body.bottom && time.bottom <= short.bottom, "Time must be inside the bubble below its text")
        compose.runOnIdle { senderName.value = "Participant with a very long display name" }
        val longName = bubbleBounds()
        assertTrue(longName.right - longName.left < 180, "A long external sender name cannot stretch a short bubble")
        compose.runOnIdle { outgoing.value = true }
        val outgoingShort = bubbleBounds()
        assertTrue(outgoingShort.right - outgoingShort.left < 180, "A short outgoing bubble must wrap its content")
        compose.runOnIdle { text.value = "unbroken".repeat(40) }
        val mobile = bubbleBounds()
        assertTrue(mobile.left >= 64 && mobile.right <= 348, "A long mobile bubble must leave both screen gutters")
        compose.runOnIdle { viewport.value = 1000.dp }
        val desktop = bubbleBounds()
        assertTrue(desktop.right - desktop.left <= 560, "Desktop bubbles must respect the width cap")
    }

}
