# Earlier messages and reading position

History is host-driven. Supply `HistoryState` and handle `HistoryRequest` through `Conversation.onLoadEarlier`. The default history is exhausted, so a basic conversation emits no paging requests.

A request includes the conversation and history revision. Publish Loading synchronously before starting asynchronous work. On completion, validate the session and request again, merge overlapping pages by stable message identity, and publish a fresh immutable snapshot in your chosen display order. The UI does not sort or merge pages for you.

Use Idle when more rows are available, Error for a readable retry state, and Exhausted when no earlier rows remain. Advance the history revision to resolve the request. `historyContent` or the corresponding slots can customize loading, empty and error feedback without replacing the timeline.

Prepending keeps the visible message and scroll offset. If an anchor disappears, the timeline finds the nearest survivor in prior order, preferring the later row on a tie. The start marker also remains an anchor when the first message survives.

Incoming rows follow a reader already at the bottom. While reading earlier messages, arrivals preserve the anchor and show a deliberate Latest control. Send and Latest resume following. Keyboard and composer-size changes preserve this mode while keeping trailing content visible.

Publish Loading during initial hydration to suppress a tail-insertion animation. Changing themes or slots at the same call site keeps timeline state; switching conversation IDs or unmounting resets its UI state. Keep drafts and in-flight paging jobs in the host and cancel obsolete work there.
