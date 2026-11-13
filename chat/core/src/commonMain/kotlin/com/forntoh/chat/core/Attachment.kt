 package com.forntoh.chat.core

/**
 * Presentation-neutral host content. Identity [id] is stable within its conversation and draft
 * or message, including upload retries; it is never a list index. [contentReference] is an opaque
 * host token, never opened by core/ui. Null denotes unavailable content. Replace the descriptor
 * when the underlying resource changes so captured callbacks become stale.
 *
 * @param id Nonblank host key, unique within its owning draft/message.
 * @param mediaType Host-declared MIME type; optional media presentation supports `image/` types.
 * @param contentReference Opaque host lookup key, or null for missing content; no URI/permission inference.
 * @param description Host-localized accessible description, or null for the media slot's fallback.
 */
data class Attachment(
    val id: String,
    val mediaType: String,
    val contentReference: String?,
    val description: String? = null
) {
    init { require(id.isNotBlank()) { "Attachment identity must not be blank" } }
}
