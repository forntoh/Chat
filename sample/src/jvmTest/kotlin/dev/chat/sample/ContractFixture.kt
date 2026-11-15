package dev.chat.sample

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.forntoh.chat.actions.ActionCommand
import dev.forntoh.chat.actions.ActionRequest
import dev.forntoh.chat.actions.ActionState
import dev.forntoh.chat.actions.EditingMessage
import dev.forntoh.chat.actions.MessageAction
import dev.forntoh.chat.core.ConversationEvent
import dev.forntoh.chat.core.ConversationState
import dev.forntoh.chat.core.Delivery
import dev.forntoh.chat.core.Draft
import dev.forntoh.chat.core.Message
import dev.forntoh.chat.core.Participant
import dev.forntoh.chat.ui.HistoryRequest
import dev.forntoh.chat.ui.HistoryState
import dev.forntoh.chat.ui.HistoryStatus

/** Manual action/history snapshots for public contract checks; no scheduler or application routing. */
@Suppress("TooManyFunctions") // Test driver resolves independent public action/history requests.
class ContractFixture {
    var custom by mutableStateOf(false)
    var alternateTheme by mutableStateOf(false)
    var state by mutableStateOf(initialState("studio"))
        private set
    var pendingDraft by mutableStateOf<ConversationEvent.SendDraft?>(null)
        private set
    private var revoked by mutableStateOf<Map<String, Set<String>>>(emptyMap())
    private var actionState by mutableStateOf(ActionState())
    val actions: ActionState get() = actionState.copy(
        capabilities = state.messages.filterNot { it.id in revoked[state.conversationId].orEmpty() }.associate {
            it.id to
                (
                    setOf(MessageAction.Copy, MessageAction.Quote) +
                        if (it.sender.id == state.currentParticipantId) setOf(MessageAction.Edit, MessageAction.Delete) else emptySet()
                    )
        },
        pending = actionState.pending || state.draft.submitting,
    )
    var pendingAction by mutableStateOf<ActionRequest?>(null)
        private set
    var copiedText: String? = null
        private set
    var pendingQuoteTarget: String? = null
        private set
    private val roomPendingActions = mutableMapOf<String, ActionRequest?>()
    private val roomActions = mutableMapOf<String, Pair<ActionState, String?>>()

    /** Fake capability changes/removal are explicit host snapshots, never service authorization. */
    fun revokeActions(messageId: String) {
        revoked = revoked + (state.conversationId to (revoked[state.conversationId].orEmpty() + messageId))
        actionState = actionState.copy(
            revision = actionState.revision + 1,
            menuTargetId = actionState.menuTargetId.takeUnless { it == messageId },
        )
        // Invalidate the in-flight operation now so restoring capability cannot revive it.
        if (pendingAction?.targetId == messageId) completeAction(false)
    }
    fun allowActions(messageId: String) {
        revoked =
            revoked + (state.conversationId to (revoked[state.conversationId].orEmpty() - messageId))
        actionState =
            actionState.copy(revision = actionState.revision + 1)
    }
    fun removeMessage(messageId: String) {
        state = state.copy(messages = state.messages.filterNot { it.id == messageId })
        actionState = actionState.copy(
            revision = actionState.revision + 1,
            menuTargetId = actionState.menuTargetId.takeUnless { it == messageId },
        )
    }

    /** Validate the current host snapshot again; copy is an explicit platform callback. */
    fun onAction(request: ActionRequest, copyText: (String) -> Unit = {}) {
        if (request.conversationId != state.conversationId || request.revision != actions.revision || actions.pending) return
        val target = state.messages.find { it.id == request.targetId }
        if (request.command in setOf(ActionCommand.OpenMenu, ActionCommand.Quote, ActionCommand.BeginEdit, ActionCommand.RequestDelete) &&
            (actionState.editing != null || actionState.deleting != null)
        ) {
            return
        }
        if (!applyAction(request, target, copyText)) return
        actionState = actionState.copy(revision = actionState.revision + 1)
    }

    private fun applyAction(request: ActionRequest, target: Message?, copyText: (String) -> Unit): Boolean = when (request.command) {
        ActionCommand.CloseMenu, ActionCommand.CancelEdit, ActionCommand.CancelDelete, ActionCommand.CancelQuote -> {
            dismissAction(request.command)
            true
        }

        ActionCommand.OpenMenu -> handleOpenMenu(target)

        ActionCommand.Copy -> handleCopy(request, target, copyText)

        ActionCommand.Quote -> handleQuote(target)

        ActionCommand.BeginEdit -> handleBeginEdit(target)

        ActionCommand.RequestDelete -> handleRequestDelete(target)

        ActionCommand.ConfirmDelete -> handleConfirmDelete(request, target)

        ActionCommand.SubmitEdit -> handleSubmitEdit(request, target)

        ActionCommand.SendQuote -> handleSendQuote(request)
    }

