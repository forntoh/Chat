package dev.chat.sample

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import dev.forntoh.chat.actions.*
import dev.forntoh.chat.core.Delivery
import dev.forntoh.chat.material3.MessageBubble
import dev.forntoh.chat.material3.MessageComposer
import dev.forntoh.chat.material3.ChatScaffold
import dev.forntoh.chat.material3.ChatIcons
import dev.forntoh.chat.ui.Conversation
import dev.forntoh.chat.ui.LocalConversationSlots

/** In-memory chat. [manualService] lets focused journeys control fake acceptance/delivery. */
@Composable
fun SampleApp(host: FakeHost? = null, manualService: Boolean = false) {
    val retainedHost = host ?: remember { FakeHost().apply { if (manualService) useChatFixture() else startSimulation() } }
    LaunchedEffect(retainedHost, retainedHost.simulationEnabled) {
        if (retainedHost.simulationEnabled) while (true) {
            delay(50)
            retainedHost.advanceBy(50)
        }
    }
    var debugPanel by remember { mutableStateOf(false) }
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
            if (!retainedHost.simulationEnabled) {
                retainedHost.accept()
                retainedHost.completeDelivery(Delivery.Sent)
            }
            if (retainedHost.pendingAction != null) retainedHost.completeAction(true)
        }
    }
    val actions = rememberMessageActions(retainedHost.state, retainedHost.actions,
        onAction = { request -> retainedHost.onAction(request) { clipboard.setText(AnnotatedString(it)) }; finishFakeSubmission() },
        onEvent = { retainedHost.onEvent(it); finishFakeSubmission() })
    var showConversation by remember { mutableStateOf(true) }
    var custom by remember { mutableStateOf(false) }
    var hostTheme by remember { mutableStateOf(false) }
    var demoMenu by remember { mutableStateOf(false) }
    SampleTheme(hostTheme) {
        if (debugPanel) SimulationPanel(retainedHost) { debugPanel = false }
        ChatScaffold(
            title = { Column {
                Text(if (retainedHost.state.conversationId == "studio") "Design team" else "Project room",
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
                    entry("Simulation debug panel") { debugPanel = true }
                    entry("Image sample") { mediaDemo = true }
                    if (!retainedHost.simulationEnabled) {
                        entry(if (manualCompletion) "Automatic service" else "Manual service") { manualCompletion = !manualCompletion }
                        entry("History fixture", retainedHost::useHistoryFixture)
                        entry("Switch room", retainedHost::switchRoom)
                    }
                    entry(if (hostTheme) "Default chat theme" else "Host theme") { hostTheme = !hostTheme }
                    entry(if (custom) "Material3 UI" else "Custom UI") { custom = !custom }
                    if (!retainedHost.simulationEnabled) {
                        entry("New arrival", retainedHost::receiveMessage)
                        if (retainedHost.pendingHistory != null) {
                            entry("Complete page") { retainedHost.completeHistory() }; entry("Fail page", retainedHost::failHistory)
                        }
                        if (retainedHost.pendingAction != null) {
                            entry("Complete action") { retainedHost.completeAction(true) }; entry("Fail action") { retainedHost.completeAction(false) }
                        }
                        if (retainedHost.pendingDraft != null) {
                            entry("Accept", retainedHost::accept); entry("Keep draft", retainedHost::refuse)
                        }
                        if (retainedHost.state.messages.any { it.delivery == Delivery.Sending }) {
                            entry("Deliver") { retainedHost.completeDelivery(Delivery.Sent) }
                            entry("Fail delivery") { retainedHost.completeDelivery(Delivery.Failed) }
                        }
                    }
                    entry("About this sample") { showConversation = false }
                }
            } },
        ) {
            if (showConversation) {
                val base = LocalConversationSlots.current
                // Wrappers add supported optional actions; default message/composer presentation comes from the artifact.
                Conversation(retainedHost.state, actions::onConversationEvent, modifier = Modifier.weight(1f),
                    slots = base.copy(message = { message, outgoing, retry ->
                        MessageActionFocus(actions, message.id,
                            above = {
                                if (custom) Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                                    Text("Message actions", Modifier.padding(horizontal = 16.dp, vertical = 12.dp), style = MaterialTheme.typography.labelLarge)
                                }
                            }, bubble = { interaction ->
                            val bubbleInteraction = Modifier.testTag("bubble-${message.id}").then(interaction)
                            Column {
                                retainedHost.actions.quotes[message.id]?.let { QuotedMessage(actions, it) }
                                if (custom && !outgoing) {
                                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                        modifier = Modifier.testTag("custom-incoming").clip(RoundedCornerShape(16.dp)).then(bubbleInteraction).clearAndSetSemantics {
                                            contentDescription = "${message.sender.displayName}, ${message.text}, ${message.displayTime}, ${message.delivery.name}"
                                        }) {
                                        Column(Modifier.padding(16.dp)) {
                                            Text(message.sender.displayName, style = MaterialTheme.typography.titleSmall)
                                            Text(message.text, style = MaterialTheme.typography.bodyLarge)
                                            Text("${message.displayTime} · ${message.delivery.name}", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                } else MessageBubble(message, outgoing, retry, base, bubbleInteraction)
                            }
                        })
                    }, composer = { content ->
                        Column {
                            ActionComposerStatus(actions)
                            if (custom) Text("Custom composer · host draft", style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                            if (retainedHost.actions.editing != null) {
                                MessageComposer(actions.composer(content), base.copy(composerSend = { composer ->
                                    TextButton(onClick = composer.onSend, enabled = composer.canSend) { Text("Save edit") }
                                }))
                            } else base.composer(actions.composer(content))
                        }
                    }), history = retainedHost.history, onLoadEarlier = retainedHost::loadEarlier)
            } else Text("In-memory sample. Messages and drafts last only while the app is open. " +
                "Images use a bundled resource; there is no server, upload service or persistence. " +
                "Your draft stays with this retained host. Delivery and connection outcomes are simulated. Scenario controls are in the debug panel.", Modifier.padding(24.dp))
        }
    }
}
