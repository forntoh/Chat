package dev.chat.ui

enum class HistoryStatus { Idle, Loading, Error, Exhausted }

/**
 * Host-owned earlier-history state, independent of transport and persistence.
 * Increment [revision] after resolving each request, including an empty page or failure.
 * An empty page must become [HistoryStatus.Exhausted] unless the host has a progressing cursor.
 * The UI latches each revision before calling the host; the host must also deduplicate across
 * unmount/remount and reject stale results before publishing a conversation snapshot.
 * Defaults disable history for existing consumers. With no messages, Loading, Error and
 * Exhausted also represent initial loading, initial failure and empty conversation respectively.
 * Never reuse a revision when a room is revisited; invalidate outstanding work on navigation.
 *
 * @param status Current availability; Error never automatically retries and retains existing rows.
 * @param revision Host request generation, changed on resolution or cancellation; defaults to zero.
 */
data class HistoryState(val status: HistoryStatus = HistoryStatus.Exhausted, val revision: Long = 0)

/**
 * Request for one earlier page. Route by [conversationId], validate [revision] against the
 * host's history state, and publish Loading synchronously before starting asynchronous work.
 * Apply a result only if conversation and request revision still match the host session.
 * Merge overlapping IDs before constructing ConversationState: retain one row in host order
 * and choose the latest authoritative content. The UI never sorts or merges host snapshots.
 *
 * @param conversationId Host room identity, scoped to the currently rendered conversation.
 * @param revision Generation to validate before starting work and again before publishing results.
 */
data class HistoryRequest(val conversationId: String, val revision: Long)

/**
 * History slot input. Render [state] and invoke [onLoadEarlier] only on explicit activation.
 * Idle requests also occur automatically at the earlier boundary; Error retries are explicit.
 * [canLoadEarlier] becomes false synchronously on activation and during Loading/Exhausted.
 * Custom slots own wording and semantics, including initial loading, empty and error feedback.
 */
class HistoryContent(val state: HistoryState, val canLoadEarlier: Boolean, val onLoadEarlier: () -> Unit)

/**
 * Return-to-latest slot input. [hasNewMessages] marks appended messages while reading earlier
 * rows. Invoke [onReturnToLatest] deliberately; it scrolls to the latest row and clears the
 * indicator. Custom slots own their labels and semantics.
 */
class LatestContent(val hasNewMessages: Boolean, val onReturnToLatest: () -> Unit)
