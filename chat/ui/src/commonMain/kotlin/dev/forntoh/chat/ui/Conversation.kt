package dev.forntoh.chat.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
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
    /** Retains the original text-composer constructor, including trailing-lambda callers. */
    constructor(draft: Draft, pending: Boolean, onTextChange: (String) -> Unit, onSend: () -> Unit) :
        this(draft, pending, onTextChange, onSend, { action -> if (!pending) action() })

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
 *
 * A send latches synchronously before invoking [onEvent]. Changing conversation or draft
 * revision resets that latch; unmounting also discards it. Retain drafts and pending state in
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
 * retaining its scroll offset. Appended rows
 * follow the bottom only if the reader was already there. Switching conversation resets
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
 * @param historyContent Optional status/retry override above the timeline; null uses the effective theme.
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
    Conversation(state, onEvent, LocalConversationSlots.current, modifier, messageContent,
        composerContent, history, onLoadEarlier, historyContent, latestContent)
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
 * @param historyContent Optional status/retry override above the timeline; null uses effective slots.
 * @param latestContent Optional latest-control override; null uses effective slots.
 */
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
    val pending = state.draft.submitting || submitted
    ConversationTheme(slots) {
        Column(modifier.fillMaxSize().imePadding()) {
            key(state.conversationId) {
                val list = rememberLazyListState()
                val scope = rememberCoroutineScope()
                var requested by remember(history.revision, history.status) { mutableStateOf(false) }
                val currentHistory by rememberUpdatedState(history)
                val currentRequest by rememberUpdatedState(onLoadEarlier)
                val loadEarlier = {
                    if (!requested && currentHistory.status in listOf(HistoryStatus.Idle, HistoryStatus.Error)) {
                        requested = true
                        currentRequest(HistoryRequest(state.conversationId, currentHistory.revision))
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
                val ids = state.messages.map { it.id }
                LaunchedEffect(ids) {
                    val oldLayout = list.layoutInfo
                    val wasAtBottom = previousIds.isEmpty() || oldLayout.visibleItemsInfo.lastOrNull()?.let {
                        it.key == previousIds.lastOrNull() && it.offset + it.size <= oldLayout.viewportEndOffset
                    } == true
                    val anchor = oldLayout.visibleItemsInfo.firstOrNull { it.index == list.firstVisibleItemIndex }
                    val anchorOffset = list.firstVisibleItemScrollOffset
                    val appended = previousIds.lastOrNull()?.let { oldLast ->
                        val index = ids.indexOf(oldLast)
                        index >= 0 && index < ids.lastIndex
                    } == true
                    if (ids.isNotEmpty()) {
                        if (wasAtBottom && (appended || previousIds.isEmpty())) {
                            list.scrollToItem(ids.lastIndex)
                        } else if (anchor != null && previousIds.isNotEmpty()) {
                            val surviving = ids.indexOf(anchor.key).takeIf { it >= 0 }
                                ?: (1..previousIds.size).firstNotNullOfOrNull { distance ->
                                    val later = previousIds.getOrNull(anchor.index + distance)?.let(ids::indexOf)
                                    val earlier = previousIds.getOrNull(anchor.index - distance)?.let(ids::indexOf)
                                    later?.takeIf { it >= 0 } ?: earlier?.takeIf { it >= 0 }
                                }
                            if (surviving != null) list.scrollToItem(surviving, anchorOffset)
                        }
                    }
                    if (appended && !wasAtBottom) newMessages = true
                    previousIds = ids
                }
                LaunchedEffect(list) {
                    snapshotFlow { !list.canScrollForward }.collect { if (it) newMessages = false }
                }
                (historyContent ?: { slots.history(HistoryFeedbackContent(it, state.messages.isEmpty())) })(
                    HistoryContent(
                        state = history,
                        canLoadEarlier = !requested && history.status in listOf(HistoryStatus.Idle, HistoryStatus.Error),
                        onLoadEarlier = loadEarlier,
                    ),
                )
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth().testTag("timeline"),
                    state = list,
                    contentPadding = PaddingValues(12.dp),
                ) {
                    itemsIndexed(state.messages, key = { _, row -> row.id }) { index, message ->
                        val isCurrent = message.sender.id == state.currentParticipantId
                        val group = MessageGroupContext(
                            isFirst = state.messages.getOrNull(index - 1)?.sender?.id != message.sender.id,
                            isLast = state.messages.getOrNull(index + 1)?.sender?.id != message.sender.id,
                        )
                        Column(
                            Modifier.fillMaxWidth().padding(top = if (index == 0) 0.dp else slots.messageSpacing(group))
                                .testTag("message-${message.id}"),
                            horizontalAlignment = if (isCurrent) Alignment.End else Alignment.Start
                        ) {
                            CompositionLocalProvider(LocalMessageGroupContext provides group) {
                                (messageContent ?: slots.message)(
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
                if (awayFromLatest) {
                    (latestContent ?: slots.latest)(
                        LatestContent(newMessages) {
                            scope.launch {
                                if (ids.isNotEmpty()) list.scrollToItem(ids.lastIndex); newMessages =
                                false
                            }
                        },
                    )
                }
            }
            (composerContent ?: slots.composer)(ComposerContent(state.draft, pending,
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
            ))
        }
    }
}
