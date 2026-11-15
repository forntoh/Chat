package dev.chat.consumer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import dev.forntoh.chat.core.ConversationEvent
import dev.forntoh.chat.core.ConversationState
import dev.forntoh.chat.media.MediaAttachments
import dev.forntoh.chat.media.MediaDraft
import dev.forntoh.chat.media.MediaRequest
import dev.forntoh.chat.media.MediaState
import dev.forntoh.chat.media.rememberMedia
import dev.forntoh.chat.ui.Conversation
import dev.forntoh.chat.ui.DefaultComposer
import dev.forntoh.chat.ui.DefaultMessage

/** Standalone foundation-only consumer; no producer projects, actions or Material3. */
@Composable
fun MediaConversation(
    state: ConversationState,
    mediaState: MediaState,
    onEvent: (ConversationEvent) -> Unit,
    onRequest: (MediaRequest) -> Unit,
) {
    val media = rememberMedia(state, mediaState, onRequest, onEvent)
    Conversation(
        state,
        media.onConversationEvent,
        messageContent = { row, _, _ ->
            Column {
                DefaultMessage(row, null)
                MediaAttachments(media, row, renderer = { BasicText("Host bundled preview") })
            }
        },
        composerContent = { content ->
            Column {
                MediaDraft(media, content)
                DefaultComposer(media.composer(content))
            }
        },
    )
}
