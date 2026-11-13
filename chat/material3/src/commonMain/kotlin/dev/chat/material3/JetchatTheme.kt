package dev.chat.material3

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.chat.ui.*

/**
 * Jetchat-referenced Material3 implementations of shared UI slots. This factory supplies
 * each standard renderer/subslot with Material3 content.
 * Customize the returned bundle with copy(...) to retain these defaults. It creates no services
 * or behavior state and requires neither actions nor media. Wording is English composable content;
 * hosts localize subslots directly, including visible and accessible delivery content together.
 */
fun jetchatConversationSlots(): ConversationSlots = ConversationSlots(
    message = { row, outgoing, retry -> MessageBubble(row, outgoing, retry) },
    composer = { MessageComposer(it) },
    history = { HistoryStatusContent(it.history, it.empty) },
    latest = { LatestControl(it) },
    messageMetadata = { Text("${it.sender.displayName} · ${it.displayTime}", style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) },
    messageBody = { Text(it.text, style = MaterialTheme.typography.bodyLarge) },
    messageDelivery = { Text(it.name, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp)) },
    messageDescription = { "${it.sender.displayName}, ${it.text}, ${it.displayTime}, ${it.delivery.name}" },
    retry = { TextButton(onClick = it.onRetry) { Text("Retry") } },
    composerField = { OutlinedTextField(value = it.draft.text, onValueChange = it.onTextChange,
        label = { Text("Message") }, enabled = !it.pending, minLines = 1, maxLines = 5,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth().testTag("composer").semantics { contentDescription = "Message" }) },
    composerSend = { Button(enabled = it.canSend, onClick = it.onSend, modifier = Modifier.heightIn(min = 48.dp)) { Text("Send") } },
    historyFeedback = { DefaultHistoryFeedback(it) },
    latestLabel = { Text(if (it.hasNewMessages) "New messages · Latest" else "Latest") },
)

/**
 * Optional Material3 presentation provider for the one shared [dev.chat.ui.Conversation]. Wrap
 * this in the host's MaterialTheme to choose colors/typography. [slots] defaults to Jetchat
 * renderers and replaces the ambient shared bundle; derive jetchatConversationSlots().copy(...)
 * for host overrides. The shared Conversation's explicit slots/renderers still take precedence.
 * Keep the shared component mounted across theme changes to retain send/history guards.
 *
 * ```kotlin
 * MaterialTheme {
 *     JetchatTheme { dev.chat.ui.Conversation(hostState, ::handleEvent) }
 * }
 * ```
 * No alternate Conversation, host state, actions/media dependency or service is installed.
 */
@Composable
fun JetchatTheme(slots: ConversationSlots = jetchatConversationSlots(), content: @Composable () -> Unit) {
    ConversationTheme(slots, content)
}

/** Separately callable Material3 history feedback default. Honors the shared request guard;
 * exhausted/empty and error/loading states have distinct English content. No fetch runs here. */
@Composable
fun DefaultHistoryFeedback(content: HistoryFeedbackContent) {
    val history = content.history
    when (history.state.status) {
        HistoryStatus.Idle -> TextButton(enabled = history.canLoadEarlier, onClick = history.onLoadEarlier) { Text("Load earlier") }
        HistoryStatus.Loading -> Text("Loading messages…", Modifier.padding(12.dp))
        HistoryStatus.Error -> TextButton(enabled = history.canLoadEarlier, onClick = history.onLoadEarlier) { Text("Couldn't load messages · Retry") }
        HistoryStatus.Exhausted -> Text(if (content.empty) "No messages yet" else "Start of conversation", Modifier.padding(12.dp))
    }
}
