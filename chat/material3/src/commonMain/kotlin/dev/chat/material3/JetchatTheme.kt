package dev.chat.material3

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.clearAndSetSemantics
import dev.chat.core.Delivery
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.SolidColor
import dev.chat.ui.*

/**
 * Reference-design Material3 implementations of shared UI slots. This factory supplies
 * each standard renderer/subslot with Material3 content.
 * Customize the returned bundle with copy(...) to retain these defaults. It creates no services
 * or behavior state and requires neither actions nor media. Wording is English composable content;
 * hosts localize subslots directly, including visible and accessible delivery content together.
 */
fun chatConversationSlots(): ConversationSlots = ConversationSlots(
    message = { row, outgoing, retry -> MessageBubble(row, outgoing, retry) },
    composer = { MessageComposer(it) },
    history = { HistoryStatusContent(it.history, it.empty) },
    latest = { LatestControl(it) },
    messageMetadata = {
        Text(it.displayTime, style = MaterialTheme.typography.labelSmall,
            color = LocalContentColor.current.copy(alpha = 0.6f),
            modifier = Modifier.padding(bottom = 1.dp))
    },
    messageBody = { Text(it.text, style = MaterialTheme.typography.bodyLarge) },
    messageDelivery = {
        if (it == Delivery.Failed) Surface(color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer) {
            Text(it.name, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(4.dp))
        } else if (it == Delivery.Sending) Text(it.name, style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 4.dp))
    },
    messageDescription = { "${it.sender.displayName}, ${it.text}, ${it.displayTime}, ${it.delivery.name}" },
    retry = { TextButton(onClick = it.onRetry) { Text("Retry") } },
    composerField = {
        BasicTextField(
            value = it.draft.text,
            onValueChange = it.onTextChange,
            enabled = !it.pending,
            minLines = 1,
            maxLines = 5,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 12.dp)
                .testTag("composer").semantics { contentDescription = "Message" },
            decorationBox = { field ->
                Box {
                    if (it.draft.text.isEmpty()) Text("Type a message…", color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge)
                    field()
                }
            },
        ) },
    composerSend = {
        FilledIconButton(
            enabled = it.canSend,
            onClick = it.onSend,
            modifier = Modifier.size(48.dp).semantics { contentDescription = "Send" }
        ) {
            Icon(ChatIcons.Send, contentDescription = null, modifier = Modifier.size(22.dp))
        }
    },
    historyFeedback = { DefaultHistoryFeedback(it) },
    latestLabel = { Text(if (it.hasNewMessages) "New messages · Latest" else "Latest") },
    messageAvatar = { message ->
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(30.dp).clearAndSetSemantics {}
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = message.sender.displayName.trim().split(Regex("\\s+"))
                        .filter(String::isNotBlank)
                        .take(2).joinToString("") { it.take(1) }.uppercase().ifEmpty { "?" },
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    },
    messageSpacing = { if (it.isFirst) 8.dp else 4.dp },
)

/** Legacy factory name. Prefer [chatConversationSlots] for the current reference design. */
fun jetchatConversationSlots(): ConversationSlots = chatConversationSlots()

/**
 * Legacy slots-only provider, retaining host MaterialTheme ownership and existing signatures.
 * Prefer [ChatTheme] for the reference palette and [ChatScaffold] for customizable screen chrome.
 * Existing explicit/bundle overrides still take precedence. No alternate Conversation is created.
 */
@Composable
fun JetchatTheme(slots: ConversationSlots = jetchatConversationSlots(), content: @Composable () -> Unit) {
    ConversationTheme(slots, content)
}

@Composable
fun DefaultHistoryFeedback(content: HistoryFeedbackContent) {
    val history = content.history
    when (history.state.status) {
        HistoryStatus.Idle -> TextButton(enabled = history.canLoadEarlier, onClick = history.onLoadEarlier) { Text("Load earlier") }
        HistoryStatus.Loading -> Text("Loading messages…", Modifier.padding(12.dp))
        HistoryStatus.Error -> TextButton(enabled = history.canLoadEarlier, onClick = history.onLoadEarlier) { Text("Couldn't load messages · Retry") }
        HistoryStatus.Exhausted -> Text(if (content.empty) "No messages yet" else "Start of conversation",
            Modifier.padding(8.dp), style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
