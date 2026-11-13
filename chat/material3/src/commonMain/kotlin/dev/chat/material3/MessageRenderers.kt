package dev.chat.material3

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.onFocusChanged
import dev.chat.core.*
import dev.chat.ui.*

/**
 * Default grouped avatar/metadata, directional bubble and delivery presentation, with Retry
 * independently actionable. Reads [LocalMessageGroupContext] from the shared timeline and
 * [ConversationTheme] subslots. [isCurrentParticipant] selects bubble contrast, not permission.
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
    val group = LocalMessageGroupContext.current
    ConversationTheme(slots) {
        Box(Modifier.fillMaxWidth(), contentAlignment = if (isCurrentParticipant) Alignment.CenterEnd else Alignment.CenterStart) {
            Row(
                Modifier.widthIn(max = 560.dp).fillMaxWidth(if (isCurrentParticipant) 0.82f else 0.88f),
                horizontalArrangement = if (isCurrentParticipant) Arrangement.End else Arrangement.Start,
                verticalAlignment = Alignment.Bottom,
            ) {
                if (!isCurrentParticipant) AvatarGutter(message, group.isLast, slots)
                Column(horizontalAlignment = if (isCurrentParticipant) Alignment.End else Alignment.Start) {
                    Surface(
                        color = if (isCurrentParticipant) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = if (isCurrentParticipant) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier.clearAndSetSemantics { contentDescription = slots.messageDescription(message) },
                    ) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.Bottom,
                                itemVerticalAlignment = Alignment.Bottom,
                            ) {
                                slots.messageBody(message)
                                slots.messageMetadata(message)
                            }
                            slots.messageDelivery(message.delivery)
                        }
                    }
                    if (onRetry != null) slots.retry(RetryContent(message, onRetry))
                }
            }
        }
    }
}

@Composable
private fun AvatarGutter(message: Message, visible: Boolean, slots: ConversationSlots) {
    Box(Modifier.width(36.dp).padding(end = 6.dp)) {
        if (visible) slots.messageAvatar(message)
    }
}

@Composable
fun MessageComposer(content: ComposerContent) {
    MessageComposer(content, LocalConversationSlots.current)
}

@Composable
fun MessageComposer(content: ComposerContent, slots: ConversationSlots) {
    var focused by remember { mutableStateOf(false) }
    ConversationTheme(slots) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                border = if (focused) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                modifier = Modifier.weight(1f).onFocusChanged { focused = it.hasFocus },
            ) {
                slots.composerField(content)
            }
            slots.composerSend(content)
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
        Box(Modifier.fillMaxWidth().heightIn(min = 32.dp), contentAlignment = Alignment.Center) {
            slots.historyFeedback(HistoryFeedbackContent(content, empty))
        }
    }
}

@Composable
fun LatestControl(content: LatestContent, slots: ConversationSlots = LocalConversationSlots.current) {
    ConversationTheme(slots) {
        Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
            FilledTonalButton(onClick = content.onReturnToLatest) { slots.latestLabel(content) }
        }
    }
}
