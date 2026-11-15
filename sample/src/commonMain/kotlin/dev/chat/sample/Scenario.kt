package dev.chat.sample

/** Named, reproducible fixture cases. These never represent real service reliability. */
enum class Scenario(
    val label: String,
    val guidance: String,
) {
    Exchange("Normal exchange", "Send a message: acknowledgement, delivery and an incoming reply arrive separately."),
    Refusal("Refuse next submission", "Send a draft. The first acknowledgement refuses it; the next attempt succeeds."),
    Failure("Fail next delivery", "Send, wait for failure, then tap Retry. The same message ID is retained."),
    Disconnected("Disconnected / reconnect", "Send while disconnected, then reconnect in this panel and retry the failed row."),
    IncomingBurst("Incoming burst", "Four arrivals are queued. Pause and scroll into history, then step or resume."),
    History("History error / overlap", "Scroll to the start: the first page fails. Retry loads a delayed overlapping page."),
    EmptyHistory("Empty / exhausted history", "An empty conversation returns an empty exhausted page."),
    Actions("Edit / delete failure", "Hold an outgoing bubble to edit or delete. The first operation fails; retry succeeds."),
    Capabilities("Action capability changes", "Hold a bubble, then revoke or restore its actions in this panel."),
    Replies("Replies / missing target", "Tap embedded replies to center their target. One reply references a missing message."),
    Composer("Multiline composer", "A five-line draft exercises growth. Hold a message to reply; dismiss preserves the draft."),
    Images("Image states", "Open the image simulator below for loading, upload, failure, retry, unavailable and unsupported content."),
    SwitchReset(
        "Switch / reset cancellation",
        "Send, pause before acknowledgement, then switch rooms or reset. Old events cannot mutate the new session.",
    ),
    LongConversation("Long conversation", "A bounded 1,000-message fixture exercises scrolling and return to latest."),
}
