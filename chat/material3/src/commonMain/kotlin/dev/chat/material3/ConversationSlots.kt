package dev.chat.material3

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import dev.chat.core.*
import dev.chat.ui.*

data class RetryContent(val message: Message, val onRetry: () -> Unit)

data class HistoryFeedbackContent(val history: HistoryContent, val empty: Boolean)

/**
 * Material3 renderers and subslots, with English built-in defaults. Hosts author localized copy
 * directly in these composable slots, including the field, send/retry controls, delivery, history
 * and latest labels. [messageDescription] supplies the default row's single ordered semantics
 * description; customize it together with [messageDelivery] when localizing delivery state.
 * Custom renderers own their input/focus/accessibility semantics and invoke callbacks only on
 * user activation. They must honor pending/canSend/canLoadEarlier and never keep a second draft.
 *
 * An explicit per-call renderer wins over the effective bundle. An explicit bundle replaces the
 * ambient bundle; derive `LocalConversationSlots.current.copy(...)` to override just one slot.
 * Standalone defaults read the same theme and can be delegated to without recursively invoking
 * the corresponding top-level slot. Themes configure presentation only, never host state.
 */
data class ConversationSlots(
    val message: @Composable (Message, Boolean, (() -> Unit)?) -> Unit = { row, outgoing, retry ->
        MessageBubble(row, outgoing, retry)
    },
    val composer: @Composable (ComposerContent) -> Unit = {
        MessageComposer(it)
    },
    val history: @Composable (HistoryFeedbackContent) -> Unit = {
        HistoryStatusContent(it.history, it.empty)
    },
    val latest: @Composable (LatestContent) -> Unit = {
        LatestControl(it)
    },
    val messageMetadata: @Composable (Message) -> Unit = {
        Text("${it.sender.displayName} · ${it.displayTime}", style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
    },
    val messageBody: @Composable (Message) -> Unit = {
        Text(it.text, style = MaterialTheme.typography.bodyLarge)
    },
    val messageDelivery: @Composable (Delivery) -> Unit = {
        Text(
            it.name,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 4.dp)
        )
    },
    val messageDescription: (Message) -> String = {
        "${it.sender.displayName}, ${it.text}, ${it.displayTime}, ${it.delivery.name}"
    },
    val retry: @Composable (RetryContent) -> Unit = {
        TextButton(onClick = it.onRetry) { Text("Retry") }
    },
    val composerField: @Composable (ComposerContent) -> Unit = {
        OutlinedTextField(value = it.draft.text, onValueChange = it.onTextChange,
            label = { Text("Message") }, enabled = !it.pending, minLines = 1, maxLines = 5,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth().testTag("composer").semantics { contentDescription = "Message" })
    },
    val composerSend: @Composable (ComposerContent) -> Unit = {
        Button(enabled = it.canSend, onClick = it.onSend, modifier = Modifier.heightIn(min = 48.dp)) { Text("Send") }
    },
    val historyFeedback: @Composable (HistoryFeedbackContent) -> Unit = {
        DefaultHistoryFeedback(it)
    },
    val latestLabel: @Composable (LatestContent) -> Unit = { Text(if (it.hasNewMessages) "New messages · Latest" else "Latest") },
)

/** Effective presentation bundle, independent of conversation/draft identity. */
val LocalConversationSlots = staticCompositionLocalOf { ConversationSlots() }

/**
 * Scoped presentation defaults inside the host's [MaterialTheme]. Unspecified [slots] inherit
 * the surrounding theme. Keep the behavior component mounted when changing slots; this wrapper
 * owns no drafts, services or submission/history state. Explicit renderer > explicit bundle >
 * ambient bundle > built-in defaults. The prerelease grouped-label APIs were removed; use e.g.:
 * ```kotlin
 * ConversationTheme(slots = LocalConversationSlots.current.copy(composerSend = { composer ->
 *     Button(onClick = composer.onSend, enabled = composer.canSend) { Text("Post") }
 * })) { Conversation(hostState, ::handleEvent) }
 * ```
 */
@Composable
fun ConversationTheme(slots: ConversationSlots = LocalConversationSlots.current, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalConversationSlots provides slots, content = content)
}

/** English history feedback default, directly callable from custom slots without recursion.
 * Idle/error buttons use the supplied guard; exhausted empty state has a distinct fallback. */
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
