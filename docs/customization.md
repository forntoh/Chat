# Conversation customization

Use `chat-ui` for host-rendered content without Material3. Optional `chat-material3` supplies
English default message, composer, history and latest controls. Core state and service results
stay host-owned. Neither integration persists drafts or installs services.

`ConversationSlots`, `LocalConversationSlots` and `ConversationTheme` live in `dev.chat.ui`
and configure presentation for the one shared `dev.chat.ui.Conversation`. The neutral defaults
work without Material3. Optional `dev.chat.material3.ChatTheme` installs Material3 renderers
through that same provider; it supplies no second Conversation implementation.

For the Material3 path, wrap the shared Conversation in `ChatTheme { ... }`.
Derive `chatConversationSlots().copy(...)` for customized defaults. Shared themes and
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
acceptance. The default Material3 composer places a circular Tabler Send beside a rounded gray input surface and scrolls after five lines. Its field preserves exact multiline text; disabled/pending controls still use the shared guards.

This is a deliberate prerelease API migration: `ConversationStrings`, `ActionLabels` and their
label-dependent overloads were removed without adapters. The unshipped Material3-specific Conversation entry points are also removed: import
`dev.chat.ui.Conversation` and install `ChatTheme` instead. Compatible shared direct slots and
standalone renderer/retry calls remain available; no published binary compatibility is claimed.

The sample uses the library's reference palette, directional pills and scaffold. Host theme and
custom UI switches retain host state. Its in-memory fake service proves no real transport,
persistence, permissions or physical-device behavior.

## Message grouping and avatars

`Conversation` provides `LocalMessageGroupContext` to every effective message renderer, including
explicit row overrides and action/media wrappers. `isFirst`/`isLast` describe contiguous sender IDs
in host list order; formatted time strings are never parsed and no date boundary is invented.
Prepending history recalculates adjacency while retaining message keys. Standalone renderers see
a complete one-message group. There is no remembered grouping state.

Material3 reserves a 36dp incoming gutter and renders a 30dp `messageAvatar` at group end, with
initials as its decorative fallback. Replace that composable to draw a host-provided avatar;
the artifact performs no image lookup or fetch. `messageSpacing` selects the gap before rows
(except the first loaded row): neutral defaults retain 12dp, Material3 uses 8dp between groups
and 4dp within groups. Legacy constructor/copy adapters preserve the original source call forms;
consumers recompile against the new local candidate. There is no published binary guarantee.
The original thirteen-parameter constructor/copy forms remain source overloads, so trailing
`latestLabel` lambdas keep their meaning. A legacy `copy { ... }` retains avatar/spacing overrides.
The immutable bundle explicitly preserves copy/equality/hash/component semantics: component13 and
thirteen-position destructuring still yield latestLabel; avatar/spacing remain components 14/15.
These mappings are independent of constructor order, so new defaults cannot shift legacy components.

Host-formatted time appears inside each bubble; each message still has its full host-authored ordered semantics.
Material3 omits routine Sent text visually, retains Sending/Failed feedback and a separate Retry.
Host delivery subslots are still invoked on every row. Explicit/bundle/ambient precedence is
unchanged. `ChatTheme { Conversation(...) }` supplies these defaults without sample renderers,
actions, media or another state engine; the independent default-material3 consumer demonstrates it.

Both sample screens use the library ChatScaffold with a left identity header and consumed safe-inset padding. The sample opens as a multi-participant conversation. Tap or long-press a message for its supported
context menu. The toolbar overflow contains theme/fixture switches and **Manual service**;
ordinary fake sends complete automatically. Manual mode exposes acceptance/refusal and delivery,
action or upload outcomes in that same menu. These remain in-memory demonstrations, not services.
See [reference comparison](jetchat-fidelity.md) for the current reference and intentional differences.

## Full scaffold and theme

```kotlin
ChatTheme { // default blue/white/gray palette; pass colorScheme/typography to customize
    ChatScaffold(
        title = { Text("Avery Chen") },
        avatar = { HostAvatar() },
        navigationIcon = {
            IconButton(onClick = ::goBack) { Icon(ChatIcons.ArrowLeft, "Back") }
        },
        actions = { HostConversationMenu() },
    ) {
        Conversation(hostState, ::handleIntent)
    }
}
```

ChatScaffold is optional. Override `topBar` to replace the full default header, or supply
`bottomBar`, `snackbarHost`, `floatingActionButton`, container/content colors and
`contentWindowInsets`. Its content is a bounded Column with Scaffold padding already applied
and consumed; do not apply it again. Conversation handles remaining IME insets. The shell
inherits ambient theme/slots and owns no navigation, chat state or services. Hosts retain all
identity content and optional controls; empty navigation/actions add no placeholder buttons.

`ChatTheme` installs `chatColorScheme()` and the default slot bundle. Pass your own ColorScheme,
Typography or `chatConversationSlots().copy(...)` to customize. Legacy `JetchatTheme` and
`jetchatConversationSlots` retain their signatures; the legacy theme remains a slots-only
provider using the host MaterialTheme. Prefer the new names for current integrations.

`ChatIcons` delegates to the requested `com.composables:icons-tabler-outline-cmp:2.2.1` library.
The version-catalog alias is `libs.tabler.icons`; the dependency stays inside chat-material3.
Core/custom-UI/actions/media consumers do not inherit it unless they select Material3.
Icons are bundled by the dependency; no copied vector paths or runtime icon fetch is used.
