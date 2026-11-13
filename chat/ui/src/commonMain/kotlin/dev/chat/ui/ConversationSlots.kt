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

/** Retry for the rendered host row. The host validates identity/eligibility and deduplicates
 * repeated activation; Conversation supplies this only for an outgoing failed message. */
data class RetryContent(val message: Message, val onRetry: () -> Unit)

/** Guarded host history state plus authoritative timeline emptiness. Renderers request pages
 * only on explicit activation; no slot owns transport or fetches during composition. */
data class HistoryFeedbackContent(val history: HistoryContent, val empty: Boolean)

/**
 * Presentation-neutral conversation renderers with usable English foundation defaults. The
 * optional Material3 module supplies a Jetchat theme by replacing these slots, without another
 * Conversation component. Hosts author wording directly in content; no label DTO is required.
 *
 * Direct per-call renderers win over the effective bundle. An explicit bundle replaces ambient
 * slots; derive LocalConversationSlots.current.copy(...) for a partial override. Otherwise the
 * nearest ConversationTheme applies, then these neutral defaults. Standalone defaults read the
 * effective subslots without recursively calling their own top-level renderer.
 *
 * Custom content owns semantics/focus and must honor pending/canSend/canLoadEarlier. Use guarded
 * callbacks only on activation; never retain a second draft or dispatch while composing.
 * Localize both messageDelivery and messageDescription together to keep visible/accessible
 * delivery wording aligned. The description announces sender, content, host time and delivery
 * once; retry remains independently actionable. Themes own no host or behavior state.
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

/** Effective shared presentation; no optional-feature or Material3 dependency is introduced. */
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

/** Neutral row default, separately callable without recursively invoking the message slot.
 * Standalone callers validate retry eligibility; subslots own wording and presentation. */
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

/** Neutral composer, using the host draft and guarded callbacks. A standalone caller supplies
 * equivalent guards. Custom field/send slots must honor pending/canSend and provide semantics. */
@Composable
fun DefaultComposer(content: ComposerContent) {
    val slots = LocalConversationSlots.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f)) { slots.composerField(content) }
        slots.composerSend(content)
    }
}

/** Delegatable neutral history container; the effective historyFeedback subslot owns copy. */
@Composable
fun DefaultHistoryFeedback(content: HistoryFeedbackContent) {
    LocalConversationSlots.current.historyFeedback(content)
}

@Composable
private fun NeutralControl(onClick: () -> Unit, enabled: Boolean = true, content: @Composable () -> Unit) {
    Box(Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(8.dp)) { content() }
}

@Composable
private fun NeutralHistoryFeedback(content: HistoryFeedbackContent) {
    val history = content.history
    when (history.state.status) {
        HistoryStatus.Idle -> NeutralControl(history.onLoadEarlier, history.canLoadEarlier) { BasicText("Load earlier") }
        HistoryStatus.Error -> NeutralControl(history.onLoadEarlier, history.canLoadEarlier) { BasicText("Couldn't load messages · Retry") }
        HistoryStatus.Loading -> BasicText("Loading messages…")
        HistoryStatus.Exhausted -> BasicText(if (content.empty) "No messages yet" else "Start of conversation")
    }
}
