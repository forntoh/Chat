package dev.chat.actions

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import dev.chat.core.*
import dev.chat.ui.ComposerContent

/** Per-message capabilities enabled by the host; visibility does not enforce service authorization. */
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
 * [editing] retains the pre-edit composer and original target; keep [quoteTargetId] null while
 * editing. [deleting] binds confirmation to the selected snapshot; do not combine it with editing.
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

/**
 * Explicit user operations. Open/close and cancel affect retained host UI state; copy delegates
 * to a host clipboard adapter; sends, edits and confirmed deletes request host validation/work.
 */
enum class ActionCommand { OpenMenu, CloseMenu, Copy, Quote, CancelQuote, SendQuote, BeginEdit, CancelEdit, SubmitEdit, RequestDelete, CancelDelete, ConfirmDelete }

/**
 * Synchronous host intent, never an assertion of persistence or permission.
 * [conversationId] and [revision] identify the rendered action snapshot; reject stale values.
 * [targetId] identifies the message, including a possibly unavailable quote target on send.
 * [text] is exact rendered content for copy or send. [draftRevision] accompanies quoted sends;
 * validate it and [text] against the retained draft before accepting. Other commands omit them.
 * Edit submission also carries [text] and [draftRevision]; validate the original target and
 * current capability before dispatch and completion. Confirmed delete carries its target ID;
 * compare the current row with [ActionState.deleting] before dispatch and completion.
 * Copy requires an explicit host/platform clipboard adapter; the module accesses no resources.
 */
data class ActionRequest(
    val conversationId: String,
    val revision: Long,
    val command: ActionCommand,
    val targetId: String? = null,
    val text: String? = null,
    val draftRevision: Long? = null,
)

/**
 * English defaults for foundation surfaces; replace all visible labels and missing-target copy
 * here. Custom renderers own their additional labels and accessibility semantics. [saveEdit]
 * is supplied for host composers; [ActionComposerStatus] never renders a text input itself.
 */
data class ActionLabels(
    val actions: String = "Actions",
    val copy: String = "Copy",
    val reply: String = "Reply",
    val close: String = "Close actions",
    val cancelReply: String = "Cancel reply",
    val unavailable: String = "Message unavailable",
    val replying: String = "Replying to",
    val pending: String = "Request pending…",
    val edit: String = "Edit",
    val editing: String = "Editing message",
    val cancelEdit: String = "Cancel edit",
    val saveEdit: String = "Save edit",
    val delete: String = "Delete",
    val confirmDelete: String = "Confirm delete",
    val cancelDelete: String = "Cancel delete",
    val deletePrompt: String = "Delete this message?",
    val actionUnavailable: String = "This action is no longer available. Cancel and select the message again.",

)

/**
 * Action snapshot and guarded callbacks for host slots, created by [rememberMessageActions].
 * Render [conversation] and [state] directly, without copying draft text into local state.
 * Call only from explicit activation; replaced snapshots and pending work are suppressed.
 * The host retains modes and resolves every request with a fresh action revision.
 */
class ActionsContent internal constructor(
    val conversation: ConversationState,
    val state: ActionState,
    private val dispatch: (ActionRequest) -> Unit,
    private val conversationEvent: (ConversationEvent) -> Unit,
) {
    /** False when no edit is active, its target changed/disappeared or capability was revoked. */
    val canEdit: Boolean get() = state.editing?.let { edit ->
        conversation.messages.find { it.id == edit.original.id } == edit.original &&
            MessageAction.Edit in state.capabilities[edit.original.id].orEmpty()
    } ?: false

    /**
     * Apply action pending/stale-edit locks to the existing UI composer slot. Pass the returned
     * value to the renderer and use [ActionLabels.saveEdit] when editing. This preserves the
     * UI's synchronous send latch. Cancel stays available through [ActionComposerStatus].
     */
    fun composer(content: ComposerContent): ComposerContent = ComposerContent(content.draft,
        content.pending || state.pending || (state.editing != null && !canEdit), content.onTextChange, content.onSend)

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
                state.quoteTargetId, event.text, event.revision))
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
    val latestConversation by rememberUpdatedState(conversation)
    val latestState by rememberUpdatedState(state)
    val latestAction by rememberUpdatedState(onAction)
    val latestEvent by rememberUpdatedState(onEvent)
    var emitted by remember(conversation.conversationId, state.revision) { mutableStateOf(false) }
    return ActionsContent(
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
                (now.draft.submitting || request.text != now.draft.text || request.text.isNullOrBlank() ||
                        request.draftRevision != now.draft.revision || request.targetId != actions.quoteTargetId)
            ) return@dispatch
            emitted = true
            latestAction(request)
        },
        conversationEvent = { event ->
            if (latestConversation.conversationId == conversation.conversationId &&
                latestConversation.draft == conversation.draft && latestState == state && !latestState.pending
            ) latestEvent(event)
        }
    )
}

/** Host-capability menu using the surrounding [ActionTheme]; existing callers keep defaults. */
@Composable
fun MessageActions(content: ActionsContent, messageId: String, labels: ActionLabels = ActionLabels()) {
    MessageActions(content, messageId, labels, LocalActionSlots.current)
}

/**
 * Menu for a current host message. Explicit [slots] override the ambient theme for this subtree.
 * Only enabled capabilities appear; pending work/edit/delete lock new menus. Controls receive
 * guarded callbacks; containers must render their children and custom controls own semantics.
 */
