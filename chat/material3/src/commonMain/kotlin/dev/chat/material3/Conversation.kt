package dev.chat.material3

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.chat.core.*
import dev.chat.ui.*
import dev.chat.ui.Conversation as BaseConversation

/**
 * Default presentation from optional `chat-material3`, using the surrounding [MaterialTheme].
 * The host supplies authoritative [state] and resolves [onEvent] as documented by
 * [dev.chat.ui.Conversation]. No messaging service or persistence is installed.
 *
 * ```kotlin
 * MaterialTheme {
 *     Conversation(state = hostState, onEvent = ::handleConversationEvent)
 * }
 * ```
 *
 * [messageContent] defaults to [MessageBubble]; [composerContent] defaults to [MessageComposer].
 * Replacements receive the same guarded intents and must provide their own semantics and labels.
 * Default labels are English; use the explicit [ConversationStrings] overload for host wording. [modifier] is passed to the shared IME-aware
 * layout. Use `chat-ui` directly when Material3 is not wanted.
 *
 * [history] and [onLoadEarlier] use the shared UI history contract. The default [historyContent]
 * displays initial loading/empty/error and page retry/exhaustion; replace it for host wording.
 * [latestContent] defaults to an explicit Latest control with a new-arrival indicator.
 *
 * @param state Authoritative host snapshot, including ordered messages and a retained conversation draft.
 * @param onEvent Synchronous user-intent handler; host validation and state updates resolve each intent.
 * @param modifier Outer IME-aware layout modifier; defaults to empty and requires bounded host space.
 * @param history Host paging availability, exhausted by default to disable loading.
 * @param onLoadEarlier Host handler that publishes Loading synchronously and validates request generations.
 * @param historyContent Replaceable status/retry presentation; defaults to English Material3 controls.
 * @param latestContent Replaceable return control, shown while away from the bottom; defaults to Latest.
 * @param messageContent Row renderer receiving the message, current-participant flag and nullable retry
 * callback; defaults to [MessageBubble]. Replacements provide their own presentation and semantics.
 * @param composerContent Composer renderer receiving guarded callbacks and pending state; defaults to
 * [MessageComposer]. Replacements provide their own labels, focus behavior and semantics.
 */
@Composable
fun Conversation(
    state: ConversationState,
    onEvent: (ConversationEvent) -> Unit,
    modifier: Modifier = Modifier,
    messageContent: @Composable (Message, Boolean, (() -> Unit)?) -> Unit = { message, isCurrent, retry ->
        MessageBubble(message, isCurrent, retry)
    },
    composerContent: @Composable (ComposerContent) -> Unit = { MessageComposer(it) },
    history: HistoryState = HistoryState(),
    onLoadEarlier: (HistoryRequest) -> Unit = {},
    historyContent: @Composable (HistoryContent) -> Unit = { HistoryStatusContent(it, state.messages.isEmpty()) },
    latestContent: @Composable (LatestContent) -> Unit = { content ->
        TextButton(onClick = content.onReturnToLatest) { Text(if (content.hasNewMessages) "New messages · Latest" else "Latest") }
    },
) {
    Conversation(state, onEvent, modifier, messageContent, composerContent, history,
        onLoadEarlier, historyContent, latestContent, strings = ConversationStrings())
}

