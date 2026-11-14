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
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.Color
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
 * takes over drawing its bubble region without composing host content or callbacks a second time.
 * [above] and [below] can independently replace/add consumer content. Defaults show only the
 * existing capability-filtered actions below. Slots receive guarded [ActionsContent] via capture.
 *
 * Selection stays in the host's ActionState. Tap does not open the menu. Outside tap, Back/Escape
 * and Close emit CloseMenu; changed/removed targets, lost capabilities, pending work and a changed
 * conversation immediately remove the presentation. No animation completion emits an intent.
 * [motionEnabled] is a host override; Compose's platform duration scale also controls animations.
 * Entrance/exit use a 180ms eased fade, a 1% bubble lift and continuous travel to/from its action
 * layout, with no retained bitmap or resource IO.
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
    var popupReady by remember { mutableStateOf(false) }
    val transition = remember { MutableTransitionState(false) }.apply { targetState = visible && popupReady }
    val animation = rememberTransition(transition, label = "Message action focus")
    val progress by animation.animateFloat(
        transitionSpec = { tween(if (motionEnabled) 180 else 0, easing = FastOutSlowInEasing) },
        label = "Bubble lift and scrim",
    ) { if (it) 1f else 0f }
    val layer = rememberGraphicsLayer()
    val presentation = remember(content.conversation.conversationId, messageId) { FocusPresentation(above, below) }
    SideEffect {
        if (visible) { presentation.above = above; presentation.below = below }
    }
    var anchor by remember { mutableStateOf(Rect.Zero) }
    var rowBounds by remember { mutableStateOf(Rect.Zero) }
    val safeTarget = allowed && target == openedTarget
    val presenting = safeTarget && (visible || transition.currentState || transition.targetState)
    Box(modifier.onGloballyPositioned {
        rowBounds = Rect(it.localToScreen(Offset.Zero), it.size.toSize())
    }.drawWithContent {
        if (!presenting || !popupReady) layer.record { this@drawWithContent.drawContent() }
        if (presenting && popupReady) {
            clipRect(
                left = anchor.left - rowBounds.left,
                top = anchor.top - rowBounds.top,
                right = anchor.right - rowBounds.left,
                bottom = anchor.bottom - rowBounds.top,
                clipOp = ClipOp.Difference
            ) {
                drawLayer(layer)
            }
        } else drawLayer(layer)
    }) {
        bubble(
            Modifier
                .onGloballyPositioned { anchor = Rect(it.localToScreen(Offset.Zero), it.size.toSize()) }
                .combinedClickable(
                    enabled = allowed,
                    onClick = {},
                    onLongClickLabel = "Message actions",
                    onLongClick = { openedTarget = target; content.request(ActionCommand.OpenMenu, messageId) },
                )
        )
    }
    if (presenting && anchor.width > 0 && anchor.height > 0) {
        val dismiss = { content.request(ActionCommand.CloseMenu) }
        Popup(
            popupPositionProvider = object : PopupPositionProvider {
                override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize,
                    layoutDirection: LayoutDirection, popupContentSize: IntSize) = IntOffset.Zero
            },
            onDismissRequest = dismiss,
            properties = focusPopupProperties(),
        ) {
            DisposableEffect(Unit) { onDispose { popupReady = false } }
            val focus = remember { FocusRequester() }
            LaunchedEffect(Unit) { focus.requestFocus() }
            var popupOrigin by remember { mutableStateOf(Offset.Zero) }
            Box(Modifier.fillMaxSize().onGloballyPositioned {
                popupOrigin = it.localToScreen(Offset.Zero)
                popupReady = true
            }.onPreviewKeyEvent {
                if (it.key == Key.Escape && it.type == KeyEventType.KeyUp) { dismiss(); true } else false
            }.focusRequester(focus).focusable()) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .20f * progress))
                    .clickable(interactionSource = null, indication = null, onClick = dismiss)
                    .semantics { contentDescription = "Dismiss message actions" })
                // Scrim spans the window; only interactive content observes native safe areas.
                val safeInsets = WindowInsets.safeDrawing
                Layout(content = {
                    FocusActionSlot(progress) { if (visible) above() else presentation.above() }
                    Box(Modifier.graphicsLayer {
                        scaleX = 1f + .01f * progress
                        scaleY = 1f + .01f * progress
                    }.verticalScroll(rememberScrollState())) {
                        val density = androidx.compose.ui.platform.LocalDensity.current
                        Canvas(
                            Modifier
                                .size(with(density) { anchor.width.toDp() }, with(density) { anchor.height.toDp() })
                                .semantics { contentDescription = "${target.sender.displayName}, ${target.text}, ${target.displayTime}" }
                        ) {
                            if (popupReady) clipRect {
                                translate(rowBounds.left - anchor.left, rowBounds.top - anchor.top) { drawLayer(layer) }
                            }
                        }
                    }
                    FocusActionSlot(progress) { if (visible) below() else presentation.below() }
                }, modifier = Modifier.fillMaxSize()) { children, constraints ->
                    val localAnchor = anchor.translate(-popupOrigin)
                    val margin = 16.dp.roundToPx()
                    val topMargin = margin + safeInsets.getTop(this)
                    val bottomMargin = margin + safeInsets.getBottom(this)
                    val safeHeight = (constraints.maxHeight - topMargin - bottomMargin).coerceAtLeast(0)
                    val availableWidth = (constraints.maxWidth - margin * 2).coerceAtLeast(0)
                    val menuConstraints = Constraints(maxWidth = availableWidth,
                        maxHeight = safeHeight / 2)
                    val top = children[0].measure(menuConstraints.copy(maxHeight = safeHeight / 4))
                    val bottom = children[2].measure(menuConstraints)
                    val bubbleHeight = anchor.height.roundToInt().coerceAtMost(
                        (safeHeight - top.height - bottom.height).coerceAtLeast(0))
                    val selectedBubble = children[1].measure(Constraints.fixed(
                        anchor.width.roundToInt().coerceAtMost(constraints.maxWidth), bubbleHeight))
                    val minimumY = topMargin + top.height
                    val maximumY = maxOf(minimumY, constraints.maxHeight - bottomMargin - bottom.height - selectedBubble.height)
                    val y = localAnchor.top.roundToInt().coerceIn(minimumY, maximumY)
                    val x = localAnchor.left.roundToInt().coerceIn(0, maxOf(0, constraints.maxWidth - selectedBubble.width))
                    fun menuX(width: Int) = x.coerceAtMost(constraints.maxWidth - margin - width).coerceAtLeast(margin)
                    if (visible) presentation.position = IntOffset(x, y)
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        val destination = presentation.position
                        val bubbleX = (localAnchor.left + (destination.x - localAnchor.left) * progress).roundToInt()
                        val bubbleY = (localAnchor.top + (destination.y - localAnchor.top) * progress).roundToInt()
                        top.place(menuX(top.width), bubbleY - top.height)
                        selectedBubble.place(bubbleX, bubbleY)
                        bottom.place(menuX(bottom.width), bubbleY + selectedBubble.height)
                    }
                }
            }
        }
    }
}

@Composable
private fun FocusActionSlot(progress: Float, content: @Composable () -> Unit) {
    Box(Modifier.graphicsLayer { alpha = progress }.verticalScroll(rememberScrollState())) {
        Layout(content = content) { children, constraints ->
            val inset = 24.dp.roundToPx()
            val items = children.map { it.measure(constraints.offset(-2 * inset, -2 * inset)) }
            val width = items.maxOfOrNull { it.width } ?: 0
            val height = items.maxOfOrNull { it.height } ?: 0
            if (width == 0 || height == 0) layout(0, 0) {}
            else layout(constraints.constrainWidth(width + 2 * inset), constraints.constrainHeight(height + 2 * inset)) {
                items.forEach { it.place(inset, inset) }
            }
        }
    }
}

// Presentation-only exit cache. Old slot callbacks remain subject to ActionsContent's live guards.
private class FocusPresentation(var above: @Composable () -> Unit, var below: @Composable () -> Unit) {
    var position = IntOffset.Zero
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
