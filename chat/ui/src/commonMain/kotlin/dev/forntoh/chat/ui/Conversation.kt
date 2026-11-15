package dev.forntoh.chat.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyLayoutScrollScope
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.forntoh.chat.core.ConversationEvent
import dev.forntoh.chat.core.ConversationState
import dev.forntoh.chat.core.Delivery
import dev.forntoh.chat.core.Message
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch

/**
 * Renders a host-owned timeline and guarded composer using foundation or optional themed slots.
 *
 * Keep immutable [state] authoritative. Stable message IDs preserve row identity in host order.
 * The host retains drafts across navigation, validates access and rejects stale service results.
 * Changing conversation or unmounting discards UI latches and remembered scroll state.
 *
 * Send latches before [onEvent] and returns to latest; a new draft revision resolves the latch.
 * Acceptance and delivery remain separate host decisions. Retry is unlatched and available only
 * for outgoing failed rows. Callbacks run synchronously and propagate exceptions: resolve a
 * locally failed submission with a new revision rather than leaving it pending.
 *
 * Explicit renderers override [ConversationTheme] slots. Custom content owns labels, focus and
 * accessibility; expose sender, body, time and delivery once, followed by separately named actions.
 * Forward guarded [ComposerContent] callbacks instead of keeping another draft in a renderer.
 * Optional `chat-material3` supplies [ConversationSlots] implementations without another state engine.
 *
 * Prepending history preserves the visible row and offset; removing it selects the nearest surviving
 * row in prior order, preferring the later row on a tie. Arrivals follow only an existing bottom reader.
 * Send and Latest resume following; keyboard/composer resizing preserves that mode. The measured
 * overlay composer height provides trailing scroll clearance. Supply bounded height and native insets.
 *
 * [LocalChatMotion] controls finite presentation timing, including a maximum 360ms Latest journey.
 * Initial rows and history prepends stay opaque; publish [HistoryStatus.Loading] during initial
 * hydration to prevent a tail entrance. Animations never emit host intents.
 *
 * @param onLoadEarlier Publish Loading before starting async paging; the default exhausted history emits no requests.
 * @param modifier Applies IME padding within the host's bounded viewport.
 */
@Composable
fun Conversation(
    state: ConversationState,
    onEvent: (ConversationEvent) -> Unit,
    modifier: Modifier = Modifier,
    messageContent: (@Composable (message: Message, isCurrentParticipant: Boolean, onRetry: (() -> Unit)?) -> Unit)? = null,
    composerContent: (@Composable (ComposerContent) -> Unit)? = null,
    history: HistoryState = HistoryState(),
    onLoadEarlier: (HistoryRequest) -> Unit = {},
    historyContent: (@Composable (HistoryContent) -> Unit)? = null,
    latestContent: (@Composable (LatestContent) -> Unit)? = null,
) {
    Conversation(
        state, onEvent, LocalConversationSlots.current, modifier, messageContent,
        composerContent, history, onLoadEarlier, historyContent, latestContent,
    )
}

