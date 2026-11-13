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
    val group = LocalMessageGroupContext.current
    ConversationTheme(slots) {
        Row(Modifier.widthIn(max = 560.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!isCurrentParticipant) AvatarGutter(message, group.isFirst, slots)
            Column(Modifier.weight(1f),
                horizontalAlignment = if (isCurrentParticipant) Alignment.End else Alignment.Start) {
                Column(Modifier.clearAndSetSemantics { contentDescription = slots.messageDescription(message) },
                    horizontalAlignment = if (isCurrentParticipant) Alignment.End else Alignment.Start) {
                    if (group.isFirst) slots.messageMetadata(message)
                    Surface(
                        color = if (isCurrentParticipant) MaterialTheme.colorScheme.inversePrimary else MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shape = RoundedCornerShape(
                            topStart = if (!isCurrentParticipant && group.isFirst) 4.dp else 20.dp,
                            topEnd = if (isCurrentParticipant && group.isFirst) 4.dp else 20.dp,
                            bottomStart = 20.dp, bottomEnd = 20.dp,
                        ),
                    ) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                            slots.messageBody(message)
                            slots.messageDelivery(message.delivery)
                        }
                    }
                }
                if (onRetry != null) slots.retry(RetryContent(message, onRetry))
            }
            if (isCurrentParticipant) AvatarGutter(message, group.isFirst, slots)
        }
    }
}

@Composable
private fun AvatarGutter(message: Message, visible: Boolean, slots: ConversationSlots) {
    Box(Modifier.width(42.dp)) {
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
        Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
            .onFocusChanged { focused = it.hasFocus }) {
            Surface(
                shape = RoundedCornerShape(32.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(
                    width = 1.dp,
                    color = if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(4.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Box(Modifier.weight(1f)) { slots.composerField(content) }
                    slots.composerSend(content)
                }
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
