package dev.chat.material3

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.chat.core.*
import dev.chat.ui.ComposerContent
import dev.chat.ui.Conversation as BaseConversation

@Composable
fun Conversation(
    state: ConversationState,
    onEvent: (ConversationEvent) -> Unit,
    modifier: Modifier = Modifier,
    messageContent: @Composable (Message, Boolean, (() -> Unit)?) -> Unit = { message, isCurrent, retry ->
        MessageBubble(message, isCurrent, retry)
    },
    composerContent: @Composable (ComposerContent) -> Unit = { MessageComposer(it) },
) {
    BaseConversation(state, onEvent, modifier, messageContent, composerContent)
}

@Composable
fun MessageBubble(message: Message, isCurrentParticipant: Boolean, onRetry: (() -> Unit)?) {
    Surface(
        color = if (isCurrentParticipant) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.widthIn(max = 560.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("${message.sender.displayName} · ${message.displayTime}", style = MaterialTheme.typography.labelMedium)
            Text(message.text, style = MaterialTheme.typography.bodyLarge)
            Text(message.delivery.name, style = MaterialTheme.typography.labelSmall)
            if (onRetry != null) TextButton(onClick = onRetry) { Text("Retry") }
        }
    }
}

@Composable
fun MessageComposer(content: ComposerContent) {
    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = content.draft.text,
            onValueChange = content.onTextChange,
            label = { Text("Message") },
            enabled = !content.pending,
            minLines = 1,
            maxLines = 5,
            modifier = Modifier.weight(1f).testTag("composer"),
        )
        Button(enabled = content.canSend, onClick = content.onSend, modifier = Modifier.heightIn(min = 48.dp)) { Text("Send") }
    }
}
