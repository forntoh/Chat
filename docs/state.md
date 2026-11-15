# Own messages, drafts and events

Retain one authoritative `ConversationState` outside the UI. Replace immutable snapshots as work completes. The timeline uses your list order and stable message IDs; timestamps do not sort rows. Preserve an ID through optimistic insertion, delivery changes and retry.

The composer renders your `Draft` and forwards exact text, including whitespace and newlines. A Send activation latches immediately. Advance `Draft.revision` on acceptance or refusal to resolve it, even when text stays unchanged. Acceptance clears the draft; refusal retains it. Eventual delivery is a separate decision.

This small offline host accepts text locally. It has no service or persistence:

```kotlin
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.forntoh.chat.core.ConversationEvent
import dev.forntoh.chat.core.ConversationState
import dev.forntoh.chat.core.Delivery
import dev.forntoh.chat.core.Draft
import dev.forntoh.chat.core.Message
import dev.forntoh.chat.core.Participant

class LocalChatHost {
    var state by mutableStateOf(ConversationState("trip", "you"))
        private set
    private var serial = 0L

    fun onEvent(event: ConversationEvent) {
        if (event.conversationId != state.conversationId) return
        when (event) {
            is ConversationEvent.DraftChanged -> {
                if (!state.draft.submitting) state = state.copy(draft = state.draft.copy(text = event.text))
            }
            is ConversationEvent.SendDraft -> {
                val draft = state.draft
                if (draft.submitting || !draft.hasContent) return
                if (event.revision != draft.revision || event.text != draft.text || event.attachments != draft.attachments) return
                serial++
                val message = Message("local-$serial", Participant("you", "You"), serial, "Now", event.text, Delivery.Sent,
                    event.attachments)
                state = state.copy(messages = state.messages + message, draft = Draft(revision = draft.revision + 1))
            }
            is ConversationEvent.RetryMessage -> Unit
        }
    }
}
```

For an asynchronous host, mark the draft `submitting = true` before starting work. Bind the result to its conversation, draft revision and exact payload. Before applying it, verify the current session and access again. On refusal, keep text/attachments, clear `submitting` and advance revision. On acceptance, insert the row and clear the draft with a new revision. Upload/send failures should update that same row rather than insert a duplicate.

Callbacks run synchronously and exceptions propagate. Return promptly and handle service failures in the host. Unmounting or switching conversation clears UI latches; it does not cancel host jobs or persist drafts for you. Cancel obsolete work at your own lifecycle boundary.
