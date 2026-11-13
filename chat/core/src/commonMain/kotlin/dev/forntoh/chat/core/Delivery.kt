package dev.forntoh.chat.core

/** Host-reported delivery outcome; draft acceptance alone does not imply [Delivery.Sent]. */
enum class Delivery {
    /** Accepted locally and awaiting a host-reported outcome. */
    Sending,
    /** The host reports successful delivery under its own service contract. */
    Sent,
    /** The host reports failure; the current participant may explicitly request retry. */
    Failed
}
