package dev.forntoh.chat.material3

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.onFocusChanged
import dev.forntoh.chat.core.*
import dev.forntoh.chat.ui.*

/**
 * Default grouped avatar/metadata, directional bubble and delivery presentation, with Retry
 * independently actionable. Sender and time come entirely from the metadata slot; body and metadata subslots
 * use measured, non-overlapping placement. The metadata slot can place a heading outside the bubble and
 * time inside it through [MessageMetadata], alongside delivery. Avatar
 * space remains reserved when a host supplies no avatar. Reads [LocalMessageGroupContext] from the shared timeline and
 * [ConversationTheme] subslots. [isCurrentParticipant] selects directional presentation, not permission.
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
        BoxWithConstraints(
            Modifier.fillMaxWidth(),
            contentAlignment = if (isCurrentParticipant) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            val maxBubbleWidth = minOf(maxWidth * if (isCurrentParticipant) ChatGeometry.OutgoingRowWidthFraction else ChatGeometry.IncomingRowWidthFraction, ChatGeometry.BubbleMaxWidth)
            Row(
                Modifier.widthIn(max = maxBubbleWidth),
                verticalAlignment = Alignment.Bottom,
            ) {
                if (!isCurrentParticipant) AvatarGutter(message, group.isLast, slots)
                Column(horizontalAlignment = if (isCurrentParticipant) Alignment.End else Alignment.Start) {
                    val outer = ChatGeometry.BubbleRadius
                    val joined = ChatGeometry.JoinedRadius
                    MessageBubbleLayout(
                        message = message,
                        outgoing = isCurrentParticipant,
                        slots = slots,
                        shape = RoundedCornerShape(
                            topStart = if (!isCurrentParticipant && !group.isFirst) joined else outer,
                            topEnd = if (isCurrentParticipant && !group.isFirst) joined else outer,
                            bottomEnd = if (isCurrentParticipant) joined else outer,
                            bottomStart = if (!isCurrentParticipant) joined else outer,
                        )
                    )
                    if (onRetry != null) slots.retry(RetryContent(message, onRetry))
                }
            }
        }
    }
}

@Composable
private fun AvatarGutter(message: Message, visible: Boolean, slots: ConversationSlots) {
    Box(
        Modifier
            .width(ChatGeometry.Avatar + ChatGeometry.AvatarGap)
            .height(ChatGeometry.Avatar)
            .padding(end = ChatGeometry.AvatarGap)
    ) {
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
            Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = ChatGeometry.Gutter,
                    vertical = ChatGeometry.ComposerVerticalPadding
                ),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(ChatGeometry.ComposerGap),
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
