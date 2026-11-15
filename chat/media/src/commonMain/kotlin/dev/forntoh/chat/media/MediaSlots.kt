package dev.forntoh.chat.media

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.visible
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import dev.forntoh.chat.core.Message
import dev.forntoh.chat.ui.ChatMotionVisibility
import dev.forntoh.chat.ui.ComposerContent
import dev.forntoh.chat.ui.Conversation

data class MediaControlContent(
    val command: MediaCommand,
    val enabled: Boolean,
    val onClick: () -> Unit,
)

data class MediaFeedbackContent(
    val target: MediaTarget,
    val image: ImageState,
)

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
    val selectionError: @Composable (String) -> Unit = { BasicText(it, style = LocalMediaTextStyle.current) },
)

val LocalMediaSlots = staticCompositionLocalOf { MediaSlots() }

/** Foundation typography/color bridge; optional host themes supply their ordinary content style. */
val LocalMediaTextStyle = staticCompositionLocalOf { TextStyle.Default }

/** Installs presentation only. Explicit slots override this theme; host state and guards are retained.
 * The text-style overload applies to default feedback, controls and errors without a Material3 dependency.
 * Custom slots own their typography; derive from [LocalMediaTextStyle] to retain the host bridge. */
@Composable
fun MediaTheme(slots: MediaSlots = LocalMediaSlots.current, content: @Composable () -> Unit) {
    MediaTheme(LocalMediaTextStyle.current, slots, content)
}

/** Host typography bridge for the foundation defaults; existing slot-only calls retain their signature. */
@Composable
fun MediaTheme(textStyle: TextStyle, slots: MediaSlots = LocalMediaSlots.current, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalMediaSlots provides slots, LocalMediaTextStyle provides textStyle, content = content)
}

/** Foundation control usable for delegation from custom slots; minimum target is 48 dp. */
@Composable
fun DefaultMediaControl(content: MediaControlContent) {
    Box(
        Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clickable(enabled = content.enabled, role = Role.Button, onClick = content.onClick).padding(8.dp),
    ) {
        BasicText(
            when (content.command) {
                MediaCommand.Select -> "Attach image"
                MediaCommand.Remove -> "Remove image"
                MediaCommand.Retry -> "Retry upload"
                MediaCommand.View -> "View image"
            },
            style = LocalMediaTextStyle.current,
        )
    }
}

@Composable
fun DefaultMediaFeedback(content: MediaFeedbackContent) {
    BasicText(
        when (content.image.status) {
            ImageStatus.Ready -> "Image attachment"
            ImageStatus.Uploading -> "Uploading image · ${(content.image.progress * 100).toInt()}%"
            ImageStatus.Loading -> "Loading image…"
            ImageStatus.Failed -> "Couldn't upload image"
            ImageStatus.Missing -> "Image unavailable"
            ImageStatus.Unsupported -> "Unsupported attachment"
        },
        style = LocalMediaTextStyle.current,
    )
}

/**
 * Draft affordance and previews, composed above the effective Conversation composer. Always pass
 * the shared [composer], including its pending latch. Select/remove emit host requests and never
 * alter content themselves. Previews share a scrollable viewport capped at 184 dp so multiple
 * attachments cannot displace the input and Send control. Host renderers keep their natural size.
 * [renderer] draws only supplied ready image resources, never fetches
 * implicitly in this module. Omitted renderer produces Unsupported fallback. Report renderer load
 * failure as ImageState(Missing) for the exact target. Explicit [slots] override MediaTheme.
 */
@Composable
fun MediaDraft(
    media: MediaContent,
    composer: ComposerContent,
    renderer: (@Composable (MediaTarget) -> Unit)? = null,
    slots: MediaSlots = LocalMediaSlots.current,
) {
    val adapted = media.composer(composer)
    key(media.conversation.conversationId) {
        Column {
            slots.control(
                MediaControlContent(MediaCommand.Select, !adapted.pending) {
                    media.request(MediaCommand.Select, composer = adapted)
                },
            )
            media.state.selectionError?.let { slots.selectionError(it) }
            val initialIds = remember { composer.draft.attachments.map { it.id }.toSet() }
            val retainedIds = remember { mutableStateListOf<String>() }
            val previews = composer.draft.attachments.associate { attachment ->
                val target = media.draftTarget(attachment)
                attachment.id to DraftPreview(
                    target,
                    media.imageState(target),
                    MediaControlContent(MediaCommand.Remove, !adapted.pending) {
                        media.request(MediaCommand.Remove, target, adapted)
                    },
                )
            }
            Column(Modifier.heightIn(max = 184.dp).verticalScroll(rememberScrollState())) {
                (previews.keys + retainedIds.filterNot { it in previews }).forEach { id ->
                    key(id) {
                        ChatMotionVisibility(previews[id], initiallyVisible = id in initialIds) { shown ->
                            DisposableEffect(id) {
                                retainedIds.add(id)
                                onDispose { retainedIds.remove(id) }
                            }
                            Column {
                                MediaImage(media, shown.target, renderer, slots, shown.image)
                                slots.control(shown.remove)
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class DraftPreview(
    val target: MediaTarget,
    val image: ImageState,
    val remove: MediaControlContent,
)

/**
 * Attachments for a message slot. Keep this outside merged/cleared message semantics so image and
 * retry/view controls remain separately accessible. Compose it after the effective text row.
 * Ready images with a renderer offer View; failed upload offers Retry with stable target identity.
 * Host delivery is independent and must never report Sent before a failed upload is resolved.
 */
@Composable
fun MediaAttachments(
    media: MediaContent,
    message: Message,
    renderer: (@Composable (MediaTarget) -> Unit)? = null,
    slots: MediaSlots = LocalMediaSlots.current,
) {
    Column { message.attachments.forEach { MediaImage(media, media.messageTarget(message, it), renderer, slots) } }
}

@Composable
private fun MediaImage(
    media: MediaContent,
    target: MediaTarget,
    renderer: (@Composable (MediaTarget) -> Unit)?,
    slots: MediaSlots,
    supplied: ImageState = media.imageState(target),
) {
    val attachment = target.attachment!!
    val display = when {
        attachment.contentReference.isNullOrBlank() -> ImageState(ImageStatus.Missing)
        !attachment.mediaType.startsWith("image/") -> ImageState(ImageStatus.Unsupported)
        supplied.status == ImageStatus.Ready && renderer == null -> ImageState(ImageStatus.Unsupported)
        else -> supplied
    }
    Column {
        if (display.status == ImageStatus.Ready) {
            Box(
                Modifier.clickable(
                    enabled = target.message != null,
                    role = Role.Button,
                    onClick = { media.request(MediaCommand.View, target) },
                )
                    .semantics(mergeDescendants = true) { contentDescription = slots.description(target) },
            ) {
                Box(Modifier.clearAndSetSemantics {}) { renderer!!(target) }
            }
            if (target.message !=
                null
            ) {
                slots.control(MediaControlContent(MediaCommand.View, true) { media.request(MediaCommand.View, target) })
            }
        } else {
            slots.feedback(MediaFeedbackContent(target, display))
        }
        if (display.status == ImageStatus.Failed && target.message != null) {
            slots.control(MediaControlContent(MediaCommand.Retry, true) { media.request(MediaCommand.Retry, target) })
        }
    }
}
