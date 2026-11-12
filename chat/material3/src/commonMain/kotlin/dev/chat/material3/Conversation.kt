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

/**
 * Default presentation from optional `chat-material3`, using the surrounding [MaterialTheme].
 * The host supplies authoritative [state] and resolves [onEvent] as documented by
 * [dev.chat.ui.Conversation]. No messaging service or persistence is installed.
 *
 * ```kotlin
 * MaterialTheme {
 *     Conversation(state = hostState, onEvent = ::handleConversationEvent)
 * }
 * ```
 *
 * [messageContent] defaults to [MessageBubble]; [composerContent] defaults to [MessageComposer].
 * Replacements receive the same guarded intents and must provide their own semantics and labels.
 * Default labels are English in this text slice. [modifier] is passed to the shared IME-aware
 * layout. Use `chat-ui` directly when Material3 is not wanted.
 */
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

/**
 * Plain-text row with host sender/time, text and delivery status. [isCurrentParticipant]
 * selects outgoing colors; it confers no permission. A non-null [onRetry] shows Retry and
 * is invoked on each activation. Standalone callers must validate eligibility themselves;
 * [Conversation] supplies retry only for current-participant failed rows.
 */
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

/**
 * Multiline host-controlled text field and explicit Send control using [content].
 * Pending submissions disable editing; blank drafts disable Send. Input is not trimmed.
 * The field shows at most five lines and scrolls further text within its own viewport.
 * Labels use English; replace the composer slot when different presentation is required.
 */
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
