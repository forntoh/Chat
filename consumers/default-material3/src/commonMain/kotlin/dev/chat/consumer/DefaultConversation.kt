package dev.chat.consumer

import androidx.compose.runtime.Composable
import dev.chat.core.*
import dev.chat.material3.Conversation

@Composable
fun DefaultConversation(state: ConversationState, onEvent: (ConversationEvent) -> Unit) {
    Conversation(state = state, onEvent = onEvent)
}

/** Compile-time consumer evidence for existing positional and trailing-lambda entry points. */
@Composable
fun LegacySlots(state: ConversationState, onEvent: (ConversationEvent) -> Unit) {
    dev.chat.material3.Conversation(state, onEvent, androidx.compose.ui.Modifier,
        { row, outgoing, retry -> dev.chat.material3.MessageBubble(row, outgoing, retry) },
        { dev.chat.material3.MessageComposer(it) }, dev.chat.ui.HistoryState(), {},
        { dev.chat.material3.HistoryStatusContent(it, state.messages.isEmpty()) }) { }
}

/** Host strings flow through defaults and independently delegated standalone renderers. */
@Composable
fun HostWording(state: ConversationState, onEvent: (ConversationEvent) -> Unit, strings: dev.chat.material3.ConversationStrings) {
    Conversation(state, onEvent, strings = strings,
        messageContent = { row, outgoing, retry -> dev.chat.material3.MessageBubble(row, outgoing, retry, strings) },
        composerContent = { dev.chat.material3.MessageComposer(it, strings) },
        historyContent = { dev.chat.material3.HistoryStatusContent(it, state.messages.isEmpty(), strings) })
}

/** Existing standalone nullable retry callback remains callable as a trailing lambda. */
@Composable
fun LegacyRetry(message: Message, retry: () -> Unit) {
    dev.chat.material3.MessageBubble(message, true) { retry() }
}
