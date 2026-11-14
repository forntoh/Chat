package dev.chat.consumer

import androidx.compose.runtime.Composable
import dev.forntoh.chat.core.*
import dev.forntoh.chat.ui.Conversation
import dev.forntoh.chat.material3.ChatTheme
import dev.forntoh.chat.material3.ChatScaffold
import androidx.compose.material3.Text

@Composable
fun DefaultConversation(state: ConversationState, onEvent: (ConversationEvent) -> Unit) {
    ChatTheme {
        ChatScaffold(title = { Text("Design team") }) { Conversation(state = state, onEvent = onEvent) }
    }
}

/** Compile-time consumer evidence for existing positional and trailing-lambda entry points. */
@Composable
fun LegacySlots(state: ConversationState, onEvent: (ConversationEvent) -> Unit) {
    dev.forntoh.chat.ui.Conversation(state, onEvent, androidx.compose.ui.Modifier,
        { row, outgoing, retry -> dev.forntoh.chat.material3.MessageBubble(row, outgoing, retry) },
        { dev.forntoh.chat.material3.MessageComposer(it) }, dev.forntoh.chat.ui.HistoryState(), {},
        { dev.forntoh.chat.material3.HistoryStatusContent(it, state.messages.isEmpty()) }) { }
}

/** Host wording is authored directly in composable subslots. */
@Composable
fun HostWording(state: ConversationState, onEvent: (ConversationEvent) -> Unit) {
    Conversation(state, onEvent, slots = dev.forntoh.chat.material3.jetchatConversationSlots().copy(
        composerSend = { composer ->
            androidx.compose.material3.Button(onClick = composer.onSend, enabled = composer.canSend) {
                androidx.compose.material3.Text("Post")
            }
        }))
}

/** Existing standalone nullable retry callback remains callable as a trailing lambda. */
@Composable
fun LegacyRetry(message: Message, retry: () -> Unit) {
    dev.forntoh.chat.material3.MessageBubble(message, true) { retry() }
}