/**
 * Renders [Conversation] with an explicit slot bundle, ahead of ambient theme defaults.
 * Explicit renderer arguments still take precedence over [slots]. All state, paging and
 * cancellation contracts are shared with the ambient-slot overload.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Composable
fun Conversation(
    state: ConversationState,
    onEvent: (ConversationEvent) -> Unit,
    slots: ConversationSlots,
    modifier: Modifier = Modifier,
    messageContent: (@Composable (message: Message, isCurrentParticipant: Boolean, onRetry: (() -> Unit)?) -> Unit)? = null,
    composerContent: (@Composable (ComposerContent) -> Unit)? = null,
    history: HistoryState = HistoryState(),
    onLoadEarlier: (HistoryRequest) -> Unit = {},
    historyContent: (@Composable (HistoryContent) -> Unit)? = null,
    latestContent: (@Composable (LatestContent) -> Unit)? = null,
) {
    val latestState by rememberUpdatedState(state)
    val motion = LocalChatMotion.current
    var submitted by remember(state.conversationId, state.draft.revision) { mutableStateOf(false) }
    var sendScrollRequest by remember(state.conversationId) { mutableIntStateOf(0) }
    val pending = state.draft.submitting || submitted
    var composerHeight by remember { mutableIntStateOf(0) }
    val composerInset = with(LocalDensity.current) { composerHeight.toDp() }
    val viewportPadding = LocalConversationViewportPadding.current
    val headerInset = viewportPadding.calculateTopPadding()
    val headerHeight = with(LocalDensity.current) { headerInset.toPx() }
    ConversationTheme(slots) {
        Box(modifier.fillMaxSize().imePadding()) {
            key(state.conversationId) {
                val list = rememberLazyListState()
                val scope = rememberCoroutineScope()
                val density = LocalDensity.current
                var requested by remember(
                    history.revision,
                    history.status,
                ) { mutableStateOf(false) }
                val currentHistory by rememberUpdatedState(history)
                val currentRequest by rememberUpdatedState(onLoadEarlier)
                val loadEarlier = {
                    if (!requested && currentHistory.status in listOf(
                            HistoryStatus.Idle,
                            HistoryStatus.Error,
                        )
                    ) {
                        requested = true
                        currentRequest(
                            HistoryRequest(
                                state.conversationId,
                                currentHistory.revision,
                            ),
                        )
                    }
                }
                val currentLoad by rememberUpdatedState(loadEarlier)
                LaunchedEffect(history) {
                    snapshotFlow { list.firstVisibleItemIndex == 0 && list.firstVisibleItemScrollOffset == 0 }
                        .distinctUntilChanged().collect { atStart ->
                            if (atStart && currentHistory.status == HistoryStatus.Idle) currentLoad()
                        }
                }
                var previousIds by remember { mutableStateOf<List<String>>(emptyList()) }
                var newMessages by remember { mutableStateOf(false) }
                var followingLatest by remember { mutableStateOf(true) }
                var highlightedId by remember { mutableStateOf<String?>(null) }
                var navigation by remember { mutableStateOf<Job?>(null) }
                var replyNavigationTarget by remember { mutableStateOf<String?>(null) }
                var latestNavigation by remember { mutableStateOf<Job?>(null) }
                // Reader intent wins even when a native viewport resize and an arrival share a frame.
                LaunchedEffect(list) {
                    list.interactionSource.interactions.collect { interaction ->
                        when (interaction) {
                            is DragInteraction.Start -> {
                                followingLatest = false
                                replyNavigationTarget = null
                                navigation?.cancel()
                            }

                            is DragInteraction.Stop, is DragInteraction.Cancel -> followingLatest = !list.canScrollForward
                        }
                    }
                }
                val navigate: (String) -> Unit = { targetId ->
                    if (latestState.conversationId == state.conversationId && latestState.messages.any { it.id == targetId }) {
                        navigation?.cancel()
                        followingLatest = false
                        highlightedId = null
                        replyNavigationTarget = targetId
                        navigation = scope.launch {
                            val located = snapshotFlow { latestState.messages.map { it.id } }
                                .mapLatest { currentIds ->
                                    val index = currentIds.indexOf(targetId)
                                    if (index < 0) return@mapLatest false
                                    if (motion.enabled) {
                                        list.animateScrollToItem(index + 1)
                                    } else {
                                        list.scrollToItem(index + 1)
                                    }
                                    val layout = list.layoutInfo
                                    val target = layout.visibleItemsInfo.find { it.key == targetId }
                                        ?: return@mapLatest false
                                    val rows = latestState.messages
                                    val row = rows.getOrNull(index)
                                    val group = MessageGroupContext(
                                        rows.getOrNull(index - 1)?.sender?.id != row?.sender?.id,
                                        rows.getOrNull(index + 1)?.sender?.id != row?.sender?.id,
                                    )
                                    val gap = if (index == 0) {
                                        0f
                                    } else {
                                        with(density) {
                                            slots.messageSpacing(group).toPx()
                                        }
                                    }
                                    val distance = target.offset + (target.size + gap) / 2f -
                                        (layout.viewportStartOffset + layout.viewportEndOffset - composerHeight + headerHeight) / 2f
                                    if (motion.enabled) {
                                        list.animateScrollBy(distance)
                                    } else {
                                        list.scrollBy(distance)
                                    }
                                    list.layoutInfo.visibleItemsInfo.any { it.key == targetId } &&
                                        latestState.messages.map { it.id } == currentIds
                                }.first()
                            if (located) {
                                highlightedId = targetId
                                try {
                                    delay(900)
                                } finally {
                                    highlightedId = null
                                }
                            }
                        }
                    }
                }
                val ids = state.messages.map { it.id }
                // Presentation admission uses identity and explicit history loading, never text/time.
                var renderedIds by remember { mutableStateOf(ids) }
                var renderedHistory by remember { mutableStateOf(history.status) }
                val enteringIds = remember(ids) {
                    val tail = renderedIds.lastOrNull()?.let(ids::indexOf) ?: -1
                    when {
                        history.status == HistoryStatus.Loading || renderedHistory == HistoryStatus.Loading -> emptySet()
                        tail >= 0 -> ids.drop(tail + 1).filterNot { it in renderedIds }.toSet()
                        renderedIds.isEmpty() -> ids.toSet()
                        else -> emptySet()
                    }
                }
                SideEffect {
                    renderedIds = ids
                    renderedHistory = history.status
                }
                // One shared progress value per appended snapshot also covers an empty timeline.
                // Virtualized rows read current progress rather than starting their own entrance.
                val insertionProgress = remember { Animatable(1f) }
                LaunchedEffect(ids) {
                    if (motion.enabled && enteringIds.isNotEmpty() && followingLatest) {
                        insertionProgress.snapTo(0f)
                        insertionProgress.animateTo(1f, tween(motion.insertionMillis, easing = motion.easing))
                    } else {
                        insertionProgress.snapTo(1f)
                    }
                }
                LaunchedEffect(motion.enabled) {
                    if (!motion.enabled) insertionProgress.snapTo(1f)
                }
                val returnToLatest = {
                    navigation?.cancel()
                    replyNavigationTarget = null
                    followingLatest = true
                    newMessages = false
                    navigation = scope.launch {
                        if (latestState.messages.isNotEmpty()) list.animateToLatest(motion)
                        followingLatest = !list.canScrollForward
                    }
                    latestNavigation = navigation
                }
                LaunchedEffect(motion.enabled) {
                    if (!motion.enabled && navigation?.isActive == true) {
                        val target = replyNavigationTarget
                        navigation?.cancel()
                        if (followingLatest) {
                            returnToLatest()
                        } else if (target != null) {
                            navigate(target)
                        }
                    }
                }
                LaunchedEffect(sendScrollRequest) {
                    if (sendScrollRequest > 0) returnToLatest()
                }
                LaunchedEffect(ids) {
                    val oldLayout = list.layoutInfo
                    val wasAtBottom = followingLatest
                    val anchor =
                        oldLayout.visibleItemsInfo.firstOrNull { it.index == list.firstVisibleItemIndex }
                    val anchorOffset = list.firstVisibleItemScrollOffset
                    val appended = previousIds.lastOrNull()?.let { oldLast ->
                        val index = ids.indexOf(oldLast)
                        index >= 0 && index < ids.lastIndex
                    } == true
                    if (ids.isNotEmpty()) {
                        if (previousIds.isEmpty()) {
                            list.scrollToLatest(ids.size)
                        } else if (wasAtBottom && appended) {
                            if (navigation?.isActive != true) returnToLatest()
                        } else if (!wasAtBottom && navigation?.isActive != true && anchor != null) {
                            // The start marker is a real scroll anchor too. Preserve it when
                            // the first row survives; prepend/removal still uses message fallback.
                            if (anchor.key == HISTORY_MARKER && ids.firstOrNull() == previousIds.firstOrNull()) {
                                list.scrollToItem(0, anchorOffset)
                            } else {
                                val surviving = ids.indexOf(anchor.key).takeIf { it >= 0 }
                                    ?: (1..previousIds.size).firstNotNullOfOrNull { distance ->
                                        val later = previousIds.getOrNull(anchor.index - 1 + distance)
                                            ?.let(ids::indexOf)
                                        val earlier = previousIds.getOrNull(anchor.index - 1 - distance)
                                            ?.let(ids::indexOf)
                                        later?.takeIf { it >= 0 } ?: earlier?.takeIf { it >= 0 }
                                    }
                                if (surviving != null) list.scrollToItem(surviving + 1, anchorOffset)
                            }
                        }
                    }
                    if (appended && !wasAtBottom) newMessages = true
                    previousIds = ids
                }
                LaunchedEffect(list) {
                    var previousViewport = list.layoutInfo.viewportSize
                    var previousIndex = list.firstVisibleItemIndex
                    var previousOffset = list.firstVisibleItemScrollOffset
                    snapshotFlow {
                        TimelineObservation(
                            previousIds,
                            list.layoutInfo,
                            list.firstVisibleItemIndex,
                            list.firstVisibleItemScrollOffset,
                            list.canScrollForward,
                        )
                    }.collect { observed ->
                        val viewportChanged = previousViewport != observed.layout.viewportSize
                        val movedBackward = observed.firstIndex < previousIndex ||
                            (observed.firstIndex == previousIndex && observed.firstOffset < previousOffset)
                        previousViewport = observed.layout.viewportSize
                        previousIndex = observed.firstIndex
                        previousOffset = observed.firstOffset
                        if (navigation?.isActive == true) {
                            if (followingLatest && movedBackward) {
                                if (!viewportChanged && navigation !== latestNavigation) {
                                    followingLatest = false
                                    navigation?.cancel()
                                }
                            }
                            return@collect
                        }
                        if (observed.layout.totalItemsCount == observed.ids.size + 1) {
                            val trailingRows = observed.canScrollForward && observed.ids.isNotEmpty()
                            val readerAtEnd = !movedBackward || viewportChanged
                            if (followingLatest && trailingRows && readerAtEnd) {
                                // Never force a remeasure from a snapshot callback during layout.
                                withFrameNanos { }
                                val samePosition = list.firstVisibleItemIndex == observed.firstIndex &&
                                    list.firstVisibleItemScrollOffset == observed.firstOffset
                                if (!list.isScrollInProgress && observed.ids == previousIds && samePosition) {
                                    list.scrollToLatest(observed.ids.size)
                                }
                            } else {
                                followingLatest = !observed.canScrollForward
                            }
                            if (followingLatest) newMessages = false
                        }
                    }
                }
                val focusManager = LocalFocusManager.current
                Box(
                    Modifier.fillMaxSize().pointerInput(focusManager) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            val up = waitForUpOrCancellation(pass = PointerEventPass.Initial)
                            if (up != null && up.uptimeMillis - down.uptimeMillis < viewConfiguration.longPressTimeoutMillis) {
                                awaitPointerEvent(PointerEventPass.Final)
                                focusManager.clearFocus()
                            }
                        }
                    },
                ) {
                    LazyColumn(
                        Modifier.fillMaxSize().testTag("timeline"),
                        state = list,
                        contentPadding = PaddingValues(
                            start = 12.dp,
                            top = headerInset + 12.dp,
                            end = 12.dp,
                            bottom = composerInset + 12.dp,
                        ),
                    ) {
                        item(key = HISTORY_MARKER) {
                            (
                                historyContent ?: {
                                    slots.history(
                                        HistoryFeedbackContent(
                                            it,
                                            state.messages.isEmpty(),
                                        ),
                                    )
                                }
                                )(
                                HistoryContent(
                                    state = history,
                                    canLoadEarlier = !requested && history.status in listOf(
                                        HistoryStatus.Idle,
                                        HistoryStatus.Error,
                                    ),
                                    onLoadEarlier = loadEarlier,
                                ),
                            )
                        }
                        itemsIndexed(state.messages, key = { _, row -> row.id }) { index, message ->
                            val isCurrent = message.sender.id == state.currentParticipantId
                            val group = MessageGroupContext(
                                isFirst = state.messages.getOrNull(index - 1)?.sender?.id != message.sender.id,
                                isLast = state.messages.getOrNull(index + 1)?.sender?.id != message.sender.id,
                            )
                            Column(
                                Modifier.graphicsLayer {
                                    alpha = if (motion.enabled && message.id in enteringIds) insertionProgress.value else 1f
                                }.fillMaxWidth().padding(
                                    top = if (index == 0) 0.dp else slots.messageSpacing(group),
                                )
                                    .testTag("message-${message.id}"),
                                horizontalAlignment = if (isCurrent) Alignment.End else Alignment.Start,
                            ) {
                                val presentation =
                                    MessagePresentation(message.id == highlightedId, navigate)
                                CompositionLocalProvider(
                                    LocalMessageGroupContext provides group,
                                    LocalMessagePresentation provides presentation,
                                ) {
                                    val renderer: @Composable (Message, Boolean, (() -> Unit)?) -> Unit =
                                        messageContent
                                            ?: slots.presentedMessage?.let { presented ->
                                                { row, outgoing, retry ->
                                                    presented(
                                                        row,
                                                        outgoing,
                                                        retry,
                                                        presentation,
                                                    )
                                                }
                                            } ?: slots.message
                                    renderer(
                                        message,
                                        isCurrent,
                                        if (isCurrent && message.delivery == Delivery.Failed) {
                                            {
                                                onEvent(
                                                    ConversationEvent.RetryMessage(
                                                        conversationId = state.conversationId,
                                                        messageId = message.id,
                                                    ),
                                                )
                                            }
                                        } else {
                                            null
                                        },
                                    )
                                }
                            }
                        }
                    }
                    val awayFromLatest by remember { derivedStateOf { list.canScrollForward } }
                    TimelineLatest(
                        awayFromLatest,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = composerInset).padding(12.dp),
                    ) {
                        (latestContent ?: slots.latest)(
                            LatestContent(newMessages) {
                                returnToLatest()
                            },
                        )
                    }
                }
            }
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().onSizeChanged {
                    composerHeight = it.height
                }.testTag("composer-area").padding(bottom = viewportPadding.calculateBottomPadding()),
            ) {
                key(state.conversationId) {
                    fun currentDraftIsEditable(): Boolean = latestState.conversationId == state.conversationId &&
                        latestState.draft == state.draft && !submitted && !state.draft.submitting
                    Box(Modifier.chatContentSize()) {
                        (composerContent ?: slots.composer)(
                            ComposerContent(
                                state.draft,
                                pending,
                                onTextChange = {
                                    if (currentDraftIsEditable()) {
                                        onEvent(
                                            ConversationEvent.DraftChanged(
                                                conversationId = state.conversationId,
                                                text = it,
                                            ),
                                        )
                                    }
                                },
                                onSend = {
                                    if (currentDraftIsEditable() &&
                                        state.draft.hasContent
                                    ) {
                                        submitted = true
                                        sendScrollRequest++
                                        onEvent(
                                            ConversationEvent.SendDraft(
                                                conversationId = state.conversationId,
                                                revision = state.draft.revision,
                                                text = state.draft.text,
                                                attachments = state.draft.attachments.toList(),
                                            ),
                                        )
                                    }
                                },
                                onDraftAction = { action ->
                                    if (currentDraftIsEditable()) {
                                        submitted = true
                                        action()
                                    }
                                },
                            ),
                        )
                    }
                }
            }
        }
    }
}

private const val HISTORY_MARKER = 0L

@Composable
private fun TimelineLatest(visible: Boolean, modifier: Modifier, content: @Composable () -> Unit) {
    val motion = LocalChatMotion.current
    if (!motion.enabled || motion.controlMillis == 0) {
        if (visible) Box(modifier) { content() }
        return
    }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(motion.duration(motion.controlMillis), easing = motion.easing)) +
            slideInVertically(
                tween(motion.duration(motion.controlMillis), easing = motion.easing),
            ) { it / 2 },
        exit = fadeOut(tween(motion.duration(motion.controlMillis), easing = motion.easing)) +
            slideOutVertically(
                tween(motion.duration(motion.controlMillis), easing = motion.easing),
            ) { it / 2 },
    ) { content() }
}

private data class TimelineObservation(
    val ids: List<String>,
    val layout: LazyListLayoutInfo,
    val firstIndex: Int,
    val firstOffset: Int,
    val canScrollForward: Boolean,
)

/** A single 360ms return, independent of history length. Nearby travel uses pixel scrolling;
 * distant travel seeks virtualized positions each frame so unseen intermediate rows do not
 * block the UI. Both paths settle at the measured trailing edge inside the same scroll session.
 * Reader drag and conversation scope retain cancellation; no distance-dependent tail is queued.
 */
