package dev.chat.sample

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import dev.chat.ui.*
import dev.chat.media.*
import dev.chat.material3.MessageComposer
import dev.chat.sample.resources.Res
import dev.chat.sample.resources.mountain
import org.jetbrains.compose.resources.painterResource

@Composable
fun MediaSample(
    host: FakeMediaHost = remember { FakeMediaHost() },
    manualService: Boolean = false,
    onBack: (() -> Unit)? = null
) {
    var denyNextSelection by remember { mutableStateOf(false) }
    var manualCompletion by remember { mutableStateOf(manualService) }
    val media = rememberMedia(host.state, host.media, { request ->
        host.onRequest(request)
        if (request.command == MediaCommand.Select && denyNextSelection) {
            denyNextSelection = false
            host.resolveSelection(denied = true)
        }
        if (!manualCompletion && request.command == MediaCommand.Retry) host.completeUpload(true)
    }, { event ->
        host.onEvent(event)
        if (!manualCompletion && host.submission != null) {
            host.resolveDraft(true)
            host.completeUpload(true)
        }
    })
    val renderer: @Composable (MediaTarget) -> Unit = { target ->
        if (target.attachment?.contentReference == "bundled:mountain")
            Image(painterResource(Res.drawable.mountain), null, Modifier.size(240.dp, 120.dp).clip(RoundedCornerShape(16.dp)))
    }
    var menu by remember { mutableStateOf(false) }
    SampleTheme(false) {
        MediaTheme(slots = LocalMediaSlots.current.copy(control = { button ->
            if (button.command == MediaCommand.Select) {
                IconButton(onClick = button.onClick, enabled = button.enabled,
                    modifier = Modifier.semantics { contentDescription = "Attach image" }) { Text("+", style = MaterialTheme.typography.headlineSmall) }
            } else TextButton(onClick = button.onClick, enabled = button.enabled) {
                Text(when (button.command) {
                    MediaCommand.Remove -> "Remove image"
                    MediaCommand.Retry -> "Retry upload"
                    MediaCommand.View -> "View image"
                    MediaCommand.Select -> "Attach image"
                })
            }
        })) {
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                Column {
                CenterAlignedTopAppBar(
                    title = { Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                        Text("# shared-images", style = MaterialTheme.typography.titleLarge)
                        Text("Share a moment with the team", style = MaterialTheme.typography.bodySmall)
                    } },
                    navigationIcon = {
                        if (onBack != null) IconButton(onClick = onBack, modifier = Modifier.semantics {
                            contentDescription = "Back to conversation"
                        }) { Text("‹", style = MaterialTheme.typography.headlineMedium) }
                    },
                    actions = { Box {
                        IconButton(onClick = { menu = true }, modifier = Modifier.testTag("media-demo-menu")
                            .semantics { contentDescription = "Image conversation options" }) { Text("⋮") }
                        DropdownMenu(menu, onDismissRequest = { menu = false }) {
                            @Composable fun entry(label: String, action: () -> Unit) {
                                DropdownMenuItem(text = { Text(label) }, onClick = { menu = false; action() })
                            }
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
                    } },
                )
                HorizontalDivider()
                }
            },
        ) { innerPadding ->
            Column(Modifier.fillMaxSize().padding(innerPadding).consumeWindowInsets(innerPadding)) {
                val base = LocalConversationSlots.current
                Conversation(host.state, media.onConversationEvent, modifier = Modifier.weight(1f),
                    slots = base.copy(
                        message = { row, outgoing, retry ->
                            Column {
                                base.message(row, outgoing, retry)
                                Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                                    contentAlignment = if (outgoing) androidx.compose.ui.Alignment.CenterEnd else androidx.compose.ui.Alignment.CenterStart) {
                                    MediaAttachments(media, row, renderer)
                                }
                            }
                        },
                        composer = { content ->
                            Column {
                                val mediaSlots = LocalMediaSlots.current
                                val adapted = media.composer(content)
                                Column(Modifier.padding(horizontal = 16.dp)) {
                                    MediaDraft(media, content, renderer, mediaSlots.copy(control = {
                                        if (it.command != MediaCommand.Select) mediaSlots.control(it)
                                    }))
                                }
                                MessageComposer(adapted, base.copy(composerField = { field ->
                                    Row(verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
                                        mediaSlots.control(MediaControlContent(MediaCommand.Select, !adapted.pending) {
                                            media.request(MediaCommand.Select, composer = adapted)
                                        })
                                        Box(Modifier.weight(1f)) { base.composerField(field) }
                                    }
                                }))
                            }
                        },
                    ))
            }
            if (host.selection != null) AlertDialog(
                onDismissRequest = { host.resolveSelection() },
                title = { Text("Choose an image") },
                text = { Column {
                    Text("This sample uses a bundled image. Your app supplies its own picker and upload service.")
                } },
                confirmButton = { TextButton(onClick = { host.resolveSelection(choose = true) }) { Text("Choose bundled image") } },
                dismissButton = { TextButton(onClick = { host.resolveSelection() }) { Text("Cancel selection") } },
            )
            host.viewer?.let { target -> AlertDialog(
                onDismissRequest = host::closeViewer, title = { Text("Shared image") },
                text = { renderer(target) },
                confirmButton = { TextButton(onClick = host::closeViewer) { Text("Close viewer") } },
            ) }
        }
        }
    }
}
