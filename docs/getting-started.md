# Get started

Compose Chat 0.0.1 provides a host-owned conversation for Compose Multiplatform. Use the same version for every Chat module.

## Add the dependency

Add `mavenCentral()` to your repositories and select the presentation module in your host's shared source set:

```kotlin
commonMain.dependencies {
    implementation("dev.forntoh.chat:chat-material3:0.0.1")
}
```

`chat-material3` brings `chat-ui` and `chat-core`. It does not select actions or images. [Select modules](modules.md) explains those choices.

Render the host's `ConversationState` inside a bounded screen. `ChatTheme` installs Material3 slots around the shared foundation `Conversation`; `ChatScaffold` supplies window insets and the header.

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

[Own state and events](state.md) shows how a host resolves draft submissions. Production hosts must validate identity, revision and access again before applying service results.

See [sample setup](sample.md) to run the bundled example and [support](support.md) for targets and runtime limits.
