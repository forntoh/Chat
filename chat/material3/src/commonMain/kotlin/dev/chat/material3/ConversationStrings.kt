package dev.chat.material3

import androidx.compose.runtime.Immutable
import dev.chat.core.Delivery

/**
 * Host wording for every library-owned text/accessible name in optional `chat-material3`.
 * Defaults are English. Supply localized values from the host's resource system; this value
 * performs no resource loading or locale detection. Sender, body and formatted time remain
 * host message data. Custom slots own their wording and semantics independently.
 *
 * Control descriptions default to their visible label at construction. When using [copy] to
 * change a label, also change its description if appropriate. Noninteractive feedback uses
 * the same string visually and in text semantics. Supply meaningful, nonblank action names.
 * The default row joins sender, content, time and [deliveryLabel] with commas; replace the row
 * slot if another reading structure is needed. Changing this value does not accept a draft.
 */
@Immutable
data class ConversationStrings(
    val message: String = "Message",
    val messageDescription: String = message,
    val send: String = "Send",
    val sendDescription: String = send,
    val retry: String = "Retry",
    val retryDescription: String = retry,
    val sending: String = "Sending",
    val sent: String = "Sent",
    val failed: String = "Failed",
    val loadEarlier: String = "Load earlier",
    val loadEarlierDescription: String = loadEarlier,
    val loading: String = "Loading messages…",
    val historyError: String = "Couldn't load messages · Retry",
    val historyErrorDescription: String = historyError,
    val empty: String = "No messages yet",
    val exhausted: String = "Start of conversation",
    val latest: String = "Latest",
    val latestDescription: String = latest,
    val newMessages: String = "New messages · Latest",
    val newMessagesDescription: String = newMessages,
) {
    /** Localized feedback for authoritative host [delivery]; never infers service success. */
    fun deliveryLabel(delivery: Delivery): String = when (delivery) {
        Delivery.Sending -> sending
        Delivery.Sent -> sent
        Delivery.Failed -> failed
    }
}
