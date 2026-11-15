# Compose Chat

A chat UI for Compose Multiplatform. Your application owns messages, drafts and service calls; Chat supplies the timeline, composer and presentation slots.

Use the default Material3 theme or bring your own renderers. Add images and message actions independently. The library targets Android, arm64 iOS and desktop JVM; see [platform support](docs/support.md) for tested environments and limits.

<img src="docs/assets/bot-chat.gif" width="320" alt="The running Scout sample: text, images, a trail card and a theme change">

## Try it

With JDK 21 and an Android SDK installed, run the offline desktop sample:

```sh
./gradlew :sample:run
```

Chat with Scout, attach the bundled illustration, and try the theme switch. Scout replies to text and occasionally shares messages, images and a custom trail card. Everything stays in memory. See [sample setup](docs/sample.md) for Android, iOS and the full toolchain requirements.

## Add a conversation

Inside this source checkout, add `implementation(project(":chat:material3"))` to your host module. Then render your host snapshot and handle its events:

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
            Conversation(state, onEvent)
        }
    }
}
```

Public packages are not published yet. The [getting-started guide](docs/getting-started.md) explains source integration and the existing local Maven stage; `0.1.0-local` coordinates are not a Maven Central installation command.

Read the [full guides](docs/index.md), [module choices](docs/modules.md), [contributing guide](docs/development.md) and [Apache-2.0 license](LICENSE). Build the documentation locally with `pip install -r requirements-docs.txt` and `mkdocs serve`; there is no deployed documentation site.
