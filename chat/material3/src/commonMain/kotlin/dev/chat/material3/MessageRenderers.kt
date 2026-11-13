package dev.chat.material3

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import dev.chat.core.*
import dev.chat.ui.*

/**
 * Default sender/time, bubble and delivery group, with Retry independently actionable. Reads
 * [ConversationTheme] subslots. [isCurrentParticipant] selects colors/corners, not permission.
 * A standalone caller validates [onRetry] eligibility and identity; Conversation supplies it only
 * for an outgoing failed row. Callback activation is repeatable; the host owns deduplication.
 */
@Composable
fun MessageBubble(message: Message, isCurrentParticipant: Boolean, onRetry: (() -> Unit)?) {
    MessageBubble(
        message = message,
        isCurrentParticipant = isCurrentParticipant,
        onRetry = onRetry,
        slots = LocalConversationSlots.current
    )
}

@Composable
fun MessageBubble(
    message: Message,
    isCurrentParticipant: Boolean,
    onRetry: (() -> Unit)?,
    slots: ConversationSlots
) {
    ConversationTheme(slots) {
        Column(
            Modifier.widthIn(max = 560.dp),
            horizontalAlignment = if (isCurrentParticipant) Alignment.End else Alignment.Start
        ) {
            Column(Modifier.clearAndSetSemantics { contentDescription = slots.messageDescription(message) }) {
                slots.messageMetadata(message)
                Surface(
                    color = if (isCurrentParticipant) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(
                        topStart = 20.dp,
                        topEnd = 20.dp,
                        bottomStart = if (isCurrentParticipant) 20.dp else 4.dp,
                        bottomEnd = if (isCurrentParticipant) 4.dp else 20.dp
                    ),
                ) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        slots.messageBody(message)
                        slots.messageDelivery(message.delivery)
                    }
                }
            }
            if (onRetry != null) slots.retry(RetryContent(message, onRetry))
        }
    }
}

@Composable
fun MessageComposer(content: ComposerContent) {
    MessageComposer(content, LocalConversationSlots.current)
}

@Composable
fun MessageComposer(content: ComposerContent, slots: ConversationSlots) {
    ConversationTheme(slots) {
        Surface(tonalElevation = 2.dp) {
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                Box(Modifier.weight(1f)) { slots.composerField(content) }
                slots.composerSend(content)
            }
        }
    }
}

@Composable
fun HistoryStatusContent(content: HistoryContent, empty: Boolean) {
    HistoryStatusContent(content, empty, LocalConversationSlots.current)
}

@Composable
fun HistoryStatusContent(content: HistoryContent, empty: Boolean, slots: ConversationSlots) {
    ConversationTheme(slots) {
        Box(Modifier.fillMaxWidth().heightIn(min = 48.dp), contentAlignment = Alignment.CenterStart) {
            slots.historyFeedback(HistoryFeedbackContent(content, empty))
        }
    }
}

@Composable
fun LatestControl(content: LatestContent, slots: ConversationSlots = LocalConversationSlots.current) {
    ConversationTheme(slots) { TextButton(onClick = content.onReturnToLatest) { slots.latestLabel(content) } }
}
