package dev.chat.media

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import dev.chat.core.*
import dev.chat.ui.*
import org.junit.Rule
import org.junit.Test
import kotlin.test.*

class MediaJourneyTest {
    @get:Rule val compose = createComposeRule()
    private val image = Attachment("photo", "image/png", "bundled:mountain", "Mountain")

    @Test fun imageOnlyAndTextWithImageUseOneGuardedSubmissionSnapshot() {
        var state by mutableStateOf(ConversationState("room", "me", Draft(attachments = listOf(image))))
        val events = mutableListOf<ConversationEvent>()
        compose.setContent { Box(Modifier.size(360.dp, 600.dp)) { Conversation(state, events::add) } }
        compose.onNodeWithText("Send").performClick().performClick()
        compose.runOnIdle {
            assertEquals(listOf<ConversationEvent>(ConversationEvent.SendDraft("room", 0, "", listOf(image))), events)
            state = state.copy(draft = Draft("Caption", 1, attachments = listOf(image)))
        }
        compose.onNodeWithText("Send").performClick()
        compose.runOnIdle {
            assertEquals(ConversationEvent.SendDraft("room", 1, "Caption", listOf(image)), events.last())
            assertEquals(2, events.size)
        }
    }
    @Test fun selectionRemovalAndStaleTargetsAreHostIntentsThatPreserveText() {
        var state by mutableStateOf(ConversationState("room", "me", Draft("Caption", attachments = listOf(image))))
        var mediaState by mutableStateOf(MediaState())
        val requests = mutableListOf<MediaRequest>()
        val events = mutableListOf<ConversationEvent>()
        var oldComposer: ComposerContent? = null
        var old: MediaContent? = null
        var current: MediaContent? = null
        compose.setContent {
            val media = rememberMedia(state, mediaState, requests::add)
            SideEffect { current = media; if (old == null) old = media }
            Box(Modifier.size(360.dp, 600.dp)) {
                Conversation(state, events::add, composerContent = { SideEffect { if (oldComposer == null) oldComposer = it }; Column { MediaDraft(media, it); DefaultComposer(it) } })
            }
        }
        compose.onNodeWithText("Remove image").performClick()
        compose.runOnIdle {
            oldComposer!!.onSend()
            assertTrue(events.isEmpty(), "An unresolved attachment mutation must suppress a custom send")
            assertEquals(MediaCommand.Remove, requests.single().command)
            assertEquals("Caption", state.draft.text)
            state = state.copy(draft = state.draft.copy(revision = 1, attachments = emptyList()))
            mediaState = MediaState(revision = 1)
        }
        compose.runOnIdle { old!!.request(MediaCommand.Remove, old!!.draftTarget(image), oldComposer) }
        compose.runOnIdle { assertEquals(1, requests.size) }
        compose.onNodeWithText("Attach image").performClick()
        compose.runOnIdle {
            assertEquals(MediaCommand.Select, requests.last().command)
            mediaState = MediaState(revision = 2, selectionError = "Permission denied")
        }
        compose.onNodeWithText("Permission denied").assertExists()
        compose.onNodeWithTag("composer").assertTextContains("Caption")
        compose.runOnIdle {
            state = state.copy(conversationId = "other")
        }
        compose.runOnIdle { current = null; old!!.request(MediaCommand.Select); assertEquals(2, requests.size) }
    }

    @Test fun uploadFailureRetryViewerAndFallbackRemainSeparateFromDelivery() {
        val message = Message("sent-photo", Participant("me", "You"), 0, "Now", "Caption", Delivery.Sending, listOf(image))
        var state by mutableStateOf(ConversationState("room", "me", messages = listOf(message)))
        val target = MediaTarget("room", image, message = message)
        var status by mutableStateOf(MediaState(images = mapOf(target to ImageState(ImageStatus.Uploading, .5f))))
        val requests = mutableListOf<MediaRequest>()
        var old: MediaContent? = null
        var renderer by mutableStateOf(true)
        compose.setContent {
            val media = rememberMedia(state, status, requests::add)
            SideEffect { old = media }
            Box(Modifier.size(360.dp, 600.dp)) {
                Conversation(state, {}, messageContent = { row, _, _ ->
                    Column { DefaultMessage(row, null); MediaAttachments(media, row,
                        renderer = if (renderer) { { androidx.compose.foundation.text.BasicText("Bundled pixels") } } else null) }
                })
            }
        }
        compose.onNodeWithText("Uploading image · 50%").assertExists()
        compose.runOnIdle { status = MediaState(1, images = mapOf(target to ImageState(ImageStatus.Failed))) }
        compose.onNodeWithText("Couldn't upload image").assertExists()
        compose.onNodeWithText("Retry upload").performClick().performClick()
        compose.runOnIdle {
            assertEquals(listOf(MediaRequest(MediaCommand.Retry, 1, target)), requests)
            assertEquals(Delivery.Sending, state.messages.single().delivery)
            status = MediaState(2)
        }
        compose.onNodeWithContentDescription("Mountain").performClick()
        var stale: MediaContent? = null
        compose.runOnIdle {
            assertEquals(MediaCommand.View, requests.last().command)
            stale = old
            state = state.copy(messages = listOf(message.copy(text = "Replacement")))
        }
        compose.runOnIdle { stale!!.request(MediaCommand.View, target); assertEquals(2, requests.size); renderer = false }
        compose.onNodeWithText("Unsupported attachment").assertExists()
        compose.runOnIdle {
            state = state.copy(messages = listOf(message.copy(attachments = listOf(image.copy(contentReference = null)))))
        }
        compose.onNodeWithText("Image unavailable").assertExists()
    }

}
