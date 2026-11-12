# Conversation customization

Use `chat-ui` for a layout with host-rendered content and no Material3 dependency. Use optional
`chat-material3` for ready-to-use rows, composer and history feedback. Core state/events stay
host-owned; neither integration loads resources, contacts services or persists drafts.

The sample's light baseline adapts the message hierarchy, sender metadata, rounded surfaces
and distinct composer area from [Jetchat](https://github.com/android/compose-samples/tree/main/Jetchat).
It is an original CMP implementation using system fonts, without Jetchat's Android navigation,
dynamic-color integration, media panels or assets. “Host theme” switches colors and typography;
“Custom UI” independently mixes a host incoming row with default outgoing rows and a decorated
default composer. Both switches keep one Conversation call mounted.

```kotlin
val labels = ConversationStrings(
    message = "Compose", messageDescription = "Conversation draft",
    send = "Post", sendDescription = "Post draft",
    retry = "Try again", retryDescription = "Retry delivery",
)
Conversation(
    state = hostState,
    onEvent = ::handleIntent,
    strings = labels,
    composerContent = { content ->
        Column {
            Text("Your conversation") // host-owned copy
            MessageComposer(content, labels)
        }
    },
)
```

The required `strings` argument selects the new overload. Null content slots resolve to defaults
with those strings; an explicit slot takes precedence and must pass strings to any standalone
renderer it delegates to. `MessageBubble`, `MessageComposer` and `HistoryStatusContent` have
strings-aware overloads. Existing positional, named and trailing-lambda entry points remain.
Control descriptions default to the visible label when constructed. When copying a strings
value to change a label, update its description too where needed. Feedback strings cover every
history state, delivery state, empty timeline and latest/new-arrival action.

The default message text group announces sender, body, host-formatted time and localized delivery
in that order once; Retry is a separate actionable control. Custom rows own equivalent semantics
and their own labels. Custom composers display the host draft, use `pending`/`canSend` and invoke
only the provided guarded callbacks for explicit input. Theme/content changes do not accept a
draft or emit a send. Hosts resolve submissions with new draft revisions; delivery is separate.
Remembered row state is keyed by conversation and message identity in the shared layout.

At default LTR/text size, the composer reserves a separate Send control while its multiline input
scrolls after five lines. Long timeline rows scroll to reveal their delivery actions. The sample
uses only an in-memory fake service; it does not prove real delivery or persistence.
