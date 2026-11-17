<div align="center">

# Compose Chat

### A conversation worth opening.

Chat UI for **Compose Multiplatform** — ready to use, easy to make your own.

**Android · iOS · Desktop JVM**

[Get started](docs/getting-started.md) · [Explore the guides](docs/index.md) · [Run the sample](docs/sample.md)

<p align="center">
<img src="docs/assets/custom-card.png" width="260" alt="A custom trail recommendation inside a regular chat bubble">
<img src="docs/assets/forest-theme.png" width="260" alt="The same conversation with a green Forest color palette">
</p>

**Your content. Your style.** Custom messages and themes in the same conversation.

*The Scout sample, running with local messages and bundled images.*

</div>

## Build the conversation. Keep your stack.

Add a message timeline and composer to your app without building every bubble, delivery indicator and scroll interaction yourself. Start with the Material 3 components, then adapt the colors, message content and controls to your product.

Your app supplies the messages and handles user events. Connect your own API, database or AI service through ordinary Kotlin callbacks.

- **A ready-made chat screen.** Grouped messages, sender details, send status, retry controls and a growing composer.
- **Reading that stays in place.** Load earlier messages without losing your position; jump back to the latest when you’re ready.
- **Room for your product.** Replace individual slots to show a booking, recommendation or another custom message.
- **Images and actions when you need them.** Add attachment presentation or copy, quote, edit and delete flows as separate modules.
- **One shared UI.** Compose targets for Android, arm64 iOS and desktop JVM.

## Get started

Add the Material 3 module to your shared module’s `commonMain` dependencies. Use `mavenCentral()` in your dependency repositories.

```toml
# gradle/libs.versions.toml
[versions]
chat = "0.0.1"

[libraries]
chat-material3 = { module = "dev.forntoh.chat:chat-material3", version.ref = "chat" }
```

```kotlin
// Your shared module's build.gradle.kts
kotlin {
    sourceSets.commonMain.dependencies {
        implementation(libs.chat.material3)
    }
}
```

Render a conversation using your screen’s state and event handler:

```kotlin
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.forntoh.chat.core.ConversationEvent
import dev.forntoh.chat.core.ConversationState
import dev.forntoh.chat.material3.ChatScaffold
import dev.forntoh.chat.material3.ChatTheme
import dev.forntoh.chat.ui.Conversation

@Composable
fun TripChat(state: ConversationState, onEvent: (ConversationEvent) -> Unit) {
    ChatTheme {
        ChatScaffold(title = { Text("Trip chat") }) {
            Conversation(state = state, onEvent = onEvent)
        }
    }
}
```

**[Create your first working conversation →](docs/getting-started.md)** shows the state and event handler, including typing and sending. The [integration guide](docs/integration.md) covers connecting a real service and choosing modules.

## Try it yourself

From this repository, with JDK 21 and Android SDK 37 configured:

```sh
./gradlew :sample:run
```

Meet Scout: send a message, attach the bundled mountain illustration, or switch to Forest from the overflow menu. The bot shares a short burst of text, images and trail ideas, then waits. No service credentials are needed.

<img src="docs/assets/bot-chat.gif" width="320" alt="Scout chat in action: sending text, receiving images and a custom trail card">

[Sample setup for desktop, Android and iOS →](docs/sample.md)

## Find your next step

| I want to… | Read |
| --- | --- |
| Get a working screen | [Getting started](docs/getting-started.md) |
| Connect my data and choose dependencies | [Integration](docs/integration.md) |
| Change the theme or render custom messages | [Customization](docs/customization.md) |
| Add images, message actions or earlier history | [Features](docs/features.md) |
| Run the project or contribute | [Sample](docs/sample.md) · [Contributing](CONTRIBUTING.md) |

## License

[Apache 2.0](LICENSE). See [third-party notices](NOTICE) for the icons used by the Material 3 components.
