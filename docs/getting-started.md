---
title: Getting started
description: Add Compose Chat to your existing messaging models and state holder.
---

# Your first conversation

Use your existing users, messages, ViewModel and backend. This guide adds a presentation mapping and a Compose screen around the conversation data your app already owns.

## 1. Add Chat

Start with a Compose Multiplatform module. Chat 0.0.1 uses Kotlin 2.4.0 and Compose Multiplatform 1.11.1; the Material 3 components use Material 3 1.11.0-alpha07.

Declare the dependency in your version catalog:

```toml
# gradle/libs.versions.toml
[versions]
chat = "0.0.1"

[libraries]
chat-material3 = { module = "dev.forntoh.chat:chat-material3", version.ref = "chat" }
```

Ensure `mavenCentral()` is included in `dependencyResolutionManagement.repositories` in `settings.gradle.kts`. Then add the catalog alias to your shared module:

```kotlin
kotlin {
    sourceSets.commonMain.dependencies {
        implementation(libs.chat.material3)
    }
}
```

`chat-material3` brings in the conversation UI and core models. You do not need to add all three separately. Your app also needs its normal Compose and Material 3 dependencies for the composables it uses directly.

(map-existing-models)=
## 2. Map your existing models

Keep your app’s users, messages, repository and ViewModel. Chat supplies the conversation UI; its `Participant` and `Message` types are presentation values you map to at the screen boundary. Your domain models do not need to implement a library interface or change their database schema.

For example, suppose your app already has models with these fields. These declarations only describe the example’s existing app types; use your own equivalents:

```kotlin
// Existing application models, independent of Chat.
data class User(val id: String, val name: String)

enum class SendStatus { Pending, Delivered, Failed }

data class ChatMessage(
    val id: String,
    val author: User,
    val sentAtMillis: Long,
    val body: String,
    val status: SendStatus,
)
```

Map those models to UI values. Preserve the original IDs so outgoing alignment, retry and custom-content lookups refer to the same users and messages your app already knows:

```kotlin
import dev.forntoh.chat.core.Delivery
import dev.forntoh.chat.core.Message as UiMessage
import dev.forntoh.chat.core.Participant

fun User.toChatParticipant(): Participant = Participant(id = id, displayName = name)

fun ChatMessage.toChatMessage(formatTime: (Long) -> String): UiMessage = UiMessage(
    id = id,
    sender = author.toChatParticipant(),
    timestampEpochMillis = sentAtMillis,
    displayTime = formatTime(sentAtMillis),
    text = body,
    delivery = when (status) {
        SendStatus.Pending -> Delivery.Sending
        SendStatus.Delivered -> Delivery.Sent
        SendStatus.Failed -> Delivery.Failed
    },
)
```

Then render the messages your app already observes. This screen receives the current user and conversation from your state holder, plus a retained UI draft and an event handler:

```kotlin
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.forntoh.chat.core.ConversationEvent
import dev.forntoh.chat.core.ConversationState
import dev.forntoh.chat.core.Draft
import dev.forntoh.chat.material3.ChatScaffold
import dev.forntoh.chat.material3.ChatTheme
import dev.forntoh.chat.ui.Conversation

@Composable
fun AppChatScreen(
    conversationId: String,
    title: String,
    currentUser: User,
    messages: List<ChatMessage>,
    draft: Draft,
    formatTime: (Long) -> String,
    onEvent: (ConversationEvent) -> Unit,
) {
    val state = ConversationState(
        conversationId = conversationId,
        currentParticipantId = currentUser.id,
        messages = messages.map { it.toChatMessage(formatTime) },
        draft = draft,
    )

    ChatTheme {
        ChatScaffold(title = { Text(title) }) {
            Conversation(state = state, onEvent = onEvent)
        }
    }
}
```

Call `AppChatScreen` with the user and messages from your existing ViewModel or state holder. Pass your app’s localized time formatter and handle `onEvent` there. When your repository emits updated messages, publish a new list; the screen maps it to a new UI snapshot. No separate in-memory message store is needed.

`Draft` is UI state for composing and acknowledging submissions; retain it in your state holder and update it in response to events. If you already have a draft model, map its text and attachments too, retaining the Chat revision and submission state alongside it. For image messages, map your existing media records to `Attachment` descriptors; keep file loading and uploads in your app. See [the media guide](features.md#image-attachments).

## 3. Route events to your app

Pass your existing state holder’s event handler as `onEvent`. `DraftChanged` updates the retained draft. `SendDraft` carries the exact text, attachments and draft revision: validate them against current state, then invoke your existing send command. On acceptance, clear that draft and increment its revision; on refusal, preserve the content and still increment the revision. Keep authorization and request deduplication in your application and backend.

Your repository continues to own messages. Map its pending, delivered and failed statuses to `Delivery.Sending`, `Sent` and `Failed`. `RetryMessage.messageId` is the original domain ID, so use it to find the failed outgoing message and call your existing retry command. Do not insert a second row on retry.

Continue with [Integration](integration.md) for the full event and state contract, or [Customization](customization.md) to make the screen match your app.
