---
title: Customization
description: Match your palette and render your own message content.
---

# Make it feel like your app

Start by changing colors. Replace individual rendering slots when you need more control; the conversation’s state and event handler stay the same.

```{figure} assets/forest-theme.png
:width: 320px
:alt: Scout conversation with a green Forest theme and a retained message draft

The sample’s Forest theme changes the palette without replacing the conversation.
```

## Set your palette

`ChatTheme` accepts a Material 3 color scheme, typography and slots. For example, apply your product’s primary color:

```kotlin
import androidx.compose.ui.graphics.Color
import dev.forntoh.chat.material3.ChatTheme
import dev.forntoh.chat.material3.chatColorScheme

ChatTheme(
    colorScheme = chatColorScheme().copy(primary = Color(0xFF356859)),
) {
    // Your ChatScaffold and Conversation.
}
```

`chatColorScheme(darkTheme = true)` supplies the dark palette. Keep theme choice in your app’s state and change the theme around the existing conversation, rather than creating another host or clearing its draft.

## Render a custom message

A message body can contain a product card, booking summary or recommendation. Keep domain data in your app and look it up by message ID. Copy the current slots so ordinary messages retain their default rendering.

This example adds a small recommendation to messages present in a host-provided `recommendations` map:

```kotlin
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.forntoh.chat.core.ConversationEvent
import dev.forntoh.chat.core.ConversationState
import dev.forntoh.chat.ui.Conversation
import dev.forntoh.chat.ui.LocalConversationSlots

@Composable
fun RecommendationsChat(
    state: ConversationState,
    onEvent: (ConversationEvent) -> Unit,
    recommendations: Map<String, String>,
) {
    val defaults = LocalConversationSlots.current
    val slots = defaults.copy(
        messageBody = { message ->
            val recommendation = recommendations[message.id]
            if (recommendation == null) {
                defaults.messageBody(message)
            } else {
                Column {
                    Text("RECOMMENDED FOR YOU")
                    Text(recommendation)
                }
            }
        },
    )
    Conversation(state = state, onEvent = onEvent, slots = slots)
}
```

Call this inside `ChatTheme` and a bounded screen such as `ChatScaffold`. The surrounding bubble, metadata and delivery status still come from the default theme.

```{figure} assets/custom-card.png
:width: 320px
:alt: A lakeside trail recommendation rendered inside the Scout conversation

The Scout sample uses this same message-body seam for a trail card.
```

## Choose the right slot

| Change | Override |
| --- | --- |
| Content inside the existing bubble | `messageBody` |
| Sender/time or delivery presentation | `messageMetadata` or `messageDelivery` |
| The whole row, including layout | `message` or `Conversation.messageContent` |
| Composer layout | `composer` or `Conversation.composerContent` |
| Field or Send control only | `composerField` or `composerSend` |
| Earlier-history or return-to-latest feedback | `history`, `historyFeedback`, `latest` or `latestLabel` |

An explicit renderer argument wins over the slot bundle; an explicit bundle wins over theme defaults. `presentedMessage`, when set, takes precedence over the regular `message` slot and provides transient highlight context.

For a custom composer, forward the supplied `ComposerContent` callbacks and pending state. Keeping a second text field state bypasses the shared draft and send guards.

Custom renderers own their accessibility: provide readable labels and separately named controls. If your visible content differs from `Message.text`, update `messageDescription` to describe it accurately. Avoid announcing sender, body, time and delivery twice.

For a complete card and a composer with an attachment button, see [SampleApp.kt](../sample/src/commonMain/kotlin/dev/chat/sample/SampleApp.kt).
