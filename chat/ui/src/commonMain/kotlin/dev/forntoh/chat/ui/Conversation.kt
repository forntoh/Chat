package dev.forntoh.chat.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.DragInteraction
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.animate
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyLayoutScrollScope
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.forntoh.chat.core.*

/**
 * Composer slot input supplied by [Conversation]. Render [draft] without owning another copy.
 * [pending] includes both host submission state and the current composition's send latch.
 * [onTextChange] and [onSend] are guarded callbacks: edits are ignored while pending and sends
 * require draft content. Call them only for explicit user input, never during composition.
 * Callbacks capture the rendered snapshot; do not retain them after that snapshot is replaced.
 * Custom content owns its labels, focus behavior and accessibility semantics.
 *
 * @param draft Rendered host snapshot; display its text without a separate local draft copy.
 * @param pending Whether host submission or the composition's send latch blocks editing and sending.
 * @param onTextChange Requests exact replacement text on user input; ignored while pending.
 * @param onDraftAction Runs an explicit attachment mutation intent only while this exact draft is current and not pending.
 * Latches until a new draft revision, including cancellation/denial. Custom adapters must forward this guard.
 * @param onSend Requests submission of the rendered draft with content; ignored while pending.
 */
class ComposerContent(
    val draft: Draft,
    val pending: Boolean,
    val onTextChange: (String) -> Unit,
    val onSend: () -> Unit,
    val onDraftAction: (() -> Unit) -> Unit,
) {
    constructor(
        draft: Draft,
        pending: Boolean,
        onTextChange: (String) -> Unit,
        onSend: () -> Unit
    ) : this(draft, pending, onTextChange, onSend, { action -> if (!pending) action() })

    val canSend: Boolean get() = !pending && draft.hasContent
}

