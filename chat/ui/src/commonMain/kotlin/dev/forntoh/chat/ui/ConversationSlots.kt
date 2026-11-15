package dev.forntoh.chat.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.toString
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.requestFocus
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.text
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.forntoh.chat.core.Delivery
import dev.forntoh.chat.core.Message

data class RetryContent(
    val message: Message,
    val onRetry: () -> Unit,
)

data class HistoryFeedbackContent(
    val history: HistoryContent,
    val empty: Boolean,
)

data class MessageGroupContext(
    val isFirst: Boolean = true,
    val isLast: Boolean = true,
)

/** Row-local presentation context, also available to explicit row overrides and wrappers.
 * Standalone renderers default to a complete one-message group. */
val LocalMessageGroupContext = staticCompositionLocalOf { MessageGroupContext() }

/**
 * Immutable renderers for a conversation, its composer and navigation feedback.
 * Derive a [copy] to replace selected renderers while retaining the surrounding theme defaults.
 * Slots own presentation and semantics; they do not own another draft or service state.
 *
 * [presentedMessage] takes precedence over [message] and receives transient highlight/navigation
 * context. An explicit `Conversation.messageContent` takes precedence over both. Custom bubbles
 * animate [MessagePresentation.isHighlighted] themselves. [messageAvatar] never fetches content;
 * [messageSpacing] defaults to 12dp between loaded rows.
 *
 * Existing copy, equality and component ordering are retained: [latestLabel] is component 13,
 * [messageAvatar] and [messageSpacing] are 14 and 15. The trailing constructor/copy lambda remains
 * [latestLabel] for source compatibility.
 */
@Suppress("LongParameterList", "TooManyFunctions")
class ConversationSlots(
    val message: @Composable (Message, Boolean, (() -> Unit)?) -> Unit = { row, _, retry ->
        DefaultMessage(row, retry)
    },
    val composer: @Composable (ComposerContent) -> Unit = { DefaultComposer(it) },
    val history: @Composable (HistoryFeedbackContent) -> Unit = { DefaultHistoryFeedback(it) },
    val latest: @Composable (LatestContent) -> Unit = {
        NeutralControl(it.onReturnToLatest) {
            LocalConversationSlots.current.latestLabel(it)
        }
    },
    val messageMetadata: @Composable (Message) -> Unit = { BasicText("${it.sender.displayName} · ${it.displayTime}") },
    val messageBody: @Composable (Message) -> Unit = { BasicText(it.text) },
    val messageDelivery: @Composable (Delivery) -> Unit = { BasicText(it.name) },
    val messageDescription: (Message) -> String = { "${it.sender.displayName}, ${it.text}, ${it.displayTime}, ${it.delivery.name}" },
    val retry: @Composable (RetryContent) -> Unit = { NeutralControl(it.onRetry) { BasicText("Retry") } },
    val composerField: @Composable (ComposerContent) -> Unit = {
        val requester = remember { FocusRequester() }
        LaunchedEffect(it.focusRequest) { if (it.focusRequest != 0L) requester.requestFocus() }
        BasicTextField(
            value = it.draft.text,
            onValueChange = { text -> if (!it.pending) it.onTextChange(text) },
            keyboardOptions = ChatComposerKeyboardOptions,
            modifier = Modifier.fillMaxWidth().focusRequester(requester).testTag("composer")
                .semantics { contentDescription = "Message" },
        )
    },
    val composerSend: @Composable (ComposerContent) -> Unit = {
        NeutralControl(
            it.onSend,
            it.canSend,
        ) { BasicText("Send") }
    },
    val historyFeedback: @Composable (HistoryFeedbackContent) -> Unit = { NeutralHistoryFeedback(it) },
    val messageAvatar: @Composable (Message) -> Unit = {},
    val messageSpacing: (MessageGroupContext) -> Dp = { 12.dp },
    val presentedMessage: (@Composable (Message, Boolean, (() -> Unit)?, MessagePresentation) -> Unit)? = null,
    val latestLabel: @Composable (LatestContent) -> Unit = { BasicText(if (it.hasNewMessages) "New messages · Latest" else "Latest") },
) {
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
        messageAvatar: @Composable (Message) -> Unit,
        messageSpacing: (MessageGroupContext) -> Dp,
        latestLabel: @Composable (LatestContent) -> Unit,
    ) : this(
        message, composer, history, latest, messageMetadata, messageBody, messageDelivery,
        messageDescription, retry, composerField, composerSend, historyFeedback, messageAvatar,
        messageSpacing, null, latestLabel,
    )

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
        messageAvatar: @Composable (Message) -> Unit,
        messageSpacing: (MessageGroupContext) -> Dp,
        latestLabel: @Composable (LatestContent) -> Unit,
    ): ConversationSlots = ConversationSlots(
        message, composer, history, latest, messageMetadata, messageBody, messageDelivery,
        messageDescription, retry, composerField, composerSend, historyFeedback, messageAvatar,
        messageSpacing, presentedMessage, latestLabel,
    )

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
        presentedMessage: (@Composable (Message, Boolean, (() -> Unit)?, MessagePresentation) -> Unit)? = this.presentedMessage,
        latestLabel: @Composable (LatestContent) -> Unit = this.latestLabel,
    ): ConversationSlots = ConversationSlots(
        message = message,
        composer = composer,
        history = history,
        latest = latest,
        messageMetadata = messageMetadata,
        messageBody = messageBody,
        messageDelivery = messageDelivery,
        messageDescription = messageDescription,
        retry = retry,
        composerField = composerField,
        composerSend = composerSend,
        historyFeedback = historyFeedback,
        messageAvatar = messageAvatar,
        messageSpacing = messageSpacing,
        presentedMessage = presentedMessage,
        latestLabel = latestLabel,
    )

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
    operator fun component16(): (@Composable (Message, Boolean, (() -> Unit)?, MessagePresentation) -> Unit)? = presentedMessage

    @Suppress("CyclomaticComplexMethod") // Field-wise equality matches the original data-class contract.
    override fun equals(other: Any?): Boolean = this === other || (
        other is ConversationSlots &&
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
            messageSpacing == other.messageSpacing && presentedMessage == other.presentedMessage
        )

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
        result = 31 * result + (presentedMessage?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String =
        "ConversationSlots(message=$message, composer=$composer, history=$history, latest=$latest, messageMetadata=$messageMetadata, messageBody=$messageBody, messageDelivery=$messageDelivery, messageDescription=$messageDescription, retry=$retry, composerField=$composerField, composerSend=$composerSend, historyFeedback=$historyFeedback, latestLabel=$latestLabel, messageAvatar=$messageAvatar, messageSpacing=$messageSpacing, presentedMessage=$presentedMessage)"
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
        Column(
            Modifier.clearAndSetSemantics { contentDescription = slots.messageDescription(message) },
        ) {
            slots.messageMetadata(message)
            slots.messageBody(message)
            slots.messageDelivery(message.delivery)
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
    val inputMode = androidx.compose.ui.platform.LocalInputModeManager.current
    Box(
        Modifier
            .focusProperties { canFocus = inputMode.inputMode == androidx.compose.ui.input.InputMode.Keyboard }
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
                enabled = history.canLoadEarlier,
            ) { BasicText("Load earlier") }
        }

        HistoryStatus.Error -> {
            NeutralControl(
                onClick = history.onLoadEarlier,
                enabled = history.canLoadEarlier,
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
