# Conversation customization

Use `chat-ui` for host-rendered content without Material3. Optional `chat-material3` supplies
English default message, composer, history and latest controls. Core state and service results
stay host-owned. Neither integration persists drafts or installs services.

`ConversationSlots`, `LocalConversationSlots` and `ConversationTheme` live in `dev.chat.ui`
and configure presentation for the one shared `dev.chat.ui.Conversation`. The neutral defaults
work without Material3. Optional `dev.chat.material3.JetchatTheme` installs Material3 renderers
through that same provider; it supplies no second Conversation implementation.

For the Material3 path, wrap the shared Conversation in `MaterialTheme { JetchatTheme { ... } }`.
Derive `jetchatConversationSlots().copy(...)` for customized Jetchat defaults. Shared themes and
foundation-only hosts derive `LocalConversationSlots.current.copy(...)`. Hosts author
wording directly with composables, using their own resources and semantics. When slot code
uses Material3 controls, declare Material3 in the host too (see the standalone default consumer):

```kotlin
ConversationTheme(slots = LocalConversationSlots.current.copy(
    composerSend = { composer ->
        Button(onClick = composer.onSend, enabled = composer.canSend) { Text("Post") }
    },
    retry = { retry -> TextButton(onClick = retry.onRetry) { Text("Try again") } },
)) {
    Conversation(hostState, ::handleIntent)
}
```

A direct renderer (`messageContent`, `composerContent`, `historyContent`, `latestContent`) wins
over its effective bundle. An explicit `slots` bundle replaces the ambient theme; derive
`LocalConversationSlots.current.copy(...)` to preserve other themed slots. Otherwise the nearest
theme applies, with usable built-in defaults when none is supplied. `MessageBubble`,
`MessageComposer`, `HistoryStatusContent` and `LatestControl` are separately callable defaults;
delegate to them without recursively invoking their top-level slot. Pass a bundle explicitly to
customize their subslots locally. MaterialTheme continues to supply colors, typography and shapes.

Message metadata, body, delivery and the ordered semantics description can be customized
independently. When localizing delivery, change both `messageDelivery` and `messageDescription`.
The default text group announces sender, content, host time and delivery once; retry stays a
separate action. Custom rows own equivalent semantics. Field/send, history feedback and latest
label subslots receive typed host state and guarded callbacks, so host copy needs no label DTO.
Custom controls must honor enabled/pending state and provide keyboard, focus and accessibility
behavior. Rendering must not invoke intents or keep a second draft.

Keep one Conversation call mounted across theme or renderer changes. Its shared behavior retains
pending submission and history position. Theme changes do not accept a draft or emit a send;
the host resolves submissions with fresh draft revisions. Delivery remains independent of draft
acceptance. The default composer reserves a separate Send control and scrolls after five lines.

This is a deliberate prerelease API migration: `ConversationStrings`, `ActionLabels` and their
label-dependent overloads were removed without adapters. The unshipped Material3-specific Conversation entry points are also removed: import
`dev.chat.ui.Conversation` and install `JetchatTheme` instead. Compatible shared direct slots and
standalone renderer/retry calls remain available; no published binary compatibility is claimed.

The sample follows [Jetchat](https://github.com/android/compose-samples/tree/main/Jetchat)'s message
hierarchy and rounded presentation using original CMP code and system fonts. Host theme and
custom UI switches retain host state. Its in-memory fake service proves no real transport,
persistence, permissions or physical-device behavior.