    private fun dismissAction(command: ActionCommand) {
        when (command) {
            ActionCommand.CloseMenu -> actionState = actionState.copy(menuTargetId = null)
            ActionCommand.CancelEdit -> restoreComposer()
            ActionCommand.CancelDelete -> actionState = actionState.copy(deleting = null, error = null)
            ActionCommand.CancelQuote -> actionState = actionState.copy(quoteTargetId = null)
            else -> error("Expected a dismissal command")
        }
    }

    private fun handleOpenMenu(target: Message?): Boolean {
        if (target == null || actions.capabilities[target.id].isNullOrEmpty()) return false
        actionState = actionState.copy(menuTargetId = target.id, error = null)
        return true
    }

    private fun handleCopy(request: ActionRequest, target: Message?, copyText: (String) -> Unit): Boolean {
        if (target == null || MessageAction.Copy !in actions.capabilities[target.id].orEmpty() ||
            request.text != target.text
        ) {
            return false
        }
        copiedText = target.text
        copyText(target.text)
        actionState = actionState.copy(menuTargetId = null)
        return true
    }

    private fun handleQuote(target: Message?): Boolean {
        if (target == null || MessageAction.Quote !in actions.capabilities[target.id].orEmpty()) return false
        actionState = actionState.copy(menuTargetId = null, quoteTargetId = target.id, error = null)
        return true
    }

    private fun handleBeginEdit(target: Message?): Boolean {
        if (target == null || MessageAction.Edit !in actions.capabilities[target.id].orEmpty() ||
            actionState.editing != null
        ) {
            return false
        }
        actionState = actionState.copy(
            menuTargetId = null,
            quoteTargetId = null,
            error = null,
            editing = EditingMessage(target, state.draft, actionState.quoteTargetId),
        )
        state = state.copy(draft = Draft(target.text, state.draft.revision + 1))
        return true
    }

    private fun handleRequestDelete(target: Message?): Boolean {
        if (target == null || MessageAction.Delete !in actions.capabilities[target.id].orEmpty()) return false
        actionState = actionState.copy(menuTargetId = null, deleting = target, error = null)
        return true
    }

    private fun handleConfirmDelete(request: ActionRequest, target: Message?): Boolean {
        if (target == null || target != actionState.deleting ||
            MessageAction.Delete !in actions.capabilities[target.id].orEmpty()
        ) {
            return false
        }
        pendingAction = request
        actionState = actionState.copy(pending = true, error = null)
        return true
    }

    private fun handleSubmitEdit(request: ActionRequest, target: Message?): Boolean {
        if (target == null || target != actionState.editing?.original ||
            MessageAction.Edit !in actions.capabilities[target.id].orEmpty()
        ) {
            return false
        }
        if (request.text != state.draft.text || request.text.isNullOrBlank() || request.draftRevision != state.draft.revision) {
            return false
        }
        pendingAction = request
        actionState = actionState.copy(pending = true, error = null)
        state = state.copy(draft = state.draft.copy(submitting = true))
        return true
    }

    private fun handleSendQuote(request: ActionRequest): Boolean {
        if (request.targetId != actionState.quoteTargetId || !state.draft.hasContent) return false
        if (request.text != state.draft.text || request.draftRevision != state.draft.revision ||
            request.attachments != state.draft.attachments
        ) {
            return false
        }
        onEvent(ConversationEvent.SendDraft(state.conversationId, state.draft.revision, state.draft.text, request.attachments))
        pendingQuoteTarget = request.targetId
        return true
    }

    private fun restoreComposer() {
        val edit = actionState.editing ?: return
        state = state.copy(draft = edit.retainedDraft.copy(revision = state.draft.revision + 1, submitting = false))
        actionState = actionState.copy(editing = null, quoteTargetId = edit.retainedQuoteTargetId, error = null)
    }

    /** A manual fake service result: only this authoritative snapshot changes the row. */
    fun completeAction(success: Boolean) {
        val request = pendingAction ?: return
        if (request.conversationId != state.conversationId) return
        val target = state.messages.find { it.id == request.targetId }
        val deleting = request.command == ActionCommand.ConfirmDelete
        val valid = target != null && if (deleting) {
            target == actionState.deleting &&
                MessageAction.Delete in actions.capabilities[target.id].orEmpty()
        } else {
            target == actionState.editing?.original && MessageAction.Edit in actions.capabilities[target.id].orEmpty()
        }
        if (success && valid && deleting) {
            removeMessage(request.targetId!!)
            actionState = actionState.copy(deleting = null, error = null)
        } else if (success && valid) {
            state = state.copy(messages = state.messages.map { if (it.id == target.id) it.copy(text = request.text!!) else it })
            restoreComposer()
        } else {
            state = state.copy(draft = state.draft.copy(submitting = false, revision = state.draft.revision + 1))
            actionState = actionState.copy(error = "Couldn't change message. Try again or cancel.")
        }
        pendingAction = null
        actionState = actionState.copy(pending = false, revision = actionState.revision + 1)
    }

