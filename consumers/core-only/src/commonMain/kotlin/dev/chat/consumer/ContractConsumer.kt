package dev.chat.consumer

import dev.chat.core.*

fun conversationFromHost(): ConversationState = ConversationState(
    conversationId = "core-consumer", currentParticipantId = "me", draft = Draft("Hello\nworld"),
    messages = listOf(Message("existing", Participant("peer", "Avery"), 1000, "09:41", "Welcome", Delivery.Sent)),
)
