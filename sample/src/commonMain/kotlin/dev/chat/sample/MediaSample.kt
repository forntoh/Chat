package dev.chat.sample

import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.chat.ui.*
import dev.chat.media.*
import dev.chat.sample.resources.Res
import dev.chat.sample.resources.mountain
import org.jetbrains.compose.resources.painterResource

/** Shared foundation-only media integration. Its renderer opens only one compiled bundled resource. */
@Composable
fun MediaSample(host: FakeMediaHost = remember { FakeMediaHost() }) {
    val media = rememberMedia(host.state, host.media, host::onRequest, host::onEvent)
    val renderer: @Composable (MediaTarget) -> Unit = { target ->
        if (target.attachment?.contentReference == "bundled:mountain")
            Image(painterResource(Res.drawable.mountain), null, Modifier.size(240.dp, 120.dp))
    }
    Column(Modifier.fillMaxSize().background(Color.White).padding(12.dp)) {
        BasicText("Host-owned images · bundled sample")
        FlowRow {
            DefaultMediaSampleButton("Switch media room", host::switchRoom)
            if (host.selection != null) {
                DefaultMediaSampleButton("Choose bundled image") { host.resolveSelection(choose = true) }
                DefaultMediaSampleButton("Cancel selection") { host.resolveSelection() }
                DefaultMediaSampleButton("Deny selection") { host.resolveSelection(denied = true) }
            }
            if (host.submission != null) {
                DefaultMediaSampleButton("Accept image draft") { host.resolveDraft(true) }
                DefaultMediaSampleButton("Keep draft") { host.resolveDraft(false) }
            }
            if (host.upload != null) {
                DefaultMediaSampleButton("Complete upload") { host.completeUpload(true) }
                DefaultMediaSampleButton("Fail upload") { host.completeUpload(false) }
            }
        }
        host.viewer?.let { target ->
            BasicText("Host viewer · ${target.message?.id} / ${target.attachment?.id}")
            renderer(target)
            DefaultMediaSampleButton("Close viewer", host::closeViewer)
        }
        // Reset presentation only; this demo proves media without actions or Material3 renderers.
        ConversationTheme(ConversationSlots()) {
            val base = LocalConversationSlots.current
            Conversation(host.state, media.onConversationEvent, modifier = Modifier.weight(1f),
                slots = base.copy(
                    message = { row, outgoing, retry -> Column { base.message(row, outgoing, null); MediaAttachments(media, row, renderer) } },
                    composer = { content -> Column { MediaDraft(media, content, renderer); base.composer(media.composer(content)) } },
                ))
        }
    }
}

@Composable
private fun DefaultMediaSampleButton(label: String, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Box(Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
        .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick).padding(8.dp)) { BasicText(label) }
}
