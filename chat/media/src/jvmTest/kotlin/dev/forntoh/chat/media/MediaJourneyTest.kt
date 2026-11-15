package dev.forntoh.chat.media

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import dev.forntoh.chat.core.*
import dev.forntoh.chat.ui.*
import org.junit.Rule
import org.junit.Test
import kotlin.test.*

class MediaJourneyTest {
    @get:Rule val compose = createComposeRule()
    private val image = Attachment("photo", "image/png", "bundled:mountain", "Mountain")

    @Test fun themedFallbackAndSelectionErrorRemainLegibleWithoutReplacingSlots() {
        val state = ConversationState("room", "me")
        val target = MediaTarget("room", image.copy(contentReference = null), draft = state.draft)
        compose.setContent {
            MediaTheme(textStyle = androidx.compose.ui.text.TextStyle(color = Color.Red)) {
                Column(Modifier.background(Color.Black)) {
                    DefaultMediaFeedback(MediaFeedbackContent(target, ImageState(ImageStatus.Missing)))
                    LocalMediaSlots.current.selectionError("Selection refused")
                }
            }
        }
        listOf("Image unavailable", "Selection refused").forEach { label ->
            val pixels = compose.onNodeWithText(label).captureToImage().toPixelMap()
            assertTrue((0 until pixels.height).any { y -> (0 until pixels.width).any { x ->
                val color = pixels[x, y]
                color.red > .5f && color.green < .1f && color.blue < .1f
            } }, "Default $label must inherit the host's text color")
        }
    }

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

    @Test fun draftAttachmentPrependAndReorderFollowHostOrderWithoutChangingCaptionOrEmittingIntents() {
        val second = image.copy(id = "second", description = "Second")
        var state by mutableStateOf(ConversationState("room", "me", Draft("Exact caption", attachments = listOf(second))))
        val requests = mutableListOf<MediaRequest>()
        val events = mutableListOf<ConversationEvent>()
        compose.setContent {
            val media = rememberMedia(state, MediaState(), requests::add)
            Box(Modifier.size(360.dp, 600.dp)) {
                Conversation(state, events::add, composerContent = {
                    Column {
                        MediaDraft(media, it, renderer = { Box(Modifier.size(80.dp, 40.dp)) })
                        DefaultComposer(it)
                    }
                })
            }
        }
        compose.onNodeWithContentDescription("Second").assertExists()
        fun assertOrder(first: String, last: String) {
            val firstBounds = compose.onNodeWithContentDescription(first).fetchSemanticsNode().boundsInRoot
            val lastBounds = compose.onNodeWithContentDescription(last).fetchSemanticsNode().boundsInRoot
            assertTrue(firstBounds.bottom <= lastBounds.top, "$first must precede $last: $firstBounds / $lastBounds")
            compose.onNodeWithTag("composer").assertTextContains("Exact caption")
            compose.runOnIdle {
                assertEquals("Exact caption", state.draft.text)
                assertTrue(requests.isEmpty() && events.isEmpty(), "Presentation must not emit host intents")
            }
        }
        compose.runOnIdle { state = state.copy(draft = state.draft.copy(revision = 1, attachments = listOf(image, second))) }
        assertOrder("Mountain", "Second")
        compose.runOnIdle { state = state.copy(draft = state.draft.copy(revision = 2, attachments = listOf(second, image))) }
        assertOrder("Second", "Mountain")
    }

    @Test fun eachDraftAttachmentFadesByIdentityAndRetainsPixelsThroughFinalRemoval() {
        val second = image.copy(id = "second", description = "Second")
        var state by mutableStateOf(ConversationState("room", "me", Draft("Exact caption", attachments = listOf(image))))
        val requests = mutableListOf<MediaRequest>()
        val events = mutableListOf<ConversationEvent>()
        compose.setContent {
            CompositionLocalProvider(LocalChatMotion provides ChatMotion(sizeMillis = 0)) {
                val media = rememberMedia(state, MediaState(), requests::add)
                Box(Modifier.size(360.dp, 600.dp).background(Color.White)) {
                    Conversation(state, events::add, composerContent = {
                        MediaDraft(media, it, renderer = { target ->
                            Box(Modifier.size(80.dp, 40.dp).background(if (target.attachment!!.id == "second") Color.Red else Color.Blue))
                        })
                    })
                }
            }
        }
        fun ink(red: Boolean): Float {
            val pixels = compose.onAllNodes(isRoot()).onFirst().captureToImage().toPixelMap()
            var sum = 0f
            for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                val c = pixels[x,y]
                sum += (if (red) c.red - c.blue else c.blue - c.red).coerceAtLeast(0f)
            }
            return sum
        }
        fun frames() { repeat(3) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() } }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { state = state.copy(draft = state.draft.copy(revision = 1, attachments = listOf(image, second))) }
        frames()
        val entering = ink(true)
        compose.mainClock.advanceTimeBy(300)
        assertTrue(entering > 0f && entering < ink(true), "Second attachment needs intermediate opacity: $entering")
        compose.runOnIdle { state = state.copy(draft = state.draft.copy(revision = 2, attachments = listOf(image))) }
        frames()
        compose.onNodeWithContentDescription("Second").assertDoesNotExist()
        assertTrue(ink(true) > 0f, "Removed attachment pixels must survive while another remains")
        compose.mainClock.advanceTimeBy(300)
        assertEquals(0f, ink(true))
        compose.runOnIdle { state = state.copy(draft = state.draft.copy(revision = 3, attachments = emptyList())) }
        frames()
        compose.onNodeWithContentDescription("Mountain").assertDoesNotExist()
        assertTrue(ink(false) > 0f, "Last attachment pixels must survive its exit")
        compose.mainClock.advanceTimeBy(300)
        assertEquals(0f, ink(false))
        assertEquals("Exact caption", state.draft.text)
        assertTrue(requests.isEmpty() && events.isEmpty(), "Motion cannot emit host intents")
        compose.mainClock.autoAdvance = true
    }

}
