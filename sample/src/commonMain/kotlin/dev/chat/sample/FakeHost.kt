package dev.chat.sample

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.chat.core.*

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
        private fun initialState(id: String) = ConversationState(id, "me", messages = listOf(
            Message("welcome", Participant("avery", "Avery"), 1_000, "09:41", "Welcome! Send a message, then choose its delivery outcome.", Delivery.Sent),
        ))
    }
}
