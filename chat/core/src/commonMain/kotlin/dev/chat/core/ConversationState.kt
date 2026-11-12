package dev.chat.core

data class Draft(val text: String = "", val revision: Long = 0, val submitting: Boolean = false)

data class Participant(val id: String, val displayName: String)

enum class Delivery { 
    Sending,
    Sent,
    Failed
}

data class Message(
    val id: String,
    val sender: Participant,
    val timestampEpochMillis: Long,
    val displayTime: String,
    val text: String,
    val delivery: Delivery,
)

data class ConversationState(
    val conversationId: String,
    val currentParticipantId: String,
    val draft: Draft = Draft(),
    val messages: List<Message> = emptyList(),
) {
    init { require(messages.map { it.id }.distinct().size == messages.size) { "Message IDs must be unique within a snapshot" } }
}

sealed interface ConversationEvent {
    val conversationId: String
    data class DraftChanged(override val conversationId: String, val text: String) : ConversationEvent
    data class SendDraft(override val conversationId: String, val revision: Long, val text: String) : ConversationEvent
    data class RetryMessage(override val conversationId: String, val messageId: String) : ConversationEvent
}

