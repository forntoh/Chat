package dev.forntoh.chat.actions

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import dev.forntoh.chat.core.*
import dev.forntoh.chat.ui.ChatMotionVisibility
import dev.forntoh.chat.ui.ComposerContent

enum class MessageAction { Copy, Quote, Edit, Delete }

/**
 * Host-retained editing session. [original] is the authoritative target at selection time;
 * reject submission if it changes. [retainedDraft] and [retainedQuoteTargetId] restore the
 * pre-edit composer on cancellation or acceptance. Editing text lives in the normal host draft.
 * Restoring the draft requires a fresh submission revision, not reuse of a resolved version.
 */
data class EditingMessage(val original: Message, val retainedDraft: Draft, val retainedQuoteTargetId: String? = null)

/**
 * Retained, immutable host action snapshot for one conversation; all fields default to inactive.
 * Increment [revision] on every transition, including refusals, so old callbacks cannot act on
 * a newer menu or draft. [capabilities] keys are stable message IDs in that conversation.
 * [quoteTargetId] refers to the current draft's quote; [quotes] maps sent row IDs to targets.
 * Keep missing targets in these maps to render an unavailable fallback rather than lose context.
 * [pending] locks actions during host work; [error] is operation feedback, separate from delivery.
 * The host retains this alongside its conversation and draft across unmount/navigation.
 */
data class ActionState(
    val revision: Long = 0,
    val capabilities: Map<String, Set<MessageAction>> = emptyMap(),
    val menuTargetId: String? = null,
    val quoteTargetId: String? = null,
    val quotes: Map<String, String> = emptyMap(),
    val editing: EditingMessage? = null,
    val deleting: Message? = null,
    val pending: Boolean = false,
    val error: String? = null,
)

enum class ActionCommand { OpenMenu, CloseMenu, Copy, Quote, CancelQuote, SendQuote, BeginEdit, CancelEdit, SubmitEdit, RequestDelete, CancelDelete, ConfirmDelete }

/**
 * Synchronous host intent, never an assertion of persistence or permission.
 * [conversationId] and [revision] identify the rendered action snapshot; reject stale values.
 * [targetId] identifies the message, including a possibly unavailable quote target on send.
 * [text] is exact rendered content for copy or send. [draftRevision] accompanies quoted sends;
 * validate it and [text] against the retained draft before accepting. Other commands omit them.
 * [attachments] carries the exact shared draft snapshot on quoted sends (empty for text-only callers);
 * validate it with text/revision rather than discarding images while translating a send.
 * Copy requires an explicit host/platform clipboard adapter; the module accesses no resources.
 */
data class ActionRequest(
    val conversationId: String,
    val revision: Long,
    val command: ActionCommand,
    val targetId: String? = null,
    val text: String? = null,
    val draftRevision: Long? = null,
    val attachments: List<Attachment> = emptyList(),
)

