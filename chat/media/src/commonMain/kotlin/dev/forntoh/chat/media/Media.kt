package dev.forntoh.chat.media

import androidx.compose.runtime.*
import dev.forntoh.chat.core.*
import dev.forntoh.chat.ui.ComposerContent

/**
 * Exact rendered resource target. Exactly one of [draft] or [message] must be supplied.
 * Hosts must compare the complete target before applying selection/upload/render results,
 * including after navigation away and back. Never re-use resolved draft revisions or resource
 * references for different content. Message replacement invalidates the entire old target.
 * [attachment] is null only for a selection request. No target conveys authorization.
 */
data class MediaTarget(
    val conversationId: String,
    val attachment: Attachment? = null,
    val draft: Draft? = null,
    val message: Message? = null
) {
    init { require((draft == null) != (message == null)); require(attachment != null || draft != null) }
}

enum class ImageStatus { Ready, Loading, Uploading, Failed, Missing, Unsupported }

/**
 * [progress] is a host fraction in 0..1, used only for Uploading. Failed offers explicit retry;
 * Loading/Missing/Unsupported offer fallback. Renderers must report load errors through host
 * state rather than throw during composition. No automatic loading or retry is performed.
 */
data class ImageState(val status: ImageStatus = ImageStatus.Ready, val progress: Float = 0f) {
    init { require(progress.isFinite() && progress in 0f..1f) }
}

/**
 * Retain per conversation. Advance [revision] after every media request resolution, including
 * cancel/denial, and advance Draft.revision to resolve every Select/Remove request even when
 * retaining identical content. [selecting] locks draft mutation/submission; [selectionError] is localized host
 * feedback and does not replace draft text/content. [images] keys bind async results to exact
 * descriptors and owner snapshots; drop obsolete keys. Absence means Ready, not delivery success.
 */
data class MediaState(
    val revision: Long = 0,
    val selecting: Boolean = false,
    val selectionError: String? = null,
    val images: Map<MediaTarget, ImageState> = emptyMap()
)

enum class MediaCommand { Select, Remove, Retry, View }

/**
 * Synchronous user intent with exact target and media revision. Validate target, revision,
 * current permission and eligibility again before work and on completion. Selection/removal
 * results replace the retained draft with a fresh revision; retry retains resource/message IDs.
 * The library opens no picker, viewer, resource or service. Callback exceptions propagate.
 */
data class MediaRequest(
    val command: MediaCommand,
    val revision: Long,
    val target: MediaTarget
)

/**
 * Adapter for media slots, without a second Conversation. Use [composer] to lock the shared
 * composer during host selection. Route [onConversationEvent] through to the normal host handler.
 * Controls must invoke [request] only on user activation; draft requests require the shared
 * [ComposerContent] so they participate in its synchronous pending guard.
 */
class MediaContent internal constructor(
    val conversation: ConversationState,
    val state: MediaState,
    private val dispatch: (MediaRequest, ComposerContent?) -> Unit,
    val onConversationEvent: (ConversationEvent) -> Unit
) {
    fun draftTarget(attachment: Attachment? = null) = MediaTarget(conversation.conversationId, attachment, draft = conversation.draft)
    fun messageTarget(message: Message, attachment: Attachment) = MediaTarget(conversation.conversationId, attachment, message = message)
    fun imageState(target: MediaTarget) = state.images[target] ?: ImageState()

    fun composer(content: ComposerContent) = ComposerContent(content.draft, content.pending || state.selecting,
        content.onTextChange, content.onSend, content.onDraftAction)

    fun request(command: MediaCommand, target: MediaTarget = draftTarget(), composer: ComposerContent? = null) {
        dispatch(MediaRequest(command, state.revision, target), composer)
    }
}

@Composable
fun rememberMedia(conversation: ConversationState, state: MediaState,
    onRequest: (MediaRequest) -> Unit, onEvent: (ConversationEvent) -> Unit = {}): MediaContent {
    val latest by rememberUpdatedState(conversation)
    val latestMedia by rememberUpdatedState(state)
    val handler by rememberUpdatedState(onRequest)
    val eventHandler by rememberUpdatedState(onEvent)
    var emitted by remember(conversation.conversationId, state.revision) { mutableStateOf(false) }
    return MediaContent(
        conversation = conversation,
        state = state,
        dispatch = dispatch@{ request, composer ->
            val now = latest
            val target = request.target
            if (now.conversationId != conversation.conversationId || latestMedia != state) return@dispatch
            if (target.conversationId != now.conversationId) return@dispatch
            val attachment = target.attachment
            if (target.draft != null) {
                if (now.draft != target.draft || now.draft.submitting || state.selecting ||
                    composer == null || composer.pending || composer.draft != now.draft
                ) return@dispatch
                if (attachment != null && attachment !in now.draft.attachments) return@dispatch
            } else if (now.messages.find { it.id == target.message?.id } != target.message ||
                attachment !in target.message!!.attachments) return@dispatch
            val image = state.images[target] ?: ImageState()
            when (request.command) {
                MediaCommand.Select -> if (target.draft == null || attachment != null) return@dispatch
                MediaCommand.Remove -> if (target.draft == null || attachment == null) return@dispatch
                MediaCommand.Retry -> if (target.message == null || image.status != ImageStatus.Failed) return@dispatch
                MediaCommand.View -> if (target.message == null || image.status != ImageStatus.Ready ||
                    attachment?.mediaType?.startsWith("image/") != true || attachment.contentReference.isNullOrBlank()
                ) return@dispatch
            }
            if (request.command != MediaCommand.View && emitted) return@dispatch
            val send = {
                if (request.command != MediaCommand.View) emitted = true
                handler(request)
            }
            if (target.draft != null) composer!!.onDraftAction(send) else send()
        },
        onConversationEvent = event@{ event ->
            if (latest.conversationId != conversation.conversationId || latest.draft != conversation.draft ||
                latestMedia != state || state.selecting
            ) return@event
            eventHandler(event)
        }
    )
}
