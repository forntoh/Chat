# Earlier history

History lives in `chat-ui`. The core model remains an immutable host snapshot; Material3 is optional. There is no paging service, persistence, or network dependency.

Pass `HistoryState` and `onLoadEarlier` to either conversation component. The host owns an increasing request revision per conversation. Publish Loading synchronously, allow one pending request, and validate both conversation and revision before applying results. Invalidate work when leaving a conversation, including when returning to the same room later. Resolve each request with a new revision and Idle, Error or Exhausted. Empty pages must become Exhausted unless the host has a progressing cursor.

```kotlin
Conversation(
    state = host.state,
    onEvent = host::onEvent,
    history = host.history,
    onLoadEarlier = host::loadEarlier,
)
```

Mount this shared `dev.forntoh.chat.ui.Conversation` inside `MaterialTheme { JetchatTheme { ... } }` for optional Material3 presentation. This example uses the shared component and the sample's in-memory `FakeHost`. For foundation-only presentation, use `dev.forntoh.chat.ui.Conversation` and supply message, composer, history and latest slots. Slots receive guarded callbacks and own their labels and semantics. History defaults to Exhausted, preserving compatibility with consumers that supply no loader.

Merge overlapping page identities in the host before constructing `ConversationState`, retaining host order and the newest authoritative row content. Duplicate IDs in a snapshot are rejected. The UI anchors prepends to visible identity and offset; if that identity disappears, it chooses the nearest surviving row by distance in the prior host order, preferring the later row on a tie and retaining the scroll offset. Incoming messages follow the bottom only when the reader was there already. A guarded Send activation deliberately returns from history to latest, then follows the host-supplied outgoing row. This does not accept or clear the draft; a refused send still retains the host draft. Otherwise the latest slot exposes a deliberate return action and new-message flag. Failed history leaves existing rows readable and requires explicit retry.

Run the desktop sample and choose **History fixture** for a reproducible source of 1,000 messages with the latest 50 loaded. Scroll up or choose **Load earlier**, then **Complete page** or **Fail page**. Successful pages overlap one identity and refresh its text. Use **New arrival**, **Switch room**, and **Custom UI** / **Material3 UI** to inspect host ownership and presentation. Drafts and unresolved submissions are retained per room in memory; no process-death or backend guarantee is made.

The original history validation used three shared JVM journeys: prepend/arrival anchoring and return with 1,000 rows; single boundary request and explicit retry/exhaustion; sample overlap reconciliation, stale room results and retained drafts. No frame-rate target is measured. Native platform/configuration campaigns and dedicated accessibility/RTL checks remain owner-deferred for this stage. That baseline evidence is recorded on PAT-242. PAT-267 adds focused reply-navigation,
multiline composer/bottom-follow and start-marker regressions plus targeted native sample journeys.

## Timeline presentation and reply navigation

History feedback is the first keyed timeline item, so “Start of conversation” scrolls away.
The Material3 Latest control is a 48dp chevron button over the lower-right timeline, with a
180ms fade/slide entrance and exit. It appears whenever newer content is outside the viewport.
Its callback uses one 360ms eased scroll session with frame-by-frame distance estimates for unmeasured rows and exact trailing-edge placement once visible. It does not use the framework’s long-distance item teleport. New arrivals during travel update that session’s destination without restarting it. User scrolling can interrupt travel; conversation changes cancel it. Host latest
slots retain the same callback and new-message flag. Bottom-follow records the prior reader
position before a changed snapshot is measured; arrivals while reading history preserve identity/offset.
Composer/safe-area viewport changes keep an existing bottom reader pinned instead of changing
reader mode simply because a multiline draft needs more height. Viewport changes retain bottom-follow even if native inset/bring-into-view scrolling is in progress. Oversized last rows align their trailing content above the composer/keyboard instead of aligning only the row’s top. Explicit reply navigation leaves bottom-follow until the reader returns or sends.

`ConversationSlots.presentedMessage` is an optional four-argument row slot. It receives the
message, sender direction, retry callback and `MessagePresentation`. It overrides `message`;
an explicit `Conversation.messageContent` still overrides both. Existing row slots can read the
same value from `LocalMessagePresentation`. `onNavigateToMessage(id)` smoothly brings a loaded
stable ID into the center, bounded by the available scroll range, then sets `isHighlighted` for
900ms. The default bubble fades its surface tint over 180ms; consumers can animate the flag
however they wish, or render it immediately. `MessageBubble` also accepts an explicit
`presentation` alongside its bubble modifier. Compose animation duration scaling applies.

Navigation never loads history or emits host intents. Missing IDs do nothing. New navigation
cancels prior travel/highlight; switching conversations cancels the scoped work. First/last rows
are limited by the available scroll range. Consumers needing unloaded-target resolution own
that history request and must navigate only after the target is in the snapshot.

```kotlin
val slots = LocalConversationSlots.current.copy(presentedMessage = { row, outgoing, retry, presentation ->
    HostBubble(row, outgoing, retry, presentation.isHighlighted) {
        presentation.onNavigateToMessage(hostQuoteTargetId)
    }
})
```
