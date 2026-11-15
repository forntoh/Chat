# Conversation customization

Use `chat-ui` for host-rendered content without Material3. Optional `chat-material3` supplies
English default message, composer, history and latest controls. Core state and service results
stay host-owned. Neither integration persists drafts or installs services.

`ConversationSlots`, `LocalConversationSlots` and `ConversationTheme` live in `dev.forntoh.chat.ui`
and configure presentation for the one shared `dev.forntoh.chat.ui.Conversation`. The neutral defaults
work without Material3. Optional `dev.forntoh.chat.material3.ChatTheme` installs Material3 renderers
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

Sender and time both belong to `messageMetadata`; the default bubble inserts no sender text outside that slot.
The default Material3 sender heading sits above and outside the first incoming bubble of each
sender group. Host-formatted time and delivery sit together at the bottom trailing edge inside the bubble.
Short text shares its line with them; multiline default text uses measured final-line space,
falling back to a separate row when that space is insufficient.
Use the optional Material3 `MessageMetadata` helper directly in the existing slot to customize
both placements:

```kotlin
messageMetadata = { message ->
    MessageMetadata(header = { HostSender(message) }) { HostTime(message) }
}
```

Do not wrap this helper in another layout. A plain existing metadata override remains footer
content. Replace or empty `messageMetadata` to control its visible content; change
`messageDescription` separately for announced content. Custom body slots use their full measured bounds for a safe fit decision; no text geometry is guessed.
For host actions, use the `MessageBubble(message, outgoing, retry, slots, bubbleModifier)` overload.
Attach click/long-click modifiers to `bubbleModifier`; its hit region and indication are clipped to
the colored bubble, excluding the external heading, avatar, row space and Retry. Avoid wrapping
an entire row in a clickable container. Custom renderers attach interaction to their own shaped surface.

Fit detection uses the visible time/status width. Outgoing minimum status sizing is reserved
separately, so it cannot force a fitting timestamp onto another row. Default status changes
preserve the body origin while the footer changes rows as needed.
The bubble measures its body/footer
independently of the heading, so a long sender name cannot stretch a short colored bubble.

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
`dev.forntoh.chat.ui.Conversation` and install `ChatTheme` instead. Compatible shared direct slots and
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

Host-formatted time uses measured trailing placement inside each bubble; each message still has its full host-authored ordered semantics.
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
`contentWindowInsets`. Its bounded content Column applies horizontal padding and consumes native
insets; vertical chrome clearance is passed to Conversation as described below. Conversation
handles remaining IME insets. The shell
inherits ambient theme/slots and owns no navigation, chat state or services. Hosts retain all
identity content and optional controls; empty navigation/actions add no placeholder buttons.

`ChatTheme` installs `chatColorScheme()` and the default slot bundle. Pass your own ColorScheme,
Typography or `chatConversationSlots().copy(...)` to customize.

`ChatIcons` delegates to the requested `com.composables:icons-tabler-outline-cmp:2.2.1` library.
The version-catalog alias is `libs.tabler.icons`; the dependency stays inside chat-material3.
Core/custom-UI/actions/media consumers do not inherit it unless they select Material3.
Icons are bundled by the dependency; no copied vector paths or runtime icon fetch is used.


### Native iOS text input

The foundation and Material3 composer fields use `ChatComposerKeyboardOptions`, which enables
Compose 1.11.1's experimental native UIKit text input on iOS. Android and desktop keep
`KeyboardOptions.Default`. Custom `composerField` implementations can pass the same options to
`BasicTextField` (or copy them to customize keyboard behavior). Draft text, pending guards and
submission remain host-owned; this option does not change inset handling.

### Floating composer

`Conversation` overlays the composer on the timeline. It measures the complete composer slot
and adds its height to scrollable bottom padding: the latest message rests above the input,
while history can scroll behind its transparent margins. Latest floats above that measured
area, and quote navigation centers within the unobscured timeline. Keyboard insets still belong
to `Conversation`; do not apply another IME inset in a custom composer.

Material3 `MessageComposer` places `composerField` and `composerSend` inside one rounded surface,
with Send aligned to the bottom during multiline growth. Its four-argument overload accepts
`headerContent` inside the surface (for example `ActionComposerStatus` from the optional actions
module) and `leadingContent` outside it (for example a host attachment button). Empty slots
reserve no space. A leading control owns its trailing gap. Existing overloads and slot overrides
remain supported; the default renderer requires neither actions nor media. Custom composers
own their surfaces, labels and controls; paint only occupied shapes to retain the floating effect.

Composer defaults use the 12dp outer gutter (26pt on iOS), 8dp vertical margin, 4dp inner surface inset,
28dp corner radius, 48dp controls and 22dp Tabler icons. The field remains bounded to five visible
lines and scrolls longer drafts; editing/pending/Send guards continue through `ComposerContent`.

The default floating surface has no outline. Placeholder and editable text share a vertically
centered decoration area; multiline content retains bottom-aligned Send. Optional actions render
reply/edit cancellation as an accessible X with the existing guarded command and replaceable
control slot. Cancelling reply leaves the draft untouched.

`ChatScaffold` overlays its transparent default navigation header. It consumes native safe insets
once and supplies the occupied top/bottom area through `LocalConversationViewportPadding` in
chat-ui. `Conversation` uses top padding inside its scrollable timeline, includes bottom clearance
in the measured composer area, and centers replies between the header and composer. Standalone
Conversation defaults to zero chrome padding. Custom composers should avoid adding a second
bottom margin when the supplied safe-area spacing is already sufficient; Material3 uses the
larger of its 8dp outer margin and the supplied bottom spacing when the keyboard is closed.
When the IME is visible, Material3 retains an explicit 8dp gap above it (12dp to the inset
editable field). Raw IME visibility selects this decorative gap; inset ownership stays unchanged.

The scaffold's content Column now applies horizontal padding only. Call Conversation directly.
For non-conversation content (for example a host's empty navigation page), apply
`Modifier.padding(LocalConversationViewportPadding.current)` yourself. Custom top/bottom bars
still own their backgrounds and behavior; no host navigation or state ownership changes.

The iOS default scaffold uses a 26pt bottom gutter for the floating surface, matching its side
gutters, while retaining top/horizontal safe insets and the full IME inset. Android keeps native
navigation-bar clearance. Explicit `contentWindowInsets` overrides remain host-owned. The field's
4dp inner inset makes its editable bounds equally inset at the sides and bottom (30pt on iOS).
The visible keyboard still gets the explicit 8dp gap.

Reply context omits the redundant “Replying to” label. `ActionStyle.composerPadding` defaults to
16dp start, 8dp top and 4dp end, aligning the dismiss control's center with Send while centering
it vertically on the quote preview. Hosts can replace the quote/control slots or adjust that
padding without affecting cancel guards or draft retention.
