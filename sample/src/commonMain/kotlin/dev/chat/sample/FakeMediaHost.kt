package dev.chat.sample

import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toLong
import dev.forntoh.chat.core.Attachment
import dev.forntoh.chat.core.ConversationEvent
import dev.forntoh.chat.core.ConversationState
import dev.forntoh.chat.core.Delivery
import dev.forntoh.chat.core.Draft
import dev.forntoh.chat.core.Message
import dev.forntoh.chat.core.Participant
import dev.forntoh.chat.media.ImageState
import dev.forntoh.chat.media.ImageStatus
import dev.forntoh.chat.media.MediaCommand
import dev.forntoh.chat.media.MediaRequest
import dev.forntoh.chat.media.MediaState
import dev.forntoh.chat.media.MediaTarget

/**
 * Shared deterministic fake media service. Bundled lookup only; no permission, storage, network,
 * authorization or persistence guarantee. Manual controls expose selection, acceptance and upload
 * as distinct decisions. All async completions validate their saved exact target before mutation.
 */
@Suppress("TooManyFunctions") // Diagnostic host keeps explicit selection/upload/viewer outcomes at one seam.
class FakeMediaHost {
    var state by mutableStateOf(ConversationState("media", "me"))
        private set
    var media by mutableStateOf(MediaState())
        private set
    var selection by mutableStateOf<MediaRequest?>(null)
        private set
    var submission by mutableStateOf<ConversationEvent.SendDraft?>(null)
        private set
    private var uploads by mutableStateOf<List<MediaTarget>>(emptyList())
    val upload: MediaTarget? get() = uploads.firstOrNull()
    var viewer by mutableStateOf<MediaTarget?>(null)
        private set
    private var serial = 1
    private val rooms = mutableMapOf<String, ConversationState>()

    fun cancelPendingWork() {
        state = state.copy(draft = state.draft.copy(revision = state.draft.revision + 1, submitting = false))
        media = media.copy(revision = media.revision + 1, selecting = false, selectionError = null)
        selection = null
        submission = null
        uploads = emptyList()
        viewer = null
    }

    fun clearConversation() {
        cancelPendingWork()
        state = state.copy(draft = Draft(revision = state.draft.revision), messages = emptyList())
        media = MediaState(revision = media.revision)
        rooms.clear()
    }

    fun receive(message: Message) {
        require(state.messages.none { it.id == message.id })
        state = state.copy(messages = state.messages + message)
    }

    fun resetSimulation() {
        val revision = state.draft.revision + 1
        state = ConversationState(
            "media",
            "me",
            draft = Draft(revision = revision),
            messages = listOf(
                ImageStatus.Ready,
                ImageStatus.Loading,
                ImageStatus.Missing,
                ImageStatus.Unsupported,
            ).map { status ->
                val attachment = Attachment(
                    "fixture-${serial++}",
                    if (status == ImageStatus.Unsupported) "application/pdf" else "image/vector",
                    if (status ==
                        ImageStatus.Missing
                    ) {
                        null
                    } else {
                        "bundled:mountain"
                    },
                    if (status == ImageStatus.Ready) null else "${status.name} fixture",
                )
                Message(
                    "media-fixture-${serial++}",
                    Participant("avery", if (status == ImageStatus.Unsupported) "" else "Avery"),
                    serial.toLong(),
                    if (status == ImageStatus.Missing) "" else "09:44",
                    "${status.name} fixture",
                    Delivery.Sent,
                    listOf(attachment),
                )
            },
        )
        media = MediaState(
            revision = media.revision + 1,
            images = state.messages.map { row ->
                MediaTarget(state.conversationId, row.attachments.first(), message = row) to ImageState(
                    when (row.text.substringBefore(" ")) {
                        "Loading" -> ImageStatus.Loading
                        "Missing" -> ImageStatus.Missing
                        "Unsupported" -> ImageStatus.Unsupported
                        else -> ImageStatus.Ready
                    },
                )
            }.toMap(),
        )
        selection = null
        submission = null
        uploads = emptyList()
        viewer = null
        rooms.clear()
    }

    fun advanceUpload() {
        val target = upload ?: return
        if (!current(target)) return
        val progress = media.images[target]?.progress ?: 0f
        if (progress >= 0.75f) {
            completeUpload(true)
        } else {
            media = media.copy(
                revision = media.revision + 1,
                images = media.images + (target to ImageState(ImageStatus.Uploading, progress + 0.25f)),
            )
        }
    }

    fun finishLoading() {
        media = media.copy(
            revision = media.revision + 1,
            images = media.images.mapValues { (_, image) ->
                if (image.status == ImageStatus.Loading) ImageState(ImageStatus.Ready) else image
            },
        )
    }

