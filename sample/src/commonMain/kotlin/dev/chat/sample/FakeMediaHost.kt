package dev.chat.sample

import androidx.compose.runtime.*
import com.forntoh.chat.core.*
import com.forntoh.chat.media.*

/**
 * Shared deterministic fake media service. Bundled lookup only; no permission, storage, network,
 * authorization or persistence guarantee. Manual controls expose selection, acceptance and upload
 * as distinct decisions. All async completions validate their saved exact target before mutation.
 */
class FakeMediaHost {
    var state by mutableStateOf(ConversationState("media", "me")); private set
    var media by mutableStateOf(MediaState()); private set
    var selection by mutableStateOf<MediaRequest?>(null); private set
    var submission by mutableStateOf<ConversationEvent.SendDraft?>(null); private set
    private var uploads by mutableStateOf<List<MediaTarget>>(emptyList())
    val upload: MediaTarget? get() = uploads.firstOrNull()
    var viewer by mutableStateOf<MediaTarget?>(null); private set
    private var serial = 1
    private val rooms = mutableMapOf<String, ConversationState>()

    fun current(target: MediaTarget): Boolean = target.conversationId == state.conversationId &&
        if (target.draft != null) target.draft == state.draft &&
            (target.attachment == null || target.attachment in state.draft.attachments)
        else state.messages.find { it.id == target.message?.id } == target.message &&
            target.attachment in target.message!!.attachments

    fun onRequest(request: MediaRequest) {
        if (request.revision != media.revision || !current(request.target)) return
        when (request.command) {
            MediaCommand.Select -> if (!state.draft.submitting && !media.selecting) {
                selection = request
                media = media.copy(selecting = true, selectionError = null)
            }
            MediaCommand.Remove -> if (!state.draft.submitting && !media.selecting) {
                state = state.copy(draft = state.draft.copy(revision = state.draft.revision + 1,
                    attachments = state.draft.attachments.filterNot { it.id == request.target.attachment?.id }))
                media = media.copy(revision = media.revision + 1)
            }
            MediaCommand.Retry -> if (media.images[request.target]?.status == ImageStatus.Failed) {
                val row = request.target.message!!.copy(delivery = Delivery.Sending)
                state = state.copy(messages = state.messages.map { if (it.id == row.id) row else it })
                val retry = request.target.copy(message = row)
                uploads = uploads + retry
                media = media.copy(revision = media.revision + 1, images = media.images - request.target + (retry to ImageState(ImageStatus.Uploading, .25f)))
            }
            MediaCommand.View -> if ((media.images[request.target]?.status ?: ImageStatus.Ready) == ImageStatus.Ready) viewer = request.target
        }
    }

    /** Null simulates cancellation; denial preserves the entire existing draft and reports feedback. */
    fun resolveSelection(choose: Boolean = false, denied: Boolean = false, request: MediaRequest? = selection) {
        if (request == null || selection != request || !current(request.target)) return
        if (choose) state = state.copy(draft = state.draft.copy(revision = state.draft.revision + 1,
            attachments = listOf(Attachment("mountain-${serial++}", "image/vector", "bundled:mountain", "Bundled mountain illustration"))))
        if (!choose) state = state.copy(draft = state.draft.copy(revision = state.draft.revision + 1))
        selection = null
        media = media.copy(revision = media.revision + 1, selecting = false,
            selectionError = if (denied) "Permission denied by fake host" else null)
    }

    fun onEvent(event: ConversationEvent) {
        if (event.conversationId != state.conversationId || media.selecting) return
        when (event) {
            is ConversationEvent.DraftChanged -> if (submission == null) state = state.copy(draft = state.draft.copy(text = event.text))
            is ConversationEvent.SendDraft -> if (submission == null && !state.draft.submitting && state.draft.hasContent &&
                event.revision == state.draft.revision && event.text == state.draft.text && event.attachments == state.draft.attachments) {
                submission = event
                state = state.copy(draft = state.draft.copy(submitting = true))
            }
            is ConversationEvent.RetryMessage -> Unit // Upload retries use the exact attachment target instead.
        }
    }

    fun resolveDraft(accept: Boolean) {
        val pending = submission ?: return
        if (accept) {
            val row = Message("image-${serial++}", Participant("me", "You"), serial.toLong(), "Now", pending.text,
                Delivery.Sending, pending.attachments)
            state = state.copy(messages = state.messages + row, draft = Draft(revision = pending.revision + 1))
            val newUpload = row.attachments.firstOrNull()?.let { MediaTarget(state.conversationId, it, message = row) }
            if (newUpload != null) uploads = uploads + newUpload
            media = media.copy(revision = media.revision + 1,
                images = newUpload?.let { media.images + (it to ImageState(ImageStatus.Uploading, .25f)) } ?: media.images)
            if (newUpload == null) state = state.copy(messages = state.messages.map { if (it.id == row.id) it.copy(delivery = Delivery.Sent) else it })
        } else state = state.copy(draft = state.draft.copy(submitting = false, revision = pending.revision + 1))
        submission = null
    }

    fun completeUpload(success: Boolean, target: MediaTarget? = upload) {
        if (target == null || target !in uploads || !current(target)) return
        val row = target.message!!.copy(delivery = if (success) Delivery.Sent else Delivery.Failed)
        state = state.copy(messages = state.messages.map { if (it.id == row.id) row else it })
        media = media.copy(revision = media.revision + 1, images = media.images - target +
            (target.copy(message = row) to ImageState(if (success) ImageStatus.Ready else ImageStatus.Failed)))
        uploads = uploads - target
    }

    fun closeViewer() { viewer = null }

    /** Retain draft/content per room while invalidating pending media callbacks, even after returning. */
    fun switchRoom() {
        val draft = state.draft.copy(revision = state.draft.revision + 1, submitting = false)
        rooms[state.conversationId] = state.copy(draft = draft, messages = state.messages.map {
            if (it.delivery == Delivery.Sending) it.copy(delivery = Delivery.Failed) else it
        })
        val room = if (state.conversationId == "media") "other-media" else "media"
        state = rooms[room]?.let { it.copy(draft = it.draft.copy(revision = it.draft.revision + 1)) } ?: ConversationState(room, "me")
        media = MediaState(revision = media.revision + 1,
            images = state.messages.flatMap { row -> row.attachments.map { MediaTarget(room, it, message = row) to
                ImageState(if (row.delivery == Delivery.Sent) ImageStatus.Ready else ImageStatus.Failed) } }.toMap())
        selection = null; submission = null; uploads = emptyList(); viewer = null
    }
}
