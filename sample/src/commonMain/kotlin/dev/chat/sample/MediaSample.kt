package dev.chat.sample

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.chat.sample.resources.Res
import dev.chat.sample.resources.mountain
import dev.forntoh.chat.material3.ChatIcons
import dev.forntoh.chat.material3.ChatScaffold
import dev.forntoh.chat.material3.MessageComposer
import dev.forntoh.chat.media.DefaultMediaFeedback
import dev.forntoh.chat.media.LocalMediaSlots
import dev.forntoh.chat.media.MediaAttachments
import dev.forntoh.chat.media.MediaCommand
import dev.forntoh.chat.media.MediaControlContent
import dev.forntoh.chat.media.MediaDraft
import dev.forntoh.chat.media.MediaTarget
import dev.forntoh.chat.media.MediaTheme
import dev.forntoh.chat.media.rememberMedia
import dev.forntoh.chat.ui.Conversation
import dev.forntoh.chat.ui.LocalConversationSlots
import org.jetbrains.compose.resources.painterResource

@Composable
fun MediaSample(host: FakeMediaHost = remember { FakeMediaHost() }, manualService: Boolean = false, onBack: (() -> Unit)? = null) {
    var custom by remember { mutableStateOf(false) }
    var denyNextSelection by remember { mutableStateOf(false) }
    var manualCompletion by remember { mutableStateOf(manualService || host.state.messages.isNotEmpty()) }
    fun finishUploads() {
        while (host.upload != null) host.completeUpload(true)
    }
    val media = rememberMedia(host.state, host.media, { request ->
        host.onRequest(request)
        if (request.command == MediaCommand.Select && denyNextSelection) {
            denyNextSelection = false
            host.resolveSelection(denied = true)
        }
        if (!manualCompletion && request.command == MediaCommand.Retry) finishUploads()
    }, { event ->
        host.onEvent(event)
        if (!manualCompletion && host.submission != null) {
            host.resolveDraft(true)
            finishUploads()
        }
    })
    val renderer: @Composable (MediaTarget) -> Unit = { target ->
        if (target.attachment?.contentReference == "bundled:mountain") {
            Image(painterResource(Res.drawable.mountain), null, Modifier.size(240.dp, 120.dp).clip(RoundedCornerShape(16.dp)))
        }
    }
    var menu by remember { mutableStateOf(false) }
    SampleTheme(custom) {
        MediaTheme(
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
            slots = LocalMediaSlots.current.copy(feedback = { feedback ->
                Column {
                    if (custom) Text("Host attachment feedback", style = MaterialTheme.typography.labelSmall)
                    DefaultMediaFeedback(feedback)
                }
            }, control = { button ->
                if (button.command == MediaCommand.Select) {
                    IconButton(
                        onClick = button.onClick,
                        enabled = button.enabled,
                        modifier = Modifier.semantics { contentDescription = "Attach image" },
                    ) { Icon(ChatIcons.Paperclip, null) }
                } else {
                    TextButton(onClick = button.onClick, enabled = button.enabled) {
                        Text(
                            when (button.command) {
                                MediaCommand.Remove -> "Remove image"
                                MediaCommand.Retry -> "Retry upload"
                                MediaCommand.View -> "View image"
                                MediaCommand.Select -> "Attach image"
                            },
                        )
                    }
                }
            }),
        ) {
            ChatScaffold(
                title = {
                    Column {
                        Text("Shared images", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Share a moment with the team",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                avatar = { SampleAvatar("SI") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.semantics {
                                contentDescription = "Back to conversation"
                            },
                        ) { Icon(ChatIcons.ArrowLeft, null) }
                    }
                },
                actions = {
                    Box {
                        IconButton(
                            onClick = { menu = true },
                            modifier = Modifier.testTag("media-demo-menu")
                                .semantics { contentDescription = "Image conversation options" },
                        ) { Icon(ChatIcons.DotsVertical, null) }
                        DropdownMenu(menu, onDismissRequest = { menu = false }) {
                            @Composable fun entry(label: String, action: () -> Unit) {
                                DropdownMenuItem(text = { Text(label) }, onClick = {
                                    menu = false
                                    action()
                                })
                            }
                            entry(if (custom) "Default image UI" else "Custom image UI") { custom = !custom }
                            entry("Reset image simulator", host::resetSimulation)
                            entry("Finish image loading", host::finishLoading)
                            entry("Revoke first image") { host.revokeResource() }
                            entry("Simulate image render failure") { host.failRendering() }
                            entry("Advance upload progress", host::advanceUpload)
                            entry("Switch media room", host::switchRoom)
                            entry(if (manualCompletion) "Automatic service" else "Manual service") { manualCompletion = !manualCompletion }
                            entry("Deny next selection") { denyNextSelection = true }
                            if (host.submission != null) {
                                entry("Accept image draft") { host.resolveDraft(true) }
                                entry("Keep draft") { host.resolveDraft(false) }
                            }
                            if (host.upload != null) {
                                entry("Complete upload") { host.completeUpload(true) }
                                entry("Fail upload") { host.completeUpload(false) }
                            }
                        }
                    }
                },
            ) {
                val base = LocalConversationSlots.current
                Conversation(
                    host.state,
                    media.onConversationEvent,
                    modifier = Modifier.weight(1f),
                    slots = base.copy(
                        message = { row, outgoing, _ ->
                            Column {
                                base.message(row, outgoing, null)
                                dev.forntoh.chat.material3.MessageSupplement(outgoing) {
                                    MediaAttachments(media, row, renderer)
                                }
                            }
                        },
                        composer = { content ->
                            Column {
                                val mediaSlots = LocalMediaSlots.current
                                val adapted = media.composer(content)
                                MessageComposer(
                                    adapted,
                                    base,
                                    headerContent = {
                                        MediaDraft(
                                            media,
                                            content,
                                            renderer,
                                            mediaSlots.copy(control = {
                                                if (it.command != MediaCommand.Select) mediaSlots.control(it)
                                            }),
                                        )
                                    },
                                    leadingContent = {
                                        mediaSlots.control(
                                            MediaControlContent(MediaCommand.Select, !adapted.pending) {
                                                media.request(MediaCommand.Select, composer = adapted)
                                            },
                                        )
                                    },
                                )
                            }
                        },
                    ),
                )
                if (host.selection != null) {
                    AlertDialog(
                        onDismissRequest = { host.resolveSelection() },
                        title = { Text("Choose an image") },
                        text = {
                            Column {
                                Text("This sample uses a bundled image. Your app supplies its own picker and upload service.")
                            }
                        },
                        confirmButton = { TextButton(onClick = { host.resolveSelection(choose = true) }) { Text("Choose bundled image") } },
                        dismissButton = { TextButton(onClick = { host.resolveSelection() }) { Text("Cancel selection") } },
                    )
                }
                host.viewer?.let { target ->
                    AlertDialog(
                        onDismissRequest = host::closeViewer,
                        title = { Text("Shared image") },
                        text = { renderer(target) },
                        confirmButton = { TextButton(onClick = host::closeViewer) { Text("Close viewer") } },
                    )
                }
            }
        }
    }
}
