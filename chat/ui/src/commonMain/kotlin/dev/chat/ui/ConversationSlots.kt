package dev.chat.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import dev.chat.core.*

data class RetryContent(val message: Message, val onRetry: () -> Unit)

data class HistoryFeedbackContent(val history: HistoryContent, val empty: Boolean)

/**
 * Slot configuration defining composable components and behaviors for conversation UI rendering.
 *
 * @property message Composable slot for rendering an individual message row.
 * @property composer Composable slot for rendering the message input composer area.
 * @property history Composable slot for rendering conversation history and pagination feedback.
 * @property latest Composable slot for returning to the latest messages.
 * @property messageMetadata Composable slot for rendering message sender and timestamp metadata.
 * @property messageBody Composable slot for rendering the main text content of a message.
 * @property messageDelivery Composable slot for rendering message delivery status.
 * @property messageDescription Function providing accessibility content description for a message.
 * @property retry Composable slot for rendering retry action controls.
 * @property composerField Composable slot for the text input field within the composer.
 * @property composerSend Composable slot for the send action control within the composer.
 * @property historyFeedback Composable slot for history loading status and feedback.
 * @property latestLabel Composable slot for the label indicating new messages or latest state.
 */
data class ConversationSlots(
    val message: @Composable (Message, Boolean, (() -> Unit)?) -> Unit = { row, _, retry -> DefaultMessage(row, retry) },
    val composer: @Composable (ComposerContent) -> Unit = { DefaultComposer(it) },
    val history: @Composable (HistoryFeedbackContent) -> Unit = { DefaultHistoryFeedback(it) },
    val latest: @Composable (LatestContent) -> Unit = { NeutralControl(it.onReturnToLatest) { LocalConversationSlots.current.latestLabel(it) } },
    val messageMetadata: @Composable (Message) -> Unit = { BasicText("${it.sender.displayName} · ${it.displayTime}") },
    val messageBody: @Composable (Message) -> Unit = { BasicText(it.text) },
    val messageDelivery: @Composable (Delivery) -> Unit = { BasicText(it.name) },
    val messageDescription: (Message) -> String = { "${it.sender.displayName}, ${it.text}, ${it.displayTime}, ${it.delivery.name}" },
    val retry: @Composable (RetryContent) -> Unit = { NeutralControl(it.onRetry) { BasicText("Retry") } },
    val composerField: @Composable (ComposerContent) -> Unit = {
        BasicTextField(it.draft.text, it.onTextChange, enabled = !it.pending,
            modifier = Modifier.fillMaxWidth().testTag("composer").semantics { contentDescription = "Message" })
    },
    val composerSend: @Composable (ComposerContent) -> Unit = { NeutralControl(it.onSend, it.canSend) { BasicText("Send") } },
    val historyFeedback: @Composable (HistoryFeedbackContent) -> Unit = { NeutralHistoryFeedback(it) },
    val latestLabel: @Composable (LatestContent) -> Unit = { BasicText(if (it.hasNewMessages) "New messages · Latest" else "Latest") },
)

val LocalConversationSlots = staticCompositionLocalOf { ConversationSlots() }

/**
 * Installs shared presentation only. Keep the same Conversation mounted while switching themes
 * or slots; its draft/send/history guards remain in that one behavior component. [slots] defaults
 * to the surrounding theme; explicit bundles replace it. Host themes may derive a copy to keep
 * unaffected renderers. Optional themes call this provider with their own implementations.
 * ```kotlin
 * ConversationTheme(slots = LocalConversationSlots.current.copy(composerSend = { draft ->
 *     HostSendControl(draft.canSend, draft.onSend)
 * })) { Conversation(hostState, ::handleEvent) }
 * ```
 */
@Composable
fun ConversationTheme(slots: ConversationSlots = LocalConversationSlots.current, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalConversationSlots provides slots, content = content)
}

@Composable
fun DefaultMessage(message: Message, onRetry: (() -> Unit)?) {
    val slots = LocalConversationSlots.current
    Column {
        Column(Modifier.clearAndSetSemantics { contentDescription = slots.messageDescription(message) }) {
            slots.messageMetadata(message); slots.messageBody(message); slots.messageDelivery(message.delivery)
        }
        if (onRetry != null) slots.retry(RetryContent(message, onRetry))
    }
}

@Composable
fun DefaultComposer(content: ComposerContent) {
    val slots = LocalConversationSlots.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f)) { slots.composerField(content) }
        slots.composerSend(content)
    }
}

@Composable
fun DefaultHistoryFeedback(content: HistoryFeedbackContent) {
    LocalConversationSlots.current.historyFeedback(content)
}

@Composable
private fun NeutralControl(onClick: () -> Unit, enabled: Boolean = true, content: @Composable () -> Unit) {
    Box(
        Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ).padding(8.dp),
    ) {
        content()
    }
}

@Composable
private fun NeutralHistoryFeedback(content: HistoryFeedbackContent) {
    val history = content.history
    when (history.state.status) {
        HistoryStatus.Idle -> {
            NeutralControl(
                onClick = history.onLoadEarlier,
                enabled = history.canLoadEarlier
            ) { BasicText("Load earlier") }
        }
        HistoryStatus.Error -> {
            NeutralControl(
                onClick = history.onLoadEarlier,
                enabled = history.canLoadEarlier
            ) { BasicText("Couldn't load messages · Retry") }
        }
        HistoryStatus.Loading -> {
            BasicText("Loading messages…")
        }
        HistoryStatus.Exhausted -> {
            BasicText(if (content.empty) "No messages yet" else "Start of conversation")
        }
    }
}
