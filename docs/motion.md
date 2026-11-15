# Conversation motion

`chat-ui` owns timeline insertion, composer sizing and Latest motion. Optional Material3 owns
bubble highlight and delivery rendering; optional actions/media own their preview affordances.
`chat-core` has no Compose or motion types. These effects present host snapshots and never send,
accept/clear a draft, retry, edit, delete, upload or assert delivery from an animation callback.

Provide `LocalChatMotion` around the conversation and any optional content:

```kotlin
CompositionLocalProvider(
    LocalChatMotion provides ChatMotion(
        controlMillis = 220,
        sizeMillis = 280,
        insertionMillis = 280,
        latestMillis = 280,
    )
) {
    Conversation(state, onEvent)
}
```

`ChatMotion(enabled = false)` immediately presents current content, including when disabled during
an active resize, Latest return or quote navigation. Disabled quote navigation positions and centers
the target immediately; disabling during travel cancels that travel and positions its latest target.
Durations are nonnegative animation-clock milliseconds;
`latestMillis` is restricted to 0..360. Compose's platform duration scale applies to finite
animations, with zero settling immediately. Platforms without a supplied system duration scale
use normal timing; hosts can provide the deterministic disabled setting. Custom slot animations
and native controls remain the host's responsibility.

| Presentation | Default | Reason |
| --- | --- | --- |
| Tail row opacity | 240ms | Brief insertion using stable message identity, without guessing a draft-to-row match |
| Composer height | 240ms, bottom anchored | Smooth line/preview growth while the input and Send stay at the bottom |
| Reply/edit/attachment preview | 180ms fade, coordinated with composer sizing | Enter/exit context without owning another draft |
| Action focus, Latest control, loading/pending feedback, bubble highlight, delivery status | 180ms | Small finite feedback, preserving existing control hit areas |
| Latest travel | At most 360ms | Retains the accepted bounded travel through virtualized history |

All use `FastOutSlowInEasing`, overridable through `ChatMotion.easing`, without bounce.
Preview exit fades for 180ms, then its removal can resize the composer for 240ms; the complete
sequential removal can therefore take 420ms. Exiting pixels are not retained interactive UI:
immutable presentation snapshots (quote/status/target/image data) survive until exit completes;
semantics and pointer activation are removed immediately, and existing snapshot guards still
reject stale callbacks. Conversation identity keys dispose all exiting composer content on switch.

Each draft attachment transitions by stable attachment ID, including insertion/removal while other
attachments remain. Retained presentation is discarded after the fade; rendering never caches a
mutable composable lambda as a snapshot. Custom uses of `ChatMotionVisibility(value)` must render
from its supplied immutable value, not from captured live host state.

Initial mounted rows, history prepends and remounted timelines are opaque. Subsequent IDs after
a previously rendered tail fade; an arrival into an already mounted empty timeline can fade too.
Hosts hydrating an empty timeline from history must report `HistoryStatus.Loading`; the first
snapshot resolving that loading state is opaque. Identity, host order and history status determine
admission; there is no text/timestamp matching. Lazy row virtualization does not replay an entrance
just because a row scrolls back into view. Removal is immediate, without retaining obsolete rows.

Insertion does not resize a history reader's rows or run placement animations over their anchor.
The existing following/reader policy owns scroll placement: arrivals follow only at the bottom;
history readers keep their anchor and explicitly return with Latest. Existing user drag cancellation,
removed-anchor fallback, pending guards and stable retry identity remain in force.

The shared composer size modifier sits inside native IME/window padding. Native inset travel is
not given another whole-window animation. Inputs remain controlled by the exact host draft and
respond during resizing. Optional previews retain only a finite exiting presentation, not a second
conversation or draft model. The default status renderer measures Sending/Failed in the host font,
reserves their widest visible region, then removes the Sent label after its fade. Existing measured
footer fitting and outgoing minimum sizing remain authoritative; invisible Sent status does not
force a fitting timestamp onto another line.

The existing sample's simulation debug panel cycles **Default motion**, **Custom motion**
(220/280/280/280ms), and **Motion disabled**. These are host token overrides around the library,
including its image sample; they do not patch default library styling. Service outcomes remain
simulated. Motion recordings show paced ordinary sample apps; bounded-clock component checks
establish deterministic timing/interrupt behavior, not hard wall-clock or frame-rate guarantees.
