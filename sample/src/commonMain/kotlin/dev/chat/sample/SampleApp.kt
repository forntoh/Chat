package dev.chat.sample

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.chat.material3.Conversation
import dev.chat.core.Delivery

@Composable
fun SampleApp(host: FakeHost = remember { FakeHost() }) {
    var showConversation by remember { mutableStateOf(true) }
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Studio chat", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 16.dp))
                    TextButton(onClick = { showConversation = !showConversation }) { Text(if (showConversation) "Leave" else "Open chat") }
                }
                if (showConversation) {
                    Text("In-memory sample · host controls", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 12.dp))
                    FlowRow(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (host.pendingDraft != null) {
                            TextButton(onClick = host::accept) { Text("Accept") }
                            TextButton(onClick = host::refuse) { Text("Keep draft") }
                        }
                        if (host.state.messages.any { it.delivery == Delivery.Sending }) {
                            TextButton(onClick = { host.completeDelivery(Delivery.Sent) }) { Text("Deliver") }
                            TextButton(onClick = { host.completeDelivery(Delivery.Failed) }) { Text("Fail delivery") }
                        }
                    }
                    Conversation(host.state, host::onEvent, Modifier.weight(1f))
                } else {
                    Text("Your draft stays with the retained host.", Modifier.padding(16.dp))
                }
            }
        }
    }
}