/**
 * Shared timeline/composer behavior from `chat-ui`; requires Compose foundation, not Material3.
 * Explicit renderers override ambient ConversationTheme slots; absent themes use neutral defaults.
 * Use optional JetchatTheme from chat-material3 to install Material3 presentation into these slots.
 *
 * The host owns [state] and handles [onEvent]. Rows retain [Message.id] keys in host list order within each conversation.
 * Changing conversation discards remembered timeline and message-slot state.
 * [messageContent] receives the row, current-sender flag and a nullable retry callback; only
 * current-participant failed rows get retry. Retry is not latched: the host must update delivery
 * and reject duplicate or stale requests. Slots own their presentation and semantics. A custom
 * row should expose sender, content, host-formatted time and delivery once in that order,
 * with separately named actions. Swapping slots at this call site does not accept a draft
 * or reset pending submission; keep [state] authoritative rather than copying it into a slot.
 * [composerContent] receives guarded composer callbacks and pending state via [ComposerContent].
 * The composer overlays the timeline. Its measured height becomes scrollable bottom clearance,
 * keeping latest content above it while history scrolls behind transparent areas. Custom composers
 * own their backgrounds; avoid painting an opaque full-width band for a floating presentation.
 *
 * A send latches synchronously before invoking [onEvent]. Changing conversation or draft
 * revision resets that latch; unmounting also discards it. An allowed Send activation also
 * returns to latest, including from history, without accepting the draft or inferring delivery.
 * Retain drafts and pending state in
 * the host across navigation. The UI does not persist data or reject stale service results.
 * [modifier] applies to a full-size layout with IME padding; the host must provide window
 * insets and bounded height. Callback exceptions propagate; the host must resolve the draft
 * with a new revision if its submission fails locally.
 *
 * For a host-rendered composer, forward changes with `content.onTextChange(newText)` and an
 * explicit send with `content.onSend()`, enabling the send control with `content.canSend`.
 *
 * [history] and [onLoadEarlier] are optional host-driven paging contracts in `chat-ui`.
 * Supply [historyContent] for loading/error/empty feedback and [latestContent] for a deliberate
 * return control while away from the bottom. Requests can originate from a boundary effect;
 * no transport runs inside this component. Stable row keys preserve position on prepend;
 * a removed anchor uses the nearest survivor in prior host order (later row wins ties),
 * retaining its scroll offset. Incoming rows follow only an existing bottom reader; Send and
 * return-to-latest explicitly resume following. Keyboard/composer resizing retains that mode,
 * keeping the final row's trailing content visible even when the row exceeds the viewport.
 * Latest travel is capped at 360ms; distant positions are virtualized to avoid measuring skipped history.
 * Switching conversation resets
 * these UI latches; the host must isolate drafts and reject obsolete asynchronous results.
 *
 * @param state Authoritative host snapshot containing ordered messages and the retained draft.
 * @param onEvent Synchronous user-intent handler; return promptly and resolve submissions through host state.
 * @param modifier Outer layout modifier, empty by default; provide bounded height and host window insets.
 * @param messageContent Optional row renderer, overriding the effective theme, receiving the message, current-participant flag and nullable
 * retry callback. Retry is available only for outgoing failed rows; supply presentation and semantics.
 * @param composerContent Optional composer renderer, overriding the effective theme, receiving guarded input/send callbacks and pending state.
 * Supply its labels, focus behavior and accessibility semantics.
 * @param history Host paging availability; exhausted by default, so no requests are emitted.
 * @param onLoadEarlier Synchronous request handler; publish Loading before starting host async work.
 * @param historyContent Optional status/retry override at the start of the timeline; null uses the effective theme.
 * @param latestContent Optional latest-control override; null uses the effective theme.
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
        composerContent, history, onLoadEarlier, historyContent, latestContent
    )
}

/**
 * Shared timeline/composer behavior from `chat-ui`; requires Compose foundation, not Material3.
 * Renders conversation messages, history loading status, and input composer using explicit [slots].
 *
 * Explicit renderers or [slots] override ambient [ConversationTheme]; absent themes use neutral defaults.
 * Use optional JetchatTheme from chat-material3 to install Material3 presentation into these slots.
 *
 * @param state Authoritative host snapshot containing ordered messages and the retained draft.
 * @param onEvent Synchronous user-intent handler; return promptly and resolve submissions through host state.
 * @param slots [ConversationSlots] implementation supplying default rendering slots for the conversation.
 * @param modifier Outer layout modifier, empty by default; provide bounded height and host window insets.
 * @param messageContent Optional row renderer, overriding effective slots, receiving the message, current-participant flag and nullable retry callback.
 * @param composerContent Optional composer renderer, overriding effective slots, receiving guarded input/send callbacks and pending state.
 * @param history Host paging availability; exhausted by default, so no requests are emitted.
 * @param onLoadEarlier Synchronous request handler; publish Loading before starting host async work.
 * @param historyContent Optional status/retry override at the start of the timeline; null uses effective slots.
 * @param latestContent Optional latest-control override; null uses effective slots.
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
                    history.status
                ) { mutableStateOf(false) }
                val currentHistory by rememberUpdatedState(history)
                val currentRequest by rememberUpdatedState(onLoadEarlier)
                val loadEarlier = {
                    if (!requested && currentHistory.status in listOf(
                            HistoryStatus.Idle,
                            HistoryStatus.Error
                        )
                    ) {
                        requested = true
                        currentRequest(
                            HistoryRequest(
                                state.conversationId,
                                currentHistory.revision
                            )
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
                var latestNavigation by remember { mutableStateOf<Job?>(null) }
                // Reader intent wins even when a native viewport resize and an arrival share a frame.
                LaunchedEffect(list) {
                    list.interactionSource.interactions.collect { interaction ->
                        when (interaction) {
                            is DragInteraction.Start -> {
                                followingLatest = false
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
                        navigation = scope.launch {
                            val located = snapshotFlow { latestState.messages.map { it.id } }
                                .mapLatest { currentIds ->
                                    val index = currentIds.indexOf(targetId)
                                    if (index < 0) return@mapLatest false
                                    list.animateScrollToItem(index + 1)
                                    val layout = list.layoutInfo
                                    val target = layout.visibleItemsInfo.find { it.key == targetId }
                                        ?: return@mapLatest false
                                    val rows = latestState.messages
                                    val row = rows.getOrNull(index)
                                    val group = MessageGroupContext(
                                        rows.getOrNull(index - 1)?.sender?.id != row?.sender?.id,
                                        rows.getOrNull(index + 1)?.sender?.id != row?.sender?.id
                                    )
                                    val gap = if (index == 0) 0f else with(density) {
                                        slots.messageSpacing(group).toPx()
                                    }
                                    list.animateScrollBy(
                                        target.offset + (target.size + gap) / 2f -
                                                (layout.viewportStartOffset + layout.viewportEndOffset - composerHeight + headerHeight) / 2f
                                    )
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
                val returnToLatest = {
                    navigation?.cancel()
                    followingLatest = true
                    newMessages = false
                    navigation = scope.launch {
                        if (latestState.messages.isNotEmpty()) list.animateToLatest()
                        followingLatest = !list.canScrollForward
                    }
                    latestNavigation = navigation
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
                        } else if (!wasAtBottom && navigation?.isActive != true && anchor != null && previousIds.isNotEmpty()) {
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
                    if (appended && !wasAtBottom) newMessages = true
                    previousIds = ids
                }
                LaunchedEffect(list) {
                    var previousViewport = list.layoutInfo.viewportSize
                    var previousIndex = list.firstVisibleItemIndex
                    var previousOffset = list.firstVisibleItemScrollOffset
                    snapshotFlow {
                        TimelineObservation(previousIds, list.layoutInfo, list.firstVisibleItemIndex,
                            list.firstVisibleItemScrollOffset, list.canScrollForward)
                    }.collect { observed ->
                        val viewportChanged = previousViewport != observed.layout.viewportSize
                        val movedBackward = observed.firstIndex < previousIndex ||
                            (observed.firstIndex == previousIndex && observed.firstOffset < previousOffset)
                        previousViewport = observed.layout.viewportSize
                        previousIndex = observed.firstIndex
                        previousOffset = observed.firstOffset
                        if (navigation?.isActive == true) {
                            // Virtualized seeks update the requested index before layout clamps it.
                            // Their clamp is not reader intent; actual reader drag cancels above.
                            if (followingLatest && movedBackward && !viewportChanged && navigation !== latestNavigation) {
                                followingLatest = false
                                navigation?.cancel()
                            }
                            return@collect
                        }
                        if (observed.layout.totalItemsCount == observed.ids.size + 1) {
                            if (followingLatest && observed.canScrollForward && observed.ids.isNotEmpty() &&
                                (!movedBackward || viewportChanged)) {
                                // Never force a remeasure from a snapshot callback during layout.
                                withFrameNanos { }
                                if (!list.isScrollInProgress && observed.ids == previousIds &&
                                    list.firstVisibleItemIndex == observed.firstIndex &&
                                    list.firstVisibleItemScrollOffset == observed.firstOffset)
                                    list.scrollToLatest(observed.ids.size)
                            } else followingLatest = !observed.canScrollForward
                            if (followingLatest) newMessages = false
                        }
                    }
                }
                Box(Modifier.fillMaxSize()) {
                    LazyColumn(
                        Modifier.fillMaxSize().testTag("timeline"),
                        state = list,
                        contentPadding = PaddingValues(start = 12.dp, top = headerInset + 12.dp, end = 12.dp, bottom = composerInset + 12.dp),
                    ) {
                        item(key = HistoryMarker) {
                            (historyContent ?: {
                                slots.history(
                                    HistoryFeedbackContent(
                                        it,
                                        state.messages.isEmpty()
                                    )
                                )
                            })(
                                HistoryContent(
                                    state = history,
                                    canLoadEarlier = !requested && history.status in listOf(
                                        HistoryStatus.Idle,
                                        HistoryStatus.Error
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
                                Modifier.fillMaxWidth().padding(
                                    top = if (index == 0) 0.dp else slots.messageSpacing(group)
                                )
                                    .testTag("message-${message.id}"),
                                horizontalAlignment = if (isCurrent) Alignment.End else Alignment.Start
                            ) {
                                val presentation =
                                    MessagePresentation(message.id == highlightedId, navigate)
                                CompositionLocalProvider(
                                    LocalMessageGroupContext provides group,
                                    LocalMessagePresentation provides presentation
                                ) {
                                    val renderer: @Composable (Message, Boolean, (() -> Unit)?) -> Unit =
                                        messageContent
                                            ?: slots.presentedMessage?.let { presented ->
                                                { row, outgoing, retry ->
                                                    presented(
                                                        row,
                                                        outgoing,
                                                        retry,
                                                        presentation
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
                                                        messageId = message.id
                                                    )
                                                )
                                            }
                                        } else null,
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
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().onSizeChanged { composerHeight = it.height }.testTag("composer-area").padding(bottom = viewportPadding.calculateBottomPadding())) {
                (composerContent ?: slots.composer)(
                    ComposerContent(
                        state.draft, pending,
                        onTextChange = {
                            if (latestState.conversationId == state.conversationId && latestState.draft == state.draft && !submitted && !state.draft.submitting) {
                                onEvent(
                                    ConversationEvent.DraftChanged(
                                        conversationId = state.conversationId,
                                        text = it,
                                    ),
                                )
                            }
                        },
                        onSend = {
                            if (latestState.conversationId == state.conversationId && latestState.draft == state.draft && !submitted && !state.draft.submitting && state.draft.hasContent) {
                                submitted = true
                                sendScrollRequest++
                                onEvent(
                                    ConversationEvent.SendDraft(
                                        conversationId = state.conversationId,
                                        revision = state.draft.revision,
                                        text = state.draft.text,
                                        attachments = state.draft.attachments.toList(),
                                    )
                                )
                            }
                        },
                        onDraftAction = { action ->
                            if (latestState.conversationId == state.conversationId && latestState.draft == state.draft && !submitted && !state.draft.submitting) {
                                submitted = true
                                action()
                            }
                        },
                    )
                )
            }
        }
    }
}

private const val HistoryMarker = 0L

@Composable
private fun TimelineLatest(visible: Boolean, modifier: Modifier, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible, modifier, enter = fadeIn(tween(180)) + slideInVertically(tween(180)) { it / 2 },
        exit = fadeOut(tween(180)) + slideOutVertically(tween(180)) { it / 2 }) { content() }
}

private data class TimelineObservation(
    val ids: List<String>, val layout: LazyListLayoutInfo,
    val firstIndex: Int, val firstOffset: Int, val canScrollForward: Boolean,
)

/** A single 360ms return, independent of history length. Nearby travel uses pixel scrolling;
 * distant travel seeks virtualized positions each frame so unseen intermediate rows do not
 * block the UI. Both paths settle at the measured trailing edge inside the same scroll session.
 * Reader drag and conversation scope retain cancellation; no distance-dependent tail is queued.
 */
