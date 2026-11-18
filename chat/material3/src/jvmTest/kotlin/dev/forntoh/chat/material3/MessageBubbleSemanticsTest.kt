package dev.forntoh.chat.material3

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import dev.forntoh.chat.core.Delivery
import dev.forntoh.chat.core.Message
import dev.forntoh.chat.core.Participant
import dev.forntoh.chat.ui.ConversationSlots
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class MessageBubbleSemanticsTest {
    @Test
    fun hostDescriptionReplacesMessageContentAndPreservesSeparateActions() = runComposeUiTest {
        var bubbleClicks = 0
        var retries = 0
        val message = Message("message", Participant("sender", "Sender"), 0, "12:34", "Body", Delivery.Failed)
        val slots = ConversationSlots(
            messageDescription = { "Host message description" },
            messageMetadata = { MessageMetadata(header = { BasicText("Sender") }) { BasicText("12:34") } },
        )
        setContent {
            MaterialTheme {
                Column {
                    MessageBubble(
                        message,
                        true,
                        { retries++ },
                        slots,
                        Modifier.clickable { bubbleClicks++ },
                    )
                }
            }
        }

        onAllNodesWithContentDescription("Host message description").assertCountEquals(1)
        listOf("Sender", "Body", "12:34", "Failed").forEach {
            onNodeWithText(it).assertDoesNotExist()
        }
        onAllNodes(hasClickAction()).assertCountEquals(2)
        onNodeWithContentDescription("Host message description").assertHasClickAction().performClick()
        onNodeWithText("Retry").performClick()
        runOnIdle {
            assertEquals(1, bubbleClicks)
            assertEquals(1, retries)
        }
    }
}
