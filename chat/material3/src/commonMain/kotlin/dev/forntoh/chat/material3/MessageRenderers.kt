package dev.forntoh.chat.material3

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.visible
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.forntoh.chat.core.Message
import dev.forntoh.chat.ui.ChatMotionVisibility
import dev.forntoh.chat.ui.ComposerContent
import dev.forntoh.chat.ui.Conversation
import dev.forntoh.chat.ui.ConversationSlots
import dev.forntoh.chat.ui.ConversationTheme
import dev.forntoh.chat.ui.HistoryContent
import dev.forntoh.chat.ui.HistoryFeedbackContent
import dev.forntoh.chat.ui.HistoryStatus
import dev.forntoh.chat.ui.LatestContent
import dev.forntoh.chat.ui.LocalConversationSlots
import dev.forntoh.chat.ui.LocalConversationViewportPadding
import dev.forntoh.chat.ui.LocalMessageGroupContext
import dev.forntoh.chat.ui.LocalMessagePresentation
import dev.forntoh.chat.ui.MessagePresentation
import dev.forntoh.chat.ui.RetryContent

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
        slots = LocalConversationSlots.current,
    )
}

@Composable
fun MessageBubble(message: Message, isCurrentParticipant: Boolean, onRetry: (() -> Unit)?, slots: ConversationSlots) =
    MessageBubble(message, isCurrentParticipant, onRetry, slots, Modifier)

/**
 * Renders the default message with [bubbleModifier] applied only to the shaped colored surface.
 * Host click/long-click handling and indication attached here exclude the external sender, avatar,
 * row gutters and separate Retry. The modifier is clipped to the bubble's grouped corners.
 * [presentation] exposes reply-navigation highlight state; custom row slots may animate it themselves.
 * Slots retain content ownership; actions and their eligibility remain host-owned.
 */
@Composable
fun MessageBubble(
    message: Message,
    isCurrentParticipant: Boolean,
    onRetry: (() -> Unit)?,
    slots: ConversationSlots,
    bubbleModifier: Modifier,
    presentation: MessagePresentation = LocalMessagePresentation.current,
) {
    val group = LocalMessageGroupContext.current
    ConversationTheme(slots) {
        BoxWithConstraints(
            Modifier.fillMaxWidth(),
            contentAlignment = if (isCurrentParticipant) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            val maxBubbleWidth =
                minOf(
                    maxWidth *
                        if (isCurrentParticipant) ChatGeometry.OUTGOING_ROW_WIDTH_FRACTION else ChatGeometry.INCOMING_ROW_WIDTH_FRACTION,
                    ChatGeometry.BubbleMaxWidth,
                )
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
                        bubbleModifier = bubbleModifier,
                        highlighted = presentation.isHighlighted,
                        shape = RoundedCornerShape(
                            topStart = if (!isCurrentParticipant && !group.isFirst) joined else outer,
                            topEnd = if (isCurrentParticipant && !group.isFirst) joined else outer,
                            bottomEnd = if (isCurrentParticipant) joined else outer,
                            bottomStart = if (!isCurrentParticipant) joined else outer,
                        ),
                    )
                    if (onRetry != null) slots.retry(RetryContent(message, onRetry))
                }
            }
        }
    }
}

/**
 * Content below a default bubble, aligned to its sender edge and bounded by the same row geometry.
 * Use for optional attachment/status controls that need their own accessible semantics outside
 * the bubble. Incoming content reserves the avatar gutter even without an avatar; outgoing
 * content aligns to the trailing edge. This renderer has no action/media dependency or state.
 */
@Composable
fun MessageSupplement(isCurrentParticipant: Boolean, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(
        modifier.fillMaxWidth(),
        contentAlignment = if (isCurrentParticipant) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        val rowWidth = minOf(
            maxWidth * if (isCurrentParticipant) {
                ChatGeometry.OUTGOING_ROW_WIDTH_FRACTION
            } else {
                ChatGeometry.INCOMING_ROW_WIDTH_FRACTION
            },
            ChatGeometry.BubbleMaxWidth,
        )
        Row(Modifier.widthIn(max = rowWidth)) {
            if (!isCurrentParticipant) Spacer(Modifier.width(ChatGeometry.Avatar + ChatGeometry.AvatarGap))
            Column(
                horizontalAlignment = if (isCurrentParticipant) Alignment.End else Alignment.Start,
                content = content,
            )
        }
    }
}

@Composable
private fun AvatarGutter(message: Message, visible: Boolean, slots: ConversationSlots) {
    Box(
        Modifier
            .width(ChatGeometry.Avatar + ChatGeometry.AvatarGap)
            .height(ChatGeometry.Avatar)
            .padding(end = ChatGeometry.AvatarGap),
    ) {
        if (visible) slots.messageAvatar(message)
    }
}

@Composable
fun MessageComposer(content: ComposerContent) {
    MessageComposer(content, LocalConversationSlots.current)
}

/** Rounded floating input with bottom-aligned field/Send subslots. See the overload with
 * `headerContent` and `leadingContent` for optional host-owned reply/attachment presentation. */
@Composable
fun MessageComposer(content: ComposerContent, slots: ConversationSlots) {
    MessageComposer(content, slots, headerContent = {}, leadingContent = {})
}

/**
 * Floating composer surface. [headerContent] renders inside the input above its field (for example,
 * optional action-module reply/edit context); [leadingContent] sits outside, aligned to the bottom
 * (for example an attachment control). Empty content reserves no space. The input and Send use
 * [slots] and keep the guarded [content] callbacks. No optional module is required by this renderer.
 * Conversation measures the entire composer slot for timeline clearance; custom wrappers should
 * paint only their occupied surfaces so messages remain visible through the surrounding space.
 */
@Composable
fun MessageComposer(
    content: ComposerContent,
    slots: ConversationSlots,
    headerContent: @Composable () -> Unit,
    leadingContent: @Composable () -> Unit,
) {
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    ConversationTheme(slots) {
        Row(
            Modifier.fillMaxWidth().padding(
                start = defaultComposerGutter(),
                end = defaultComposerGutter(),
                top = ChatGeometry.ComposerVerticalPadding,
                bottom = if (keyboardVisible) {
                    ChatGeometry.ComposerVerticalPadding
                } else {
                    val viewportBottom = LocalConversationViewportPadding.current.calculateBottomPadding()
                    (ChatGeometry.ComposerVerticalPadding - viewportBottom).coerceAtLeast(0.dp)
                },
            ),
            verticalAlignment = Alignment.Bottom,
        ) {
            leadingContent()
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.96f),
                modifier = Modifier.weight(1f),
            ) {
                Column {
                    headerContent()
                    Row(Modifier.fillMaxWidth().padding(ChatGeometry.ComposerInnerPadding), verticalAlignment = Alignment.Bottom) {
                        Box(Modifier.weight(1f)) { slots.composerField(content) }
                        slots.composerSend(content)
                    }
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
            ChatMotionVisibility(HistoryFeedbackContent(content, empty).takeIf { content.state.status == HistoryStatus.Loading }) { shown ->
                slots.historyFeedback(shown)
            }
            if (content.state.status != HistoryStatus.Loading) {
                slots.historyFeedback(HistoryFeedbackContent(content, empty))
            }
        }
    }
}

@Composable
fun LatestControl(content: LatestContent, slots: ConversationSlots = LocalConversationSlots.current) {
    ConversationTheme(slots) {
        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainerHigh, shadowElevation = 3.dp) {
            IconButton(onClick = content.onReturnToLatest) { slots.latestLabel(content) }
        }
    }
}
