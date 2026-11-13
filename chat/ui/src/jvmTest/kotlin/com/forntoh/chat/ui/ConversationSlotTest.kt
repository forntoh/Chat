package com.forntoh.chat.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.forntoh.chat.core.*
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class ConversationSlotTest {
    @get:Rule val compose = createComposeRule()

    @Test fun groupingFollowsAdjacentSenderIdentityAcrossPrependAndConversationChanges() {
        val sender = Participant("avery", "Avery")
        val a = Message("a", sender, 1, "Yesterday", "A", Delivery.Sent)
        val b = a.copy(id = "b", text = "B", displayTime = "Today")
        val c = a.copy(id = "c", sender = Participant("sam", "Avery"), text = "C")
        val snapshot = mutableStateOf(ConversationState("room", "me", messages = listOf(a, b, c)))
        compose.setContent {
            Conversation(snapshot.value, {}, slots = ConversationSlots(message = { _, _, _ -> BasicText("bundle") }),
                messageContent = { row, _, _ ->
                    val group = LocalMessageGroupContext.current
                    BasicText("${row.id}: ${group.isFirst}/${group.isLast}")
                }, composerContent = {})
        }
        compose.onNodeWithText("a: true/false").assertExists()
        compose.onNodeWithText("b: false/true").assertExists()
        compose.onNodeWithText("c: true/true").assertExists()
        compose.onNodeWithText("bundle").assertDoesNotExist()
        compose.runOnIdle { snapshot.value = snapshot.value.copy(messages = listOf(a.copy(id = "older"), a, b, c)) }
        compose.onNodeWithText("a: false/false").assertExists()
        compose.runOnIdle { snapshot.value = ConversationState("other", "me", messages = listOf(a)) }
        compose.onNodeWithText("a: true/true").assertExists()
    }

    @Test fun overlappingMessageIdsRetainSlotStateOnlyWithinTheirConversation() {
        val message = Message("shared", Participant("me", "You"), 1_000, "09:41", "Room A", Delivery.Sent)
        val snapshot = mutableStateOf(ConversationState("a", "me", messages = listOf(message)))
        compose.setContent {
            Conversation(
                state = snapshot.value,
                onEvent = {},
                messageContent = { row, _, _ ->
                    val originalText = remember { row.text }
                    BasicText("Remembered: $originalText")
                },
                composerContent = {},
            )
        }
        compose.onNodeWithText("Remembered: Room A").assertExists()
        compose.runOnIdle {
            snapshot.value = snapshot.value.copy(messages = listOf(message.copy(text = "Edited A")))
        }
        compose.onNodeWithText("Remembered: Room A").assertExists()
        compose.runOnIdle {
            snapshot.value = snapshot.value.copy(conversationId = "b", messages = listOf(message.copy(text = "Room B")))
        }
        compose.onNodeWithText("Remembered: Room B").assertExists()
        compose.onNodeWithText("Remembered: Room A").assertDoesNotExist()
    }

    @Test fun replacingComposerPresentationPreservesDraftAndPendingSubmission() {
        val snapshot = mutableStateOf(ConversationState("room", "me", draft = Draft("Hello\n🌍", 2)))
        val custom = mutableStateOf(false)
        val events = mutableListOf<ConversationEvent>()
        compose.setContent {
            Conversation(
                state = snapshot.value,
                onEvent = events::add,
                messageContent = { _, _, _ -> },
                composerContent = { content ->
                    BasicText(content.draft.text)
                    BasicText(if (custom.value) "Custom send" else "Default send",
                        Modifier.clickable(enabled = content.canSend, onClick = content.onSend)
                            .semantics { if (!content.canSend) disabled() })
                },
            )
        }
        compose.onNodeWithText("Default send").performClick()
        compose.runOnIdle { custom.value = true }
        compose.onNodeWithText("Hello\n🌍").assertExists()
        compose.onNodeWithText("Custom send").assertIsNotEnabled().performClick()
        compose.runOnIdle {
            assertEquals<List<ConversationEvent>>(listOf(ConversationEvent.SendDraft("room", 2, "Hello\n🌍")), events)
            snapshot.value = snapshot.value.copy(draft = Draft("Hello\n🌍", 3))
        }
        compose.onNodeWithText("Custom send").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(ConversationEvent.SendDraft("room", 3, "Hello\n🌍"), events.last()) }
    }

    @Test fun sendBlocksEditsAndRepeatedSendInTheSameUserActivation() {
        val events = mutableListOf<ConversationEvent>()
        lateinit var composer: ComposerContent
        compose.setContent {
            Conversation(
                state = ConversationState("room", "me", draft = Draft("Hello\n🌍", 2)),
                onEvent = events::add,
                messageContent = { _, _, _ -> Box {} },
                composerContent = { composer = it },
            )
        }
        compose.runOnIdle {
            composer.onTextChange("Before send")
            composer.onSend()
            composer.onTextChange("After send")
            composer.onSend()
            assertEquals<List<ConversationEvent>>(listOf(
                ConversationEvent.DraftChanged("room", "Before send"),
                ConversationEvent.SendDraft("room", 2, "Hello\n🌍"),
            ), events)
        }
    }
}
