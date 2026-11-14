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
import androidx.compose.ui.graphics.TransformOrigin
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
 * Entrance/exit use a 180ms eased fade, a 5% bubble lift anchored to the sender edge and continuous travel to/from its action
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
    val outgoing = target?.sender?.id == content.conversation.currentParticipantId
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
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .40f * progress))
                    .clickable(interactionSource = null, indication = null, onClick = dismiss)
                    .semantics { contentDescription = "Dismiss message actions" })
                // Scrim spans the window; only interactive content observes native safe areas.
                val safeInsets = WindowInsets.safeDrawing
                Layout(content = {
                    FocusActionSlot(progress) { if (visible) above() else presentation.above() }
                    Box(Modifier.graphicsLayer {
                        scaleX = 1f + .05f * progress
                        scaleY = 1f + .05f * progress
                        transformOrigin = TransformOrigin(if (outgoing) 1f else 0f, .5f)
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
                    val topMargin = 16.dp.roundToPx() + safeInsets.getTop(this)
                    val bottomMargin = 16.dp.roundToPx() + safeInsets.getBottom(this)
                    val leftInset = safeInsets.getLeft(this, layoutDirection)
                    val rightInset = safeInsets.getRight(this, layoutDirection)
                    val safeHeight = (constraints.maxHeight - topMargin - bottomMargin).coerceAtLeast(0)
                    val availableWidth = (constraints.maxWidth - leftInset - rightInset).coerceAtLeast(0)
                    val menuConstraints = Constraints(maxWidth = availableWidth,
                        maxHeight = safeHeight / 2)
                    val top = children[0].measure(menuConstraints.copy(maxHeight = safeHeight / 4))
                    val bottom = children[2].measure(menuConstraints)
                    val topGap = if (top.height > 0) 8.dp.roundToPx() else 0
                    val bottomGap = if (bottom.height > 0) 8.dp.roundToPx() else 0
                    val bubbleHeight = anchor.height.roundToInt().coerceAtMost(
                        ((safeHeight - top.height - bottom.height - topGap - bottomGap) / 1.05f).roundToInt().coerceAtLeast(0))
                    val selectedBubble = children[1].measure(Constraints.fixed(
                        anchor.width.roundToInt().coerceAtMost(availableWidth), bubbleHeight))
                    val growthY = selectedBubble.height * .025f
                    val minimumY = topMargin + top.height + topGap + kotlin.math.ceil(growthY).toInt()
                    val maximumY = maxOf(minimumY, constraints.maxHeight - bottomMargin - bottom.height - bottomGap - selectedBubble.height - kotlin.math.ceil(growthY).toInt())
                    val y = localAnchor.top.roundToInt().coerceIn(minimumY, maximumY)
                    val growthX = kotlin.math.ceil(selectedBubble.width * .05f).toInt()
                    val minimumX = leftInset + if (outgoing) growthX else 0
                    val maximumX = maxOf(minimumX, constraints.maxWidth - rightInset - selectedBubble.width - if (outgoing) 0 else growthX)
                    val x = localAnchor.left.roundToInt().coerceIn(minimumX, maximumX)
                    fun menuX(width: Int, bubbleX: Int) =
                        (if (outgoing) bubbleX + selectedBubble.width - width else bubbleX)
                            .coerceIn(leftInset, maxOf(leftInset, constraints.maxWidth - rightInset - width))
                    if (visible) presentation.position = IntOffset(x, y)
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        val destination = presentation.position
                        val bubbleX = (localAnchor.left + (destination.x - localAnchor.left) * progress).roundToInt()
                        val bubbleY = (localAnchor.top + (destination.y - localAnchor.top) * progress).roundToInt()
                        top.place(menuX(top.width, bubbleX), (bubbleY - growthY * progress).roundToInt() - topGap - top.height)
                        selectedBubble.place(bubbleX, bubbleY)
                        bottom.place(menuX(bottom.width, bubbleX), (bubbleY + selectedBubble.height + growthY * progress).roundToInt() + bottomGap)
                    }
                }
            }
        }
    }
}

@Composable
private fun FocusActionSlot(progress: Float, content: @Composable () -> Unit) {
    Layout(content = {
        Box(Modifier.graphicsLayer { alpha = progress }.verticalScroll(rememberScrollState())) {
            Layout(content = content) { children, constraints ->
                val clearance = 24.dp.roundToPx()
                val items = children.map { it.measure(constraints.offset(-2 * clearance, -2 * clearance)) }
                val width = items.maxOfOrNull { it.width } ?: 0
                val height = items.maxOfOrNull { it.height } ?: 0
                if (width == 0 || height == 0) layout(0, 0) {}
                else layout(width + 2 * clearance, height + 2 * clearance) {
                    items.forEach { it.place(clearance, clearance) }
                }
            }
        }
    }) { children, constraints ->
        val clearance = 24.dp.roundToPx()
        val viewport = children.single().measure(constraints.offset(2 * clearance, 2 * clearance))
        val width = (viewport.width - 2 * clearance).coerceAtLeast(0)
        val height = (viewport.height - 2 * clearance).coerceAtLeast(0)
        layout(width, height) { viewport.place(-clearance, -clearance) }
    }
}

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
