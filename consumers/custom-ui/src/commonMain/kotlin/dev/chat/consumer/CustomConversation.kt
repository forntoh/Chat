package dev.chat.consumer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.unit.dp
import dev.forntoh.chat.core.ConversationEvent
import dev.forntoh.chat.core.ConversationState
import dev.forntoh.chat.core.Message
import dev.forntoh.chat.ui.Conversation
import dev.forntoh.chat.ui.ConversationTheme
import dev.forntoh.chat.ui.LocalConversationSlots

@Composable
fun CustomConversation(state: ConversationState, onEvent: (ConversationEvent) -> Unit) {
    ConversationTheme(
        slots = LocalConversationSlots.current.copy(
            message = { message, _, retry ->
                Column {
                    BasicText("${message.sender.displayName} · ${message.displayTime}")
                    BasicText(message.text)
                    BasicText(message.delivery.name)
                    if (retry != null) Control("Retry", true, retry)
                }
            },
            composer = { content ->
                Row {
                    BasicTextField(
                        content.draft.text,
                        content.onTextChange,
                        enabled = !content.pending,
                        modifier = Modifier.weight(1f).semantics { contentDescription = "Message" },
                    )
                    Control("Send", content.canSend, content.onSend)
                }
            },
        ),
    ) { Conversation(state, onEvent) }
}

@Composable
private fun Control(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).clickable(enabled = enabled, role = Role.Button, onClick = onClick)) {
        BasicText(label)
    }
}