    private var nextMessage = 1
    var history by mutableStateOf(HistoryState())
        private set
    var pendingHistory by mutableStateOf<HistoryRequest?>(null)
        private set
    private val rooms = mutableMapOf<String, Pair<ConversationState, HistoryState>>()
    private val roomSubmissions = mutableMapOf<String, ConversationEvent.SendDraft?>()

    /** Start a reproducible 1,000-row source with its latest 50 rows loaded. */
    fun useHistoryFixture() {
        if (pendingAction != null || actionState.editing != null || actionState.deleting != null) return
        if (pendingDraft != null) state = state.copy(draft = state.draft.copy(submitting = false, revision = state.draft.revision + 1))
        pendingDraft = null
        pendingHistory = null
        state = state.copy(messages = (950..999).map(::historyMessage))
        history = HistoryState(HistoryStatus.Idle, history.revision + 1)
    }

    /** Synchronously latch one request; a real host would now start its service call. */
    fun loadEarlier(request: HistoryRequest) {
        if (request.conversationId != state.conversationId || request.revision != history.revision) return
        if (pendingHistory != null || history.status !in listOf(HistoryStatus.Idle, HistoryStatus.Error)) {
            return
        }
        pendingHistory = request
        history = history.copy(status = HistoryStatus.Loading)
    }

    fun failHistory() {
        if (pendingHistory == null) return
        pendingHistory = null
        history = HistoryState(HistoryStatus.Error, history.revision + 1)
    }

    /**
     * Manually resolve a page, ignoring old room/session requests. The last row overlaps
     * deliberately: new page content wins by identity, and host numeric order is preserved.
     */
    fun completeHistory(request: HistoryRequest? = pendingHistory) {
        if (request == null || request != pendingHistory) return
        if (request.conversationId != state.conversationId || request.revision != history.revision) {
            return
        }
        val first = state.messages.firstOrNull()?.id?.substringAfter("history-")?.toIntOrNull() ?: 1000
        val start = (first - 50).coerceAtLeast(0)
        val page = (start..first.coerceAtMost(999)).map {
            historyMessage(it).let { row ->
                if (it == first) row.copy(text = "Message $it · refreshed") else row
            }
        }
        val latest = page.associateBy { it.id }
        state = state.copy(messages = page + state.messages.filterNot { it.id in latest })
        pendingHistory = null
        history = HistoryState(if (start == 0) HistoryStatus.Exhausted else HistoryStatus.Idle, history.revision + 1)
    }

    /** Switching invalidates in-flight work even when returning to the same room later. */
    fun switchRoom() {
        pendingHistory = null
        history = HistoryState(if (history.status == HistoryStatus.Loading) HistoryStatus.Idle else history.status, history.revision + 1)
        actionState = actionState.copy(revision = actionState.revision + 1, menuTargetId = null)
        roomPendingActions[state.conversationId] = pendingAction
        roomActions[state.conversationId] = actionState to pendingQuoteTarget
        rooms[state.conversationId] = state to history
        roomSubmissions[state.conversationId] = pendingDraft
        val target = if (state.conversationId == "studio") "other" else "studio"
        val saved = rooms[target]
        state = saved?.first ?: initialState(target)
        history = saved?.second ?: HistoryState()
        pendingDraft = roomSubmissions[target]
        val restoredActions = roomActions[target]?.first ?: ActionState()
        actionState = restoredActions.copy(revision = restoredActions.revision + 1, menuTargetId = null)
        pendingQuoteTarget = roomActions[target]?.second
        pendingAction = roomPendingActions[target]
    }

    fun receiveMessage() {
        state = state.copy(
            messages = state.messages + Message(
                "arrival-${nextMessage++}",
                Participant("avery", "Avery"),
                2_000L + nextMessage,
                "09:43",
                "A new message arrived",
                Delivery.Sent,
            ),
        )
    }

    fun onEvent(event: ConversationEvent) {
        if (event.conversationId != state.conversationId || pendingAction != null) return
        when (event) {
            is ConversationEvent.DraftChanged -> if (pendingDraft == null) {
                state = state.copy(draft = state.draft.copy(text = event.text))
            }

            is ConversationEvent.SendDraft -> handleSendDraft(event)

            is ConversationEvent.RetryMessage -> handleRetryMessage(event)
        }
    }

