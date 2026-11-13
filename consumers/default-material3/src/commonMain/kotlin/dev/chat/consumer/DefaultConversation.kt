package dev.chat.consumer

import androidx.compose.runtime.Composable
import com.forntoh.chat.core.*
import com.forntoh.chat.ui.Conversation
import com.forntoh.chat.material3.ChatTheme
import com.forntoh.chat.material3.ChatScaffold
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
    com.forntoh.chat.ui.Conversation(state, onEvent, androidx.compose.ui.Modifier,
        { row, outgoing, retry -> com.forntoh.chat.material3.MessageBubble(row, outgoing, retry) },
        { com.forntoh.chat.material3.MessageComposer(it) }, com.forntoh.chat.ui.HistoryState(), {},
        { com.forntoh.chat.material3.HistoryStatusContent(it, state.messages.isEmpty()) }) { }
}

/** Host wording is authored directly in composable subslots. */
@Composable
fun HostWording(state: ConversationState, onEvent: (ConversationEvent) -> Unit) {
    Conversation(state, onEvent, slots = com.forntoh.chat.material3.jetchatConversationSlots().copy(
        composerSend = { composer ->
            androidx.compose.material3.Button(onClick = composer.onSend, enabled = composer.canSend) {
                androidx.compose.material3.Text("Post")
            }
        }))
}

/** Existing standalone nullable retry callback remains callable as a trailing lambda. */
@Composable
fun LegacyRetry(message: Message, retry: () -> Unit) {
    com.forntoh.chat.material3.MessageBubble(message, true) { retry() }
}
