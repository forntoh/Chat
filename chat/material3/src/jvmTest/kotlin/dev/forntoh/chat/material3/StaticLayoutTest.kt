package dev.forntoh.chat.material3

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
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

    @Test fun metadataIsBelowBodyAndDeliverySharesItsFooter() {
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
        assertTrue(time.top >= body.bottom, "Metadata must reserve a region below the text")
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

}