class ActionsContent internal constructor(
    val conversation: ConversationState,
    val state: ActionState,
    private val dispatch: (ActionRequest) -> Unit,
    private val conversationEvent: (ConversationEvent) -> Unit,
    internal val composerFocus: Long,
) {
    val canEdit: Boolean get() = state.editing?.let { edit ->
        conversation.messages.find { it.id == edit.original.id } == edit.original &&
            MessageAction.Edit in state.capabilities[edit.original.id].orEmpty()
    } ?: false

    /**
     * Apply action pending/stale-edit locks to the existing UI composer slot. Pass the returned
     * value to the renderer and render an edit-submit control while editing. This preserves the
     * UI's synchronous send latch. Cancel stays available through [ActionComposerStatus].
     */
    fun composer(content: ComposerContent): ComposerContent = ComposerContent(content.draft,
        content.pending || state.pending || (state.editing != null && !canEdit), content.onTextChange, content.onSend, content.onDraftAction, composerFocus)

    /** Emit only explicit user activation; targets/capabilities and current context are revalidated. */
    fun request(command: ActionCommand, targetId: String? = null) {
        val text = if (command == ActionCommand.Copy) conversation.messages.find { it.id == targetId }?.text else null
        dispatch(ActionRequest(conversation.conversationId, state.revision, command, targetId, text))
    }

    /**
     * Forward guarded chat-ui events here. Sends while editing emit [ActionCommand.SubmitEdit];
     * quoted sends emit [ActionCommand.SendQuote]; otherwise [ConversationEvent] is forwarded.
     * Hosts resolve acceptance or refusal with fresh action and draft revisions. Pending and
     * replaced snapshots are rejected; no callback implies delivery or persisted modification.
     */
    fun onConversationEvent(event: ConversationEvent) {
        if (event is ConversationEvent.SendDraft && state.editing != null) {
            dispatch(ActionRequest(event.conversationId, state.revision, ActionCommand.SubmitEdit,
                state.editing.original.id, event.text, event.revision))
        } else if (event is ConversationEvent.SendDraft && state.quoteTargetId != null) {
            dispatch(ActionRequest(event.conversationId, state.revision, ActionCommand.SendQuote,
                state.quoteTargetId, event.text, event.revision, event.attachments))
        } else conversationEvent(event)
    }
}

/**
 * Adapts optional actions to existing chat-ui slots without importing actions into core/ui.
 * Pass [ActionsContent.onConversationEvent] to Conversation, [MessageActions] into its message
 * slot and [ActionComposerStatus] above its composer. [onAction] and [onEvent] return promptly;
 * exceptions propagate, and the host must publish a new revision to resolve a refused intent.
 *
 * [conversation] and [state] must belong to the same retained host context. Callbacks compare
 * the current conversation, action revision and draft with the rendered snapshot, and resolve
 * target/capability again on activation. Pending operations block further action requests.
 * No callbacks run during composition. Copy is emitted to [onAction], never performed implicitly.
 *
 * @param conversation Latest immutable host message/draft snapshot; never own a second draft here.
 * @param state Retained actions for this conversation; advance revision on acceptance/refusal.
 * @param onAction Explicit action handler; validate identities, capability, revision and payload.
 * @param onEvent Existing text/retry handler, excluding quoted sends and edit submissions.
 */
@Composable
fun rememberMessageActions(
    conversation: ConversationState,
    state: ActionState,
    onAction: (ActionRequest) -> Unit,
    onEvent: (ConversationEvent) -> Unit,
): ActionsContent {
    var composerFocus by remember(conversation.conversationId) { mutableStateOf(0L) }
    val latestConversation by rememberUpdatedState(conversation)
    val latestState by rememberUpdatedState(state)
    val latestAction by rememberUpdatedState(onAction)
    val latestEvent by rememberUpdatedState(onEvent)
    var emitted by remember(conversation.conversationId, state.revision) { mutableStateOf(false) }
    return ActionsContent(
        composerFocus = composerFocus,
        conversation = conversation,
        state = state,
        dispatch = dispatch@{ request ->
            val now = latestConversation
            val actions = latestState
            if (emitted || actions.pending || now.conversationId != conversation.conversationId ||
                actions != state || now.draft != conversation.draft
            ) return@dispatch
            val target = now.messages.find { it.id == request.targetId }
            val capability = when (request.command) {
                ActionCommand.Copy -> MessageAction.Copy
                ActionCommand.Quote -> MessageAction.Quote
                ActionCommand.BeginEdit, ActionCommand.SubmitEdit -> MessageAction.Edit
                ActionCommand.RequestDelete, ActionCommand.ConfirmDelete -> MessageAction.Delete
                else -> null
            }
            if (capability != null && (target == null || capability !in actions.capabilities[target.id].orEmpty() ||
                        target != conversation.messages.find { it.id == target.id })
            ) return@dispatch
            if (request.command in setOf(
                    ActionCommand.BeginEdit,
                    ActionCommand.Quote,
                    ActionCommand.RequestDelete,
                    ActionCommand.OpenMenu
                ) &&
                (actions.editing != null || actions.deleting != null || now.draft.submitting)
            ) return@dispatch
            if (request.command == ActionCommand.ConfirmDelete && target != actions.deleting) return@dispatch
            if (request.command == ActionCommand.OpenMenu &&
                (target == null || actions.capabilities[target.id].isNullOrEmpty())
            ) return@dispatch
            if (request.command == ActionCommand.SubmitEdit &&
                (target != actions.editing?.original || now.draft.submitting ||
                        request.draftRevision != now.draft.revision || request.text != now.draft.text || request.text.isNullOrBlank())
            ) return@dispatch
            if (request.command == ActionCommand.SendQuote &&
                (now.draft.submitting || request.text != now.draft.text || !now.draft.hasContent || request.attachments != now.draft.attachments ||
                        request.draftRevision != now.draft.revision || request.targetId != actions.quoteTargetId)
            ) return@dispatch
            emitted = true
            if (request.command in setOf(ActionCommand.Quote, ActionCommand.BeginEdit,
                    ActionCommand.CancelEdit, ActionCommand.CancelQuote)) composerFocus++
            latestAction(request)
        },
        conversationEvent = { event ->
            if (latestConversation.conversationId == conversation.conversationId &&
                latestConversation.draft == conversation.draft && latestState == state && !latestState.pending
            ) latestEvent(event)
        }
    )
}

