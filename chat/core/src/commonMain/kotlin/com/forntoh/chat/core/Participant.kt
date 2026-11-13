package com.forntoh.chat.core

/**
 * Host-supplied sender identity. Keep [id] stable; [displayName] is presentation text.
 *
 * @param id Stable host participant key, compared with [ConversationState.currentParticipantId].
 * @param displayName Host-provided readable sender label; need not be unique.
 */
data class Participant(val id: String, val displayName: String)