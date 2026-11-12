package dev.chat.sample

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.chat.material3.Conversation
import dev.chat.core.Delivery
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import dev.chat.ui.*

@Composable
fun SampleApp(host: FakeHost = remember { FakeHost() }) {
    var showConversation by remember { mutableStateOf(true) }
    var custom by remember { mutableStateOf(false) }
    MaterialTheme {
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
                        TextButton(onClick = { custom = !custom }) { Text(if (custom) "Material3 UI" else "Custom UI") }
                        TextButton(onClick = host::receiveMessage) { Text("New arrival") }
                        if (host.pendingHistory != null) {
                            TextButton(onClick = { host.completeHistory() }) { Text("Complete page") }
                            TextButton(onClick = host::failHistory) { Text("Fail page") }
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
                    if (custom) {
                        dev.chat.ui.Conversation(host.state, host::onEvent, Modifier.weight(1f),
                            messageContent = { message, _, _ ->
                                Column(Modifier.padding(8.dp)) {
                                    BasicText("${message.sender.displayName} · ${message.displayTime}")
                                    BasicText(message.text)
                                }
                            },
                            composerContent = { content ->
                                Row(Modifier.padding(12.dp)) {
                                    BasicTextField(content.draft.text, content.onTextChange, Modifier.weight(1f), enabled = !content.pending)
                                    BasicText("Send", Modifier.padding(12.dp).clickable(enabled = content.canSend, onClick = content.onSend))
                                }
                            },
                            history = host.history, onLoadEarlier = host::loadEarlier,
                            historyContent = { content ->
                                val label = when (content.state.status) {
                                    HistoryStatus.Idle -> "Load earlier"
                                    HistoryStatus.Loading -> "Loading messages…"
                                    HistoryStatus.Error -> "Couldn't load messages · Retry"
                                    HistoryStatus.Exhausted -> if (host.state.messages.isEmpty()) "No messages yet" else "Start of conversation"
                                }
                                BasicText(label, Modifier.padding(12.dp).clickable(enabled = content.canLoadEarlier, onClick = content.onLoadEarlier))
                            },
                            latestContent = { content ->
                                BasicText(if (content.hasNewMessages) "New messages · Latest" else "Latest",
                                    Modifier.padding(12.dp).clickable(onClick = content.onReturnToLatest))
                            })
                    } else {
                        Conversation(host.state, host::onEvent, Modifier.weight(1f),
                            history = host.history, onLoadEarlier = host::loadEarlier)
                    }
                } else {
                    Text("Your draft stays with the retained host.", Modifier.padding(16.dp))
                }
            }
        }
    }
}
