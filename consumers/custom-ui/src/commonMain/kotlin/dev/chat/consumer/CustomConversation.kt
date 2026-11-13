package dev.chat.consumer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.forntoh.chat.core.*
import com.forntoh.chat.ui.Conversation
import com.forntoh.chat.ui.ConversationTheme
import com.forntoh.chat.ui.LocalConversationSlots

@Composable
fun CustomConversation(state: ConversationState, onEvent: (ConversationEvent) -> Unit) {
    ConversationTheme(slots = LocalConversationSlots.current.copy(
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
                BasicTextField(content.draft.text, content.onTextChange, enabled = !content.pending,
                    modifier = Modifier.weight(1f).semantics { contentDescription = "Message" })
                Control("Send", content.canSend, content.onSend)
            }
        },
    )) { Conversation(state, onEvent) }
}

@Composable
private fun Control(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).clickable(enabled = enabled, role = Role.Button, onClick = onClick)) {
        BasicText(label)
    }
}
