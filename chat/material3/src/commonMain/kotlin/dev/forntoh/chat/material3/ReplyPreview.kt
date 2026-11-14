package dev.forntoh.chat.material3

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.forntoh.chat.core.Message

/**
 * Embedded reply presentation for a host quote slot; no dependency on the optional actions module.
 * [message] is the host-resolved target, null for an unavailable fallback. [onClick] navigates only
 * when supplied and the target exists. Hosts own wording/localization by replacing their quote slot.
 * Colors inherit the enclosing bubble/composer content, with a subtle inset and accent stripe.
 */
@Composable
fun ReplyPreview(message: Message?, onClick: (() -> Unit)? = null) {
    val foreground = LocalContentColor.current
    val accent = if (LocalBubbleOutgoing.current) MaterialTheme.colorScheme.inversePrimary else MaterialTheme.colorScheme.primary
    Row(Modifier.widthIn(max = 320.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp))
        .background(foreground.copy(alpha = 0.08f))
        .then(if (message != null && onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
        .semantics(mergeDescendants = true) {
            contentDescription = message?.let { "Reply to ${it.sender.displayName}" } ?: "Message unavailable"
        }.height(IntrinsicSize.Min)) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(accent))
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            if (message != null) Text(message.sender.displayName.ifBlank { "Unknown sender" },
                style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(message?.text?.ifBlank { "Message unavailable" } ?: "Message unavailable",
                style = MaterialTheme.typography.bodyMedium, color = foreground.copy(alpha = 0.85f),
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Measures an embedded preview and body together so the inset spans the content width without
 * forcing unrelated bubbles to full width. Emit body text last to retain final-line time fitting. */
@Composable
fun ReplyMessageBody(preview: @Composable () -> Unit, body: @Composable () -> Unit) {
    Column(Modifier.width(IntrinsicSize.Max), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        preview()
        body()
    }
}