@Composable
fun MessageActions(content: ActionsContent, messageId: String, labels: ActionLabels = ActionLabels(), slots: ActionSlots) {
    val enabled = content.state.capabilities[messageId].orEmpty()
    if (content.conversation.messages.none { it.id == messageId } || enabled.isEmpty()) return
    ActionTheme(slots = slots) {
        slots.menu {
            slots.control(ActionControlContent(labels.actions, !content.state.pending && content.state.editing == null && content.state.deleting == null) { content.request(ActionCommand.OpenMenu, messageId) })
            if (content.state.menuTargetId == messageId) {
                FlowRow {
                    if (MessageAction.Copy in enabled) slots.control(ActionControlContent(labels.copy, !content.state.pending) { content.request(ActionCommand.Copy, messageId) })
                    if (MessageAction.Quote in enabled) slots.control(ActionControlContent(labels.reply, !content.state.pending) { content.request(ActionCommand.Quote, messageId) })
                    if (MessageAction.Edit in enabled) slots.control(ActionControlContent(labels.edit, !content.state.pending) { content.request(ActionCommand.BeginEdit, messageId) })
                    if (MessageAction.Delete in enabled) slots.control(ActionControlContent(labels.delete, !content.state.pending) { content.request(ActionCommand.RequestDelete, messageId) })
                    slots.control(ActionControlContent(labels.close, !content.state.pending) { content.request(ActionCommand.CloseMenu) })
                }
            }
        }
    }
}

/** Latest host quote excerpt and replaceable missing-target fallback, using [ActionTheme]. */
@Composable
fun QuotedMessage(content: ActionsContent, targetId: String, labels: ActionLabels = ActionLabels()) {
    QuotedMessage(content, targetId, labels, LocalActionSlots.current)
}

/** Resolve [targetId] on each snapshot; [slots] overrides the quote renderer and nested theme.
 * A missing message retains its identity and passes [ActionLabels.unavailable] as visible copy. */
@Composable
fun QuotedMessage(content: ActionsContent, targetId: String, labels: ActionLabels = ActionLabels(), slots: ActionSlots) {
    val target = content.conversation.messages.find { it.id == targetId }
    ActionTheme(slots = slots) {
        slots.quote(ActionQuoteContent(targetId, target, target?.let { "${it.sender.displayName}: ${it.text}" } ?: labels.unavailable))
    }
}

/** Retained edit/quote context and operation feedback; place above any composer. Uses [ActionTheme]. */
@Composable
fun ActionComposerStatus(content: ActionsContent, labels: ActionLabels = ActionLabels()) {
    ActionComposerStatus(content, labels, LocalActionSlots.current)
}

/**
 * [slots] overrides all nested edit/quote/confirmation and status presentation. Pending/errors
 * reflect host action state, independently of delivery. Cancellation preserves the host draft
 * through an intent; the host restores retained pre-edit content. No input is rendered here.
 */
@Composable
fun ActionComposerStatus(content: ActionsContent, labels: ActionLabels = ActionLabels(), slots: ActionSlots) {
    ActionTheme(slots = slots) {
        slots.composerStatus {
            content.state.editing?.let {
                slots.text(labels.editing)
                if (!content.canEdit) slots.text(labels.actionUnavailable)
                slots.control(ActionControlContent(labels.cancelEdit, !content.state.pending) { content.request(ActionCommand.CancelEdit) })
            }
            content.state.quoteTargetId?.let {
                slots.text(labels.replying)
                QuotedMessage(content, it, labels)
                slots.control(ActionControlContent(labels.cancelReply, !content.state.pending && !content.conversation.draft.submitting) { content.request(ActionCommand.CancelQuote) })
            }
            DeleteConfirmation(content, labels)
            if (content.state.pending) slots.text(labels.pending)
            content.state.error?.let { slots.text(it) }
        }
    }
}

/** Target-bound confirmation using [ActionTheme], disabled when the target/capability changes. */
@Composable
fun DeleteConfirmation(content: ActionsContent, labels: ActionLabels = ActionLabels()) {
    DeleteConfirmation(content, labels, LocalActionSlots.current)
}

/**
 * Explicit confirmation with [slots] overriding the container, quote, text and controls. The
 * selected message snapshot is revalidated on activation; Cancel stays available while idle.
 * Custom controls must honor disabled state. The host revalidates again before service work;
 * rendering or confirming never asserts persisted success.
 */
@Composable
fun DeleteConfirmation(content: ActionsContent, labels: ActionLabels = ActionLabels(), slots: ActionSlots) {
    val target = content.state.deleting ?: return
    val current = content.conversation.messages.find { it.id == target.id }
    val canDelete = current == target && MessageAction.Delete in content.state.capabilities[target.id].orEmpty()
    ActionTheme(slots = slots) {
        slots.confirmation {
            slots.text(labels.deletePrompt)
            QuotedMessage(content, target.id, labels)
            Row {
                slots.control(ActionControlContent(labels.confirmDelete, canDelete && !content.state.pending) { content.request(ActionCommand.ConfirmDelete, target.id) })
                slots.control(ActionControlContent(labels.cancelDelete, !content.state.pending) { content.request(ActionCommand.CancelDelete) })
            }
        }
    }
}
