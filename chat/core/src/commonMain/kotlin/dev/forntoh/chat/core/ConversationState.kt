package dev.forntoh.chat.core

/**
 * Authoritative conversation snapshot. Supply immutable lists and replace snapshots on updates.
 *
 * [conversationId] scopes drafts, events and row identities. [currentParticipantId] determines
 * outgoing alignment and retry eligibility; it is not an authorization check. The host must
 * enforce access control and reject stale asynchronous results before updating this state.
 * [messages] uses host order, including equal-timestamp tie breaks; the UI does not sort or
 * merge rows. IDs must be unique within this list, but may recur in another conversation.
 * Empty defaults show an empty timeline and a blank, enabled-for-editing composer.
 *
 * @param conversationId Stable host key scoping events, drafts and message identities.
 * @param currentParticipantId Host participant key used for outgoing presentation, not authorization.
 * @param draft Retained host composer state; defaults to an empty, editable draft at revision zero.
 * @param messages Immutable snapshot in host-defined display order; defaults to empty and requires unique IDs.
 * @throws IllegalArgumentException when a snapshot contains duplicate message IDs.
 */
data class ConversationState(
    val conversationId: String,
    val currentParticipantId: String,
    val draft: Draft = Draft(),
    val messages: List<Message> = emptyList(),
) {
    init { require(messages.map { it.id }.distinct().size == messages.size) { "Message IDs must be unique within a snapshot" } }
}
