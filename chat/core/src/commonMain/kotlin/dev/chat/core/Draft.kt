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
 * Supply immutable [attachments]; advance [revision] on selection/removal/replacement so old results
 * cannot target newer content. Pending submissions lock attachment mutation as well as text.
 * @param attachments Presentation-neutral host content; empty by default for text-only callers.
 * @param text Exact composer content; defaults to empty and is never trimmed by the library.
 * @param revision Host submission version, initially zero; advance it to resolve a send.
 * @param submitting Whether the host is resolving a submission; defaults to false and locks input when true.
 */
data class Draft(
    val text: String = "",
    val revision: Long = 0,
    val submitting: Boolean = false,
    val attachments: List<Attachment> = emptyList()
) {
    val hasContent: Boolean get() = text.isNotBlank() || attachments.any { it.mediaType.startsWith("image/") && !it.contentReference.isNullOrBlank() }
    init { require(attachments.map { it.id }.distinct().size == attachments.size) { "Attachment IDs must be unique within a draft" } }
}