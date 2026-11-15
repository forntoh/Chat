package dev.chat.consumer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.unit.dp
import dev.forntoh.chat.actions.ActionComposerStatus
import dev.forntoh.chat.actions.ActionRequest
import dev.forntoh.chat.actions.ActionState
import dev.forntoh.chat.actions.LocalActionSlots
import dev.forntoh.chat.actions.MessageActions
import dev.forntoh.chat.actions.QuotedMessage
import dev.forntoh.chat.actions.rememberMessageActions
import dev.forntoh.chat.core.ConversationEvent
import dev.forntoh.chat.core.ConversationState
import dev.forntoh.chat.core.Message
import dev.forntoh.chat.ui.Conversation

@Composable
fun ActionsConversation(
    state: ConversationState,
    actions: ActionState,
    onEvent: (ConversationEvent) -> Unit,
    onAction: (ActionRequest) -> Unit,
) {
    val content = rememberMessageActions(state, actions, onAction, onEvent)
    Conversation(
        state,
        content::onConversationEvent,
        messageContent = { message, _, retry ->
            Column {
                BasicText("${message.sender.displayName} · ${message.displayTime}")
                actions.quotes[message.id]?.let {
                    QuotedMessage(
                        content,
                        it,
                        LocalActionSlots.current.copy(quote = {
                            BasicText(
                                it.message?.let { message ->
                                    "${message.sender.displayName}: ${message.text}"
                                } ?: "Quoted message was removed",
                            )
                        }),
                    )
                }
                BasicText(message.text)
                BasicText(message.delivery.name)
                MessageActions(content, message.id)
                if (retry != null) Control("Retry", true, retry)
            }
        },
        composerContent = { composer ->
            val guarded = content.composer(composer)
            Column {
                ActionComposerStatus(
                    content,
                    LocalActionSlots.current.copy(quote = {
                        BasicText(
                            it.message?.let { message ->
                                "${message.sender.displayName}: ${message.text}"
                            } ?: "Quoted message was removed",
                        )
                    }),
                )
                Row {
                    BasicTextField(
                        guarded.draft.text,
                        guarded.onTextChange,
                        enabled = !guarded.pending,
                        modifier = Modifier.weight(1f).semantics { contentDescription = "Message" },
                    )
                    Control(if (actions.editing == null) "Send" else "Save edit", guarded.canSend, guarded.onSend)
                }
            }
        },
    )
}

@Composable
private fun Control(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).clickable(enabled = enabled, role = Role.Button, onClick = onClick)) {
        BasicText(label)
    }
}
