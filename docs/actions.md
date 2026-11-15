# Message actions

Select `chat-actions` with `chat-ui`. It stays independent of media and Material3. A host retains `ActionState`, supplies explicit per-message capabilities and resolves requests; the module does not edit storage, authorize users or access the clipboard implicitly.

Create `rememberMessageActions(conversation, actionState, onAction, onEvent)` beside your retained snapshots. Wrap a row in `MessageActionFocus` for the focused action surface, or render `MessageActions` for an explicit menu. Forward the adapted composer and render `ActionComposerStatus` in its header. The test-only `ActionContractHarness` in the sample JVM test sources contains a complete combined reference for these public slots.

Capabilities determine which actions are offered. Validate them again in the host, including after a pending result. Requests bind conversation, action revision and target identity; edits and quoted sends also bind draft revision and payload. Reject stale callbacks and results after message replacement, capability revocation, navigation or reset.

For a quoted reply, keep the quote target in `ActionState.quoteTargetId`. On acceptance, store the relationship in `quotes` and resolve the original draft. `QuotedMessage` renders the current target or an unavailable-message fallback. Navigation/highlight uses message identity rather than a row index.

Editing retains the prior draft and quote context in `EditingMessage`. Cancel restores that state with a new draft revision. Accept replaces the original row without changing its identity. Delete uses an explicit prompt and a pending request; only a valid accepted result removes the row. Keep readable messages during errors and allow an intentional retry.

A Copy request is a host callback. The host chooses whether and how to write the platform clipboard after revalidating the current message and capability. `ActionTheme` and `ActionSlots` customize labels, focusable controls and surfaces; custom renderers own their semantics.