/**
 * Capability-filtered message menu. [slots] replaces the ambient theme for this subtree; derive
 * LocalActionSlots.current.copy(...) for a partial override. Controls receive typed commands and
 * guarded activation, never a label bundle. Pending/edit/delete state locks new menus. Custom
 * containers render their children once; custom controls own labels and input semantics.
 */
@Composable
fun MessageActions(content: ActionsContent, messageId: String, slots: ActionSlots = LocalActionSlots.current) {
    val enabled = content.state.capabilities[messageId].orEmpty()
    if (content.conversation.messages.none { it.id == messageId } || enabled.isEmpty()) return
    ActionTheme(slots = slots) {
        slots.menu {
            slots.control(ActionControlContent(ActionCommand.OpenMenu, !content.state.pending && content.state.editing == null && content.state.deleting == null) { content.request(ActionCommand.OpenMenu, messageId) })
            if (content.state.menuTargetId == messageId) {
                FlowRow {
                    if (MessageAction.Copy in enabled) slots.control(ActionControlContent(ActionCommand.Copy, !content.state.pending) { content.request(ActionCommand.Copy, messageId) })
                    if (MessageAction.Quote in enabled) slots.control(ActionControlContent(ActionCommand.Quote, !content.state.pending) { content.request(ActionCommand.Quote, messageId) })
                    if (MessageAction.Edit in enabled) slots.control(ActionControlContent(ActionCommand.BeginEdit, !content.state.pending) { content.request(ActionCommand.BeginEdit, messageId) })
                    if (MessageAction.Delete in enabled) slots.control(ActionControlContent(ActionCommand.RequestDelete, !content.state.pending) { content.request(ActionCommand.RequestDelete, messageId) })
                    slots.control(ActionControlContent(ActionCommand.CloseMenu, !content.state.pending) { content.request(ActionCommand.CloseMenu) })
                }
            }
        }
    }
}

/** Latest quote snapshot for [targetId], including a nullable message for unavailable content.
 * [slots] overrides the ambient quote renderer; hosts author fallback wording in that slot. */
@Composable
fun QuotedMessage(content: ActionsContent, targetId: String, slots: ActionSlots = LocalActionSlots.current) {
    ActionTheme(slots = slots) {
        val target = content.conversation.messages.find { it.id == targetId }
        val presentation = dev.forntoh.chat.ui.LocalMessagePresentation.current
        slots.quote(ActionQuoteContent(targetId, target,
            if (target != null) ({ presentation.onNavigateToMessage(targetId) }) else null))
    }
}

