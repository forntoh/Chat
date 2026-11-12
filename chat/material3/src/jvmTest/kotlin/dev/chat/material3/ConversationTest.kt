package dev.chat.material3

import dev.chat.core.*

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class ConversationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun multilineSendIsExplicitAndEmittedOnceUntilHostResolvesDraft() {
        val snapshot = mutableStateOf(ConversationState("room", "me", draft = Draft(" \n\t", 1)))
        val events = mutableListOf<ConversationEvent>()
        compose.setContent { MaterialTheme { Conversation(snapshot.value, events::add) } }
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
        compose.setContent { MaterialTheme { Conversation(snapshot.value, events::add) } }
        compose.onNodeWithText("Avery · 09:41").assertExists()
        compose.onNodeWithText("You · 09:41").assertExists()
        compose.onNodeWithText("Sending").assertExists()
        compose.runOnIdle { snapshot.value = snapshot.value.copy(messages = listOf(
            snapshot.value.messages.first(), outgoing.copy(delivery = Delivery.Failed),
        )) }
        compose.onNodeWithText("Failed").assertExists()
        compose.onNodeWithText("Retry").performClick()
        compose.runOnIdle {
            assertEquals<List<ConversationEvent>>(listOf(ConversationEvent.RetryMessage("room", "m2")), events)
            snapshot.value = snapshot.value.copy(messages = listOf(
                snapshot.value.messages.first(), outgoing.copy(delivery = Delivery.Sent),
            ))
        }
        compose.onAllNodesWithText("Hello\n🌍").assertCountEquals(1)
        compose.onNodeWithText("Retry").assertDoesNotExist()
        compose.onAllNodesWithText("Sent").assertCountEquals(2)
    }

    @Test fun switchingConversationsDoesNotReuseAnotherConversationsSubmissionLatch() {
        val snapshot = mutableStateOf(ConversationState("a", "me", draft = Draft("Room A", 1)))
        val events = mutableListOf<ConversationEvent>()
        compose.setContent { MaterialTheme { Conversation(snapshot.value, events::add) } }
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