private suspend fun LazyListState.animateToLatest() {
    scroll {
        val lazyScope = LazyLayoutScrollScope(this@animateToLatest, this)
        val startIndex = firstVisibleItemIndex
        val startOffset = firstVisibleItemScrollOffset
        val visible = layoutInfo.visibleItemsInfo
        val rowHeight = if (visible.isEmpty()) 1f else
            ((visible.last().offset + visible.last().size - visible.first().offset).toFloat() / visible.size).coerceAtLeast(1f)
        val distant = layoutInfo.totalItemsCount - startIndex > maxOf(30, visible.size * 3)
        var previous = 0f
        animate(initialValue = 0f, targetValue = 1f, animationSpec = tween(360)) { fraction, _ ->
            val layout = layoutInfo
            val lastIndex = layout.totalItemsCount - 1
            if (lastIndex >= 0 && canScrollForward) {
                if (distant) {
                    val position = startIndex + (lastIndex - startIndex) * fraction
                    val index = position.toInt().coerceIn(0, lastIndex)
                    val offset = ((position - index) * rowHeight + startOffset * (1f - fraction)).toInt()
                    // Estimated row heights may temporarily put us ahead of the interpolation.
                    // Wait for it to catch up instead of reversing and cancelling reader intent.
                    if (index > firstVisibleItemIndex ||
                        (index == firstVisibleItemIndex && offset >= firstVisibleItemScrollOffset))
                        lazyScope.snapToItem(index, offset)
                } else {
                    val last = layout.visibleItemsInfo.lastOrNull { it.index == lastIndex }
                    val remaining = if (last != null)
                        (last.offset + last.size + layout.afterContentPadding - layout.viewportEndOffset).toFloat()
                    else lazyScope.calculateDistanceTo(lastIndex).toFloat()
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
        scrollBy((last.offset + last.size + layoutInfo.afterContentPadding - layoutInfo.viewportEndOffset)
            .coerceAtLeast(0).toFloat())
    }
}
