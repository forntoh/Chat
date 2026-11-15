# Host-owned message actions

Select `dev.forntoh.chat.candidate:chat-actions:0.1.0-local` alongside `chat-ui`. Actions depend on core,
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

`FakeHost` can manually complete/refuse operations through the toolbar’s **Manual service** mode;
the default sample completes fake operations automatically. Only a successful validated host completion
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


## Long-press message focus

`MessageActionFocus` wraps a message renderer in the optional actions artifact. Apply its supplied
modifier to the shaped bubble only (for Material3, pass it as `bubbleModifier`). Sender headings,
avatars, gutters and Retry remain outside the interaction. A tap does not open actions.

```kotlin
MessageActionFocus(actions, message.id,
    above = { HostTopContent() }, // optional, empty by default
    below = { MessageActionMenu(actions, message.id) },
) { bubbleInteraction ->
    MessageBubble(message, outgoing, retry, conversationSlots, bubbleInteraction)
}
```

The default lower surface is a rounded vertical menu with 48dp controls, filtered by the latest
host capabilities. Replace either surrounding slot independently; slots may contain custom content
or guarded actions and must not emit intents during composition. `ActionStyle.menuBackground` and
`menuShape` map the menu to a host theme without importing Material3 into actions. Existing
`ActionSlots.control` still owns labels/icons/semantics. The sample maps its existing theme and
Tabler icons and demonstrates additional content above in Custom UI mode.

The selected bubble transfers drawing of its recorded graphics layer from the timeline to the focus
surface; its host content is composed once and the timeline leaves no duplicate under the scrim.
A 180ms FastOutSlowIn transition moves it continuously from its measured screen position to the
action layout, accounting for the popup origin and native insets. Native animation starts after
popup placement is known; the recording remains stable while the popup draws it. A bubble that
already has room for the occupied slots retains its position, including its original horizontal
gutter. Empty slots reserve no height or gap. Occupied slots extend their bounded scroll viewport 24dp beyond each visible content edge so
shadows fade before clipping. This overflow adds no visible horizontal inset. Occupied top/bottom
slots keep an 8dp gap from the scaled bubble edge and align with its sender edge. On iOS the
popup opts out of platform inset clipping: the scrim covers the complete window, while menu
placement still observes native safe drawing insets. It enlarges the bubble to 1.05x,
anchored on the right for outgoing messages and on the left for incoming messages. Uniform
growth is capped by the safe window width; full-width custom bubbles keep their original size
and content instead of clipping or reflowing. The same fitted scale applies throughout opening
and closing. The presentation
fades a 40% black scrim and fades the surrounding content. Dismissal reverses the same
travel before drawing returns to the timeline. Exit converges on the latest host state. Set
`motionEnabled = false` for immediate transitions; Compose's duration scale is also respected.
This supplies no glass/blur or reaction feature. Long bubbles and lower content scroll within
bounded viewport regions; the group is repositioned to keep the menu on screen.

Close, outside tap, Back/Escape dismiss through guarded `CloseMenu`. A changed/removed message,
changed conversation, pending operation or loss of all capabilities immediately removes the
presentation. Remaining actions always use the latest capability snapshot and existing request
revision guards. Presentation invalidation emits no host event. The host must clear an invalid
menu target in its next snapshot. The retained draft and delivery state remain host-owned.

`MessageActions` remains available for existing explicitly controlled integrations. The sample's
standard interaction uses `MessageActionFocus` and has no tap-to-open button or dropdown.

## Embedded replies

The host's `ActionState.quotes` maps a sent message ID to its quoted target. Render
`QuotedMessage(actions, targetId)` inside the message-body slot, before the body text, to keep
the preview inside the bubble. `ActionSlots.quote` owns all preview content and can replace it
or leave it empty. `ActionQuoteContent` supplies the resolved nullable message and optional
`onClick`; forward that callback from a custom preview to retain navigation.

Foundation defaults use a rounded inset with a 3dp accent, sender and a two-line excerpt.
`ActionStyle.quoteBackground` and `quoteAccent` customize that presentation. Material3 hosts
can bridge their quote slot to `ReplyPreview(it.message, it.onClick)`; this component ships in
`chat-material3` without importing actions. It inherits the bubble foreground and supplies the
same inset/ellipsis/fallback presentation. `ReplyMessageBody(preview, body)` measures the two
together so the inset spans the content width without stretching unrelated bubbles. A deleted/unloaded target renders “Message unavailable”
without activation. Preview navigation is UI-only; reply submission and target validation remain
host-owned. The default sample uses the same library renderers in default and custom modes.

Selecting or cancelling a reply/edit requests focus through the existing adapted
`ComposerContent.focusRequest`; no input or draft is retained by the actions adapter.
The sample clears a selected menu on target removal or capability revocation, so restoring
capabilities cannot reopen an obsolete overlay. Host invalidation does not emit an action.