/**
 * Strings-aware default presentation with the same state/event contract as [dev.chat.ui.Conversation].
 * [strings] is required to distinguish this overload from existing positional/trailing-lambda calls.
 * Null slots select library renderers using [strings]; explicitly supplied slots take precedence
 * and own their strings and semantics. Wrap [MessageComposer] or delegate individual rows to
 * [MessageBubble] with the same strings to mix custom content and defaults.
 *
 * Keep this call at the same composition location when changing a theme or renderer. The
 * shared layout retains pending submission and history state; rendering never emits send.
 * Custom rows should expose sender, content, host time and delivery in that order, once, with
 * separately named actions. Custom composers must use [ComposerContent]'s guarded callbacks,
 * host draft and pending state, and provide accessible labels and focus behavior.
 *
 * ```kotlin
 * Conversation(state = hostState, onEvent = ::handleEvent, strings = hostStrings,
 *     composerContent = { content -> MessageComposer(content, strings = hostStrings) })
 * ```
 *
 * @param state Authoritative ordered host rows and retained draft; no services are installed.
 * @param onEvent Synchronous user-intent handler; submission acceptance and delivery are host decisions.
 * @param modifier Bounded outer layout, including the shared IME handling.
 * @param messageContent Null uses [MessageBubble]; an explicit renderer receives guarded retry.
 * @param composerContent Null uses [MessageComposer]; replacement does not reset the send latch.
 * @param history Host paging availability; exhausted by default.
 * @param onLoadEarlier Host paging callback; publish Loading synchronously and reject stale results.
 * @param historyContent Null selects localized default feedback; explicit content owns its semantics.
 * @param latestContent Null selects the localized return control; explicit content owns its action label.
 * @param strings Host wording used by all default slots, including history and delivery feedback.
 */
@Composable
fun Conversation(
    state: ConversationState,
    onEvent: (ConversationEvent) -> Unit,
    modifier: Modifier = Modifier,
    messageContent: (@Composable (Message, Boolean, (() -> Unit)?) -> Unit)? = null,
    composerContent: (@Composable (ComposerContent) -> Unit)? = null,
    history: HistoryState = HistoryState(),
    onLoadEarlier: (HistoryRequest) -> Unit = {},
    historyContent: (@Composable (HistoryContent) -> Unit)? = null,
    latestContent: (@Composable (LatestContent) -> Unit)? = null,
    strings: ConversationStrings,
) {
    BaseConversation(state, onEvent, modifier,
        messageContent ?: { message, isCurrent, retry -> MessageBubble(message, isCurrent, retry, strings) },
        composerContent ?: { MessageComposer(it, strings) }, history, onLoadEarlier,
        historyContent ?: { HistoryStatusContent(it, state.messages.isEmpty(), strings) },
        latestContent ?: { content ->
            TextButton(onClick = content.onReturnToLatest, modifier = Modifier.semantics {
                contentDescription = if (content.hasNewMessages) strings.newMessagesDescription else strings.latestDescription
            }) { Text(if (content.hasNewMessages) strings.newMessages else strings.latest) }
        })
}

/**
 * Plain-text row with host sender/time, text and delivery status. [isCurrentParticipant]
 * selects outgoing colors; it confers no permission. A non-null [onRetry] shows Retry and
 * is invoked on each activation. Standalone callers must validate eligibility themselves;
 * [Conversation] supplies retry only for current-participant failed rows.
 *
 * @param message Host row whose sender, formatted time, plain text and delivery state are displayed.
 * @param isCurrentParticipant Whether to use outgoing colors; does not grant permission to retry.
 * @param onRetry Explicit retry callback, or null to hide Retry; standalone callers validate eligibility.
 */
@Composable
fun MessageBubble(message: Message, isCurrentParticipant: Boolean, onRetry: (() -> Unit)?) {
    MessageBubble(message, isCurrentParticipant, onRetry, ConversationStrings())
}

/**
 * Strings-aware row. The text group exposes sender, body, host time and localized delivery
 * once through a single description; visible children do not repeat those announcements.
 * Retry stays outside that group as a separate control. [strings] supplies delivery/retry
 * wording. [isCurrentParticipant] selects colors and bubble corners, never authorization.
 * [onRetry] is explicit, repeatable user intent; standalone hosts validate eligibility.
 */
