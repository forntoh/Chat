# Motion

`LocalChatMotion` supplies finite presentation timing to the conversation and optional renderers. The defaults use 180ms controls, 240ms insertion/content sizing and a capped 360ms return to latest. Native inset changes remain outside content-size animation.

```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import dev.forntoh.chat.ui.ChatMotion
import dev.forntoh.chat.ui.LocalChatMotion

@Composable
fun WithoutChatMotion(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalChatMotion provides ChatMotion(enabled = false), content = content)
}
```

Disabled motion or a zero platform duration scale settles immediately. Custom renderers can read the same tokens but own their animations. No animation completion is a service result, and motion never accepts a draft or updates delivery.

Tail insertion uses stable IDs. Initial rows, history prepends and remounts stay opaque. Reader drag, navigation replacement and conversation disposal cancel obsolete scroll work. These are presentation behaviors, not frame-rate or wall-clock guarantees.
