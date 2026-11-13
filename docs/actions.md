# Host-owned message actions

Select `dev.chat.candidate:chat-actions:0.1.0-local` alongside `chat-ui`. Actions depend on core,
UI and Compose foundation; they do not add Material3, media, networking, storage or permissions.
`consumers/actions-only` is a standalone foundation-only integration using staged artifacts.
The sample uses the same slots with its existing optional Material3 renderers and Jetchat reference.

```kotlin
val actions = rememberMessageActions(hostConversation, hostActions, ::handleAction, ::handleEvent)
Conversation(hostConversation, actions::onConversationEvent,
    messageContent = { message, outgoing, retry ->
        Column {
            hostActions.quotes[message.id]?.let { QuotedMessage(actions, it) }
            HostMessage(message, outgoing, retry)
            MessageActions(actions, message.id)
        }
    },
    composerContent = { composer ->
        Column {
            ActionComposerStatus(actions)
            HostComposer(actions.composer(composer),
                if (hostActions.editing == null) sendLabel else editLabel)
        }
    })
```

`HostMessage` and `HostComposer` are consumer renderers, not library APIs. They own their text,
focus and semantics. Pass the adapted composer so pending action work and stale edits lock the
input along with the existing draft-submission latch. The foundation action controls support
pointer/touch and keyboard activation with button semantics and minimum 48 dp targets. Action control wording, quote fallback and confirmation content are authored in `ActionSlots`; host errors
are already localized strings supplied in `ActionState.error`. Custom surfaces can invoke
`ActionsContent.request` instead. Call callbacks only from user activation.

The host retains `ActionState` for each conversation and owns every transition. Supply immutable
capability maps and quote associations, increment action revision for every accepted/refused
operation, and keep a pending flag until completion. Menus show only the supplied capabilities;
visibility is not backend authorization. The adapter rejects callbacks whose action state,
conversation or draft was replaced, resolves targets/capabilities again, and synchronously latches
one request per action revision. The host must validate/deduplicate again across remounts,
queued callbacks and services. Handler exceptions propagate; resolve local failures with a new
revision and retained draft rather than relying on an implicit retry.

- Copy emits exact target text. The host supplies its clipboard implementation explicitly.
  The sample's callback uses Compose's platform clipboard; `copiedText` is diagnostic fake-host
  evidence, not storage. The actions module itself opens no resources.
- Quote selection changes only retained quote context; cancellation preserves draft text.
  `SendQuote` includes conversation, target ID, action revision, exact text and draft revision.
  The host accepts/refuses through new draft/action snapshots and stores the quote association
  on its accepted row. Missing targets retain their identity and use the replaceable fallback;
  existing targets show current text through the replaceable quote slot.
- Begin edit retains the original target and the entire pre-edit draft/quote context in
  `EditingMessage`, then the host publishes selected message text as a fresh composer draft.
  Edit mode and quote mode are exclusive. Submit edit carries target, text and draft revision;
  the host revalidates the original snapshot and capability. Cancel and successful completion
  restore the retained pre-edit draft/quote with a fresh draft revision. Failed edits retain
  the edited text and expose an actionable host error, while the original row stays unchanged.
- Delete selection stores the selected `Message` snapshot in `ActionState.deleting` and shows
  explicit confirm/cancel controls. Confirmation is disabled if the target changes, disappears
  or loses delete capability. The host validates again both on request and completion. Cancelling
  leaves messages and composer text intact. Pending confirmations cannot emit repeatedly.

`FakeHost` manually completes/refuses operations; only a successful validated host completion
replaces/removes rows. Failed operations are distinct from message delivery failure. Navigation
retains each room's drafts, action context and pending work; menu revisions invalidate old
sessions. Demo capability revocation is scoped to a room. There is no server, persistence,
production permission enforcement, image/media or real transport in this sample.

Focused evidence: sample action journeys; foundation-only outside-consumer tests including stale
callbacks, localized missing quote fallback and ordinary Enter activation; selection checks for
JVM/Android/iOS that exclude Material3/media; Android clipboard instrumentation because that is
a new platform integration. Desktop captures come from the shared Compose test renderer.
These checks do not imply a repeated native platform matrix or physical-device coverage.
Owner-deferred screen-reader, RTL/locale, orientation, font scaling, dark-mode, exhaustive
keyboard/device/configuration and every-module-combination runtime campaigns remain deferred.

## Slots and themes

Every action surface inherits `ActionTheme`. Existing calls keep foundation defaults. Replace
individual slots for controls, typed status/prompt/error content, quote excerpts, and the menu, composer
status and confirmation containers. Containers receive their children as composable content;
render it once, or provide an equivalent complete host surface. Custom controls must honor
`enabled`, invoke the supplied guarded callback only on activation, and provide keyboard/focus
and accessibility semantics. Host state remains authoritative when renderers or themes change.

```kotlin
ActionTheme(
    style = ActionStyle(textStyle = hostTextStyle),
    slots = LocalActionSlots.current.copy(
        control = { button -> HostActionButton(button.command, button.enabled, button.onClick) },
        quote = { quote -> HostQuote(quote.targetId, quote.message) },
    ),
) {
    MessageActions(actions, message.id)
    ActionComposerStatus(actions)
}
```

`ActionStyle` controls foundation text, control background, shape, padding and minimum size.
`ActionControl` is a public foundation renderer that custom slots can delegate to without
recursing into their own slot. For a local override, pass `slots = LocalActionSlots.current.copy(...)`
to `MessageActions`, `QuotedMessage`, `ActionComposerStatus` or `DeleteConfirmation`; this set
also reaches nested quotes and confirmation. `ActionQuoteContent` retains the requested target
ID even when `message` is null; the quote renderer authors its unavailable fallback.

For Material3, keep the bridge in the host (as in `SampleTheme`): wrap the conversation in
`MaterialTheme`, map its typography/colors to `ActionStyle`, and supply `TextButton`/`Text`
slots. The actions artifact itself has no Material3 dependency. English default composables remain available for delegation. The unshipped `ActionLabels`
type was removed; controls now receive `ActionCommand`, and status slots receive
`ActionStatusContent` with a distinct kind and optional host error. Theme changes do not authorize
requests, clear drafts, or report persisted results.