@Composable
fun MessageBubble(message: Message, isCurrentParticipant: Boolean, onRetry: (() -> Unit)?, strings: ConversationStrings) {
    Column(Modifier.widthIn(max = 560.dp), horizontalAlignment = if (isCurrentParticipant) Alignment.End else Alignment.Start) {
        Column(Modifier.clearAndSetSemantics {
            contentDescription = "${message.sender.displayName}, ${message.text}, ${message.displayTime}, ${strings.deliveryLabel(message.delivery)}"
        }) {
            Text("${message.sender.displayName} · ${message.displayTime}", style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
            Surface(
                color = if (isCurrentParticipant) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp,
                    bottomStart = if (isCurrentParticipant) 20.dp else 4.dp,
                    bottomEnd = if (isCurrentParticipant) 4.dp else 20.dp),
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(message.text, style = MaterialTheme.typography.bodyLarge)
                    Text(strings.deliveryLabel(message.delivery), style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        if (onRetry != null) TextButton(onClick = onRetry,
            modifier = Modifier.semantics { contentDescription = strings.retryDescription }) { Text(strings.retry) }
    }
}

/**
 * English default multiline composer. Preserves the original callable signature; use the
 * strings-aware overload for host wording. Host draft, guarded input and explicit send are
 * supplied through [content]; no separate local text state is retained.
 */
@Composable
fun MessageComposer(content: ComposerContent) {
    MessageComposer(content, ConversationStrings())
}

/**
 * Host-controlled multiline field and explicit send, with [strings] for its label/action names.
 * [content] owns the rendered host draft and guarded callbacks; pending disables editing and
 * blank/pending disables send. Text is never trimmed. The field occupies remaining row width
 * and scrolls after five lines, reserving a separate minimum 48dp send target for long input.
 * Replacing this presentation must preserve these guards and supply meaningful semantics.
 */
@Composable
fun MessageComposer(content: ComposerContent, strings: ConversationStrings) {
    Surface(tonalElevation = 2.dp) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(
                value = content.draft.text,
                onValueChange = content.onTextChange,
                label = { Text(strings.message) },
                enabled = !content.pending,
                minLines = 1,
                maxLines = 5,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.weight(1f).testTag("composer").semantics { contentDescription = strings.messageDescription },
            )
            Button(enabled = content.canSend, onClick = content.onSend,
                modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = strings.sendDescription }) { Text(strings.send) }
        }
    }
}

/**
 * English default history feedback. Preserves the original callable signature; the overload
 * with [ConversationStrings] replaces all feedback. [empty] distinguishes an empty exhausted
 * conversation from the start of existing history; [content] guards explicit retry requests.
 */
@Composable
fun HistoryStatusContent(content: HistoryContent, empty: Boolean) {
    HistoryStatusContent(content, empty, ConversationStrings())
}

/**
 * Host-worded history feedback, with failure retaining readable rows. [strings] supplies every
 * label and action name; [empty] affects only Exhausted feedback. [content] enables an explicit
 * request only when the shared history guard permits it. Loading never starts a service here.
 */
@Composable
fun HistoryStatusContent(content: HistoryContent, empty: Boolean, strings: ConversationStrings) {
    Box(Modifier.fillMaxWidth().heightIn(min = 48.dp), contentAlignment = Alignment.CenterStart) {
        when (content.state.status) {
            HistoryStatus.Idle -> TextButton(enabled = content.canLoadEarlier, onClick = content.onLoadEarlier,
                modifier = Modifier.semantics { contentDescription = strings.loadEarlierDescription }) { Text(strings.loadEarlier) }
            HistoryStatus.Loading -> Text(strings.loading, Modifier.padding(12.dp))
            HistoryStatus.Error -> TextButton(enabled = content.canLoadEarlier, onClick = content.onLoadEarlier,
                modifier = Modifier.semantics { contentDescription = strings.historyErrorDescription }) { Text(strings.historyError) }
            HistoryStatus.Exhausted -> Text(if (empty) strings.empty else strings.exhausted, Modifier.padding(12.dp))
        }
    }
}
