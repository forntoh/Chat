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

This example uses the optional Material3 component and the sample's in-memory `FakeHost`. For foundation-only presentation, use `dev.chat.ui.Conversation` and supply message, composer, history and latest slots. Slots receive guarded callbacks and own their labels and semantics. History defaults to Exhausted, preserving compatibility with consumers that supply no loader.

Merge overlapping page identities in the host before constructing `ConversationState`, retaining host order and the newest authoritative row content. Duplicate IDs in a snapshot are rejected. The UI anchors prepends to visible identity and offset; if that identity disappears, it chooses the nearest surviving row by distance in the prior host order, preferring the later row on a tie and retaining the scroll offset. Appended messages follow the bottom only when the reader was there already. Otherwise the latest slot exposes a deliberate return action and new-message flag. Failed history leaves existing rows readable and requires explicit retry.

Run the desktop sample and choose **History fixture** for a reproducible source of 1,000 messages with the latest 50 loaded. Scroll up or choose **Load earlier**, then **Complete page** or **Fail page**. Successful pages overlap one identity and refresh its text. Use **New arrival**, **Switch room**, and **Custom UI** / **Material3 UI** to inspect host ownership and presentation. Drafts and unresolved submissions are retained per room in memory; no process-death or backend guarantee is made.

Validation is deliberately limited to three shared JVM journeys: prepend/arrival anchoring and return with 1,000 rows; single boundary request and explicit retry/exhaustion; sample overlap reconciliation, stale room results and retained drafts. No frame-rate target is measured. Native platform/configuration campaigns and dedicated accessibility/RTL checks remain owner-deferred for this stage. Current candidate evidence is recorded on PAT-242.