/**
 * Retained edit/quote context and operation feedback for a host composer header. Dismiss controls
 * sit beside context; pending/error/delete feedback remains below it. [slots] replaces the
 * ambient bundle, including nested quote/confirmation content. Pending/errors describe actions,
 * separately from message delivery. The host restores the retained pre-edit draft on cancellation;
 * this component only emits guarded intents. Selecting/cancelling reply or edit requests focus
 * through the adapted composer. It renders no input and retains no second draft.
 */
@Composable
fun ActionComposerStatus(content: ActionsContent, slots: ActionSlots = LocalActionSlots.current) {
    key(content.conversation.conversationId) {
        ActionTheme(slots = slots) {
            slots.composerStatus {
                val quoteId = content.state.quoteTargetId
                val presentation = dev.forntoh.chat.ui.LocalMessagePresentation.current
                val quote = quoteId?.let { id ->
                    val message = content.conversation.messages.find { it.id == id }
                    ActionQuoteContent(id, message, if (message != null) ({ presentation.onNavigateToMessage(id) }) else null)
                }
                val preview = if (content.state.editing != null || quote != null) ComposerPreview(
                    editing = content.state.editing != null,
                    unavailable = content.state.editing != null && !content.canEdit,
                    quote = quote,
                    cancelEdit = if (content.state.editing != null) ActionControlContent(ActionCommand.CancelEdit, !content.state.pending) { content.request(ActionCommand.CancelEdit) } else null,
                    cancelQuote = if (quote != null) ActionControlContent(ActionCommand.CancelQuote, !content.state.pending && !content.conversation.draft.submitting) { content.request(ActionCommand.CancelQuote) } else null,
                ) else null
                ChatMotionVisibility(preview) { shown ->
                    Row(Modifier.fillMaxWidth().padding(LocalActionStyle.current.composerPadding), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            if (shown.editing) slots.status(ActionStatusContent(ActionStatusKind.Editing))
                            if (shown.unavailable) slots.status(ActionStatusContent(ActionStatusKind.Unavailable))
                            shown.quote?.let { slots.quote(it) }
                        }
                        shown.cancelEdit?.let { slots.control(it) }
                        shown.cancelQuote?.let { slots.control(it) }
                    }
                }
                DeleteConfirmation(content)
                ChatMotionVisibility(content.state.pending) { slots.status(ActionStatusContent(ActionStatusKind.Pending)) }
                content.state.error?.let { slots.status(ActionStatusContent(ActionStatusKind.Error, it)) }
            }
        }
    }
}

// Only presentation and already-guarded controls survive the finite exit, never another draft.
private data class ComposerPreview(
    val editing: Boolean,
    val unavailable: Boolean,
    val quote: ActionQuoteContent?,
    val cancelEdit: ActionControlContent?,
    val cancelQuote: ActionControlContent?,
)

/**
 * Explicit target-bound confirm/cancel, using typed controls and prompt/quote slots. [slots]
 * replaces ambient presentation. A changed/removed target or revoked capability disables confirm;
 * Cancel remains available while idle. The host revalidates on completion. Custom controls must
 * honor enabled state; activation remains guarded and never asserts persisted success.
 */
@Composable
fun DeleteConfirmation(content: ActionsContent, slots: ActionSlots = LocalActionSlots.current) {
    val target = content.state.deleting ?: return
    val current = content.conversation.messages.find { it.id == target.id }
    val canDelete = current == target && MessageAction.Delete in content.state.capabilities[target.id].orEmpty()
    ActionTheme(slots = slots) {
        slots.confirmation {
            slots.status(ActionStatusContent(ActionStatusKind.DeletePrompt))
            QuotedMessage(content, target.id)
            Row {
                slots.control(ActionControlContent(ActionCommand.ConfirmDelete, canDelete && !content.state.pending) { content.request(ActionCommand.ConfirmDelete, target.id) })
                slots.control(ActionControlContent(ActionCommand.CancelDelete, !content.state.pending) { content.request(ActionCommand.CancelDelete) })
            }
        }
    }
}
