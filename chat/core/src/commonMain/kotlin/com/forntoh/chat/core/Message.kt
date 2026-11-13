package com.forntoh.chat.core

/**
 * A host-owned timeline row. Preserve [id] through optimistic insertion, delivery changes and
 * retry; later snapshots replace the row's content without changing its logical identity.
 *
 * [timestampEpochMillis] is metadata, not a sorting instruction: list order comes from the host.
 * [displayTime] is already formatted/localized by the host. [text] renders as plain text.
 * [sender] identifies ownership by its ID, rather than by its display name.
 *
 * [attachments] are immutable host descriptors; replacing a resource must replace its descriptor.
 * The core opens no content. Optional media presentation belongs to chat-media.
 * @param attachments Ordered host content, empty for text-only rows; IDs unique within this row.
 * @param id Stable key unique within a conversation, retained across delivery updates and retries.
 * @param sender Host participant whose ID determines outgoing presentation and retry eligibility.
 * @param timestampEpochMillis Host timestamp in milliseconds since the Unix epoch; does not determine row order.
 * @param displayTime Host-formatted time label displayed without additional formatting.
 * @param text Exact plain-text message body; no rich-text parsing is performed.
 * @param delivery Authoritative host delivery state, independent of composer acceptance.
 */
data class Message(
    val id: String,
    val sender: Participant,
    val timestampEpochMillis: Long,
    val displayTime: String,
    val text: String,
    val delivery: Delivery,
    val attachments: List<Attachment> = emptyList(),
) {
    init { require(attachments.map { it.id }.distinct().size == attachments.size) { "Attachment IDs must be unique within a message" } }
}
