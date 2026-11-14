package dev.chat.sample

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import dev.forntoh.chat.actions.*
import dev.forntoh.chat.core.Delivery
import dev.forntoh.chat.material3.MessageComposer
import dev.forntoh.chat.material3.ChatScaffold
import dev.forntoh.chat.material3.ChatIcons
import dev.forntoh.chat.ui.Conversation
import dev.forntoh.chat.ui.LocalConversationSlots

/** In-memory chat. [manualService] lets focused journeys control fake acceptance/delivery. */
@Composable
fun SampleApp(host: FakeHost = remember { FakeHost().apply { useChatFixture() } }, manualService: Boolean = false) {
    var mediaDemo by remember { mutableStateOf(false) }
    val mediaHost = remember { FakeMediaHost() }
    if (mediaDemo) {
        MediaSample(mediaHost, onBack = { mediaDemo = false })
        return
    }
    val clipboard = LocalClipboardManager.current
    var manualCompletion by remember { mutableStateOf(manualService) }
    fun finishFakeSubmission() {
        if (!manualCompletion) {
            host.accept()
            host.completeDelivery(Delivery.Sent)
            if (host.pendingAction != null) host.completeAction(true)
        }
    }
    val actions = rememberMessageActions(host.state, host.actions,
        onAction = { request -> host.onAction(request) { clipboard.setText(AnnotatedString(it)) }; finishFakeSubmission() },
        onEvent = { host.onEvent(it); finishFakeSubmission() })
    var showConversation by remember { mutableStateOf(true) }
    var custom by remember { mutableStateOf(false) }
    var hostTheme by remember { mutableStateOf(false) }
    var demoMenu by remember { mutableStateOf(false) }
    SampleTheme(hostTheme) {
        ChatScaffold(
            title = { Column {
                Text(if (host.state.conversationId == "studio") "Design team" else "Project room",
                    style = MaterialTheme.typography.titleMedium)
                Text("Avery, Morgan and you", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } },
            avatar = { SampleAvatar("DT") },
            navigationIcon = {
                IconButton(onClick = { showConversation = !showConversation }, modifier = Modifier.semantics {
                    contentDescription = if (showConversation) "Leave" else "Open chat"
                }) { Icon(if (showConversation) ChatIcons.ArrowLeft else ChatIcons.ArrowRight, null) }
            },
            actions = { Box {
                IconButton(onClick = { demoMenu = true }, modifier = Modifier.testTag("demo-menu")
                    .semantics { contentDescription = "Conversation options" }) { Icon(ChatIcons.DotsVertical, null) }
                DropdownMenu(expanded = demoMenu, onDismissRequest = { demoMenu = false }) {
                    @Composable fun entry(label: String, action: () -> Unit) {
                        DropdownMenuItem(text = { Text(label) }, onClick = { demoMenu = false; action() })
                    }
                    entry("Image sample") { mediaDemo = true }
                    entry(if (manualCompletion) "Automatic service" else "Manual service") { manualCompletion = !manualCompletion }
                    entry("History fixture", host::useHistoryFixture)
                    entry("Switch room", host::switchRoom)
                    entry(if (hostTheme) "Default chat theme" else "Host theme") { hostTheme = !hostTheme }
                    entry(if (custom) "Material3 UI" else "Custom UI") { custom = !custom }
                    entry("New arrival", host::receiveMessage)
                    if (host.pendingHistory != null) {
                        entry("Complete page") { host.completeHistory() }; entry("Fail page", host::failHistory)
                    }
                    if (host.pendingAction != null) {
                        entry("Complete action") { host.completeAction(true) }; entry("Fail action") { host.completeAction(false) }
                    }
                    if (host.pendingDraft != null) {
                        entry("Accept", host::accept); entry("Keep draft", host::refuse)
                    }
                    if (host.state.messages.any { it.delivery == Delivery.Sending }) {
                        entry("Deliver") { host.completeDelivery(Delivery.Sent) }
                        entry("Fail delivery") { host.completeDelivery(Delivery.Failed) }
                    }
                    entry("About this sample") { showConversation = false }
                }
            } },
        ) {
            if (showConversation) {
                val base = LocalConversationSlots.current
                // Wrappers add supported optional actions; default message/composer presentation comes from the artifact.
                Conversation(host.state, actions::onConversationEvent, modifier = Modifier.weight(1f),
                    slots = base.copy(message = { message, outgoing, retry ->
                        Box {
                            Column(Modifier.combinedClickable(
                                onClickLabel = "Message actions",
                                onClick = { actions.request(ActionCommand.OpenMenu, message.id) },
                                onLongClick = { actions.request(ActionCommand.OpenMenu, message.id) },
                            )) {
                                host.actions.quotes[message.id]?.let { QuotedMessage(actions, it) }
                                if (custom && !outgoing) {
                                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                        modifier = Modifier.testTag("custom-incoming").clearAndSetSemantics {
                                            contentDescription = "${message.sender.displayName}, ${message.text}, ${message.displayTime}, ${message.delivery.name}"
                                        }) {
                                        Column(Modifier.padding(16.dp)) {
                                            Text(message.sender.displayName, style = MaterialTheme.typography.titleSmall)
                                            Text(message.text, style = MaterialTheme.typography.bodyLarge)
                                            Text("${message.displayTime} · ${message.delivery.name}", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                } else base.message(message, outgoing, retry)
                            }
                            DropdownMenu(expanded = host.actions.menuTargetId == message.id,
                                onDismissRequest = { actions.request(ActionCommand.CloseMenu) }) {
                                MessageActions(actions, message.id, LocalActionSlots.current.copy(
                                    control = { button ->
                                        if (button.command != ActionCommand.OpenMenu) DropdownMenuItem(
                                            text = { ActionLabel(button.command) }, enabled = button.enabled, onClick = button.onClick)
                                    },
                                    menu = { it() },
                                ))
                            }
                        }
                    }, composer = { content ->
                        Column {
                            ActionComposerStatus(actions)
                            if (custom) Text("Custom composer · host draft", style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                            if (host.actions.editing != null) {
                                MessageComposer(actions.composer(content), base.copy(composerSend = { composer ->
                                    TextButton(onClick = composer.onSend, enabled = composer.canSend) { Text("Save edit") }
                                }))
                            } else base.composer(actions.composer(content))
                        }
                    }), history = host.history, onLoadEarlier = host::loadEarlier)
            } else Text("In-memory sample. Messages and drafts last only while the app is open. " +
                "Images use a bundled resource; there is no server, upload service or persistence. " +
                "Your draft stays with this retained host. Demo scenarios are in the toolbar menu.", Modifier.padding(24.dp))
        }
    }
}
