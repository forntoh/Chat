package dev.forntoh.chat.core

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
     * Requests one explicit submission of exact [text] and immutable [attachments] at [revision]. Empty content is suppressed
     * by the UI. While mounted, repeat sends are suppressed until the host changes revision.
     * Publish a new draft revision to accept/clear or refuse/retain; delivery remains separate.
     * Hosts must deduplicate submissions across unmount/remount and asynchronous boundaries.
     *
     * @param conversationId Conversation whose rendered draft was submitted; reject if stale.
     * @param revision Submitted draft version; validate against the current retained draft.
     * @param attachments Exact content snapshot; validate against the retained draft alongside text/revision.
     * @param text Exact submitted content; validate together with the revision before accepting.
     */
    data class SendDraft(
        override val conversationId: String,
        val revision: Long,
        val text: String,
        val attachments: List<Attachment> = emptyList()
    ) : ConversationEvent
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