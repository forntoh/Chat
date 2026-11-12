package dev.chat.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import dev.chat.core.*
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class ConversationSlotTest {
    @get:Rule val compose = createComposeRule()

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
