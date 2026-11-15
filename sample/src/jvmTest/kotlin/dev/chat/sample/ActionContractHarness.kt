package dev.chat.sample

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import dev.forntoh.chat.actions.ActionComposerStatus
import dev.forntoh.chat.actions.MessageActionFocus
import dev.forntoh.chat.actions.QuotedMessage
import dev.forntoh.chat.actions.rememberMessageActions
import dev.forntoh.chat.material3.ChatScaffold
import dev.forntoh.chat.material3.MessageBubble
import dev.forntoh.chat.material3.MessageComposer
import dev.forntoh.chat.ui.Conversation
import dev.forntoh.chat.ui.LocalConversationSlots

@Composable
fun ActionContractHarness(host: ContractFixture) {
    val clipboard = LocalClipboardManager.current
    val actions = rememberMessageActions(
        host.state,
        host.actions,
        onAction = { request -> host.onAction(request) { clipboard.setText(AnnotatedString(it)) } },
        onEvent = host::onEvent,
    )
    SampleTheme(host.alternateTheme) {
        ChatScaffold(title = { Text("Contract fixture") }) {
            val base = LocalConversationSlots.current
            Conversation(
                host.state,
                actions::onConversationEvent,
                modifier = Modifier.weight(1f),
                slots = base.copy(message = { message, outgoing, retry ->
                    MessageActionFocus(
                        actions,
                        message.id,
                        above = {
                            if (host.custom) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                ) {
                                    Text(
                                        "Message actions",
                                        Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                }
                            }
                        },
                        bubble = { interaction ->
                            val bubbleInteraction = Modifier.testTag("bubble-${message.id}").then(interaction)
                            val replyBody = base.copy(messageBody = { row ->
                                val target = host.actions.quotes[row.id]
                                if (target == null) {
                                    base.messageBody(row)
                                } else {
                                    dev.forntoh.chat.material3.ReplyMessageBody(
                                        preview = { QuotedMessage(actions, target) },
                                        body = { base.messageBody(row) },
                                    )
                                }
                            })
                            Column {
                                if (host.custom && !outgoing) {
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                        modifier = Modifier.testTag(
                                            "custom-incoming",
                                        ).clip(RoundedCornerShape(16.dp)).then(bubbleInteraction).semantics {
                                            contentDescription =
                                                "${message.sender.displayName}, ${message.text}, ${message.displayTime}, ${message.delivery.name}"
                                        },
                                    ) {
                                        Column(Modifier.padding(16.dp)) {
                                            host.actions.quotes[message.id]?.let { QuotedMessage(actions, it) }
                                            Text(message.sender.displayName, style = MaterialTheme.typography.titleSmall)
                                            Text(message.text, style = MaterialTheme.typography.bodyLarge)
                                            Text(
                                                "${message.displayTime} · ${message.delivery.name}",
                                                style = MaterialTheme.typography.labelSmall,
                                            )
                                        }
                                    }
                                } else {
                                    MessageBubble(message, outgoing, retry, replyBody, bubbleInteraction)
                                }
                            }
                        },
                    )
                }, composer = { content ->
                    Column {
                        if (host.custom) {
                            Text(
                                "Custom composer · host draft",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
                        val composerSlots = if (host.actions.editing != null) {
                            base.copy(composerSend = { composer ->
                                TextButton(onClick = composer.onSend, enabled = composer.canSend) { Text("Save edit") }
                            })
                        } else {
                            base
                        }
                        MessageComposer(
                            actions.composer(content),
                            composerSlots,
                            headerContent = { ActionComposerStatus(actions) },
                            leadingContent = {},
                        )
                    }
                }),
                history = host.history,
                onLoadEarlier = host::loadEarlier,
            )
        }
    }
}
