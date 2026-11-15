package dev.chat.consumer

import dev.forntoh.chat.core.ConversationState
import dev.forntoh.chat.core.Delivery
import dev.forntoh.chat.core.Draft
import dev.forntoh.chat.core.Message
import dev.forntoh.chat.core.Participant

fun conversationFromHost(): ConversationState = ConversationState(
    conversationId = "core-consumer",
    currentParticipantId = "me",
    draft = Draft("Hello\nworld"),
    messages = listOf(Message("existing", Participant("peer", "Avery"), 1000, "09:41", "Welcome", Delivery.Sent)),
)
