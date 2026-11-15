package dev.chat.sample

import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.toArgb
import java.awt.image.BufferedImage
import java.awt.RenderingHints
import java.io.File
import javax.imageio.ImageIO
import org.junit.Rule
import org.junit.Test
import kotlin.test.*
import dev.forntoh.chat.core.*

class MediaSampleTest {
    private fun mediaDemo(label: String) {
        compose.onNodeWithTag("media-demo-menu").performClick()
        compose.onNodeWithText(label).performClick()
    }

    @get:Rule val compose = createComposeRule()
    private fun capture(name: String) {
        val pixels = compose.onAllNodes(isRoot()).onLast().captureToImage().toPixelMap()
        val raw = BufferedImage(pixels.width, pixels.height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) raw.setRGB(x, y, pixels[x, y].toArgb())
        val scaled = BufferedImage(320, (pixels.height * 320.0 / pixels.width).toInt(), BufferedImage.TYPE_INT_ARGB)
        scaled.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            drawImage(raw, 0, 0, scaled.width, scaled.height, null); dispose()
        }
        val directory = File("build/pat245-evidence").apply { mkdirs() }
        ImageIO.write(scaled, "png", File(directory, "$name.png"))
    }
    @Test fun automaticServiceCompletesEveryAcceptedImage() {
        val host = FakeMediaHost()
        compose.setContent { Box(Modifier.size(400.dp, 900.dp)) { MediaSample(host) } }
        repeat(2) {
            compose.onNodeWithContentDescription("Attach image").performClick()
            compose.onNodeWithText("Choose bundled image").performClick()
        }
        compose.onNodeWithContentDescription("Send").performClick()
        compose.runOnIdle {
            assertNull(host.upload)
            assertEquals(Delivery.Sent, host.state.messages.single().delivery)
        }
        compose.onAllNodesWithText("View image").assertCountEquals(2)
    }

    @Test fun imageSupplementAlignsWithDirectionalBubbleAndOmitsUnsupportedRowRetry() {
        val host = FakeMediaHost().apply { resetSimulation() }
        compose.setContent { Box(Modifier.size(400.dp, 900.dp)) { MediaSample(host, manualService = true) } }
        compose.onNodeWithTag("message-${host.state.messages.first().id}").performScrollTo()
        fun bubble(row: Message) = compose.onNodeWithContentDescription(
            dev.forntoh.chat.material3.chatConversationSlots().messageDescription(row), useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val incoming = bubble(host.state.messages.first())
        val ready = compose.onNodeWithContentDescription("Image attachment").fetchSemanticsNode().boundsInRoot
        assertEquals(incoming.left, ready.left, 1f)
        compose.runOnIdle { host.switchRoom() }
        compose.onNodeWithContentDescription("Attach image").performClick()
        compose.onNodeWithText("Choose bundled image").performClick()
        compose.onNodeWithContentDescription("Send").performClick()
        mediaDemo("Accept image draft")
        mediaDemo("Fail upload")
        compose.onNodeWithText("Retry").assertDoesNotExist()
        compose.onNodeWithText("Retry upload").assertIsDisplayed().performClick()
        mediaDemo("Complete upload")
        val outgoing = bubble(host.state.messages.single())
        val image = compose.onNodeWithContentDescription("Bundled mountain illustration").fetchSemanticsNode().boundsInRoot
        assertEquals(outgoing.right, image.right, 1f)
        capture("14-aligned-outgoing-image")
    }

    @Test fun manyAttachmentPreviewsKeepSendAndCaptionUsable() {
        val host = FakeMediaHost()
        compose.setContent { Box(Modifier.size(360.dp, 600.dp)) { MediaSample(host, manualService = true) } }
        compose.onNodeWithTag("composer").performTextInput("Keep this caption")
        repeat(5) {
            compose.onNodeWithContentDescription("Attach image").performClick()
            compose.onNodeWithText("Choose bundled image").performClick()
        }
        compose.onNodeWithTag("composer").assertIsDisplayed().assertTextContains("Keep this caption")
        compose.onNodeWithContentDescription("Send").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals("Keep this caption", host.submission!!.text)
            assertEquals(5, host.submission!!.attachments.size)
        }
        capture("09-bounded-previews")
    }

    @Test fun everyImageInOneSubmissionResolvesAndFailedImageRetriesWithoutAnotherRow() {
        val host = FakeMediaHost()
        compose.setContent { Box(Modifier.size(400.dp, 900.dp)) { MediaSample(host, manualService = true) } }
        repeat(2) {
            compose.onNodeWithContentDescription("Attach image").performClick()
            compose.onNodeWithText("Choose bundled image").performClick()
        }
        compose.onNodeWithContentDescription("Send").performClick()
        mediaDemo("Accept image draft")
        val id = host.state.messages.single().id
        val attachments = host.state.messages.single().attachments
        compose.onAllNodesWithText("Uploading image · 25%").assertCountEquals(2)
        mediaDemo("Complete upload")
        compose.runOnIdle {
            assertEquals(Delivery.Sending, host.state.messages.single().delivery)
            assertEquals(attachments[1], host.upload!!.attachment)
        }
        mediaDemo("Fail upload")
        compose.onNodeWithText("Couldn't upload image").assertExists()
        compose.runOnIdle { assertEquals(Delivery.Failed, host.state.messages.single().delivery) }
        compose.onNodeWithText("Retry upload").performScrollTo().performClick()
        mediaDemo("Complete upload")
        compose.runOnIdle {
            assertEquals(id, host.state.messages.single().id)
            assertEquals(attachments, host.state.messages.single().attachments)
            assertEquals(Delivery.Sent, host.state.messages.single().delivery)
            assertNull(host.upload)
        }
        compose.onAllNodesWithText("View image").assertCountEquals(2)
        capture("10-multiple-resolved-images")
    }

    @Test fun revokedResourceClosesViewerAndRejectsLateUploadWithoutChangingNewDraft() {
        val host = FakeMediaHost()
        compose.setContent { Box(Modifier.size(400.dp, 900.dp)) { MediaSample(host, manualService = true) } }
        repeat(2) {
            compose.onNodeWithContentDescription("Attach image").performClick()
            compose.onNodeWithText("Choose bundled image").performClick()
        }
        compose.onNodeWithContentDescription("Send").performClick()
        mediaDemo("Accept image draft")
        val oldUpload = host.upload!!
        compose.onNodeWithTag("composer").performTextInput("Newer unrelated draft")
        compose.runOnIdle { host.revokeResource(oldUpload) }
        compose.onNodeWithText("Image unavailable").assertExists()
        compose.runOnIdle { host.completeUpload(true, oldUpload) }
        compose.onNodeWithTag("composer").assertTextContains("Newer unrelated draft")
        compose.runOnIdle { assertFalse(host.current(oldUpload)); assertNull(host.upload) }
        compose.onNodeWithText("Retry upload").performScrollTo().performClick()
        mediaDemo("Complete upload")
        compose.runOnIdle { assertEquals(Delivery.Failed, host.state.messages.single().delivery) }
        compose.onNodeWithText("Image unavailable").assertExists()
        compose.runOnIdle { host.resetSimulation() }
        // The ready fixture is first; navigate to it using the ordinary timeline.
        compose.onNodeWithTag("message-${host.state.messages.first().id}").performScrollTo()
        compose.onNodeWithContentDescription("Image attachment").performClick()
        compose.onNodeWithText("Shared image").assertExists()
        compose.runOnIdle { host.revokeResource(host.viewer!!) }
        compose.onNodeWithText("Shared image").assertDoesNotExist()
        compose.runOnIdle { assertNull(host.viewer) }
        capture("11-revoked-resource")
    }

    @Test fun selectingAnotherImageAppendsWithoutReplacingTheExistingDraft() {
        val host = FakeMediaHost()
        compose.setContent { Box(Modifier.size(400.dp, 900.dp)) { MediaSample(host, manualService = true) } }
        compose.onNodeWithTag("composer").performTextInput("Exact caption")
        compose.onNodeWithContentDescription("Attach image").performClick()
        compose.onNodeWithText("Choose bundled image").performClick()
        val first = host.state.draft.attachments.single()
        compose.onNodeWithContentDescription("Attach image").performClick()
        compose.onNodeWithText("Choose bundled image").performClick()
        compose.runOnIdle {
            assertEquals(2, host.state.draft.attachments.size)
            assertEquals(first, host.state.draft.attachments.first())
            assertEquals(2, host.state.draft.attachments.map { it.id }.distinct().size)
            assertEquals("Exact caption", host.state.draft.text)
            assertEquals(null, host.submission)
        }
    }

    @Test fun customImageSlotsRetainLoadingFallbacksAndMissingMetadata() {
        val host = FakeMediaHost().apply { resetSimulation() }
        compose.setContent { Box(Modifier.size(400.dp, 900.dp)) { MediaSample(host) } }
        compose.onNodeWithTag("message-${host.state.messages.first().id}").performScrollTo()
        compose.onNodeWithContentDescription("Image attachment").assertExists()
        capture("12-default-loading-missing-metadata")
        mediaDemo("Custom image UI")
        compose.onAllNodesWithText("Host attachment feedback").assertCountEquals(3)
        compose.onNodeWithText("Loading image…").assertExists()
        compose.onNodeWithText("Image unavailable").assertExists()
        compose.onNodeWithText("Unsupported attachment").assertExists()
        capture("13-custom-loading-fallbacks")
        mediaDemo("Finish image loading")
        compose.onNodeWithText("Loading image…").assertDoesNotExist()
        compose.onNodeWithContentDescription("Loading fixture").assertExists()
        compose.runOnIdle {
            assertEquals("", host.state.messages[2].displayTime)
            assertEquals("", host.state.messages[3].sender.displayName)
            assertNull(host.state.messages[0].attachments.single().description)
        }
    }

    @Test fun imageSimulatorRendersLoadingAndUnavailableFallbacks() {
        val host = FakeMediaHost().apply { resetSimulation() }
        compose.setContent { Box(Modifier.size(400.dp, 900.dp)) { MediaSample(host) } }
        compose.onNodeWithText("Unsupported attachment").assertExists()
        compose.onNodeWithText("Image unavailable").assertExists()
        capture("08-simulator-fallbacks")
    }
    @Test fun bundledImageJourneyRetainsDraftOnCancelDenyAndFailureThenRetriesSameIdentity() {
        val host = FakeMediaHost()
        compose.setContent { Box(Modifier.size(400.dp, 900.dp)) { MediaSample(host, manualService = true) } }
        compose.onNodeWithTag("composer").performTextInput("Caption")
        compose.onNodeWithContentDescription("Attach image").performClick()
        compose.onNodeWithText("Cancel selection").performClick()
        compose.onNodeWithTag("composer").assertTextContains("Caption")
        mediaDemo("Deny next selection")
        compose.onNodeWithContentDescription("Attach image").performClick()
        compose.onNodeWithText("Permission denied by fake host").assertExists()
        compose.onNodeWithContentDescription("Attach image").performClick()
        compose.onNodeWithText("Choose bundled image").performClick()
        compose.onNodeWithContentDescription("Attach image").performClick()
        compose.onNodeWithText("Cancel selection").performClick()
        compose.onNodeWithContentDescription("Bundled mountain illustration").assertExists()
        compose.onNodeWithText("Remove image").performClick()
        compose.onNodeWithTag("composer").assertTextContains("Caption")
        compose.onNodeWithContentDescription("Attach image").performClick()
        compose.onNodeWithText("Choose bundled image").performClick()
        capture("01-selected-draft")
        compose.onNodeWithContentDescription("Send").performClick().performClick()
        capture("02-pending-submission")
        compose.onNodeWithText("Remove image").assertIsNotEnabled()
        mediaDemo("Keep draft")
        compose.onNodeWithTag("composer").assertTextContains("Caption")
        compose.onNodeWithContentDescription("Send").performClick()
        mediaDemo("Accept image draft")
        compose.onNodeWithText("Uploading image · 25%").assertExists()
        capture("03-upload-progress")
        mediaDemo("Fail upload")
        capture("04-upload-failure")
        val id = host.state.messages.single().id
        compose.runOnIdle { assertEquals(Delivery.Failed, host.state.messages.single().delivery) }
        compose.onNodeWithText("Retry upload").performClick()
        mediaDemo("Complete upload")
        compose.runOnIdle { assertEquals(id, host.state.messages.single().id); assertEquals(Delivery.Sent, host.state.messages.single().delivery) }
        compose.onNodeWithContentDescription("Bundled mountain illustration").performClick()
        compose.onNodeWithText("Shared image").assertExists()
        capture("05-host-viewer")
        compose.onNodeWithText("Close viewer").performClick()
    }
    @Test fun oldSelectionAndUploadResultsCannotAffectAnotherRoomOrRemovedDraft() {
        val host = FakeMediaHost()
        compose.setContent { Box(Modifier.size(400.dp, 900.dp)) { MediaSample(host, manualService = true) } }
        compose.onNodeWithTag("composer").performTextInput("Retained")
        compose.onNodeWithContentDescription("Attach image").performClick()
        val oldSelection = host.selection
        compose.onNodeWithText("Choose bundled image").performClick()
        compose.onNodeWithText("Remove image").performClick()
        compose.runOnIdle { host.resolveSelection(choose = true, request = oldSelection); assertTrue(host.state.draft.attachments.isEmpty()) }
        compose.onNodeWithContentDescription("Attach image").performClick()
        val navigationSelection = host.selection
        compose.runOnIdle { host.switchRoom(); host.switchRoom() }
        compose.runOnIdle { host.resolveSelection(choose = true, request = navigationSelection); assertTrue(host.state.draft.attachments.isEmpty()) }
        compose.onNodeWithTag("composer").assertTextContains("Retained")
        compose.onNodeWithContentDescription("Attach image").performClick()
        compose.onNodeWithText("Choose bundled image").performClick()
        compose.onNodeWithContentDescription("Send").performClick()
        mediaDemo("Accept image draft")
        val oldUpload = host.upload
        mediaDemo("Switch media room")
        compose.runOnIdle { host.completeUpload(true, oldUpload); assertTrue(host.state.messages.isEmpty()) }
        mediaDemo("Switch media room")
        compose.runOnIdle { host.completeUpload(true, oldUpload); assertEquals(Delivery.Failed, host.state.messages.single().delivery) }
        compose.onNodeWithText("Couldn't upload image").assertExists()
    }

    @Test fun laterTextImageAndRetryDoNotReplaceAnUnresolvedUpload() {
        val host = FakeMediaHost()
        compose.setContent { Box(Modifier.size(400.dp, 900.dp)) { MediaSample(host, manualService = true) } }
        compose.onNodeWithContentDescription("Attach image").performClick()
        compose.onNodeWithText("Choose bundled image").performClick()
        compose.onNodeWithContentDescription("Send").performClick()
        mediaDemo("Accept image draft")
        val first = host.upload!!

        compose.onNodeWithTag("composer").performTextInput("A later text message")
        compose.onNodeWithContentDescription("Send").performClick()
        mediaDemo("Accept image draft")
        compose.runOnIdle {
            assertEquals(first, host.upload, "Text acceptance must retain the earlier upload controls")
            assertEquals(Delivery.Sent, host.state.messages.last().delivery)
        }
        compose.runOnIdle { assertNotNull(host.upload) }

        compose.onNodeWithContentDescription("Attach image").performClick()
        compose.onNodeWithText("Choose bundled image").performClick()
        compose.onNodeWithContentDescription("Send").performClick()
        mediaDemo("Accept image draft")
        val secondId = host.state.messages.last().id
        compose.runOnIdle { assertEquals(first, host.upload, "Image acceptance must retain the earlier upload") }
        capture("06-overlapping-uploads")
        mediaDemo("Fail upload")
        compose.runOnIdle {
            assertEquals(secondId, host.upload!!.message!!.id)
            assertEquals(Delivery.Failed, host.state.messages.first().delivery)
        }
        compose.onNodeWithText("Retry upload").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(secondId, host.upload!!.message!!.id, "Retry must not replace another active upload") }
        mediaDemo("Complete upload")
        compose.runOnIdle {
            assertEquals(first.attachment!!.id, host.upload!!.attachment!!.id)
            assertEquals(first.message!!.id, host.upload!!.message!!.id)
            assertEquals(Delivery.Sent, host.state.messages.last().delivery)
        }
        mediaDemo("Complete upload")
        compose.runOnIdle {
            assertNull(host.upload)
            assertEquals(3, host.state.messages.size)
            assertTrue(host.state.messages.all { it.delivery == Delivery.Sent })
        }
        capture("07-resolved-uploads")
    }

}
