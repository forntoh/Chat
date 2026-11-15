package dev.chat.sample

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.forntoh.chat.core.*
import dev.forntoh.chat.ui.*
import dev.forntoh.chat.actions.*

/**
 * In-memory sample adapter, called on the UI thread. A seeded logical clock schedules
 * separate submission, delivery and incoming-reply snapshots; manual mode remains available. Retention lasts only as long as this
 * instance; there is no network, persistence or production authorization implementation.
 */
class FakeHost {
    var state by mutableStateOf(initialState("studio"))
        private set
    var pendingDraft by mutableStateOf<ConversationEvent.SendDraft?>(null)
        private set
    private var revoked by mutableStateOf<Map<String, Set<String>>>(emptyMap())
    private var actionState by mutableStateOf(ActionState())
    val actions: ActionState get() = actionState.copy(
        capabilities = state.messages.filterNot { it.id in revoked[state.conversationId].orEmpty() }.associate { it.id to (setOf(MessageAction.Copy, MessageAction.Quote) +
            if (it.sender.id == state.currentParticipantId) setOf(MessageAction.Edit, MessageAction.Delete) else emptySet()) },
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
    fun revokeActions(messageId: String) { revoked = revoked + (state.conversationId to (revoked[state.conversationId].orEmpty() + messageId)); actionState = actionState.copy(revision = actionState.revision + 1) }
    fun allowActions(messageId: String) { revoked = revoked + (state.conversationId to (revoked[state.conversationId].orEmpty() - messageId)); actionState = actionState.copy(revision = actionState.revision + 1) }
    fun removeMessage(messageId: String) { state = state.copy(messages = state.messages.filterNot { it.id == messageId }) }

    /** Validate the current host snapshot again; copy is an explicit platform callback. */
    fun onAction(request: ActionRequest, copyText: (String) -> Unit = {}) {
        if (request.conversationId != state.conversationId || request.revision != actions.revision || actions.pending) return
        val target = state.messages.find { it.id == request.targetId }
        if (request.command in setOf(ActionCommand.OpenMenu, ActionCommand.Quote, ActionCommand.BeginEdit, ActionCommand.RequestDelete) &&
            (actionState.editing != null || actionState.deleting != null)) return
        when (request.command) {
            ActionCommand.OpenMenu -> {
                if (target == null || actions.capabilities[target.id].isNullOrEmpty()) return
                actionState = actionState.copy(menuTargetId = target.id, error = null)
            }
            ActionCommand.CloseMenu -> actionState = actionState.copy(menuTargetId = null)
            ActionCommand.Copy -> {
                if (target == null || MessageAction.Copy !in actions.capabilities[target.id].orEmpty() || request.text != target.text) return
                copiedText = target.text
                copyText(target.text)
                actionState = actionState.copy(menuTargetId = null)
            }
            ActionCommand.Quote -> {
                if (target == null || MessageAction.Quote !in actions.capabilities[target.id].orEmpty()) return
                actionState = actionState.copy(menuTargetId = null, quoteTargetId = target.id, error = null)
            }
            ActionCommand.BeginEdit -> {
                if (target == null || MessageAction.Edit !in actions.capabilities[target.id].orEmpty() || actionState.editing != null) return
                actionState = actionState.copy(menuTargetId = null, quoteTargetId = null, error = null,
                    editing = EditingMessage(target, state.draft, actionState.quoteTargetId))
                state = state.copy(draft = Draft(target.text, state.draft.revision + 1))
            }
            ActionCommand.CancelEdit -> restoreComposer()
            ActionCommand.RequestDelete -> {
                if (target == null || MessageAction.Delete !in actions.capabilities[target.id].orEmpty()) return
                actionState = actionState.copy(menuTargetId = null, deleting = target, error = null)
            }
            ActionCommand.CancelDelete -> actionState = actionState.copy(deleting = null, error = null)
            ActionCommand.ConfirmDelete -> {
                if (target == null || target != actionState.deleting || MessageAction.Delete !in actions.capabilities[target.id].orEmpty()) return
                pendingAction = request
                actionState = actionState.copy(pending = true, error = null)
            }
            ActionCommand.SubmitEdit -> {
                if (target == null || target != actionState.editing?.original ||
                    MessageAction.Edit !in actions.capabilities[target.id].orEmpty() ||
                    request.text != state.draft.text || request.text.isNullOrBlank() || request.draftRevision != state.draft.revision) return
                pendingAction = request
                actionState = actionState.copy(pending = true, error = null)
                state = state.copy(draft = state.draft.copy(submitting = true))
            }
            ActionCommand.CancelQuote -> actionState = actionState.copy(quoteTargetId = null)
            ActionCommand.SendQuote -> {
                if (request.targetId != actionState.quoteTargetId || request.text != state.draft.text ||
                    request.draftRevision != state.draft.revision || !state.draft.hasContent || request.attachments != state.draft.attachments) return
                onEvent(ConversationEvent.SendDraft(state.conversationId, state.draft.revision, state.draft.text, request.attachments))
                pendingQuoteTarget = request.targetId
            }
        }
        actionState = actionState.copy(revision = actionState.revision + 1)
        if (simulationEnabled && pendingAction == request) schedule(700, "action ${request.command}") {
            if (pendingAction == request) {
                val success = !failActionNext
                failActionNext = false
                completeAction(success)
            }
        }
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
        val valid = target != null && if (deleting) target == actionState.deleting &&
            MessageAction.Delete in actions.capabilities[target.id].orEmpty()
            else target == actionState.editing?.original && MessageAction.Edit in actions.capabilities[target.id].orEmpty()
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

    var simulationEnabled by mutableStateOf(false)
        private set
    var paused by mutableStateOf(false)
        private set
    var clockMillis by mutableStateOf(0L)
        private set
    var trace by mutableStateOf<List<String>>(emptyList())
        private set
    var acknowledgementMillis by mutableStateOf(400L)
        private set
    var deliveryMillis by mutableStateOf(900L)
        private set

    /** Applies to newly scheduled work; already queued deadlines remain reproducible. */
    fun configureDelays(acknowledgement: Long, delivery: Long) {
        require(acknowledgement in 0..5_000 && delivery in 0..5_000)
        acknowledgementMillis = acknowledgement
        deliveryMillis = delivery
        record("delays ack=$acknowledgement delivery=$delivery")
    }

    var selectedScenario by mutableStateOf(Scenario.Exchange)
        private set
    var selectedSeed by mutableStateOf(42)
        private set
    private var failNext = false
    private var refuseNext = false
    var connected by mutableStateOf(true)
        private set
    private var failHistoryNext = false
    private var failActionNext = false

    /** Simulated connection state only; queued deliveries observe it when they resolve. */
    fun changeConnection(value: Boolean) { connected = value; record("connected=$value") }

    /** Capability mutation is deliberately exposed in debug controls for stale-menu cases. */
    fun changeSelectedCapabilities(allowed: Boolean) {
        val id = actions.menuTargetId ?: state.messages.lastOrNull()?.id ?: return
        if (allowed) allowActions(id) else revokeActions(id)
        record("capability id=$id allowed=$allowed")
    }

    fun removeQuotedTarget() {
        val id = actions.quoteTargetId ?: actions.quotes.values.firstOrNull { target -> state.messages.any { it.id == target } } ?: return
        removeMessage(id)
        record("removed quote target=$id")
    }

    fun removeFirstVisibleFixtureRow() {
        state.messages.firstOrNull()?.let { removeMessage(it.id); record("removed anchor id=${it.id}") }
    }
    private var sequence = 0L
    private var generation = 0L
    private var random = kotlin.random.Random(42)
    private data class Scheduled(val due: Long, val order: Long, val generation: Long,
        val room: String, val label: String, val apply: () -> Unit)
    private val scheduled = mutableListOf<Scheduled>()

    /** Restart from fixture data. Clock steps are deterministic; no wall clock enters snapshots. */
    fun startSimulation(scenario: Scenario = Scenario.Exchange, seed: Int = 42) {
        selectedScenario = scenario
        selectedSeed = seed
        paused = false
        val revision = state.draft.revision + 1
        pendingDraft = null
        pendingHistory = null
        pendingAction = null
        pendingQuoteTarget = null
        actionState = ActionState(revision = actionState.revision + 1)
        revoked = emptyMap()
        rooms.clear()
        roomSubmissions.clear()
        roomActions.clear()
        roomPendingActions.clear()
        failNext = scenario == Scenario.Failure
        refuseNext = scenario == Scenario.Refusal
        connected = scenario != Scenario.Disconnected
        failHistoryNext = scenario == Scenario.History
        failActionNext = scenario == Scenario.Actions
        simulationEnabled = true
        generation++
        scheduled.clear()
        sequence = 0
        clockMillis = 0
        random = kotlin.random.Random(seed)
        trace = emptyList()
        state = initialState("studio").copy(draft = Draft(revision = revision))
        nextMessage = 1
        useChatFixture()
        when (scenario) {
            Scenario.IncomingBurst -> repeat(4) { n -> schedule(400L * (n + 1), "burst $n") { receiveMessage() } }
            Scenario.History -> useHistoryFixture()
            Scenario.EmptyHistory -> { state = state.copy(messages = emptyList()); history = HistoryState(HistoryStatus.Idle, history.revision + 1) }
            Scenario.LongConversation -> { state = state.copy(messages = (0..999).map(::historyMessage)); history = HistoryState(HistoryStatus.Exhausted, history.revision + 1) }
            Scenario.Replies -> actionState = actionState.copy(quotes = actionState.quotes + ("morgan" to "removed-target"))
            Scenario.Composer -> state = state.copy(draft = Draft("First line\nSecond line\nThird line\nFourth line\nFifth line", state.draft.revision))
            else -> Unit
        }
        record("start seed=$seed scenario=${scenario.name} ack=$acknowledgementMillis delivery=$deliveryMillis")
    }

    /** Cancel prior-session work and restore the selected seed and scenario. */
    fun resetSimulation() = startSimulation(selectedScenario, selectedSeed)

    private fun record(label: String) {
        trace = (trace + "$clockMillis #$sequence ${state.conversationId} $label").takeLast(80)
    }

    private fun schedule(delay: Long, label: String, apply: () -> Unit) {
        scheduled += Scheduled(clockMillis + delay, sequence++, generation, state.conversationId, label, apply)
        record("scheduled $label")
    }

    /** Advance one event even while paused, ordering equal deadlines by insertion sequence. */
    fun step() {
        val next = scheduled.minWithOrNull(compareBy<Scheduled> { it.due }.thenBy { it.order }) ?: return
        clockMillis = maxOf(clockMillis, next.due)
        scheduled.remove(next)
        if (next.generation == generation && next.room == state.conversationId) {
            next.apply()
            record("applied ${next.label} rows=${state.messages.size} draft=${state.draft.revision}")
        }
    }

    /** Interactive pacing calls this with elapsed ticks; checks advance directly without sleeps. */
    fun advanceBy(millis: Long) {
        require(millis >= 0)
        if (paused) return
        val target = clockMillis + millis
        while (scheduled.any { it.due <= target }) step()
        clockMillis = target
    }

    fun pause(value: Boolean) { paused = value }

    private fun scheduleDelivery(id: String) {
        schedule(deliveryMillis, "delivery id=$id") {
            if (state.messages.none { it.id == id && it.delivery == Delivery.Sending }) return@schedule
            val failed = failNext || !connected
            failNext = false
            completeDelivery(if (failed) Delivery.Failed else Delivery.Sent, id)
            if (!failed) schedule(700 + random.nextLong(400), "reply to=$id") { receiveMessage() }
        }
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
        if (request.conversationId != state.conversationId || request.revision != history.revision ||
            pendingHistory != null || history.status !in listOf(HistoryStatus.Idle, HistoryStatus.Error)) return
        pendingHistory = request
        history = history.copy(status = HistoryStatus.Loading)
        if (simulationEnabled) schedule(700, "history revision=${request.revision}") {
            if (pendingHistory != request) return@schedule
            if (failHistoryNext) { failHistoryNext = false; failHistory() }
            else if (selectedScenario == Scenario.EmptyHistory) {
                pendingHistory = null
                history = HistoryState(HistoryStatus.Exhausted, history.revision + 1)
            } else completeHistory(request)
        }
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
        if (simulationEnabled) {
            generation++
            scheduled.clear()
            if (pendingDraft != null) refuse()
            if (pendingAction != null) completeAction(false)
            state = state.copy(messages = state.messages.map {
                if (it.delivery == Delivery.Sending) it.copy(delivery = Delivery.Failed) else it
            })
            record("cancel on switch")
        }
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
        state = state.copy(messages = state.messages + Message("arrival-${nextMessage++}", Participant("avery", "Avery"),
            2_000L + nextMessage, "09:43", "A new message arrived", Delivery.Sent))
    }

    fun onEvent(event: ConversationEvent) {
        if (event.conversationId != state.conversationId || pendingAction != null) return
        when (event) {
            is ConversationEvent.DraftChanged -> if (pendingDraft == null) {
                state = state.copy(draft = state.draft.copy(text = event.text))
            }
            is ConversationEvent.SendDraft -> {
                if (pendingDraft != null || event.revision != state.draft.revision ||
                    event.text != state.draft.text || event.attachments != state.draft.attachments || !state.draft.hasContent) return
                record("intent send revision=${event.revision}")
                pendingDraft = event
                state = state.copy(draft = state.draft.copy(submitting = true))
                if (simulationEnabled) schedule(acknowledgementMillis, "ack revision=${event.revision}") {
                    if (pendingDraft == event) {
                        if (refuseNext) {
                            refuseNext = false
                            refuse()
                        } else {
                            accept()
                            scheduleDelivery(state.messages.last().id)
                        }
                    }
                }
            }
            is ConversationEvent.RetryMessage -> {
                val eligible = state.messages.any { it.id == event.messageId &&
                    it.sender.id == state.currentParticipantId && it.delivery == Delivery.Failed }
                if (!eligible) return
                record("intent retry id=${event.messageId}")
                state = state.copy(messages = state.messages.map {
                    if (it.id == event.messageId && it.sender.id == state.currentParticipantId && it.delivery == Delivery.Failed)
                        it.copy(delivery = Delivery.Sending) else it
                })
                if (simulationEnabled) scheduleDelivery(event.messageId)
            }
        }
    }

    fun accept(expected: ConversationEvent.SendDraft? = pendingDraft) {
        val pending = pendingDraft ?: return
        if (expected != pending || pending.revision != state.draft.revision) return
        val id = "${state.conversationId}-${nextMessage++}"
        state = state.copy(
            draft = Draft(revision = pending.revision + 1),
            messages = state.messages + Message(id, Participant("me", "You"), 1_000L + nextMessage, "09:42", pending.text, Delivery.Sending, pending.attachments),
        )
        actionState = actionState.copy(revision = actionState.revision + 1, quoteTargetId = null,
            quotes = pendingQuoteTarget?.let { actionState.quotes + (id to it) } ?: actionState.quotes)
        pendingQuoteTarget = null
        pendingDraft = null
    }

    fun completeDelivery(delivery: Delivery, messageId: String? = null) {
        require(delivery != Delivery.Sending)
        state = state.copy(messages = state.messages.map {
            if ((messageId == null || it.id == messageId) && it.sender.id == state.currentParticipantId && it.delivery == Delivery.Sending) it.copy(delivery = delivery) else it
        })
    }

    fun refuse() {
        val pending = pendingDraft ?: return
        state = state.copy(draft = Draft(pending.text, pending.revision + 1, attachments = pending.attachments))
        pendingDraft = null
        pendingQuoteTarget = null
        actionState = actionState.copy(revision = actionState.revision + 1)
    }

    fun useChatFixture() {
        state = state.copy(messages = listOf(
            Message("welcome", Participant("avery", "Avery Chen"), 1_000, "09:41", "Morning! I’ve added the latest sketches for the shared space.", Delivery.Sent),
            Message("sketches", Participant("avery", "Avery Chen"), 1_001, "09:41", "The quieter corner by the window could work well for reading.", Delivery.Sent),
            Message("response", Participant("me", "You"), 1_002, "09:42", "Love that idea. Let’s keep the materials warm and simple.", Delivery.Sent),
            Message("morgan", Participant("morgan", "Morgan Lee"), 1_003, "09:43", "I’ll bring a few samples to our afternoon review ☀️", Delivery.Sent),
            Message("morgan-2", Participant("morgan", "Morgan Lee"), 1_004, "09:43", "Does 2:30 work for everyone?", Delivery.Sent),
            Message("confirmed", Participant("me", "You"), 1_005, "09:44", "Perfect — see you both there!", Delivery.Sent),
        ))
        actionState = actionState.copy(quotes = mapOf("response" to "sketches", "confirmed" to "morgan-2"))
        history = HistoryState(HistoryStatus.Exhausted)
    }

    companion object {
        private fun historyMessage(n: Int) = Message(
            id = "history-$n",
            sender = Participant("avery", "Avery"),
            timestampEpochMillis = n.toLong(),
            displayTime = "09:41",
            text = "Message $n",
            delivery = Delivery.Sent
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
                    delivery = Delivery.Sent
                ),
            )
        )
    }
}
