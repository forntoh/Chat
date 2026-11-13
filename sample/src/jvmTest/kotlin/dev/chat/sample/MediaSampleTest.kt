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
import dev.chat.core.*

class MediaSampleTest {
    @get:Rule val compose = createComposeRule()
    private fun capture(name: String) {
        val pixels = compose.onRoot().captureToImage().toPixelMap()
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
        compose.setContent { Box(Modifier.size(400.dp, 900.dp)) { MediaSample(host) } }
        compose.onNodeWithTag("composer").performTextInput("Caption")
        compose.onNodeWithText("Attach image").performClick()
        compose.onNodeWithText("Cancel selection").performClick()
        compose.onNodeWithTag("composer").assertTextContains("Caption")
        compose.onNodeWithText("Attach image").performClick()
        compose.onNodeWithText("Deny selection").performClick()
        compose.onNodeWithText("Permission denied by fake host").assertExists()
        compose.onNodeWithText("Attach image").performClick()
        compose.onNodeWithText("Choose bundled image").performClick()
        compose.onNodeWithText("Attach image").performClick()
        compose.onNodeWithText("Cancel selection").performClick()
        compose.onNodeWithContentDescription("Bundled mountain illustration").assertExists()
        compose.onNodeWithText("Remove image").performClick()
        compose.onNodeWithTag("composer").assertTextContains("Caption")
        compose.onNodeWithText("Attach image").performClick()
        compose.onNodeWithText("Choose bundled image").performClick()
        capture("01-selected-draft")
        compose.onNodeWithText("Send").performClick().performClick()
        capture("02-pending-submission")
        compose.onNodeWithText("Remove image").assertIsNotEnabled()
        compose.onNodeWithText("Keep draft").performClick()
        compose.onNodeWithTag("composer").assertTextContains("Caption")
        compose.onNodeWithText("Send").performClick()
        compose.onNodeWithText("Accept image draft").performClick()
        compose.onNodeWithText("Uploading image · 25%").assertExists()
        capture("03-upload-progress")
        compose.onNodeWithText("Fail upload").performClick()
        capture("04-upload-failure")
        val id = host.state.messages.single().id
        compose.runOnIdle { assertEquals(Delivery.Failed, host.state.messages.single().delivery) }
        compose.onNodeWithText("Retry upload").performClick()
        compose.onNodeWithText("Complete upload").performClick()
        compose.runOnIdle { assertEquals(id, host.state.messages.single().id); assertEquals(Delivery.Sent, host.state.messages.single().delivery) }
        compose.onNodeWithContentDescription("Bundled mountain illustration").performClick()
        compose.onNodeWithText("Host viewer · $id / mountain-2").assertExists()
        capture("05-host-viewer")
        compose.onNodeWithText("Close viewer").performClick()
    }
    @Test fun oldSelectionAndUploadResultsCannotAffectAnotherRoomOrRemovedDraft() {
        val host = FakeMediaHost()
        compose.setContent { Box(Modifier.size(400.dp, 900.dp)) { MediaSample(host) } }
        compose.onNodeWithTag("composer").performTextInput("Retained")
        compose.onNodeWithText("Attach image").performClick()
        val oldSelection = host.selection
        compose.onNodeWithText("Choose bundled image").performClick()
        compose.onNodeWithText("Remove image").performClick()
        compose.runOnIdle { host.resolveSelection(choose = true, request = oldSelection); assertTrue(host.state.draft.attachments.isEmpty()) }
        compose.onNodeWithText("Attach image").performClick()
        val navigationSelection = host.selection
        compose.onNodeWithText("Switch media room").performClick()
        compose.onNodeWithText("Switch media room").performClick()
        compose.runOnIdle { host.resolveSelection(choose = true, request = navigationSelection); assertTrue(host.state.draft.attachments.isEmpty()) }
        compose.onNodeWithTag("composer").assertTextContains("Retained")
        compose.onNodeWithText("Attach image").performClick()
        compose.onNodeWithText("Choose bundled image").performClick()
        compose.onNodeWithText("Send").performClick()
        compose.onNodeWithText("Accept image draft").performClick()
        val oldUpload = host.upload
        compose.onNodeWithText("Switch media room").performClick()
        compose.runOnIdle { host.completeUpload(true, oldUpload); assertTrue(host.state.messages.isEmpty()) }
        compose.onNodeWithText("Switch media room").performClick()
        compose.runOnIdle { host.completeUpload(true, oldUpload); assertEquals(Delivery.Failed, host.state.messages.single().delivery) }
        compose.onNodeWithText("Couldn't upload image").assertExists()
    }

}
