package dev.chat.consumer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import dev.chat.core.*
import dev.chat.ui.*
import dev.chat.media.*

/** Standalone foundation-only consumer; no producer projects, actions or Material3. */
@Composable
fun MediaConversation(state: ConversationState, mediaState: MediaState,
    onEvent: (ConversationEvent) -> Unit, onRequest: (MediaRequest) -> Unit) {
    val media = rememberMedia(state, mediaState, onRequest, onEvent)
    Conversation(state, media.onConversationEvent,
        messageContent = { row, _, _ -> Column { DefaultMessage(row, null)
            MediaAttachments(media, row, renderer = { BasicText("Host bundled preview") }) } },
        composerContent = { content -> Column { MediaDraft(media, content); DefaultComposer(media.composer(content)) } })
}
