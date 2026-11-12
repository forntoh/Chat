package dev.chat.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.chat.core.*

/**
 * Composer slot input supplied by [Conversation]. Render [draft] without owning another copy.
 * [pending] includes both host submission state and the current composition's send latch.
 * [onTextChange] and [onSend] are guarded callbacks: edits are ignored while pending and sends
 * require nonblank text. Call them only for explicit user input, never during composition.
 * Callbacks capture the rendered snapshot; do not retain them after that snapshot is replaced.
 * Custom content owns its labels, focus behavior and accessibility semantics.
 */
class ComposerContent(
    val draft: Draft,
    val pending: Boolean,
    val onTextChange: (String) -> Unit,
    val onSend: () -> Unit,
) {
    /** Whether the rendered draft can be explicitly submitted now. */
    val canSend: Boolean get() = !pending && draft.text.isNotBlank()
}

/**
 * Shared timeline/composer layout from `chat-ui`; requires Compose foundation, not Material3.
 *
 * The host owns [state] and handles [onEvent]. Rows retain [Message.id] keys in host list order.
 * [messageContent] receives the row, current-sender flag and a nullable retry callback; only
 * current-participant failed rows get retry. Retry is not latched: the host must update delivery
 * and reject duplicate or stale requests. Slots own their presentation and semantics.
 * [composerContent] receives guarded composer callbacks and pending state via [ComposerContent].
 *
 * A send latches synchronously before invoking [onEvent]. Changing conversation or draft
 * revision resets that latch; unmounting also discards it. Retain drafts and pending state in
 * the host across navigation. The UI does not persist data or reject stale service results.
 * [modifier] applies to a full-size layout with IME padding; the host must provide window
 * insets and bounded height. Callback exceptions propagate; the host must resolve the draft
 * with a new revision if its submission fails locally.
 *
 * For a host-rendered composer, forward changes with `content.onTextChange(newText)` and an
 * explicit send with `content.onSend()`, enabling the send control with `content.canSend`.
 */
@Composable
fun Conversation(
    state: ConversationState,
    onEvent: (ConversationEvent) -> Unit,
    modifier: Modifier = Modifier,
    messageContent: @Composable (message: Message, isCurrentParticipant: Boolean, onRetry: (() -> Unit)?) -> Unit,
    composerContent: @Composable (ComposerContent) -> Unit,
) {
    var submitted by remember(state.conversationId, state.draft.revision) { mutableStateOf(false) }
    val pending = state.draft.submitting || submitted
    Column(modifier.fillMaxSize().imePadding()) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("timeline"), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(state.messages, key = { it.id }) { message ->
                val isCurrent = message.sender.id == state.currentParticipantId
                Column(Modifier.fillMaxWidth().testTag("message-${message.id}"), horizontalAlignment = if (isCurrent) Alignment.End else Alignment.Start) {
                    messageContent(message, isCurrent,
                        if (isCurrent && message.delivery == Delivery.Failed) {
                            { onEvent(ConversationEvent.RetryMessage(state.conversationId, message.id)) }
                        } else null,
                    )
                }
            }
        }
        composerContent(ComposerContent(state.draft, pending,
            onTextChange = { if (!pending) onEvent(ConversationEvent.DraftChanged(state.conversationId, it)) },
            onSend = {
                if (!submitted && !state.draft.submitting && state.draft.text.isNotBlank()) {
                    submitted = true
                    onEvent(ConversationEvent.SendDraft(state.conversationId, state.draft.revision, state.draft.text))
                }
            },
        ))
    }
}