private suspend fun LazyListState.animateToLatest(motion: ChatMotion) {
    scroll {
        val lazyScope = LazyLayoutScrollScope(this@animateToLatest, this)
        val startIndex = firstVisibleItemIndex
        val startOffset = firstVisibleItemScrollOffset
        val visible = layoutInfo.visibleItemsInfo
        val rowHeight = if (visible.isEmpty()) {
            1f
        } else {
            ((visible.last().offset + visible.last().size - visible.first().offset).toFloat() / visible.size).coerceAtLeast(1f)
        }
        val distant = layoutInfo.totalItemsCount - startIndex > maxOf(DISTANT_ROW_THRESHOLD, visible.size * DISTANT_VIEWPORT_COUNT)
        var previous = 0f
        animate(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = tween(motion.duration(motion.latestMillis), easing = motion.easing),
        ) { fraction, _ ->
            val layout = layoutInfo
            val lastIndex = layout.totalItemsCount - 1
            if (lastIndex >= 0 && canScrollForward) {
                if (distant) {
                    val position = startIndex + (lastIndex - startIndex) * fraction
                    val index = position.toInt().coerceIn(0, lastIndex)
                    val offset = ((position - index) * rowHeight + startOffset * (1f - fraction)).toInt()
                    if (index > firstVisibleItemIndex ||
                        (index == firstVisibleItemIndex && offset >= firstVisibleItemScrollOffset)
                    ) {
                        lazyScope.snapToItem(index, offset)
                    }
                } else {
                    val last = layout.visibleItemsInfo.lastOrNull { it.index == lastIndex }
                    val remaining = if (last != null) {
                        (last.offset + last.size + layout.afterContentPadding - layout.viewportEndOffset).toFloat()
                    } else {
                        lazyScope.calculateDistanceTo(lastIndex).toFloat()
                    }
                    val share = if (previous >= 1f) 1f else ((fraction - previous) / (1f - previous)).coerceIn(0f, 1f)
                    scrollBy(remaining.coerceAtLeast(0f) * share)
                }
            }
            previous = fraction
        }
        val layout = layoutInfo
        layout.visibleItemsInfo.lastOrNull { it.index == layout.totalItemsCount - 1 }?.let { last ->
            scrollBy((last.offset + last.size + layout.afterContentPadding - layout.viewportEndOffset).coerceAtLeast(0).toFloat())
        }
    }
}

/** Align the trailing edge too when the last row is taller than the timeline. */
private suspend fun LazyListState.scrollToLatest(lastIndex: Int) {
    scrollToItem(lastIndex)
    layoutInfo.visibleItemsInfo.lastOrNull { it.index == lastIndex }?.let { last ->
        scrollBy(
            (last.offset + last.size + layoutInfo.afterContentPadding - layoutInfo.viewportEndOffset)
                .coerceAtLeast(0).toFloat(),
        )
    }
}

private const val DISTANT_ROW_THRESHOLD = 30
private const val DISTANT_VIEWPORT_COUNT = 3
