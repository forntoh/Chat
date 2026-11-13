package dev.chat.consumer

import androidx.compose.runtime.Composable
import dev.chat.core.*
import dev.chat.ui.Conversation
import dev.chat.material3.JetchatTheme

@Composable
fun DefaultConversation(state: ConversationState, onEvent: (ConversationEvent) -> Unit) {
    JetchatTheme { Conversation(state = state, onEvent = onEvent) }
}

/** Compile-time consumer evidence for existing positional and trailing-lambda entry points. */
@Composable
fun LegacySlots(state: ConversationState, onEvent: (ConversationEvent) -> Unit) {
    dev.chat.ui.Conversation(state, onEvent, androidx.compose.ui.Modifier,
        { row, outgoing, retry -> dev.chat.material3.MessageBubble(row, outgoing, retry) },
        { dev.chat.material3.MessageComposer(it) }, dev.chat.ui.HistoryState(), {},
        { dev.chat.material3.HistoryStatusContent(it, state.messages.isEmpty()) }) { }
}

/** Host wording is authored directly in composable subslots. */
@Composable
fun HostWording(state: ConversationState, onEvent: (ConversationEvent) -> Unit) {
    Conversation(state, onEvent, slots = dev.chat.material3.jetchatConversationSlots().copy(
        composerSend = { composer ->
            androidx.compose.material3.Button(onClick = composer.onSend, enabled = composer.canSend) {
                androidx.compose.material3.Text("Post")
            }
        }))
}

/** Existing standalone nullable retry callback remains callable as a trailing lambda. */
@Composable
fun LegacyRetry(message: Message, retry: () -> Unit) {
    dev.chat.material3.MessageBubble(message, true) { retry() }
}