    private fun handleSendDraft(event: ConversationEvent.SendDraft) {
        if (pendingDraft != null || !state.draft.hasContent) return
        if (event.revision != state.draft.revision || event.text != state.draft.text ||
            event.attachments != state.draft.attachments
        ) {
            return
        }
        pendingDraft = event
        state = state.copy(draft = state.draft.copy(submitting = true))
    }

    private fun handleRetryMessage(event: ConversationEvent.RetryMessage) {
        val eligible = state.messages.any {
            it.id == event.messageId &&
                it.sender.id == state.currentParticipantId && it.delivery == Delivery.Failed
        }
        if (!eligible) return
        state = state.copy(
            messages = state.messages.map {
                if (it.id == event.messageId && it.sender.id == state.currentParticipantId && it.delivery == Delivery.Failed) {
                    it.copy(delivery = Delivery.Sending)
                } else {
                    it
                }
            },
        )
    }

    fun accept(expected: ConversationEvent.SendDraft? = pendingDraft) {
        val pending = pendingDraft ?: return
        if (expected != pending || pending.revision != state.draft.revision) return
        val id = "${state.conversationId}-${nextMessage++}"
        state = state.copy(
            draft = Draft(revision = pending.revision + 1),
            messages =
            state.messages +
                Message(
                    id,
                    Participant("me", "You"),
                    1_000L + nextMessage,
                    "09:42",
                    pending.text,
                    Delivery.Sending,
                    pending.attachments,
                ),
        )
        actionState = actionState.copy(
            revision = actionState.revision + 1,
            quoteTargetId = null,
            quotes = pendingQuoteTarget?.let { actionState.quotes + (id to it) } ?: actionState.quotes,
        )
        pendingQuoteTarget = null
        pendingDraft = null
    }

    fun completeDelivery(delivery: Delivery, messageId: String? = null) {
        require(delivery != Delivery.Sending)
        state = state.copy(
            messages = state.messages.map {
                val matchesId = messageId == null || it.id == messageId
                if (matchesId && it.sender.id == state.currentParticipantId && it.delivery == Delivery.Sending) {
                    it.copy(delivery = delivery)
                } else {
                    it
                }
            },
        )
    }

    fun refuse() {
        val pending = pendingDraft ?: return
        state = state.copy(draft = Draft(pending.text, pending.revision + 1, attachments = pending.attachments))
        pendingDraft = null
        pendingQuoteTarget = null
        actionState = actionState.copy(revision = actionState.revision + 1)
    }

    fun useChatFixture() {
        state = state.copy(
            messages = listOf(
                Message(
                    "welcome",
                    Participant("avery", "Avery Chen"),
                    1_000,
                    "09:41",
                    "Morning! I’ve added the latest sketches for the shared space.",
                    Delivery.Sent,
                ),
                Message(
                    "sketches",
                    Participant("avery", "Avery Chen"),
                    1_001,
                    "09:41",
                    "The quieter corner by the window could work well for reading.",
                    Delivery.Sent,
                ),
                Message(
                    "response",
                    Participant("me", "You"),
                    1_002,
                    "09:42",
                    "Love that idea. Let’s keep the materials warm and simple.",
                    Delivery.Sent,
                ),
                Message(
                    "morgan",
                    Participant("morgan", "Morgan Lee"),
                    1_003,
                    "09:43",
                    "I’ll bring a few samples to our afternoon review ☀️",
                    Delivery.Sent,
                ),
                Message("morgan-2", Participant("morgan", "Morgan Lee"), 1_004, "09:43", "Does 2:30 work for everyone?", Delivery.Sent),
                Message("confirmed", Participant("me", "You"), 1_005, "09:44", "Perfect — see you both there!", Delivery.Sent),
            ),
        )
        actionState = actionState.copy(quotes = mapOf("response" to "sketches", "confirmed" to "morgan-2"))
        history = HistoryState(HistoryStatus.Exhausted, history.revision + 1)
    }

    companion object {
        private fun historyMessage(n: Int) = Message(
            id = "history-$n",
            sender = Participant("avery", "Avery"),
            timestampEpochMillis = n.toLong(),
            displayTime = "09:41",
            text = "Message $n",
            delivery = Delivery.Sent,
        )
        private fun initialState(id: String) = ConversationState(
            conversationId = id,
            currentParticipantId = "me",
            messages = listOf(
                Message(
                    id = "welcome",
                    sender = Participant("avery", "Avery"),
                    timestampEpochMillis = 1_000,
                    displayTime = "09:41",
                    text = "Welcome! Send a message to start a simulated exchange.",
                    delivery = Delivery.Sent,
                ),
            ),
        )
    }
}
