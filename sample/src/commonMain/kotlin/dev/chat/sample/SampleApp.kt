package dev.chat.sample

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.chat.sample.resources.Res
import dev.chat.sample.resources.mountain
import dev.forntoh.chat.material3.ChatIcons
import dev.forntoh.chat.material3.ChatScaffold
import dev.forntoh.chat.material3.MessageBubble
import dev.forntoh.chat.material3.MessageComposer
import dev.forntoh.chat.material3.MessageSupplement
import dev.forntoh.chat.media.LocalMediaSlots
import dev.forntoh.chat.media.MediaAttachments
import dev.forntoh.chat.media.MediaCommand
import dev.forntoh.chat.media.MediaDraft
import dev.forntoh.chat.media.MediaTarget
import dev.forntoh.chat.media.MediaTheme
import dev.forntoh.chat.media.rememberMedia
import dev.forntoh.chat.ui.Conversation
import dev.forntoh.chat.ui.LocalConversationSlots
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun SampleApp(host: BotHost = remember { BotHost() }) {
    var alternateTheme by remember { mutableStateOf(false) }
    DisposableEffect(host) {
        host.start()
        onDispose { host.stop() }
    }
    LaunchedEffect(host) {
        while (true) {
            delay(DEMO_TICK_MILLIS.milliseconds)
            host.advanceBy(DEMO_TICK_MILLIS)
        }
    }
    SampleTheme(alternateTheme) {
        MediaTheme(textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
            BotConversation(host, alternateTheme) { alternateTheme = !alternateTheme }
        }
    }
}

@Composable
private fun BotConversation(host: BotHost, alternateTheme: Boolean, onTheme: () -> Unit) {
    val images = host.mediaHost
    val media = rememberMedia(host.state, images.media, images::onRequest, host::onEvent)
    val base = LocalConversationSlots.current
    val bodySlots = base.copy(
        messageBody = { row ->
            val suggestion = host.suggestions[row.id]
            if (suggestion == null) base.messageBody(row) else TrailCard(suggestion)
        },
    )
    ChatScaffold(
        title = {
            Column {
                Text("Scout", style = MaterialTheme.typography.titleMedium)
                Text(
                    "A little inspiration for outside",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        avatar = { SampleAvatar("S") },
        actions = {
            TextButton(
                onClick = onTheme,
                modifier = Modifier.testTag("theme-switch").semantics {
                    contentDescription = "Switch theme"
                },
            ) { Text(if (alternateTheme) "Classic theme" else "Forest theme") }
        },
    ) {
        Conversation(
            host.state,
            media.onConversationEvent,
            modifier = Modifier.weight(1f),
            slots = bodySlots.copy(
                message = { row, outgoing, retry ->
                    Column {
                        MessageBubble(row, outgoing, retry, bodySlots)
                        MessageSupplement(outgoing) { MediaAttachments(media, row, { BundledImage(it) }) }
                    }
                },
                composer = { content ->
                    val adapted = media.composer(content)
                    val mediaSlots = LocalMediaSlots.current
                    MessageComposer(
                        adapted,
                        base,
                        headerContent = {
                            MediaDraft(
                                media,
                                content,
                                { BundledImage(it) },
                                mediaSlots.copy(control = {
                                    if (it.command != MediaCommand.Select) mediaSlots.control(it)
                                }),
                            )
                        },
                        leadingContent = {
                            IconButton(
                                onClick = { media.request(MediaCommand.Select, composer = adapted) },
                                enabled = !adapted.pending,
                                modifier = Modifier.semantics { contentDescription = "Attach image" },
                            ) {
                                Icon(ChatIcons.Paperclip, null)
                            }
                        },
                    )
                },
            ),
        )
        if (images.selection != null) {
            AlertDialog(
                onDismissRequest = { images.resolveSelection() },
                title = { Text("Mountain morning") },
                text = {
                    Column {
                        Image(painterResource(Res.drawable.mountain), "Mountain morning illustration", Modifier.size(240.dp, 120.dp))
                        Text("An illustration bundled with this offline demo.", style = MaterialTheme.typography.bodyMedium)
                    }
                },
                confirmButton = { TextButton(onClick = { images.resolveSelection(choose = true) }) { Text("Attach image") } },
                dismissButton = { TextButton(onClick = { images.resolveSelection() }) { Text("Cancel") } },
            )
        }
        images.viewer?.let { target ->
            AlertDialog(
                onDismissRequest = images::closeViewer,
                title = { Text("Mountain morning") },
                text = { BundledImage(target) },
                confirmButton = { TextButton(onClick = images::closeViewer) { Text("Close") } },
            )
        }
    }
}

@Composable
private fun TrailCard(suggestion: TrailSuggestion) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.testTag("trail-suggestion"),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("TRAIL IDEA", style = MaterialTheme.typography.labelSmall)
            Text(suggestion.title, style = MaterialTheme.typography.titleMedium)
            Text(suggestion.detail, style = MaterialTheme.typography.labelMedium)
            Text(suggestion.description, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun BundledImage(target: MediaTarget) {
    if (target.attachment?.contentReference == "bundled:mountain") {
        Image(painterResource(Res.drawable.mountain), null, Modifier.size(240.dp, 120.dp))
    }
}

private const val DEMO_TICK_MILLIS = 100L
