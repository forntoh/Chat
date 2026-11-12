package dev.chat.consumer

import androidx.compose.runtime.Composable
import dev.chat.core.*
import dev.chat.material3.Conversation

@Composable
fun DefaultConversation(state: ConversationState, onEvent: (ConversationEvent) -> Unit) {
    Conversation(state, onEvent)
}
