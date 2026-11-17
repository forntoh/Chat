---
title: Features
description: Add image attachments, message actions and earlier history.
---

# Add the features you need

Images and message actions extend the existing conversation through slots. Earlier history and motion settings are part of `chat-ui`. All use your retained application state.

## Image attachments

Add `chat-media` as described in [Integration](integration.md#choose-your-modules). It supplies image states, progress, retry and attachment controls. Your app provides image rendering, selection, upload and viewing.

```{figure} assets/user-image.png
:width: 320px
:alt: A mountain illustration sent as an image attachment in the Scout sample

User and bot images share the regular message timeline.
```

Wire the module into your screen:

1. Retain a `MediaState` alongside `ConversationState`. Put image descriptors in `Draft.attachments` or `Message.attachments`.
2. Call `rememberMedia(conversation, mediaState, onRequest, onEvent)` inside composition and pass its `onConversationEvent` to `Conversation`.
3. Render `MediaAttachments(media, message, renderer)` in your message slot and `MediaDraft(media, composer, renderer)` above the composer field. Your renderer receives a `MediaTarget` and resolves its attachment’s `contentReference`.
4. Pass `media.composer(content)` to your composer renderer so selection locks apply to the shared field and Send control.

Handle `MediaRequest` in your host:

| Command | Host responsibility |
| --- | --- |
| `Select` | Open your picker or chooser, then replace the draft attachments. |
| `Remove` | Remove the exact attachment from the current draft. |
| `Retry` | Retry an eligible failed image upload, retaining its IDs. |
| `View` | Open the ready image in your viewer. |

Advance `MediaState.revision` after resolving each request, including cancellation or denial. Selection and removal also need a fresh `Draft.revision`, even if the content is unchanged. Check the complete target and revisions again before applying asynchronous results.

Image status can be `Ready`, `Loading`, `Uploading`, `Failed`, `Missing` or `Unsupported`. Upload progress is a fraction from 0 to 1. A ready thumbnail does not imply the message was delivered. Content references are opaque host tokens; the library does not open a URI or infer resource permissions.

The [sample screen](../sample/src/commonMain/kotlin/dev/chat/sample/SampleApp.kt) shows complete slot wiring with a bundled image and a local chooser. Replace those two adapters with your platform picker and image loader.

## Message actions

Add `chat-actions` for copy, quote, edit and delete flows. The module supplies presentation and user intents; your host decides capabilities and performs the operation.

Retain an `ActionState` with a capabilities map keyed by message ID. Use `rememberMessageActions(conversation, actionState, onAction, onEvent)` to obtain the adapter. Route the conversation’s events through its `onConversationEvent`, wrap rows with `MessageActions`, and render `ActionComposerStatus` above the shared composer. Pass the adapter’s `composer(content)` to your composer renderer.

The `ActionTheme` slots let you adapt the controls to your design. The public [action components](../chat/actions/src/commonMain/kotlin/dev/forntoh/chat/actions/MessageActions.kt) document their parameters and available commands.

Copy emits a request for your clipboard adapter. Quoting retains a target ID; editing uses the ordinary draft and retains its pre-edit content for restoration. Delete uses an explicit confirmation flow. Advance the action revision after every transition, including refusals, and the draft revision whenever resolving a submission or restoring a draft.

Recheck permissions, target content and revisions in the host and backend. A visible action is a capability offered by your UI, not proof of authorization. If you combine media and actions, compose their event adapters into one chain and keep one shared draft; preserve attachments when forwarding quoted sends.

## Earlier history

History is disabled by default. To enable it, pass a `HistoryState` and handler to your existing conversation:

```kotlin
import dev.forntoh.chat.ui.Conversation
import dev.forntoh.chat.ui.HistoryRequest
import dev.forntoh.chat.ui.HistoryState
import dev.forntoh.chat.ui.HistoryStatus

// Inside your screen, with state and handlers supplied by its state holder:
Conversation(
    state = state,
    onEvent = onEvent,
    history = history,
    onLoadEarlier = onLoadEarlier,
)
```

Your retained `history` starts with `HistoryState(status = HistoryStatus.Idle)` when an earlier page exists. The handler receives a `HistoryRequest` containing the conversation ID and request revision.

Publish `Loading` before launching a request. On success, merge overlapping IDs in your host, prepend rows in display order and increment the history revision. Return to `Idle` if more pages exist, or `Exhausted` when finished. An empty page must become exhausted unless your service has a progressing cursor. On failure, publish `Error` with a fresh revision; retry requires explicit user activation.

Chat preserves the visible row and offset while you prepend. New arrivals follow a reader already at the bottom; someone reading earlier messages sees a Latest control instead. Send and Latest return to following. During initial hydration, publish `Loading` so initial rows do not appear as new arrivals.

## Motion preferences

Provide `LocalChatMotion` around the conversation to change the presentation timing or disable animation:

```kotlin
import androidx.compose.runtime.CompositionLocalProvider
import dev.forntoh.chat.ui.ChatMotion
import dev.forntoh.chat.ui.LocalChatMotion

CompositionLocalProvider(LocalChatMotion provides ChatMotion(enabled = false)) {
    // Your conversation and optional action/media content.
}
```

The default transitions are finite and respect the platform animation duration scale. Custom renderers remain responsible for their own animation and accessibility behavior.
