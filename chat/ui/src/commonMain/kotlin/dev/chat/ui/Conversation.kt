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

/** Presentation receives host data and guarded intents; it owns labels and accessibility semantics. */
class ComposerContent(
    val draft: Draft,
    val pending: Boolean,
    val onTextChange: (String) -> Unit,
    val onSend: () -> Unit,
) {
    val canSend: Boolean get() = !pending && draft.text.isNotBlank()
}

/** Shared timeline and submission behavior. Slots allow presentation without Material3. */
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
