---
title: Getting started
description: Add Compose Chat and build your first working conversation.
---

# Your first conversation

This guide builds a small, local chat screen. You can type a message, press Send and see it in the timeline. Once it works, replace the local handler with your app’s state holder and service.

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

## 2. Create a working screen

Place this in your shared source set and call `FirstChat()` from your app’s Compose content:

```kotlin
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.forntoh.chat.core.ConversationEvent
import dev.forntoh.chat.core.ConversationState
import dev.forntoh.chat.core.Delivery
import dev.forntoh.chat.core.Message
import dev.forntoh.chat.core.Participant
import dev.forntoh.chat.material3.ChatScaffold
import dev.forntoh.chat.material3.ChatTheme
import dev.forntoh.chat.ui.Conversation

@Composable
fun FirstChat() {
    val me = remember { Participant("me", "You") }
    var nextId by remember { mutableStateOf(1L) }
    var state by remember {
        mutableStateOf(
            ConversationState(
                conversationId = "weekend",
                currentParticipantId = me.id,
                messages = listOf(
                    Message(
                        id = "welcome",
                        sender = Participant("scout", "Scout"),
                        timestampEpochMillis = 0L,
                        displayTime = "Now",
                        text = "Where shall we go this weekend?",
                        delivery = Delivery.Sent,
                    ),
                ),
            ),
        )
    }

    val onEvent: (ConversationEvent) -> Unit = event@{ event ->
        if (event.conversationId != state.conversationId) return@event
        when (event) {
            is ConversationEvent.DraftChanged -> {
                state = state.copy(draft = state.draft.copy(text = event.text))
            }
            is ConversationEvent.SendDraft -> {
                val draft = state.draft
                if (draft.submitting || !draft.hasContent ||
                    event.revision != draft.revision ||
                    event.text != draft.text || event.attachments != draft.attachments
                ) return@event
                val row = Message(
                    id = "local-${nextId++}",
                    sender = me,
                    timestampEpochMillis = 0L,
                    displayTime = "Now",
                    text = event.text,
                    delivery = Delivery.Sent,
                    attachments = event.attachments,
                )
                state = state.copy(
                    messages = state.messages + row,
                    draft = draft.copy(
                        text = "",
                        attachments = emptyList(),
                        revision = draft.revision + 1,
                    ),
                )
            }
            is ConversationEvent.RetryMessage -> Unit
        }
    }

    ChatTheme {
        ChatScaffold(title = { Text("Weekend plans") }) {
            Conversation(state = state, onEvent = onEvent)
        }
    }
}
```

This example accepts messages locally, so it immediately marks them `Sent`. It keeps state in `remember`, which lasts only while this screen stays mounted. The sample’s `Now` label and zero timestamps are placeholders for your app’s localized times.

## 3. Connect your app

Move the state and event handler into your ViewModel or another retained state holder. Keep the same `Conversation(state, onEvent)` call.

For a network send, insert one stable message ID with `Delivery.Sending`, clear the accepted draft with a new revision, then update that row to `Sent` or `Failed` when the service completes. The UI offers Retry for your own failed messages; your handler retries the existing row.

Continue with [Integration](integration.md) for state ownership, deduplication and service errors, or [Customization](customization.md) to make the screen match your app.
