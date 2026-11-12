package dev.chat.core

/**
 * Host-owned composer snapshot. Text is forwarded verbatim, including whitespace and newlines.
 *
 * Increment [revision] after accepting or refusing a send, even if [text] stays unchanged.
 * Never reuse a resolved revision within a retained conversation. Acceptance normally clears
 * text; refusal retains it. Both are distinct from eventual [Message.delivery].
 *
 * [submitting] locks editing and sending while the host resolves a submission. The UI also
 * latches a send locally until the revision changes or that composition leaves the tree.
 * Retain this snapshot outside the conversation UI to preserve drafts across navigation;
 * neither this model nor the UI provides persistence after process death.
 *
 * @param text Exact composer content; defaults to empty and is never trimmed by the library.
 * @param revision Host submission version, initially zero; advance it to resolve a send.
 * @param submitting Whether the host is resolving a submission; defaults to false and locks input when true.
 */
data class Draft(val text: String = "", val revision: Long = 0, val submitting: Boolean = false)

/**
 * Host-supplied sender identity. Keep [id] stable; [displayName] is presentation text.
 *
 * @param id Stable host participant key, compared with [ConversationState.currentParticipantId].
 * @param displayName Host-provided readable sender label; need not be unique.
 */
data class Participant(val id: String, val displayName: String)

/** Host-reported delivery outcome; draft acceptance alone does not imply [Delivery.Sent]. */
enum class Delivery {
    /** Accepted locally and awaiting a host-reported outcome. */
    Sending,
    /** The host reports successful delivery under its own service contract. */
    Sent,
    /** The host reports failure; the current participant may explicitly request retry. */
    Failed
}

/**
 * A host-owned timeline row. Preserve [id] through optimistic insertion, delivery changes and
 * retry; later snapshots replace the row's content without changing its logical identity.
 *
 * [timestampEpochMillis] is metadata, not a sorting instruction: list order comes from the host.
 * [displayTime] is already formatted/localized by the host. [text] renders as plain text.
 * [sender] identifies ownership by its ID, rather than by its display name.
 *
 * @param id Stable key unique within a conversation, retained across delivery updates and retries.
 * @param sender Host participant whose ID determines outgoing presentation and retry eligibility.
 * @param timestampEpochMillis Host timestamp in milliseconds since the Unix epoch; does not determine row order.
 * @param displayTime Host-formatted time label displayed without additional formatting.
 * @param text Exact plain-text message body; no rich-text parsing is performed.
 * @param delivery Authoritative host delivery state, independent of composer acceptance.
 */
data class Message(
    val id: String,
    val sender: Participant,
    val timestampEpochMillis: Long,
    val displayTime: String,
    val text: String,
    val delivery: Delivery,
)

/**
 * Authoritative conversation snapshot. Supply immutable lists and replace snapshots on updates.
 *
 * [conversationId] scopes drafts, events and row identities. [currentParticipantId] determines
 * outgoing alignment and retry eligibility; it is not an authorization check. The host must
 * enforce access control and reject stale asynchronous results before updating this state.
 * [messages] uses host order, including equal-timestamp tie breaks; the UI does not sort or
 * merge rows. IDs must be unique within this list, but may recur in another conversation.
 * Empty defaults show an empty timeline and a blank, enabled-for-editing composer.
 *
 * @param conversationId Stable host key scoping events, drafts and message identities.
 * @param currentParticipantId Host participant key used for outgoing presentation, not authorization.
 * @param draft Retained host composer state; defaults to an empty, editable draft at revision zero.
 * @param messages Immutable snapshot in host-defined display order; defaults to empty and requires unique IDs.
 * @throws IllegalArgumentException when a snapshot contains duplicate message IDs.
 */
data class ConversationState(
    /** Stable key used to route host snapshots and user intents. */
    val conversationId: String,
    val currentParticipantId: String,
    val draft: Draft = Draft(),
    val messages: List<Message> = emptyList(),
) {
    init { require(messages.map { it.id }.distinct().size == messages.size) { "Message IDs must be unique within a snapshot" } }
}

/**
 * User intent, emitted synchronously from UI callbacks, never from mounting or recomposition.
 * The host owns validation, state updates, transport, authorization and error handling.
 * Callbacks should return promptly; schedule asynchronous work in a host-owned scope.
 * Exceptions from the callback propagate to its caller; no library retry is scheduled.
 *
 * Route every event by [conversationId]. Discard events/results for a stale conversation;
 * validate send revision and text against the current draft before accepting work.
 */
sealed interface ConversationEvent {
    /** Conversation whose snapshot produced this intent. */
    val conversationId: String
    /**
     * Requests replacement composer text. Publish the new draft to display the edit.
     * This event carries no revision: hosts that queue edits must preserve their order and
     * discard callbacks from obsolete UI instances rather than applying them to newer drafts.
     *
     * @param conversationId Conversation whose rendered composer produced the edit.
     * @param text Exact replacement content, including whitespace and newlines.
     */
    data class DraftChanged(override val conversationId: String, val text: String) : ConversationEvent
    /**
     * Requests one explicit submission of exact [text] at [revision]. Blank text is suppressed
     * by the UI. While mounted, repeat sends are suppressed until the host changes revision.
     * Publish a new draft revision to accept/clear or refuse/retain; delivery remains separate.
     * Hosts must deduplicate submissions across unmount/remount and asynchronous boundaries.
     *
     * @param conversationId Conversation whose rendered draft was submitted; reject if stale.
     * @param revision Submitted draft version; validate against the current retained draft.
     * @param text Exact submitted content; validate together with the revision before accepting.
     */
    data class SendDraft(override val conversationId: String, val revision: Long, val text: String) : ConversationEvent
    /**
     * Requests retry of the existing [messageId], without creating a second timeline row.
     * Offered for the current participant's failed messages. Each activation can emit again
     * until the host updates the row; revalidate eligibility and deduplicate service requests.
     *
     * @param conversationId Conversation containing the failed row; reject if stale.
     * @param messageId Existing logical message key to retry, never a key for a new row.
     */
    data class RetryMessage(override val conversationId: String, val messageId: String) : ConversationEvent
}

