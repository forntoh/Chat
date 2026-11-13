package dev.chat.sample

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.chat.material3.Conversation
import dev.chat.core.Delivery
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import dev.chat.material3.ConversationStrings
import dev.chat.material3.MessageBubble
import dev.chat.material3.MessageComposer
import dev.chat.ui.*
import dev.chat.actions.*
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString

/** In-memory integration with independent theme/content switches and host-owned draft state. */
@Composable
fun SampleApp(host: FakeHost = remember { FakeHost() }) {
    val clipboard = LocalClipboardManager.current
    val actions = rememberMessageActions(host.state, host.actions,
        onAction = { request -> host.onAction(request) { clipboard.setText(AnnotatedString(it)) } }, onEvent = host::onEvent)
    var showConversation by remember { mutableStateOf(true) }
    var custom by remember { mutableStateOf(false) }
    var hostTheme by remember { mutableStateOf(false) }
    SampleTheme(hostTheme) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${host.state.conversationId} chat", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 16.dp))
                    TextButton(onClick = { showConversation = !showConversation }) { Text(if (showConversation) "Leave" else "Open chat") }
                }
                if (showConversation) {
                    Text("In-memory sample · host controls", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 12.dp))
                    FlowRow(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = host::useHistoryFixture) { Text("History fixture") }
                        TextButton(onClick = host::switchRoom) { Text("Switch room") }
                        TextButton(onClick = { hostTheme = !hostTheme }) { Text(if (hostTheme) "Jetchat theme" else "Host theme") }
                        TextButton(onClick = { custom = !custom }) { Text(if (custom) "Material3 UI" else "Custom UI") }
                        TextButton(onClick = host::receiveMessage) { Text("New arrival") }
                        if (host.pendingHistory != null) {
                            TextButton(onClick = { host.completeHistory() }) { Text("Complete page") }
                            TextButton(onClick = host::failHistory) { Text("Fail page") }
                        }
                        if (host.pendingAction != null) {
                            TextButton(onClick = { host.completeAction(true) }) { Text("Complete action") }
                            TextButton(onClick = { host.completeAction(false) }) { Text("Fail action") }
                        }
                        if (host.pendingDraft != null) {
                            TextButton(onClick = host::accept) { Text("Accept") }
                            TextButton(onClick = host::refuse) { Text("Keep draft") }
                        }
                        if (host.state.messages.any { it.delivery == Delivery.Sending }) {
                            TextButton(onClick = { host.completeDelivery(Delivery.Sent) }) { Text("Deliver") }
                            TextButton(onClick = { host.completeDelivery(Delivery.Failed) }) { Text("Fail delivery") }
                        }
                    }
                    // One Conversation instance: switching presentation retains the send/history latches.
                    val strings = ConversationStrings()
                    Conversation(host.state, actions::onConversationEvent, Modifier.weight(1f),
                        strings = strings,
                        messageContent = { message, outgoing, retry ->
                            Column {
                            host.actions.quotes[message.id]?.let { QuotedMessage(actions, it) }
                            if (custom && !outgoing) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier.testTag("custom-incoming").clearAndSetSemantics {
                                        contentDescription = "${message.sender.displayName}, ${message.text}, ${message.displayTime}, ${strings.deliveryLabel(message.delivery)}"
                                    },
                                ) {
                                    Column(Modifier.padding(16.dp)) {
                                        Text(message.sender.displayName, style = MaterialTheme.typography.titleSmall)
                                        Text(message.text, style = MaterialTheme.typography.bodyLarge)
                                        Text("${message.displayTime} · ${strings.deliveryLabel(message.delivery)}",
                                            style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            } else {
                                MessageBubble(message, outgoing, retry, strings)
                            }
                            MessageActions(actions, message.id)
                            }
                        },
                        composerContent = { content ->
                            Column {
                                ActionComposerStatus(actions)
                                if (custom) Text("Custom composer · host draft",
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                                MessageComposer(actions.composer(content), if (host.actions.editing != null)
                                    strings.copy(send = "Save edit", sendDescription = "Save edit") else strings)
                            }
                        },
                        history = host.history, onLoadEarlier = host::loadEarlier)

                } else {
                    Text("Your draft stays with the retained host.", Modifier.padding(16.dp))
                }
            }
        }
    }
}
