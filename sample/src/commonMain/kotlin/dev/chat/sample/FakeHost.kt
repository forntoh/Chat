package dev.chat.sample

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.chat.core.*
import dev.chat.ui.*

/**
 * In-memory sample adapter, called on the UI thread. Manual acceptance and delivery controls
 * demonstrate separate submission/delivery decisions. Retention lasts only as long as this
 * instance; there is no network, persistence or production authorization implementation.
 */
class FakeHost {
    var state by mutableStateOf(initialState("studio"))
        private set
    var pendingDraft by mutableStateOf<ConversationEvent.SendDraft?>(null)
        private set
    private var nextMessage = 1
    var history by mutableStateOf(HistoryState())
        private set
    var pendingHistory by mutableStateOf<HistoryRequest?>(null)
        private set
    private val rooms = mutableMapOf<String, Pair<ConversationState, HistoryState>>()
    private val roomSubmissions = mutableMapOf<String, ConversationEvent.SendDraft?>()

    /** Start a reproducible 1,000-row source with its latest 50 rows loaded. */
    fun useHistoryFixture() {
        if (pendingDraft != null) state = state.copy(draft = state.draft.copy(submitting = false, revision = state.draft.revision + 1))
        pendingDraft = null
        pendingHistory = null
        state = state.copy(messages = (950..999).map(::historyMessage))
        history = HistoryState(HistoryStatus.Idle, history.revision + 1)
    }

    /** Synchronously latch one request; a real host would now start its service call. */
    fun loadEarlier(request: HistoryRequest) {
        if (request.conversationId != state.conversationId || request.revision != history.revision ||
            pendingHistory != null || history.status !in listOf(HistoryStatus.Idle, HistoryStatus.Error)) return
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
        if (request == null || request != pendingHistory || request.conversationId != state.conversationId ||
            request.revision != history.revision) return
        val first = state.messages.firstOrNull()?.id?.substringAfter("history-")?.toIntOrNull() ?: 1000
        val start = (first - 50).coerceAtLeast(0)
        val page = (start..first.coerceAtMost(999)).map { historyMessage(it).let { row ->
            if (it == first) row.copy(text = "Message $it · refreshed") else row
        } }
        val latest = page.associateBy { it.id }
        state = state.copy(messages = page + state.messages.filterNot { it.id in latest })
        pendingHistory = null
        history = HistoryState(if (start == 0) HistoryStatus.Exhausted else HistoryStatus.Idle, history.revision + 1)
    }

    /** Switching invalidates in-flight work even when returning to the same room later. */
    fun switchRoom() {
        pendingHistory = null
        history = HistoryState(if (history.status == HistoryStatus.Loading) HistoryStatus.Idle else history.status, history.revision + 1)
        rooms[state.conversationId] = state to history
        roomSubmissions[state.conversationId] = pendingDraft
        val target = if (state.conversationId == "studio") "other" else "studio"
        val saved = rooms[target]
        state = saved?.first ?: initialState(target)
        history = saved?.second ?: HistoryState()
        pendingDraft = roomSubmissions[target]
    }

    fun receiveMessage() {
        state = state.copy(messages = state.messages + Message("arrival-${nextMessage++}", Participant("avery", "Avery"),
            2_000L + nextMessage, "09:43", "A new message arrived", Delivery.Sent))
    }

    fun onEvent(event: ConversationEvent) {
        if (event.conversationId != state.conversationId) return
        when (event) {
            is ConversationEvent.DraftChanged -> if (pendingDraft == null) {
                state = state.copy(draft = state.draft.copy(text = event.text))
            }
            is ConversationEvent.SendDraft -> {
                if (pendingDraft != null || event.revision != state.draft.revision ||
                    event.text != state.draft.text || event.text.isBlank()) return
                pendingDraft = event
                state = state.copy(draft = state.draft.copy(submitting = true))
            }
            is ConversationEvent.RetryMessage -> {
                state = state.copy(messages = state.messages.map {
                    if (it.id == event.messageId && it.sender.id == state.currentParticipantId && it.delivery == Delivery.Failed)
                        it.copy(delivery = Delivery.Sending) else it
                })
            }
        }
    }

    fun accept() {
        val pending = pendingDraft ?: return
        val id = "${state.conversationId}-${nextMessage++}"
        state = state.copy(
            draft = Draft(revision = pending.revision + 1),
            messages = state.messages + Message(id, Participant("me", "You"), 1_000L + nextMessage, "09:42", pending.text, Delivery.Sending),
        )
        pendingDraft = null
    }

    fun completeDelivery(delivery: Delivery) {
        require(delivery != Delivery.Sending)
        state = state.copy(messages = state.messages.map {
            if (it.sender.id == state.currentParticipantId && it.delivery == Delivery.Sending) it.copy(delivery = delivery) else it
        })
    }

    fun refuse() {
        val pending = pendingDraft ?: return
        state = state.copy(draft = Draft(pending.text, pending.revision + 1))
        pendingDraft = null
    }

    companion object {
        private fun historyMessage(n: Int) = Message("history-$n", Participant("avery", "Avery"),
            n.toLong(), "09:41", "Message $n", Delivery.Sent)
        private fun initialState(id: String) = ConversationState(id, "me", messages = listOf(
            Message("welcome", Participant("avery", "Avery"), 1_000, "09:41", "Welcome! Send a message, then choose its delivery outcome.", Delivery.Sent),
        ))
    }
}
