package dev.forntoh.chat.material3

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.zIndex
import dev.forntoh.chat.core.Message
import dev.forntoh.chat.ui.ConversationSlots

/**
 * Splits one [ConversationSlots.messageMetadata] invocation into a heading above the bubble and
 * a footer inside it. Call directly from that slot, without an enclosing layout. The heading
 * inherits the host surface text color; the footer inherits the directional bubble text color.
 * Both regions are host-authored composables and may be empty. Plain metadata overrides remain
 * supported as footer content, so existing slots need no adapter and emit no default heading.
 *
 * For example: `messageMetadata = { row -> MessageMetadata(header = { HostSender(row) }) { HostTime(row) } }`.
 * This supplies presentation only; it owns no state, identities, intents or transport.
 */
@Composable
fun MessageMetadata(
    header: @Composable () -> Unit = {},
    footer: @Composable () -> Unit
) {
    Box(Modifier.layoutId(MetadataElement.Header)) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) { header() }
    }
    Box(Modifier.layoutId(MetadataElement.Footer)) { footer() }
}

internal val LocalBubbleOutgoing = staticCompositionLocalOf { false }

private enum class MetadataElement { Header, Footer, Body, Delivery, Background }

@Composable
internal fun MessageBubbleLayout(message: Message, outgoing: Boolean, slots: ConversationSlots, shape: Shape) {
    val bubbleColor = if (outgoing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest
    val bubbleContentColor = if (outgoing) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Layout(
        modifier = Modifier.clearAndSetSemantics { contentDescription = slots.messageDescription(message) },
        content = {
            CompositionLocalProvider(
                LocalContentColor provides bubbleContentColor,
                LocalBubbleOutgoing provides outgoing
            ) {
                Column(
                    Modifier.layoutId(MetadataElement.Body),
                    verticalArrangement = Arrangement.spacedBy(ChatGeometry.ContentGap)
                ) {
                    slots.messageBody(message)
                }
                slots.messageMetadata(message)
                Box(Modifier.layoutId(MetadataElement.Delivery)) { slots.messageDelivery(message.delivery) }
                Surface(
                    Modifier.layoutId(MetadataElement.Background).zIndex(-1f),
                    color = bubbleColor,
                    shape = shape
                ) {}
            }
        },
    ) { children, constraints ->
        val padX = ChatGeometry.BubbleHorizontalPadding.roundToPx()
        val padY = ChatGeometry.BubbleVerticalPadding.roundToPx()
        val gap = ChatGeometry.ContentGap.roundToPx()
        val metadataGap = ChatGeometry.MetadataGap.roundToPx()
        val maxInnerWidth = (constraints.maxWidth - 2 * padX).coerceAtLeast(0)
        val body = children.single { it.layoutId == MetadataElement.Body }.measure(Constraints(maxWidth = maxInnerWidth))
        val delivery = children.single { it.layoutId == MetadataElement.Delivery }.measure(Constraints(maxWidth = maxInnerWidth))
        val hasDelivery = delivery.width > 0 || delivery.height > 0
        val headers = children.filter { it.layoutId == MetadataElement.Header }
            .map { it.measure(Constraints(maxWidth = constraints.maxWidth)) }.filter { it.height > 0 }
        val footerWidthLimit = (maxInnerWidth - delivery.width - if (hasDelivery) metadataGap else 0).coerceAtLeast(0)
        val footers = children.filter { it.layoutId != MetadataElement.Header && it.layoutId != MetadataElement.Body &&
            it.layoutId != MetadataElement.Delivery && it.layoutId != MetadataElement.Background }
            .map { it.measure(Constraints(maxWidth = footerWidthLimit)) }.filter { it.height > 0 }
        val metadataWidth = footers.maxOfOrNull { it.width } ?: 0
        val metadataHeight = footers.sumOf { it.height } + gap * (footers.size - 1).coerceAtLeast(0)
        val footerWidth = metadataWidth + delivery.width + if (footers.isNotEmpty() && hasDelivery) metadataGap else 0
        val hasFooter = footers.isNotEmpty() || hasDelivery
        val footerHeight = if (hasFooter) maxOf(metadataHeight, delivery.height, ChatGeometry.MetadataMinHeight.roundToPx()) else 0
        val innerWidth = maxOf(body.width, footerWidth,
            if (outgoing && hasFooter) ChatGeometry.OutgoingMetadataMinWidth.roundToPx() else 0).coerceAtMost(maxInnerWidth)
        val bubbleWidth = innerWidth + 2 * padX
        val bubbleHeight = body.height + 2 * padY + if (hasFooter) gap + footerHeight else 0
        val headerHeight = headers.sumOf { it.height } + gap * (headers.size - 1).coerceAtLeast(0)
        val bubbleTop = headerHeight + if (headers.isNotEmpty()) gap else 0
        val width = constraints.constrainWidth(maxOf(bubbleWidth, headers.maxOfOrNull { it.width } ?: 0))
        val height = constraints.constrainHeight(bubbleTop + bubbleHeight)
        val background = children.single { it.layoutId == MetadataElement.Background }
            .measure(Constraints.fixed(bubbleWidth, bubbleHeight))
        layout(width, height) {
            val bubbleX = if (outgoing) width - bubbleWidth else 0
            background.placeRelative(bubbleX, bubbleTop)
            var headerY = 0
            headers.forEach {
                it.placeRelative(if (outgoing) width - it.width else 0, headerY)
                headerY += it.height + gap
            }
            body.placeRelative(bubbleX + padX, bubbleTop + padY)
            val footerY = bubbleTop + padY + body.height + gap
            var metadataY = footerY + footerHeight - metadataHeight
            footers.forEach {
                it.placeRelative(bubbleX + padX + innerWidth - footerWidth + metadataWidth - it.width, metadataY)
                metadataY += it.height + gap
            }
            if (hasDelivery) delivery.placeRelative(bubbleX + padX + innerWidth - delivery.width,
                footerY + footerHeight - delivery.height)
        }
    }
}
