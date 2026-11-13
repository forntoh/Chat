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
import com.forntoh.chat.core.*

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
