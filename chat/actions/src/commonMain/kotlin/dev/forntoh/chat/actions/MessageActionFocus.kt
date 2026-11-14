package dev.forntoh.chat.actions

import androidx.compose.animation.core.*
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.*
import kotlin.math.roundToInt

/**
 * Long-press presentation around an existing message renderer. Apply the supplied modifier only
 * to its bubble surface, excluding sender/avatar/retry. The row is recorded once; the overlay
 * draws that exact bubble without composing the host's content or callbacks a second time.
 * [above] and [below] can independently replace/add consumer content. Defaults show only the
 * existing capability-filtered actions below. Slots receive guarded [ActionsContent] via capture.
 *
 * Selection stays in the host's ActionState. Tap does not open the menu. Outside tap, Back/Escape
 * and Close emit CloseMenu; changed/removed targets, lost capabilities, pending work and a changed
 * conversation immediately remove the presentation. No animation completion emits an intent.
 * [motionEnabled] is a host override; Compose's platform duration scale also controls animations.
 * Entrance/exit use a 180ms eased fade and a 1% bubble lift, with no retained bitmap or resource IO.
 */
@Composable
fun MessageActionFocus(
    content: ActionsContent,
    messageId: String,
    modifier: Modifier = Modifier,
    motionEnabled: Boolean = true,
    above: @Composable () -> Unit = {},
    below: @Composable () -> Unit = { MessageActionMenu(content, messageId) },
    bubble: @Composable (Modifier) -> Unit,
) {
    val target = content.conversation.messages.find { it.id == messageId }
    val allowed = target != null && content.state.capabilities[messageId].orEmpty().isNotEmpty() &&
        !content.state.pending && content.state.editing == null && content.state.deleting == null
    val selected = allowed && content.state.menuTargetId == messageId
    var openedTarget by remember(content.conversation.conversationId, messageId) { mutableStateOf(target) }
    LaunchedEffect(selected) { if (!selected) openedTarget = target }
    val visible = selected && target == openedTarget
    val transition = remember { MutableTransitionState(false) }.apply { targetState = visible }
    val animation = rememberTransition(transition, label = "Message action focus")
    val progress by animation.animateFloat(
        transitionSpec = { tween(if (motionEnabled) 180 else 0, easing = FastOutSlowInEasing) },
        label = "Bubble lift and scrim",
    ) { if (it) 1f else 0f }
    val layer = rememberGraphicsLayer()
    var rowBounds by remember { mutableStateOf(Rect.Zero) }
    var anchor by remember { mutableStateOf(Rect.Zero) }
    Box(
        modifier
            .onGloballyPositioned {
                rowBounds = Rect(it.localToWindow(Offset.Zero), it.size.toSize())
            }
            .drawWithContent {
                layer.record { this@drawWithContent.drawContent() }
                drawLayer(layer)
            }
    ) {
        bubble(
            Modifier
                .onGloballyPositioned { anchor = Rect(it.localToWindow(Offset.Zero), it.size.toSize()) }
                .combinedClickable(
                    enabled = allowed,
                    onClick = {},
                    onLongClickLabel = "Message actions",
                    onLongClick = { openedTarget = target; content.request(ActionCommand.OpenMenu, messageId) },
                )
        )
    }
    val safe = allowed && target == openedTarget
    if (safe && (transition.currentState || transition.targetState) && anchor.width > 0 && anchor.height > 0) {
        val dismiss = { content.request(ActionCommand.CloseMenu) }
        Popup(
            popupPositionProvider = object : PopupPositionProvider {
                override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize,
                    layoutDirection: LayoutDirection, popupContentSize: IntSize) = IntOffset.Zero
            },
            onDismissRequest = dismiss,
            properties = PopupProperties(focusable = true, clippingEnabled = false),
        ) {
            val focus = remember { FocusRequester() }
            LaunchedEffect(Unit) { focus.requestFocus() }
            Box(Modifier.fillMaxSize().onPreviewKeyEvent {
                if (it.key == Key.Escape && it.type == KeyEventType.KeyUp) { dismiss(); true } else false
            }.focusRequester(focus).focusable()) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .20f * progress))
                    .clickable(interactionSource = null, indication = null, onClick = dismiss)
                    .semantics { contentDescription = "Dismiss message actions" })
                Layout(content = {
                    Box(Modifier.graphicsLayer { alpha = progress }.verticalScroll(rememberScrollState())) { above() }
                    Box(Modifier.graphicsLayer {
                        scaleX = 1f + .01f * progress
                        scaleY = 1f + .01f * progress
                    }.verticalScroll(rememberScrollState())) {
                        val density = androidx.compose.ui.platform.LocalDensity.current
                        Canvas(Modifier.size(with(density) { anchor.width.toDp() }, with(density) { anchor.height.toDp() })
                            .semantics { contentDescription = "${target.sender.displayName}, ${target.text}, ${target.displayTime}" }) {
                            clipRect {
                                translate(rowBounds.left - anchor.left, rowBounds.top - anchor.top) { drawLayer(layer) }
                            }
                        }
                    }
                    Box(Modifier.graphicsLayer { alpha = progress; translationY = 8.dp.toPx() * (1f - progress) }.verticalScroll(rememberScrollState())) { below() }
                }, modifier = Modifier.fillMaxSize()) { children, constraints ->
                    val margin = 16.dp.roundToPx()
                    val gap = 12.dp.roundToPx()
                    val availableWidth = (constraints.maxWidth - margin * 2).coerceAtLeast(0)
                    val menuConstraints = Constraints(maxWidth = availableWidth,
                        maxHeight = (constraints.maxHeight / 2 - margin * 2).coerceAtLeast(0))
                    val top = children[0].measure(menuConstraints.copy(maxHeight = (constraints.maxHeight / 4 - margin).coerceAtLeast(0)))
                    val bottom = children[2].measure(menuConstraints)
                    val topGap = if (top.height > 0) gap else 0
                    val bottomGap = if (bottom.height > 0) gap else 0
                    val bubbleHeight = anchor.height.roundToInt().coerceAtMost(
                        (constraints.maxHeight - margin * 2 - top.height - bottom.height - topGap - bottomGap).coerceAtLeast(0))
                    val selectedBubble = children[1].measure(Constraints.fixed(
                        anchor.width.roundToInt().coerceAtMost(availableWidth), bubbleHeight))
                    val groupHeight = top.height + topGap + selectedBubble.height + bottomGap + bottom.height
                    val y = (anchor.top.roundToInt() - top.height - topGap)
                        .coerceIn(margin, maxOf(margin, constraints.maxHeight - margin - groupHeight))
                    val x = anchor.left.roundToInt().coerceIn(margin, maxOf(margin, constraints.maxWidth - margin - selectedBubble.width))
                    fun menuX(width: Int) = x.coerceAtMost(constraints.maxWidth - margin - width).coerceAtLeast(margin)
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        top.place(menuX(top.width), y)
                        selectedBubble.place(x, y + top.height + topGap)
                        bottom.place(menuX(bottom.width), y + top.height + topGap + selectedBubble.height + bottomGap)
                    }
                }
            }
        }
    }
}

