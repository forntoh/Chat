package dev.forntoh.chat.media

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import dev.forntoh.chat.core.Message
import dev.forntoh.chat.ui.*

data class MediaControlContent(val command: MediaCommand, val enabled: Boolean, val onClick: () -> Unit)

data class MediaFeedbackContent(val target: MediaTarget, val image: ImageState)

/**
 * Replaceable media content; defaults use English foundation controls. [description] supplies
 * missing-description fallback. [feedback] authors visible/accessible state, including unsupported
 * and missing resources; host selection errors are already localized. [control] must honor enabled
 * and invoke the callback only on activation. No grouped-label DTO or Material3 dependency is used.
 */
data class MediaSlots(
    val control: @Composable (MediaControlContent) -> Unit = { DefaultMediaControl(it) },
    val feedback: @Composable (MediaFeedbackContent) -> Unit = { DefaultMediaFeedback(it) },
    val description: (MediaTarget) -> String = { it.attachment?.description?.takeIf(String::isNotBlank) ?: "Image attachment" },
    val selectionError: @Composable (String) -> Unit = { BasicText(it) },
)

val LocalMediaSlots = staticCompositionLocalOf { MediaSlots() }

/** Installs presentation only. Explicit slots override this theme; host state and guards are retained. */
@Composable
fun MediaTheme(slots: MediaSlots = LocalMediaSlots.current, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalMediaSlots provides slots, content = content)
}

/** Foundation control usable for delegation from custom slots; minimum target is 48 dp. */
@Composable
fun DefaultMediaControl(content: MediaControlContent) {
    Box(Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
        .clickable(enabled = content.enabled, role = Role.Button, onClick = content.onClick).padding(8.dp)) {
        BasicText(when (content.command) {
            MediaCommand.Select -> "Attach image"
            MediaCommand.Remove -> "Remove image"
            MediaCommand.Retry -> "Retry upload"
            MediaCommand.View -> "View image"
        })
    }
}

/** Visible feedback also serves as accessible fallback when a host renderer cannot present content. */
@Composable
fun DefaultMediaFeedback(content: MediaFeedbackContent) {
    BasicText(when (content.image.status) {
        ImageStatus.Ready -> "Image attachment"
        ImageStatus.Uploading -> "Uploading image · ${(content.image.progress * 100).toInt()}%"
        ImageStatus.Loading -> "Loading image…"
        ImageStatus.Failed -> "Couldn't upload image"
        ImageStatus.Missing -> "Image unavailable"
        ImageStatus.Unsupported -> "Unsupported attachment"
    })
}

/**
 * Draft affordance and previews, composed above the effective Conversation composer. Always pass
 * the shared [composer], including its pending latch. Select/remove emit host requests and never
 * alter content themselves. [renderer] draws only supplied ready image resources, never fetches
 * implicitly in this module. Omitted renderer produces Unsupported fallback. Report renderer load
 * failure as ImageState(Missing) for the exact target. Explicit [slots] override MediaTheme.
 */
@Composable
fun MediaDraft(media: MediaContent, composer: ComposerContent,
    renderer: (@Composable (MediaTarget) -> Unit)? = null, slots: MediaSlots = LocalMediaSlots.current) {
    val adapted = media.composer(composer)
    Column {
        slots.control(MediaControlContent(MediaCommand.Select, !adapted.pending) {
            media.request(MediaCommand.Select, composer = adapted)
        })
        media.state.selectionError?.let { slots.selectionError(it) }
        composer.draft.attachments.forEach { attachment ->
            val target = media.draftTarget(attachment)
            MediaImage(media, target, renderer, slots)
            slots.control(MediaControlContent(MediaCommand.Remove, !adapted.pending) {
                media.request(MediaCommand.Remove, target, adapted)
            })
        }
    }
}

/**
 * Attachments for a message slot. Keep this outside merged/cleared message semantics so image and
 * retry/view controls remain separately accessible. Compose it after the effective text row.
 * Ready images with a renderer offer View; failed upload offers Retry with stable target identity.
 * Host delivery is independent and must never report Sent before a failed upload is resolved.
 */
@Composable
fun MediaAttachments(media: MediaContent, message: Message,
    renderer: (@Composable (MediaTarget) -> Unit)? = null, slots: MediaSlots = LocalMediaSlots.current) {
    Column { message.attachments.forEach { MediaImage(media, media.messageTarget(message, it), renderer, slots) } }
}

@Composable
private fun MediaImage(media: MediaContent, target: MediaTarget,
    renderer: (@Composable (MediaTarget) -> Unit)?, slots: MediaSlots) {
    val supplied = media.imageState(target)
    val attachment = target.attachment!!
    val display = when {
        attachment.contentReference.isNullOrBlank() -> ImageState(ImageStatus.Missing)
        !attachment.mediaType.startsWith("image/") -> ImageState(ImageStatus.Unsupported)
        supplied.status == ImageStatus.Ready && renderer == null -> ImageState(ImageStatus.Unsupported)
        else -> supplied
    }
    Column {
        if (display.status == ImageStatus.Ready) {
            Box(Modifier.clickable(enabled = target.message != null, role = Role.Button,
                onClick = { media.request(MediaCommand.View, target) })
                .semantics(mergeDescendants = true) { contentDescription = slots.description(target) }) {
                Box(Modifier.clearAndSetSemantics {}) { renderer!!(target) }
            }
            if (target.message != null) slots.control(MediaControlContent(MediaCommand.View, true) { media.request(MediaCommand.View, target) })
        } else slots.feedback(MediaFeedbackContent(target, display))
        if (display.status == ImageStatus.Failed && target.message != null) {
            slots.control(MediaControlContent(MediaCommand.Retry, true) { media.request(MediaCommand.Retry, target) })
        }
    }
}
