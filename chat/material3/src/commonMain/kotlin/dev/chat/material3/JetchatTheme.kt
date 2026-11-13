package dev.chat.material3

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import dev.chat.ui.*

/**
 * Jetchat-referenced Material3 implementations of shared UI slots. This factory supplies
 * each standard renderer/subslot with Material3 content.
 * Customize the returned bundle with copy(...) to retain these defaults. It creates no services
 * or behavior state and requires neither actions nor media. Wording is English composable content;
 * hosts localize subslots directly, including visible and accessible delivery content together.
 */
fun jetchatConversationSlots(): ConversationSlots = ConversationSlots(
    message = { row, outgoing, retry -> MessageBubble(row, outgoing, retry) },
    composer = { MessageComposer(it) },
    history = { HistoryStatusContent(it.history, it.empty) },
    latest = { LatestControl(it) },
    messageMetadata = {
        Text(
            "${it.sender.displayName} · ${it.displayTime}", style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )
    },
    messageBody = { Text(it.text, style = MaterialTheme.typography.bodyLarge) },
    messageDelivery = {
        if (it != Delivery.Sent) Text(it.name, style = MaterialTheme.typography.labelSmall,
            color = if (it == Delivery.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
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
                    if (it.draft.text.isEmpty()) Text("Message", color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            val color = LocalContentColor.current
            Canvas(Modifier.size(22.dp)) {
                val arrow = Path().apply {
                    moveTo(0f, 0f); lineTo(size.width, size.height / 2)
                    lineTo(0f, size.height); lineTo(0f, size.height * 0.6f)
                    lineTo(size.width * 0.65f, size.height / 2)
                    lineTo(0f, size.height * 0.4f); close()
                }
                drawPath(arrow, color)
            }
        }
    },
    historyFeedback = { DefaultHistoryFeedback(it) },
    latestLabel = { Text(if (it.hasNewMessages) "New messages · Latest" else "Latest") },
    messageAvatar = { message ->
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(42.dp).clearAndSetSemantics {}
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

/**
 * Optional Material3 presentation provider for the one shared [dev.chat.ui.Conversation]. Wrap
 * this in the host's MaterialTheme to choose colors/typography. [slots] defaults to Jetchat
 * renderers and replaces the ambient shared bundle; derive jetchatConversationSlots().copy(...)
 * for host overrides. The shared Conversation's explicit slots/renderers still take precedence.
 * Keep the shared component mounted across theme changes to retain send/history guards.
 *
 * ```kotlin
 * MaterialTheme {
 *     JetchatTheme { Conversation(hostState, ::handleEvent) }
 * }
 * ```
 * No alternate Conversation, host state, actions/media dependency or service is installed.
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
