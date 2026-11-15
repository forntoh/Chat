package dev.forntoh.chat.material3

import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.lerp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.HorizontalAlignmentLine
import androidx.compose.ui.layout.VerticalAlignmentLine
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.constrainHeight
import dev.forntoh.chat.core.Message
import dev.forntoh.chat.ui.LocalChatMotion
import dev.forntoh.chat.ui.ConversationSlots

/**
 * Splits one [ConversationSlots.messageMetadata] invocation into a heading above the bubble and
 * a footer inside it. Call directly from that slot, without an enclosing layout. The heading
 * inherits the host surface text color; the footer inherits the directional bubble text color.
 * The footer shares the final text line when measured space permits, otherwise it gets its own row.
 * Custom bodies use their full bounds for a conservative non-overlapping fit.
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

// Default text publishes its final-line geometry; arbitrary bodies use their full measured bounds.
internal val LastTextRight = VerticalAlignmentLine(::maxOf)
internal val LastTextTop = HorizontalAlignmentLine(::maxOf)
internal val LastTextBottom = HorizontalAlignmentLine(::maxOf)

internal val LocalBubbleOutgoing = staticCompositionLocalOf { false }

private enum class MetadataElement { Header, Footer, Body, Delivery }

private val BubbleWidth = VerticalAlignmentLine(::maxOf)
private val BubbleTop = HorizontalAlignmentLine(::maxOf)
private val BubbleHeight = HorizontalAlignmentLine(::maxOf)

@Composable
internal fun MessageBubbleLayout(
    message: Message,
    outgoing: Boolean,
    slots: ConversationSlots,
    shape: Shape,
    bubbleModifier: Modifier,
    highlighted: Boolean
) {
    val bubbleColor =
        if (outgoing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest
    val motion = LocalChatMotion.current
    val targetColor = if (highlighted) lerp(
            bubbleColor,
            MaterialTheme.colorScheme.tertiaryContainer,
            0.35f
        ) else bubbleColor
    val displayedColor = if (motion.enabled) {
        val animated by animateColorAsState(targetColor, animationSpec = tween(motion.controlMillis, easing = motion.easing))
        animated
    } else {
        targetColor
    }
    val bubbleContentColor =
        if (outgoing) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Layout(content = {
        Surface(
            modifier = Modifier.clip(shape).then(bubbleModifier).clearAndSetSemantics {
                contentDescription = slots.messageDescription(message)
            },
            color = displayedColor,
            shape = shape,
        ) {}
        MessageBubbleContent(message, outgoing, slots, bubbleContentColor)
    }) { children, constraints ->
        val content = children[1].measure(constraints)
        val bubbleWidth = content[BubbleWidth]
        val bubbleTop = content[BubbleTop]
        val bubbleHeight = content[BubbleHeight]
        val background = children[0].measure(Constraints.fixed(bubbleWidth, bubbleHeight))
        layout(content.width, content.height) {
            background.placeRelative(if (outgoing) content.width - bubbleWidth else 0, bubbleTop)
            content.placeRelative(0, 0)
        }
    }
}

@Composable
private fun MessageBubbleContent(
    message: Message,
    outgoing: Boolean,
    slots: ConversationSlots,
    bubbleContentColor: androidx.compose.ui.graphics.Color
) {
    Layout(
        modifier = Modifier,
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
            }
        },
    ) { children, constraints ->
        val padX = ChatGeometry.BubbleHorizontalPadding.roundToPx()
        val padY = ChatGeometry.BubbleVerticalPadding.roundToPx()
        val gap = ChatGeometry.ContentGap.roundToPx()
        val metadataGap = ChatGeometry.MetadataGap.roundToPx()
        val maxInnerWidth = (constraints.maxWidth - 2 * padX).coerceAtLeast(0)
        val body = children.single { it.layoutId == MetadataElement.Body }
            .measure(Constraints(maxWidth = maxInnerWidth))
        val delivery = children.single { it.layoutId == MetadataElement.Delivery }
            .measure(Constraints(maxWidth = maxInnerWidth))
        val hasDelivery = delivery.width > 0 || delivery.height > 0
        val headers = children.filter { it.layoutId == MetadataElement.Header }
            .map { it.measure(Constraints(maxWidth = constraints.maxWidth)) }
            .filter { it.height > 0 }
        val footerWidthLimit =
            (maxInnerWidth - delivery.width - if (hasDelivery) metadataGap else 0).coerceAtLeast(0)
        val footers = children.filter {
            it.layoutId != MetadataElement.Header && it.layoutId != MetadataElement.Body &&
                    it.layoutId != MetadataElement.Delivery
        }
            .map { it.measure(Constraints(maxWidth = footerWidthLimit)) }.filter { it.height > 0 }
        val metadataWidth = footers.maxOfOrNull { it.width } ?: 0
        val metadataHeight = footers.sumOf { it.height } + gap * (footers.size - 1).coerceAtLeast(0)
        val hasFooter = footers.isNotEmpty() || hasDelivery
        val footerWidth = (metadataWidth + delivery.width +
                if (footers.isNotEmpty() && hasDelivery) metadataGap else 0).coerceAtMost(
            maxInnerWidth
        )
        val footerHeight = if (hasFooter) maxOf(
            metadataHeight,
            delivery.height,
            ChatGeometry.MetadataMinHeight.roundToPx()
        ) else 0
        val textRight =
            body[LastTextRight].takeUnless { it == AlignmentLine.Unspecified } ?: body.width
        val textTop = body[LastTextTop].takeUnless { it == AlignmentLine.Unspecified } ?: 0
        val textBottom =
            body[LastTextBottom].takeUnless { it == AlignmentLine.Unspecified } ?: body.height
        // Text must also be the bottom content, not an earlier quote or attachment.
        val inline =
            hasFooter && textBottom == body.height && textRight + metadataGap + footerWidth <= maxInnerWidth
        val innerWidth = maxOf(
            body.width, if (inline) textRight + metadataGap + footerWidth else footerWidth,
            if (outgoing && hasFooter) textRight + metadataGap + ChatGeometry.OutgoingMetadataMinWidth.roundToPx() else 0
        ).coerceAtMost(maxInnerWidth)
        val bubbleWidth = innerWidth + 2 * padX
        val footerTop =
            if (inline) maxOf(textTop, body.height - footerHeight) else body.height + gap
        val bubbleHeight =
            2 * padY + if (hasFooter) maxOf(body.height, footerTop + footerHeight) else body.height
        val headerHeight = headers.sumOf { it.height } + gap * (headers.size - 1).coerceAtLeast(0)
        val bubbleTop = headerHeight + if (headers.isNotEmpty()) gap else 0
        val width =
            constraints.constrainWidth(maxOf(bubbleWidth, headers.maxOfOrNull { it.width } ?: 0))
        val height = constraints.constrainHeight(bubbleTop + bubbleHeight)
        layout(
            width = width,
            height = height,
            alignmentLines = mapOf(BubbleWidth to bubbleWidth, BubbleTop to bubbleTop, BubbleHeight to bubbleHeight)
        ) {
            val bubbleX = if (outgoing) width - bubbleWidth else 0
            var headerY = 0
            headers.forEach {
                it.placeRelative(if (outgoing) width - it.width else 0, headerY)
                headerY += it.height + gap
            }
            body.placeRelative(bubbleX + padX, bubbleTop + padY)
            val footerY = bubbleTop + padY + footerTop
            var metadataY = footerY + footerHeight - metadataHeight
            footers.forEach {
                it.placeRelative(
                    bubbleX + padX + innerWidth - delivery.width - (if (hasDelivery) metadataGap else 0) - it.width,
                    metadataY
                )
                metadataY += it.height + gap
            }
            if (hasDelivery) delivery.placeRelative(
                bubbleX + padX + innerWidth - delivery.width,
                footerY + footerHeight - delivery.height
            )
        }
    }
}
