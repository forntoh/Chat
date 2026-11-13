package dev.chat.sample

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import dev.chat.core.*
import dev.chat.actions.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.toArgb
import java.awt.image.BufferedImage
import java.awt.RenderingHints
import java.io.File
import javax.imageio.ImageIO

class ActionsJourneyTest {
    @get:Rule val compose = createComposeRule()

    private fun capture(name: String) {
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val raw = BufferedImage(pixels.width, pixels.height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) raw.setRGB(x, y, pixels[x, y].toArgb())
        val scaled = BufferedImage(320, (pixels.height * 320.0 / pixels.width).toInt(), BufferedImage.TYPE_INT_ARGB)
        scaled.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            drawImage(raw, 0, 0, scaled.width, scaled.height, null)
            dispose()
        }
        val directory = File("build/pat244-evidence").apply { mkdirs() }
        ImageIO.write(scaled, "png", File(directory, "$name.png"))
    }

    @Test fun quoteCancelRetainsDraftAndAcceptedReplyCarriesTarget() {
        val host = FakeHost()
        compose.setContent { Box(Modifier.width(360.dp).height(800.dp)) { SampleApp(host) } }
        compose.onNodeWithTag("composer").performTextInput("My reply")
        compose.onNodeWithText("Actions").performClick()
        capture("01-copy-reply-menu")
        compose.onNodeWithText("Copy").performClick()
        compose.runOnIdle { assertEquals(host.state.messages.first().text, host.copiedText) }
        compose.onNodeWithText("Actions").performClick()
        compose.onNodeWithText("Reply").performClick()
        capture("02-quoted-draft")
        compose.onNodeWithText("Host theme").performClick()
        compose.onNodeWithTag("composer").assertTextContains("My reply")
        capture("05-host-theme-quoted-draft")
        compose.onNodeWithText("Jetchat theme").performClick()
        compose.onNodeWithText("Cancel reply").performClick()
        compose.onNodeWithTag("composer").assertTextContains("My reply")
        compose.onNodeWithText("Actions").performClick()
        compose.onNodeWithText("Reply").performClick()
        compose.onNodeWithText("Send").performClick()
        compose.onNodeWithText("Send").assertIsNotEnabled()
        compose.runOnIdle { assertEquals("welcome", host.pendingQuoteTarget) }
        compose.onNodeWithText("Accept").performClick()
        compose.runOnIdle { assertEquals("welcome", host.actions.quotes[host.state.messages.last().id]) }
        compose.onNodeWithTag("composer").assertTextContains("")
    }
    @Test fun editCancelIsLosslessAndFailureDoesNotChangeAuthoritativeMessage() {
        val host = FakeHost()
        host.onEvent(ConversationEvent.DraftChanged("studio", "Original message"))
        host.onEvent(ConversationEvent.SendDraft("studio", 0, "Original message"))
        host.accept()
        host.completeDelivery(Delivery.Sent)
        host.onEvent(ConversationEvent.DraftChanged("studio", "Unsent draft"))
        compose.setContent { Box(Modifier.width(360.dp).height(800.dp)) { SampleApp(host) } }
        compose.onAllNodesWithText("Actions").onFirst().performClick()
        compose.onNodeWithText("Reply").performClick()
        compose.onAllNodesWithText("Actions").onLast().performScrollTo().performClick()
        compose.onNodeWithText("Edit").performScrollTo().performClick()
        compose.onNodeWithTag("composer").assertTextContains("Original message")
        compose.onNodeWithText("Cancel edit").performClick()
        compose.onNodeWithTag("composer").assertTextContains("Unsent draft")
        compose.onNodeWithText("Cancel reply").assertExists()
        compose.onAllNodesWithText("Actions").onLast().performScrollTo().performClick()
        compose.onNodeWithText("Edit").performScrollTo().performClick()
        compose.onNodeWithTag("composer").performTextReplacement("Revised message")
        compose.onNodeWithText("Save edit").performClick()
        compose.onNodeWithText("Save edit").assertIsNotEnabled().performClick()
        compose.runOnIdle { assertEquals("Original message", host.state.messages.last().text) }
        compose.onNodeWithText("Fail action").performClick()
        compose.onNodeWithText("Couldn't change message. Try again or cancel.").assertExists()
        capture("03-edit-failure")
        compose.onNodeWithText("Save edit").performClick()
        compose.onNodeWithText("Complete action").performClick()
        compose.runOnIdle { assertEquals("Revised message", host.state.messages.last().text) }
        compose.onNodeWithTag("composer").assertTextContains("Unsent draft")
        compose.onNodeWithText("Cancel reply").assertExists()
    }
    @Test fun deleteRequiresConfirmationAndRevalidatesCapabilityAndTarget() {
        val host = FakeHost()
        host.onEvent(ConversationEvent.DraftChanged("studio", "Disposable"))
        host.onEvent(ConversationEvent.SendDraft("studio", 0, "Disposable"))
        host.accept()
        host.completeDelivery(Delivery.Sent)
        compose.setContent { Box(Modifier.width(360.dp).height(800.dp)) { SampleApp(host) } }
        compose.onAllNodesWithText("Actions").onFirst().performClick()
        compose.onNodeWithText("Edit").assertDoesNotExist()
        compose.onNodeWithText("Delete").assertDoesNotExist()
        compose.onNodeWithText("Close actions").performClick()
        compose.onAllNodesWithText("Actions").onLast().performScrollTo().performClick()
        compose.onNodeWithText("Delete").performClick()
        compose.runOnIdle { assertEquals(null, host.pendingAction) }
        capture("04-delete-confirmation")
        compose.onNodeWithText("Cancel delete").performClick()
        compose.runOnIdle { assertEquals(2, host.state.messages.size) }
        compose.onAllNodesWithText("Actions").onLast().performScrollTo().performClick()
        compose.onNodeWithText("Delete").performClick()
        compose.runOnIdle { host.revokeActions("studio-1") }
        compose.onNodeWithText("Confirm delete").assertIsNotEnabled().performClick()
        compose.runOnIdle { assertEquals(null, host.pendingAction) }
        compose.onNodeWithText("Cancel delete").performClick()
        compose.runOnIdle { host.allowActions("studio-1") }
        compose.onAllNodesWithText("Actions").onLast().performScrollTo().performClick()
        compose.onNodeWithText("Delete").performClick()
        compose.onNodeWithText("Confirm delete").performClick()
        compose.runOnIdle { assertEquals(2, host.state.messages.size) }
        compose.onNodeWithText("Fail action").performClick()
        compose.runOnIdle { assertEquals(2, host.state.messages.size) }
        compose.onNodeWithText("Confirm delete").performClick()
        compose.onNodeWithText("Complete action").performClick()
        compose.runOnIdle { assertEquals(listOf("welcome"), host.state.messages.map { it.id }) }
    }

    @Test fun quotedImageSubmissionPreservesTheSharedDraftSnapshot() {
        val image = Attachment("photo", "image/png", "host-bundle")
        val conversation = ConversationState("room", "me", Draft(attachments = listOf(image)))
        val requests = mutableListOf<ActionRequest>()
        compose.setContent {
            val actions = rememberMessageActions(conversation, ActionState(quoteTargetId = "quote"), requests::add, {})
            Box(Modifier.size(360.dp, 600.dp)) { dev.chat.ui.Conversation(conversation, actions::onConversationEvent) }
        }
        compose.onNodeWithText("Send").performClick()
        compose.runOnIdle {
            assertEquals(ActionCommand.SendQuote, requests.single().command)
            assertEquals(listOf(image), requests.single().attachments)
        }
    }

}
