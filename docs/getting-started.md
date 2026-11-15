# Get started

This repository is currently distributed as source and local staging artifacts. There is no public Maven release or live documentation URL. Use one aligned version of all Chat modules.

## Work inside the checkout

The root settings already include the five library projects. Add the presentation module to your host's shared source set:

```kotlin
commonMain.dependencies {
    implementation(project(":chat:material3"))
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

## Use the local Maven stage

To exercise integration from another build, stage only the modules you need on your machine:

```sh
./gradlew :chat:core:publishAllPublicationsToStagingRepository \
  :chat:ui:publishAllPublicationsToStagingRepository \
  :chat:material3:publishAllPublicationsToStagingRepository
```

This writes to this checkout's `build/staging`. In the consuming build, use that absolute directory as a Maven repository:

```kotlin
repositories {
    maven { url = uri("/absolute/path/to/Chat/build/staging") }
    google()
    mavenCentral()
}
```

The corresponding dependency is `dev.forntoh.chat.candidate:chat-material3:0.1.0-local`. These replaceable local coordinates are not publicly available. The separate `consumers` build contains integrations that resolve staged artifacts rather than producer project dependencies.

See [sample setup](sample.md) for the pinned tools and [support](support.md) for configured targets and runtime limits.
