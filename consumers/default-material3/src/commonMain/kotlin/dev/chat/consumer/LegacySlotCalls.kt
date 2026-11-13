package dev.chat.consumer

import androidx.compose.foundation.text.BasicText
import dev.chat.ui.ConversationSlots

/** Independent source compatibility checks against staged artifacts, including lambda inference. */
fun legacySlotCalls(base: ConversationSlots): List<ConversationSlots> = listOf(
    ConversationSlots { BasicText(if (it.hasNewMessages) "Arrivals" else "Latest") },
    base.copy { BasicText(if (it.hasNewMessages) "New messages" else "Newest") },
    ConversationSlots(messageBody = { BasicText(it.text) }) { BasicText("Latest") },
    base.copy(messageBody = { BasicText(it.text) }) { BasicText("Newest") },
    // Original thirteen positional parameters, with the last lambda inside parentheses.
    ConversationSlots(base.message, base.composer, base.history, base.latest,
        base.messageMetadata, base.messageBody, base.messageDelivery, base.messageDescription,
        base.retry, base.composerField, base.composerSend, base.historyFeedback, base.latestLabel),
    base.copy(base.message, base.composer, base.history, base.latest,
        base.messageMetadata, base.messageBody, base.messageDelivery, base.messageDescription,
        base.retry, base.composerField, base.composerSend, base.historyFeedback, base.latestLabel),
    // A trailing latestLabel following all other original positional parameters.
    ConversationSlots(base.message, base.composer, base.history, base.latest,
        base.messageMetadata, base.messageBody, base.messageDelivery, base.messageDescription,
        base.retry, base.composerField, base.composerSend, base.historyFeedback) { BasicText("Latest") },
    base.copy(base.message, base.composer, base.history, base.latest,
        base.messageMetadata, base.messageBody, base.messageDelivery, base.messageDescription,
        base.retry, base.composerField, base.composerSend, base.historyFeedback) { BasicText("Newest") },
)