/** Vertical, capability-filtered menu with no tap-to-open control. Existing explicit
 * [MessageActions] remains available for hosts choosing their own activation presentation. */
@Composable
fun MessageActionMenu(content: ActionsContent, messageId: String, slots: ActionSlots = LocalActionSlots.current) {
    val enabled = content.state.capabilities[messageId].orEmpty()
    if (content.state.menuTargetId != messageId || content.conversation.messages.none { it.id == messageId } || enabled.isEmpty()) return
    ActionTheme(slots = slots) {
        val style = LocalActionStyle.current
        Column(Modifier.widthIn(min = 192.dp, max = 240.dp).shadow(8.dp, style.menuShape).background(style.menuBackground, style.menuShape)
            .padding(vertical = 4.dp)) {
            listOf(MessageAction.Copy to ActionCommand.Copy, MessageAction.Quote to ActionCommand.Quote,
                MessageAction.Edit to ActionCommand.BeginEdit, MessageAction.Delete to ActionCommand.RequestDelete).forEach { (capability, command) ->
                if (capability in enabled) Box(Modifier.fillMaxWidth(), propagateMinConstraints = true) {
                    slots.control(ActionControlContent(command, !content.state.pending) { content.request(command, messageId) })
                }
            }
            Box(Modifier.fillMaxWidth(), propagateMinConstraints = true) {
                slots.control(ActionControlContent(ActionCommand.CloseMenu, !content.state.pending) { content.request(ActionCommand.CloseMenu) })
            }
        }
    }
}
