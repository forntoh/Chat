package dev.chat.consumer

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import dev.chat.actions.*
import dev.chat.core.*
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions

class ActionsTest {
    @get:Rule val compose = createComposeRule()
    private val target = Message("m1", Participant("me", "You"), 1, "09:42", "Original", Delivery.Sent)

    @Test fun quotedSendAndEditDeleteRemainHostIntentsWithoutMaterial3() {
        val conversation = mutableStateOf(ConversationState("room", "me", Draft("Reply draft", 7), listOf(target)))
        val state = mutableStateOf(ActionState(quoteTargetId = "m1", capabilities = mapOf("m1" to MessageAction.entries.toSet())))
        val events = mutableListOf<ActionRequest>()
        compose.setContent { ActionsConversation(conversation.value, state.value, {}, events::add) }
        compose.onNodeWithText("Send").performClick()
        compose.runOnIdle {
            assertEquals(listOf(ActionRequest("room", 0, ActionCommand.SendQuote, "m1", "Reply draft", 7)), events)
            state.value = state.value.copy(revision = 1, quoteTargetId = null, editing = EditingMessage(target, Draft("Retained")))
            conversation.value = conversation.value.copy(draft = Draft("Edited", 8))
        }
        compose.onNodeWithText("Save edit").performClick()
        compose.onNodeWithText("Original").assertExists()
        compose.runOnIdle {
            assertEquals(ActionRequest("room", 1, ActionCommand.SubmitEdit, "m1", "Edited", 8), events.last())
            state.value = state.value.copy(revision = 2, editing = null, deleting = target)
        }
        compose.onNodeWithText("Confirm delete").performClick()
        compose.onNodeWithText("Original").assertExists()
        compose.runOnIdle {
            assertEquals(ActionRequest("room", 2, ActionCommand.ConfirmDelete, "m1"), events.last())
            state.value = state.value.copy(revision = 3, pending = true)
        }
        compose.onNodeWithText("Confirm delete").assertIsNotEnabled()
    }

    @Test fun removedQuotedTargetUsesReplaceableFallbackAndPreservesReplyIdentity() {
        val conversation = ConversationState("room", "me", Draft("Reply", 3))
        val events = mutableListOf<ActionRequest>()
        compose.setContent { ActionsConversation(conversation, ActionState(quoteTargetId = "removed"), {}, events::add) }
        compose.onNodeWithText("Quoted message was removed").assertExists()
        compose.onNodeWithText("Send").performClick()
        compose.runOnIdle { assertEquals(ActionRequest("room", 0, ActionCommand.SendQuote, "removed", "Reply", 3), events.single()) }
    }

    @Test fun savedCallbacksRejectRemovedTargetsCapabilitiesAndOtherConversations() {
        val conversation = mutableStateOf(ConversationState("room", "me", messages = listOf(target)))
        val state = mutableStateOf(ActionState(capabilities = mapOf("m1" to MessageAction.entries.toSet())))
        val events = mutableListOf<ActionRequest>()
        var rendered: ActionsContent? = null
        compose.setContent { rendered = rememberMessageActions(conversation.value, state.value, events::add, {}) }
        lateinit var old: ActionsContent
        compose.runOnIdle { old = rendered!!; conversation.value = conversation.value.copy(messages = emptyList()) }
        compose.runOnIdle { old.request(ActionCommand.Copy, "m1"); assertEquals(0, events.size); conversation.value = conversation.value.copy(messages = listOf(target)) }
        compose.runOnIdle { old = rendered!!; state.value = state.value.copy(capabilities = emptyMap()) }
        compose.runOnIdle { old.request(ActionCommand.RequestDelete, "m1"); assertEquals(0, events.size); state.value = state.value.copy(capabilities = mapOf("m1" to MessageAction.entries.toSet())) }
        compose.runOnIdle { old = rendered!!; conversation.value = conversation.value.copy(conversationId = "other") }
        compose.runOnIdle { old.request(ActionCommand.Quote, "m1"); assertEquals(0, events.size) }
    }
    @Test fun keyboardActivationIsExplicitAndRepeatedRequestsAreLatched() {
        val events = mutableListOf<ActionRequest>()
        compose.setContent {
            ActionsConversation(ConversationState("room", "me", messages = listOf(target)),
                ActionState(capabilities = mapOf("m1" to setOf(MessageAction.Copy))), {}, events::add)
        }
        compose.onNodeWithText("Actions").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithText("Actions").performKeyInput { pressKey(Key.Enter); pressKey(Key.Enter) }
        compose.runOnIdle { assertEquals(listOf(ActionRequest("room", 0, ActionCommand.OpenMenu, "m1")), events) }
    }

}
