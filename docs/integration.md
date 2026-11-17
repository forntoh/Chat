---
title: Integration
description: Choose modules and connect your application state to the conversation.
---

# Connect your app

Chat renders your conversation state and emits user intent. Keep your API client, database and retained state in your application layer. The UI does not create accounts, persist messages or send requests on your behalf.

## Choose your modules

Start with `chat-material3` for the default look. Add the optional modules only for the features you use. All modules use version **0.0.1** and group **`dev.forntoh.chat`**.

| Module | Use it for | Depends on |
| --- | --- | --- |
| `chat-core` | Message, participant, draft, attachment and event models without Compose UI | Kotlin |
| `chat-ui` | Conversation behavior with foundation renderers and replaceable slots | `chat-core` |
| `chat-material3` | Material 3 bubbles, composer, theme and screen scaffold | `chat-ui` |
| `chat-media` | Image presentation, draft attachments, progress and media intents | `chat-ui` |
| `chat-actions` | Copy, quote, edit and delete presentation and intents | `chat-ui` |

Media and actions are independent of Material 3 and of each other. For a custom design system, use `chat-ui` with your own slots instead of `chat-material3`.

Add optional dependencies to your catalog, reusing the `chat` version:

```toml
[libraries]
chat-media = { module = "dev.forntoh.chat:chat-media", version.ref = "chat" }
chat-actions = { module = "dev.forntoh.chat:chat-actions", version.ref = "chat" }
```

Then add `implementation(libs.chat.media)` or `implementation(libs.chat.actions)` to your shared module’s `commonMain.dependencies`.

## Adapt your existing models

Keep your existing user and message types as the source of truth. Map them to Chat’s `Participant` and `Message` presentation values at the UI boundary, preserving user IDs and message IDs. Your models do not need to inherit from Chat types, and your repository schema stays in your application.

The [getting-started example](getting-started.md#map-existing-models) maps an existing `User` and `ChatMessage`, including the author, localized time and delivery status, then passes the result to `ConversationState`. The current signed-in user’s domain ID becomes `currentParticipantId`. Map media records to attachment descriptors when needed; keep domain-specific card data accessible by the same message IDs for custom slots.

Route UI events back to your existing commands: send accepted draft content through your send operation, resolve retry IDs against your domain messages, and let repository updates produce the next UI snapshot. Retain the composer’s `Draft` separately, or adapt your existing draft while retaining Chat’s revision/submission state. UI values are not a replacement persistence model.

## Publish immutable snapshots

A `ConversationState` has a conversation ID, current participant ID, draft and messages. Replace the snapshot when data changes; do not mutate a list already handed to the UI.

Keep message IDs stable from optimistic insertion through delivery and retry. IDs must be unique within a conversation snapshot. Supply messages in display order, oldest to newest: Chat does not sort by timestamp or merge duplicate rows. Format `displayTime` in your application’s locale.

The current participant ID determines outgoing alignment. Your application and backend must still enforce who can read, send, edit or delete each message.

## Handle typing, sending and retry

| Event | Your handler should… |
| --- | --- |
| `DraftChanged` | Publish the new text in the retained draft, preserving edit order. |
| `SendDraft` | Check conversation ID, draft revision, text and attachments; accept or refuse that exact submission. |
| `RetryMessage` | Check that the existing row is yours and failed, then retry it without inserting another row. |

On acceptance, clear the draft and increment `Draft.revision`. On refusal, retain its content and still increment the revision to unlock sending. Use `Draft.submitting` while acceptance is unresolved. Draft acceptance is separate from message delivery.

Chat blocks repeated sends within the mounted UI until the revision changes. Your host must also deduplicate requests across navigation, remounts and retries. Retain monotonically advancing revisions for each conversation session.

## Connect a service safely

Return promptly from the event callback and launch service work in a host-owned coroutine scope. Capture the conversation and request identity before launching; validate them again before applying a result. Cancel or invalidate obsolete work when leaving a conversation.

If the service rejects a send after local acceptance, replace the matching row’s delivery with `Failed`. Preserve its ID and content so Retry has a meaningful target. If a callback throws, the exception propagates; resolve local failures in your host and publish the next draft revision.

Keep drafts outside `Conversation` to retain them across navigation. Use your own persistence layer if they must survive process termination.

## Give the conversation room

`ChatScaffold` supplies a bounded viewport and coordinates the header and safe insets. With your own shell, give `Conversation` a bounded height, for example `Modifier.weight(1f)` in a full-height column, and handle your platform’s window insets.

The conversation accommodates the software keyboard and measures the composer for timeline clearance. Keep it mounted during theme or title changes to preserve its reading position. Changing the conversation ID starts a new UI session.

Next: [customize the appearance](customization.md) or [add images, actions and history](features.md).