    fun current(target: MediaTarget): Boolean = target.conversationId == state.conversationId &&
        if (target.draft != null) {
            target.draft == state.draft &&
                (target.attachment == null || target.attachment in state.draft.attachments)
        } else {
            state.messages.find { it.id == target.message?.id } == target.message &&
                target.attachment in target.message!!.attachments
        }

    fun onRequest(request: MediaRequest) {
        if (request.revision != media.revision || !current(request.target)) return
        when (request.command) {
            MediaCommand.Select -> if (!state.draft.submitting && !media.selecting) {
                selection = request
                media = media.copy(selecting = true, selectionError = null)
            }

            MediaCommand.Remove -> if (!state.draft.submitting && !media.selecting) {
                state = state.copy(
                    draft = state.draft.copy(
                        revision = state.draft.revision + 1,
                        attachments = state.draft.attachments.filterNot { it.id == request.target.attachment?.id },
                    ),
                )
                media = media.copy(revision = media.revision + 1)
            }

            MediaCommand.Retry -> if (media.images[request.target]?.status == ImageStatus.Failed) {
                uploads = uploads + request.target
                updateUpload(request.target, ImageState(ImageStatus.Uploading, 0.25f))
            }

            MediaCommand.View -> if ((media.images[request.target]?.status ?: ImageStatus.Ready) ==
                ImageStatus.Ready
            ) {
                viewer = request.target
            }
        }
    }

    /** Null simulates cancellation; denial preserves the entire existing draft and reports feedback. */
    fun resolveSelection(choose: Boolean = false, denied: Boolean = false, request: MediaRequest? = selection) {
        if (request == null || selection != request || !current(request.target)) return
        if (choose) {
            state = state.copy(
                draft = state.draft.copy(
                    revision = state.draft.revision + 1,
                    attachments =
                    state.draft.attachments +
                        Attachment("mountain-${serial++}", "image/vector", "bundled:mountain", "Bundled mountain illustration"),
                ),
            )
        }
        if (!choose) state = state.copy(draft = state.draft.copy(revision = state.draft.revision + 1))
        selection = null
        media = media.copy(
            revision = media.revision + 1,
            selecting = false,
            selectionError = if (denied) "Permission denied by fake host" else null,
        )
    }

    fun onEvent(event: ConversationEvent) {
        if (event.conversationId != state.conversationId || media.selecting) return
        when (event) {
            is ConversationEvent.DraftChanged -> if (submission == null) state = state.copy(draft = state.draft.copy(text = event.text))

            is ConversationEvent.SendDraft -> {
                val available = submission == null && !state.draft.submitting && state.draft.hasContent
                val matches = event.revision == state.draft.revision && event.text == state.draft.text &&
                    event.attachments == state.draft.attachments
                if (available && matches) {
                    submission = event
                    state = state.copy(draft = state.draft.copy(submitting = true))
                }
            }

            is ConversationEvent.RetryMessage -> Unit // Upload retries use the exact attachment target instead.
        }
    }

    fun resolveDraft(accept: Boolean) {
        val pending = submission ?: return
        if (accept) {
            val row = Message(
                "image-${serial++}",
                Participant("me", "You"),
                serial.toLong(),
                "Now",
                pending.text,
                Delivery.Sending,
                pending.attachments,
            )
            state = state.copy(messages = state.messages + row, draft = Draft(revision = pending.revision + 1))
            val newUploads = row.attachments.map { MediaTarget(state.conversationId, it, message = row) }
            uploads = uploads + newUploads
            media = media.copy(
                revision = media.revision + 1,
                images = media.images + newUploads.associateWith { ImageState(ImageStatus.Uploading, 0.25f) },
            )
            if (newUploads.isEmpty()) {
                state =
                    state.copy(messages = state.messages.map { if (it.id == row.id) it.copy(delivery = Delivery.Sent) else it })
            }
        } else {
            state = state.copy(draft = state.draft.copy(submitting = false, revision = pending.revision + 1))
        }
        submission = null
    }

    fun completeUpload(success: Boolean, target: MediaTarget? = upload) {
        if (target == null || target !in uploads || !current(target)) return
        uploads = uploads - target
        updateUpload(target, ImageState(if (success) ImageStatus.Ready else ImageStatus.Failed))
    }

