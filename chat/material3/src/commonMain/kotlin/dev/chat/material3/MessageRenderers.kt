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
 * [ConversationTheme] subslots; use [JetchatTheme] or an explicit Jetchat bundle for Material3 defaults. [isCurrentParticipant] selects colors/corners, not permission.
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

/** Explicit subslot bundle for metadata, body, delivery, retry and ordered semantics. [slots]
 * replaces ambient presentation for this subtree; derive LocalConversationSlots.current.copy
 * for partial replacement. [message] is the host snapshot; [isCurrentParticipant] affects only
 * visuals. Custom message descriptions announce sender, content, host time and delivery once.
 * A standalone caller validates the eligibility/identity of the optional [onRetry] callback. */
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

/** Multiline default composer using ambient field/send slots. [content] must carry guarded
 * callbacks from chat-ui, or equivalent guards supplied by a standalone host. No separate draft
 * is retained. Default labels are English; localize field/control content through slots. */
@Composable
fun MessageComposer(content: ComposerContent) {
    MessageComposer(content, LocalConversationSlots.current)
}

/** Explicit field/send subslots. [slots] replaces the ambient bundle; field/send renderers receive
 * the same host [content]. Defaults disable input while pending, disable blank/pending send,
 * reserve a separate 48 dp send target and scroll input after five lines. Custom renderers must
 * honor pending/canSend, supply focus/accessibility semantics and invoke only on explicit input.
 * Rendering or changing the bundle never accepts or submits a draft. */
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

/** Ambient history feedback. [empty] distinguishes exhausted history from an empty conversation;
 * [content] supplies guarded explicit request callbacks, not a transport or storage service. */
@Composable
fun HistoryStatusContent(content: HistoryContent, empty: Boolean) {
    HistoryStatusContent(content, empty, LocalConversationSlots.current)
}

/** Explicit history feedback subslot. [slots] replaces the ambient bundle, including loading,
 * error, empty and retry presentation. [empty] affects exhausted feedback only. Replacements
 * honor [HistoryContent.canLoadEarlier] and invoke its callback only on activation. */
@Composable
fun HistoryStatusContent(content: HistoryContent, empty: Boolean, slots: ConversationSlots) {
    ConversationTheme(slots) {
        Box(Modifier.fillMaxWidth().heightIn(min = 48.dp), contentAlignment = Alignment.CenterStart) {
            slots.historyFeedback(HistoryFeedbackContent(content, empty))
        }
    }
}

/** Deliberate return-to-latest control using [slots] (ambient by default). Its latestLabel subslot
 * can author wording without replacing the scroll callback in [content]. The shared layout owns
 * scrolling; a standalone caller supplies that behavior. Rendering emits no scroll intent. */
@Composable
fun LatestControl(content: LatestContent, slots: ConversationSlots = LocalConversationSlots.current) {
    ConversationTheme(slots) { TextButton(onClick = content.onReturnToLatest) { slots.latestLabel(content) } }
}
