package dev.forntoh.chat.ui

import dev.forntoh.chat.core.Draft

/**
 * Composer slot input supplied by [Conversation]. Render [draft] without owning another copy.
 * [pending] includes both host submission state and the current composition's send latch.
 * [onTextChange] and [onSend] are guarded callbacks: edits are ignored while pending and sends
 * require draft content. Call them only for explicit user input, never during composition.
 * Callbacks capture the rendered snapshot; do not retain them after that snapshot is replaced.
 * Custom content owns its labels, focus behavior and accessibility semantics.
 *
 * @param draft Rendered host snapshot; display its text without a separate local draft copy.
 * @param pending Whether host submission or the composition's send latch blocks editing and sending.
 * @param onTextChange Requests exact replacement text on user input; ignored while pending.
 * @param onDraftAction Runs an explicit attachment mutation intent only while this exact draft is current and not pending.
 * Latches until a new draft revision, including cancellation/denial. Custom adapters must forward this guard.
 * @param onSend Requests submission of the rendered draft with content; ignored while pending.
 */
class ComposerContent(
    val draft: Draft,
    val pending: Boolean,
    val onTextChange: (String) -> Unit,
    val onSend: () -> Unit,
    val onDraftAction: (() -> Unit) -> Unit,
    /** Incremented on explicit Reply activation. Custom inputs request focus in an effect keyed
     * by this value when nonzero; ordinary draft updates and Send do not request focus. */
    val focusRequest: Long = 0,
) {
    constructor(
        draft: Draft,
        pending: Boolean,
        onTextChange: (String) -> Unit,
        onSend: () -> Unit,
        onDraftAction: (() -> Unit) -> Unit,
    ) : this(draft, pending, onTextChange, onSend, onDraftAction, 0)

    constructor(
        draft: Draft,
        pending: Boolean,
        onTextChange: (String) -> Unit,
        onSend: () -> Unit,
    ) : this(draft, pending, onTextChange, onSend, { action -> if (!pending) action() })

    val canSend: Boolean get() = !pending && draft.hasContent
}