    // Rebind every sibling image and queued upload when aggregate delivery changes the row snapshot.
    // Individual success cannot mark a multi-image row Sent while another image is unresolved.
    private fun updateUpload(target: MediaTarget, image: ImageState) {
        val original = target.message!!
        val images = media.images + (target to image)
        val statuses = original.attachments.map {
            images[MediaTarget(state.conversationId, it, message = original)]?.status ?: ImageStatus.Ready
        }
        val delivery = when {
            statuses.any { it == ImageStatus.Uploading } -> Delivery.Sending
            statuses.any { it in setOf(ImageStatus.Failed, ImageStatus.Missing, ImageStatus.Unsupported) } -> Delivery.Failed
            else -> Delivery.Sent
        }
        val row = original.copy(delivery = delivery)
        fun rebind(saved: MediaTarget) = if (saved.conversationId == target.conversationId && saved.message == original) {
            saved.copy(message = row)
        } else {
            saved
        }
        state = state.copy(messages = state.messages.map { if (it == original) row else it })
        media = media.copy(
            revision = media.revision + 1,
            images = images.entries.associate { (saved, value) -> rebind(saved) to value },
        )
        uploads = uploads.map(::rebind)
        viewer = viewer?.let(::rebind)
    }

    private fun revocableTarget(): MediaTarget? = viewer ?: state.messages.firstOrNull { row ->
        row.attachments.any { !it.contentReference.isNullOrBlank() }
    }?.let { row ->
        MediaTarget(
            state.conversationId,
            row.attachments.first { !it.contentReference.isNullOrBlank() },
            message = row,
        )
    }

    /** Simulate host resource revocation; obsolete viewers/uploads cannot retain the old descriptor. */
    fun revokeResource(target: MediaTarget? = revocableTarget()) {
        if (target == null || target.message == null || !current(target)) return
        val original = target.message ?: return
        val row = original.copy(
            attachments = original.attachments.map { if (it == target.attachment) it.copy(contentReference = null) else it },
            delivery = if (original.delivery == Delivery.Sending) Delivery.Failed else original.delivery,
        )
        val previous = media.images
        val replacements = row.attachments.associate { attachment ->
            val old = original.attachments.first { it.id == attachment.id }
            val image = previous[MediaTarget(state.conversationId, old, message = original)] ?: ImageState()
            MediaTarget(state.conversationId, attachment, message = row) to when {
                attachment.id == target.attachment!!.id -> ImageState(ImageStatus.Missing)
                image.status == ImageStatus.Uploading -> ImageState(ImageStatus.Failed)
                else -> image
            }
        }
        state = state.copy(messages = state.messages.map { if (it == original) row else it })
        media = media.copy(
            revision = media.revision + 1,
            images = previous.filterKeys { it.message != original } + replacements,
        )
        uploads = uploads.filterNot { it.message == original }
        if (viewer?.message == original) viewer = null
    }

    fun closeViewer() {
        viewer = null
    }

    /**
     * Inject a renderer load error through public media state, without revoking the descriptor
     * or changing delivery. Debug injection starts and finishes in the current media revision.
     */
    fun failRendering() {
        val target = viewer ?: media.images.entries.firstOrNull {
            it.value.status == ImageStatus.Ready && current(it.key)
        }?.key ?: return
        renderFailureCallback(target)()
    }

    /**
     * Capture when renderer work starts, then invoke on failure. The saved revision rejects work
     * after media changes or navigation, including returning to identical Sent message snapshots.
     * Complete target validation also rejects replaced drafts and revoked attachment descriptors.
     */
    fun renderFailureCallback(target: MediaTarget): () -> Unit {
        val revision = media.revision
        return {
            if (revision == media.revision && current(target) &&
                (media.images[target]?.status ?: ImageStatus.Ready) == ImageStatus.Ready
            ) {
                media = media.copy(
                    revision = media.revision + 1,
                    images = media.images + (target to ImageState(ImageStatus.Missing)),
                )
                if (viewer == target) viewer = null
            }
        }
    }

    /** Retain draft/content per room while invalidating pending media callbacks, even after returning. */
    fun switchRoom() {
        val draft = state.draft.copy(revision = state.draft.revision + 1, submitting = false)
        rooms[state.conversationId] = state.copy(
            draft = draft,
            messages = state.messages.map {
                if (it.delivery == Delivery.Sending) it.copy(delivery = Delivery.Failed) else it
            },
        )
        val room = if (state.conversationId == "media") "other-media" else "media"
        state = rooms[room]?.let { it.copy(draft = it.draft.copy(revision = it.draft.revision + 1)) } ?: ConversationState(room, "me")
        media = MediaState(
            revision = media.revision + 1,
            images = state.messages.flatMap { row ->
                row.attachments.map {
                    MediaTarget(room, it, message = row) to
                        ImageState(if (row.delivery == Delivery.Sent) ImageStatus.Ready else ImageStatus.Failed)
                }
            }.toMap(),
        )
        selection = null
        submission = null
        uploads = emptyList()
        viewer = null
    }
}
