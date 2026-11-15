package dev.forntoh.chat.core

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
    init {
        require(attachments.map { it.id }.distinct().size == attachments.size) { "Attachment IDs must be unique within a message" }
    }
}
