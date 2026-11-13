package com.forntoh.chat.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.forntoh.chat.core.*

data class RetryContent(val message: Message, val onRetry: () -> Unit)

data class HistoryFeedbackContent(val history: HistoryContent, val empty: Boolean)

data class MessageGroupContext(val isFirst: Boolean = true, val isLast: Boolean = true)

/** Row-local presentation context, also available to explicit row overrides and wrappers.
 * Standalone renderers default to a complete one-message group. */
val LocalMessageGroupContext = staticCompositionLocalOf { MessageGroupContext() }

/**
 * Immutable slot value configuration for conversation UI rendering. Explicit copy, equality,
 * hash and component functions retain the original data-class source behavior. Components 1–13
 * keep their original order (latestLabel is 13); avatar and spacing are components 14 and 15.
 * Constructor/copy place latestLabel last so trailing lambdas keep their original meaning.
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
 * @property messageAvatar Optional host avatar presentation; never fetches implicitly.
 * @property messageSpacing Gap before each row except the first loaded row; neutral default is 12dp.
 */
class ConversationSlots(
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
    val messageAvatar: @Composable (Message) -> Unit = {},
    val messageSpacing: (MessageGroupContext) -> Dp = { 12.dp },
    val latestLabel: @Composable (LatestContent) -> Unit = { BasicText(if (it.hasNewMessages) "New messages · Latest" else "Latest") },
) {

    /** Retains the original thirteen-argument positional constructor. Optional avatar/spacing
     * precede latestLabel in the primary constructor so trailing lambdas still target the label. */
    constructor(
        message: @Composable (Message, Boolean, (() -> Unit)?) -> Unit,
        composer: @Composable (ComposerContent) -> Unit,
        history: @Composable (HistoryFeedbackContent) -> Unit,
        latest: @Composable (LatestContent) -> Unit,
        messageMetadata: @Composable (Message) -> Unit,
        messageBody: @Composable (Message) -> Unit,
        messageDelivery: @Composable (Delivery) -> Unit,
        messageDescription: (Message) -> String,
        retry: @Composable (RetryContent) -> Unit,
        composerField: @Composable (ComposerContent) -> Unit,
        composerSend: @Composable (ComposerContent) -> Unit,
        historyFeedback: @Composable (HistoryFeedbackContent) -> Unit,
        latestLabel: @Composable (LatestContent) -> Unit,
    ) : this(
        message, composer, history, latest, messageMetadata, messageBody, messageDelivery,
        messageDescription, retry, composerField, composerSend, historyFeedback,
        messageAvatar = {}, messageSpacing = { 12.dp }, latestLabel = latestLabel,
    )

    /** Original thirteen-argument positional copy; retains this bundle's avatar/spacing overrides. */
    fun copy(
        message: @Composable (Message, Boolean, (() -> Unit)?) -> Unit,
        composer: @Composable (ComposerContent) -> Unit,
        history: @Composable (HistoryFeedbackContent) -> Unit,
        latest: @Composable (LatestContent) -> Unit,
        messageMetadata: @Composable (Message) -> Unit,
        messageBody: @Composable (Message) -> Unit,
        messageDelivery: @Composable (Delivery) -> Unit,
        messageDescription: (Message) -> String,
        retry: @Composable (RetryContent) -> Unit,
        composerField: @Composable (ComposerContent) -> Unit,
        composerSend: @Composable (ComposerContent) -> Unit,
        historyFeedback: @Composable (HistoryFeedbackContent) -> Unit,
        latestLabel: @Composable (LatestContent) -> Unit,
    ): ConversationSlots = copy(
        message, composer, history, latest, messageMetadata, messageBody, messageDelivery,
        messageDescription, retry, composerField, composerSend, historyFeedback,
        messageAvatar = messageAvatar, messageSpacing = messageSpacing, latestLabel = latestLabel,
    )
    /** Copies this immutable bundle; trailing lambdas customize latestLabel. */
    fun copy(
        message: @Composable (Message, Boolean, (() -> Unit)?) -> Unit = this.message,
        composer: @Composable (ComposerContent) -> Unit = this.composer,
        history: @Composable (HistoryFeedbackContent) -> Unit = this.history,
        latest: @Composable (LatestContent) -> Unit = this.latest,
        messageMetadata: @Composable (Message) -> Unit = this.messageMetadata,
        messageBody: @Composable (Message) -> Unit = this.messageBody,
        messageDelivery: @Composable (Delivery) -> Unit = this.messageDelivery,
        messageDescription: (Message) -> String = this.messageDescription,
        retry: @Composable (RetryContent) -> Unit = this.retry,
        composerField: @Composable (ComposerContent) -> Unit = this.composerField,
        composerSend: @Composable (ComposerContent) -> Unit = this.composerSend,
        historyFeedback: @Composable (HistoryFeedbackContent) -> Unit = this.historyFeedback,
        messageAvatar: @Composable (Message) -> Unit = this.messageAvatar,
        messageSpacing: (MessageGroupContext) -> Dp = this.messageSpacing,
        latestLabel: @Composable (LatestContent) -> Unit = this.latestLabel,
    ): ConversationSlots = ConversationSlots(
        message, composer, history, latest, messageMetadata, messageBody, messageDelivery,
        messageDescription, retry, composerField, composerSend, historyFeedback, messageAvatar, messageSpacing, latestLabel,
    )

    // Keep the original data-class component order independently of constructor defaults.
    operator fun component1(): @Composable (Message, Boolean, (() -> Unit)?) -> Unit = message
    operator fun component2(): @Composable (ComposerContent) -> Unit = composer
    operator fun component3(): @Composable (HistoryFeedbackContent) -> Unit = history
    operator fun component4(): @Composable (LatestContent) -> Unit = latest
    operator fun component5(): @Composable (Message) -> Unit = messageMetadata
    operator fun component6(): @Composable (Message) -> Unit = messageBody
    operator fun component7(): @Composable (Delivery) -> Unit = messageDelivery
    operator fun component8(): (Message) -> String = messageDescription
    operator fun component9(): @Composable (RetryContent) -> Unit = retry
    operator fun component10(): @Composable (ComposerContent) -> Unit = composerField
    operator fun component11(): @Composable (ComposerContent) -> Unit = composerSend
    operator fun component12(): @Composable (HistoryFeedbackContent) -> Unit = historyFeedback
    operator fun component13(): @Composable (LatestContent) -> Unit = latestLabel
    operator fun component14(): @Composable (Message) -> Unit = messageAvatar
    operator fun component15(): (MessageGroupContext) -> Dp = messageSpacing

    override fun equals(other: Any?): Boolean = this === other || (other is ConversationSlots &&
        message == other.message &&
        composer == other.composer &&
        history == other.history &&
        latest == other.latest &&
        messageMetadata == other.messageMetadata &&
        messageBody == other.messageBody &&
        messageDelivery == other.messageDelivery &&
        messageDescription == other.messageDescription &&
        retry == other.retry &&
        composerField == other.composerField &&
        composerSend == other.composerSend &&
        historyFeedback == other.historyFeedback &&
        latestLabel == other.latestLabel &&
        messageAvatar == other.messageAvatar &&
        messageSpacing == other.messageSpacing)

    override fun hashCode(): Int {
        var result = message.hashCode()
        result = 31 * result + composer.hashCode()
        result = 31 * result + history.hashCode()
        result = 31 * result + latest.hashCode()
        result = 31 * result + messageMetadata.hashCode()
        result = 31 * result + messageBody.hashCode()
        result = 31 * result + messageDelivery.hashCode()
        result = 31 * result + messageDescription.hashCode()
        result = 31 * result + retry.hashCode()
        result = 31 * result + composerField.hashCode()
        result = 31 * result + composerSend.hashCode()
        result = 31 * result + historyFeedback.hashCode()
        result = 31 * result + latestLabel.hashCode()
        result = 31 * result + messageAvatar.hashCode()
        result = 31 * result + messageSpacing.hashCode()
        return result
    }

    override fun toString(): String = "ConversationSlots(message=$message, composer=$composer, history=$history, latest=$latest, messageMetadata=$messageMetadata, messageBody=$messageBody, messageDelivery=$messageDelivery, messageDescription=$messageDescription, retry=$retry, composerField=$composerField, composerSend=$composerSend, historyFeedback=$historyFeedback, latestLabel=$latestLabel, messageAvatar=$messageAvatar, messageSpacing=$messageSpacing)"

}

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
