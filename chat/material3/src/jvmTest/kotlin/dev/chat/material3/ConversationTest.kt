package dev.chat.material3

import dev.chat.core.*
import dev.chat.ui.*

import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class ConversationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun hostSlotsOwnControlWordingAndOrderedMessageSemantics() {
        val row = Message("m", Participant("me", "Sam"), 1, "10:15", "Hello 🌍", Delivery.Failed)
        val snapshot = mutableStateOf(ConversationState("room", "me", messages = listOf(row)))
        val history = mutableStateOf(dev.chat.ui.HistoryState(dev.chat.ui.HistoryStatus.Error))
        fun delivery(value: Delivery) = when (value) {
            Delivery.Sending -> "In transit"
            Delivery.Sent -> "Delivered"
            Delivery.Failed -> "Not delivered"
        }
        val slots = ConversationSlots(
            messageDescription = { "${it.sender.displayName}, ${it.text}, ${it.displayTime}, ${delivery(it.delivery)}" },
            messageDelivery = { Text(delivery(it)) },
            retry = { control -> TextButton(onClick = control.onRetry,
                modifier = Modifier.semantics { contentDescription = "Retry delivery" }) { Text("Again") } },
            composerField = { content -> OutlinedTextField(content.draft.text, content.onTextChange,
                enabled = !content.pending, label = { Text("Write here") },
                modifier = Modifier.semantics { contentDescription = "Draft input" }) },
            composerSend = { content -> Button(onClick = content.onSend, enabled = content.canSend,
                modifier = Modifier.semantics { contentDescription = "Post draft" }) { Text("Post") } },
            historyFeedback = { content ->
                when (content.history.state.status) {
                    dev.chat.ui.HistoryStatus.Error -> TextButton(onClick = content.history.onLoadEarlier,
                        enabled = content.history.canLoadEarlier,
                        modifier = Modifier.semantics { contentDescription = "Retry earlier messages" }) { Text("Earlier messages unavailable") }
                    dev.chat.ui.HistoryStatus.Loading -> Text("Fetching")
                    dev.chat.ui.HistoryStatus.Exhausted -> Text(if (content.empty) "Begin here" else "Beginning")
                    dev.chat.ui.HistoryStatus.Idle -> TextButton(onClick = content.history.onLoadEarlier,
                        enabled = content.history.canLoadEarlier) { Text("Older") }
                }
            },
        )
        val events = mutableListOf<ConversationEvent>()
        compose.setContent { JetchatTheme {
            Conversation(snapshot.value, events::add, history = history.value, slots = slots)
        } }
        compose.onNodeWithContentDescription("Draft input").assertExists()
        compose.onNodeWithText("Write here").assertExists()
        compose.onNodeWithContentDescription("Post draft").assertIsNotEnabled()
        compose.onNodeWithText("Post").assertExists()
        compose.onNodeWithContentDescription("Sam, Hello 🌍, 10:15, Not delivered").assertExists()
        compose.onNodeWithText("Hello 🌍").assertDoesNotExist() // grouped once for accessibility
        compose.onNodeWithContentDescription("Retry delivery").performClick()
        compose.onNodeWithText("Again").assertExists()
        compose.runOnIdle { snapshot.value = snapshot.value.copy(messages = listOf(row.copy(delivery = Delivery.Sending))) }
        compose.onNodeWithContentDescription("Sam, Hello 🌍, 10:15, In transit").assertExists()
        compose.runOnIdle { snapshot.value = snapshot.value.copy(messages = listOf(row.copy(delivery = Delivery.Sent))) }
        compose.onNodeWithContentDescription("Sam, Hello 🌍, 10:15, Delivered").assertExists()
        compose.runOnIdle { assertEquals<List<ConversationEvent>>(listOf(ConversationEvent.RetryMessage("room", "m")), events) }
        compose.onNodeWithContentDescription("Retry earlier messages").assertExists()
        compose.onNodeWithText("Earlier messages unavailable").assertExists()
        compose.runOnIdle { history.value = dev.chat.ui.HistoryState(dev.chat.ui.HistoryStatus.Loading, 1) }
        compose.onNodeWithText("Fetching").assertExists()
        compose.runOnIdle { history.value = dev.chat.ui.HistoryState(dev.chat.ui.HistoryStatus.Exhausted, 2) }
        compose.onNodeWithText("Beginning").assertExists()
        compose.runOnIdle { snapshot.value = snapshot.value.copy(messages = emptyList()) }
        compose.onNodeWithText("Begin here").assertExists()
        compose.runOnIdle { history.value = dev.chat.ui.HistoryState(dev.chat.ui.HistoryStatus.Idle, 3) }
        compose.onNodeWithText("Older").assertExists()
    }

    @Test fun latestAndArrivalActionsUseHostLabelsAndKeepReaderControlExplicit() {
        val rows = (0..39).map { Message("m$it", Participant("other", "Avery"), it.toLong(), "09:41", "Message $it", Delivery.Sent) }
        val snapshot = mutableStateOf(ConversationState("room", "me", messages = rows))
        compose.setContent { JetchatTheme {
            Conversation(snapshot.value, {}, slots = ConversationSlots(latestLabel = {
                Text(if (it.hasNewMessages) "Arrivals" else "Return", modifier = Modifier.semantics {
                    contentDescription = if (it.hasNewMessages) "Read arrivals" else "Return to newest"
                })
            }))
        } }
        compose.onNodeWithTag("timeline").performScrollToIndex(0)
        compose.onNodeWithText("Return").assertIsDisplayed()
        compose.onNodeWithContentDescription("Return to newest").assertExists()
        compose.runOnIdle { snapshot.value = snapshot.value.copy(messages = rows + rows.last().copy(id = "new", text = "Arrival")) }
        compose.onNodeWithText("Arrivals").assertIsDisplayed()
        compose.onNodeWithContentDescription("Read arrivals").performClick()
        compose.onNodeWithText("Arrivals").assertDoesNotExist()
        compose.onNodeWithTag("message-new").assertIsDisplayed()
    }

    @Test fun multilineSendIsExplicitAndEmittedOnceUntilHostResolvesDraft() {
        val snapshot = mutableStateOf(ConversationState("room", "me", draft = Draft(" \n\t", 1)))
        val events = mutableListOf<ConversationEvent>()
        compose.setContent { JetchatTheme { Conversation(snapshot.value, events::add) } }
        compose.onNodeWithText("Send").assertIsNotEnabled()
        compose.runOnIdle { snapshot.value = snapshot.value.copy(draft = Draft("Hello\n🌍", 2)) }
        compose.onNodeWithText("Send").performClick()
        compose.onNodeWithText("Send").assertIsNotEnabled().performClick()
        compose.runOnIdle {
            assertEquals<List<ConversationEvent>>(listOf(ConversationEvent.SendDraft("room", 2, "Hello\n🌍")), events)
            // Local refusal retains content but resolves this submission explicitly.
            snapshot.value = snapshot.value.copy(draft = Draft("Hello\n🌍", 3))
        }
        compose.onNodeWithText("Send").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(2, events.size) }
    }

    @Test fun hostDeliveryUpdatesReplaceTheSameRowAndRetryCarriesItsIdentity() {
        val me = Participant("me", "You")
        val other = Participant("other", "Avery")
        val outgoing = Message("m2", me, 1_000, "09:41", "Hello\n🌍", Delivery.Sending)
        val snapshot = mutableStateOf(ConversationState("room", "me", messages = listOf(
            Message("m1", other, 1_000, "09:41", "Welcome", Delivery.Sent), outgoing,
        )))
        val events = mutableListOf<ConversationEvent>()
        compose.setContent { JetchatTheme { Conversation(snapshot.value, events::add) } }
        compose.onNodeWithText("Avery · 09:41", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("You · 09:41", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Sending", useUnmergedTree = true).assertExists()
        compose.runOnIdle { snapshot.value = snapshot.value.copy(messages = listOf(
            snapshot.value.messages.first(), outgoing.copy(delivery = Delivery.Failed),
        )) }
        compose.onNodeWithText("Failed", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Retry").performClick()
        compose.runOnIdle {
            assertEquals<List<ConversationEvent>>(listOf(ConversationEvent.RetryMessage("room", "m2")), events)
            snapshot.value = snapshot.value.copy(messages = listOf(
                snapshot.value.messages.first(), outgoing.copy(delivery = Delivery.Sent),
            ))
        }
        compose.onAllNodesWithText("Hello\n🌍", useUnmergedTree = true).assertCountEquals(1)
        compose.onNodeWithText("Retry").assertDoesNotExist()
        compose.onAllNodesWithText("Sent", useUnmergedTree = true).assertCountEquals(2)
    }

    @Test fun switchingConversationsDoesNotReuseAnotherConversationsSubmissionLatch() {
        val snapshot = mutableStateOf(ConversationState("a", "me", draft = Draft("Room A", 1)))
        val events = mutableListOf<ConversationEvent>()
        compose.setContent { JetchatTheme { Conversation(snapshot.value, events::add) } }
        compose.onNodeWithText("Send").performClick()
        compose.runOnIdle { snapshot.value = ConversationState("b", "me", draft = Draft("Room B", 1, submitting = true)) }
        compose.onNodeWithTag("composer").assertTextContains("Room B")
        compose.onNodeWithText("Send").assertIsNotEnabled()
        compose.runOnIdle { snapshot.value = snapshot.value.copy(draft = Draft("Room B", 2)) }
        compose.onNodeWithText("Send").performClick()
        compose.runOnIdle {
            assertEquals<List<ConversationEvent>>(listOf(
                ConversationEvent.SendDraft("a", 1, "Room A"),
                ConversationEvent.SendDraft("b", 2, "Room B"),
            ), events)
        }
    }
}
