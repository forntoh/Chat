package dev.chat.sample

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.bottom
import androidx.compose.ui.test.cancel
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.center
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.height
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.top
import androidx.compose.ui.test.width
import androidx.compose.ui.unit.dp
import dev.forntoh.chat.actions.ActionCommand
import dev.forntoh.chat.actions.ActionRequest
import dev.forntoh.chat.actions.ActionState
import dev.forntoh.chat.actions.rememberMessageActions
import dev.forntoh.chat.core.Attachment
import dev.forntoh.chat.core.ConversationEvent
import dev.forntoh.chat.core.ConversationState
import dev.forntoh.chat.core.Delivery
import dev.forntoh.chat.core.Draft
import dev.forntoh.chat.core.Message
import org.junit.Rule
import org.junit.Test
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.assertEquals

class ActionsJourneyTest {
    private fun demo(host: ContractFixture, label: String) = compose.runOnIdle {
        when (label) {
            "Host theme" -> host.alternateTheme = true
            "Default chat theme" -> host.alternateTheme = false
            "Custom UI" -> host.custom = true
            "Accept" -> host.accept()
            "Fail action" -> host.completeAction(false)
            "Complete action" -> host.completeAction(true)
            else -> error("Unknown fixture operation: $label")
        }
    }

    @get:Rule val compose = createComposeRule()

    private fun capture(name: String) {
        val pixels = compose.onAllNodes(isRoot()).onLast().captureToImage().toPixelMap()
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

    @Test fun replyFocusesComposerSendRetainsFocusAndOutsideTapClearsIt() {
        val host = ContractFixture()
        compose.setContent { Box(Modifier.size(360.dp, 800.dp)) { ActionContractHarness(host) } }
        val field = compose.onNodeWithTag("composer")
        compose.onNodeWithTag("bubble-welcome", useUnmergedTree = true).performTouchInput { longClick() }
        compose.onNodeWithText("Reply").performClick()
        field.assertIsFocused()
        field.performTextInput("Focus survives send")
        compose.onNodeWithContentDescription("Send").performClick()
        field.assertIsFocused()
        field.performTextInput("ignored while pending")
        compose.runOnIdle { assertEquals("Focus survives send", host.state.draft.text) }
        compose.onNodeWithTag("timeline").performTouchInput { click(androidx.compose.ui.geometry.Offset(4f, 250f)) }
        field.assertIsNotFocused()
    }

    @Test fun ordinaryTapDoesNotOpenActionsButHoldDoesAndDismissesWithoutChangingDraft() {
        val host = ContractFixture()
        compose.setContent { Box(Modifier.size(360.dp, 800.dp)) { ActionContractHarness(host) } }
        compose.onNodeWithTag("composer").performTextInput("Keep this draft")
        val bubble = compose.onNodeWithTag("bubble-welcome", useUnmergedTree = true)
        bubble.performClick()
        compose.runOnIdle { assertEquals(null, host.actions.menuTargetId) }
        bubble.performTouchInput { longClick() }
        compose.onNodeWithText("Copy").assertExists()
        compose.onNodeWithText("Copy").performKeyInput { pressKey(androidx.compose.ui.input.key.Key.Escape) }
        compose.runOnIdle { assertEquals(null, host.actions.menuTargetId) }
        bubble.performTouchInput { longClick() }
        compose.onNodeWithText("Close actions").performClick()
        compose.runOnIdle { assertEquals(null, host.actions.menuTargetId) }
        compose.onNodeWithTag("composer").assertTextContains("Keep this draft")
    }

    @Test fun editSelectionAndCancellationReturnFocusToTheRetainedComposer() {
        val host = ContractFixture().apply {
            onEvent(ConversationEvent.DraftChanged("studio", "Original message"))
            onEvent(ConversationEvent.SendDraft("studio", 0, "Original message"))
            accept()
            completeDelivery(Delivery.Sent)
        }
        compose.setContent { Box(Modifier.size(360.dp, 800.dp)) { ActionContractHarness(host) } }
        val field = compose.onNodeWithTag("composer")
        field.performTextInput("Keep this draft")
        compose.onNodeWithTag("bubble-studio-1", useUnmergedTree = true).performScrollTo().performTouchInput { longClick() }
        compose.onNodeWithText("Edit").performScrollTo().performClick()
        field.assertIsFocused()
        field.performTextReplacement("Unsent edit")
        compose.onNodeWithContentDescription("Cancel edit").performClick()
        field.assertIsFocused().assertTextContains("Keep this draft")
        compose.runOnIdle { assertEquals("Original message", host.state.messages.last().text) }
    }

    @Test fun revokedMenuStaysDismissedWhenCapabilitiesReturn() {
        val host = ContractFixture()
        compose.setContent { Box(Modifier.size(360.dp, 800.dp)) { ActionContractHarness(host) } }
        compose.onNodeWithTag("composer").performTextInput("Unrelated draft")
        compose.onNodeWithTag("bubble-welcome", useUnmergedTree = true).performTouchInput { longClick() }
        compose.onNodeWithText("Copy").assertExists()
        compose.runOnIdle { host.revokeActions("welcome") }
        compose.onNodeWithText("Copy").assertDoesNotExist()
        compose.runOnIdle { host.allowActions("welcome") }
        compose.onNodeWithText("Copy").assertDoesNotExist()
        compose.onNodeWithTag("composer").assertTextContains("Unrelated draft")
        compose.runOnIdle { assertEquals(null, host.actions.menuTargetId) }
    }

    @Test fun quoteCancelRetainsDraftAndAcceptedReplyCarriesTarget() {
        val host = ContractFixture()
        compose.setContent { Box(Modifier.width(360.dp).height(800.dp)) { ActionContractHarness(host) } }
        compose.onNodeWithTag("composer").performTextInput("My reply")
        compose.onNodeWithTag("bubble-${host.state.messages.first().id}", useUnmergedTree = true).performTouchInput { longClick() }
        capture("01-copy-reply-menu")
        compose.onNodeWithText("Copy").performClick()
        compose.runOnIdle { assertEquals(host.state.messages.first().text, host.copiedText) }
        compose.onNodeWithTag("bubble-${host.state.messages.first().id}", useUnmergedTree = true).performTouchInput { longClick() }
        compose.onNodeWithText("Reply").performClick()
        capture("02-quoted-draft")
        demo(host, "Host theme")
        compose.onNodeWithTag("composer").assertTextContains("My reply")
        capture("05-host-theme-quoted-draft")
        demo(host, "Default chat theme")
        compose.onNodeWithContentDescription("Cancel reply").performClick()
        compose.onNodeWithTag("composer").assertTextContains("My reply")
        compose.onNodeWithTag("bubble-${host.state.messages.first().id}", useUnmergedTree = true).performTouchInput { longClick() }
        compose.onNodeWithText("Reply").performClick()
        compose.onNodeWithContentDescription("Send").performClick()
        compose.onNodeWithContentDescription("Send").assertIsNotEnabled()
        compose.runOnIdle { assertEquals("welcome", host.pendingQuoteTarget) }
        demo(host, "Accept")
        compose.runOnIdle { assertEquals("welcome", host.actions.quotes[host.state.messages.last().id]) }
        compose.onNodeWithTag("composer").assertTextContains("")
    }

    @Test fun replyContextKeepsDismissBesidePreviewInsideComposer() {
        val host = ContractFixture()
        host.onAction(ActionRequest(host.state.conversationId, host.actions.revision, ActionCommand.Quote, host.state.messages.first().id))
        compose.setContent { Box(Modifier.size(360.dp, 720.dp)) { ActionContractHarness(host) } }
        val quote = compose.onNodeWithContentDescription(
            "Reply to Avery",
            substring = true,
            useUnmergedTree = true,
        ).fetchSemanticsNode().boundsInRoot
        val cancel = compose.onNodeWithContentDescription("Cancel reply").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithText("Replying to").assertDoesNotExist()
        assertEquals(quote.center.y, cancel.center.y, 1f, "Dismiss must be vertically centered on the preview")
        val send = compose.onNodeWithContentDescription("Send").fetchSemanticsNode().boundsInRoot
        assertEquals(send.center.x, cancel.center.x, 1f, "Dismiss and Send must share the trailing alignment")
    }

    @Test fun embeddedReplyIsInsideBubbleAndTappingCentersItsTarget() {
        val host = ContractFixture()
        host.useHistoryFixture()
        val target = host.state.messages[20]
        host.onAction(ActionRequest(host.state.conversationId, host.actions.revision, ActionCommand.Quote, target.id))
        host.onEvent(ConversationEvent.DraftChanged(host.state.conversationId, "A reply with an embedded preview"))
        val draft = host.state.draft
        host.onAction(
            ActionRequest(
                host.state.conversationId,
                host.actions.revision,
                ActionCommand.SendQuote,
                target.id,
                draft.text,
                draft.revision,
            ),
        )
        host.accept()
        host.completeDelivery(Delivery.Sent)
        compose.setContent { Box(Modifier.size(360.dp, 800.dp)) { ActionContractHarness(host) } }
        val preview = compose.onNodeWithContentDescription("Reply to ${target.sender.displayName}", useUnmergedTree = true)
        val previewBounds = preview.fetchSemanticsNode().boundsInRoot
        val bubbleBounds = compose.onNodeWithTag(
            "bubble-${host.state.messages.last().id}",
            useUnmergedTree = true,
        ).fetchSemanticsNode().boundsInRoot
        kotlin.test.assertTrue(previewBounds.top >= bubbleBounds.top && previewBounds.bottom < bubbleBounds.bottom)
        kotlin.test.assertTrue(previewBounds.width >= bubbleBounds.width * 0.75f, "The inset preview should span its parent bubble content")
        capture("reply-embedded")
        preview.performClick()
        val row = compose.onNodeWithTag("message-${target.id}").fetchSemanticsNode().boundsInRoot
        val composer = compose.onNodeWithTag("composer-area").fetchSemanticsNode().boundsInRoot
        val header = compose.onNodeWithTag("conversation-header").fetchSemanticsNode().boundsInRoot
        assertEquals((header.bottom + composer.top) / 2f, row.center.y, 2f)
        capture("reply-target-centered")
        demo(host, "Custom UI")
        capture("reply-custom-target-centered")
        compose.onNodeWithContentDescription("Latest").performClick()
        capture("reply-custom-embedded")
        compose.runOnIdle { host.removeMessage(target.id) }
        compose.onNodeWithContentDescription("Message unavailable", useUnmergedTree = true).assert(hasClickAction().not())
    }

    @Test fun shortConversationKeepsFollowingAfterReplyNavigationAndMultilineInput() {
        val host = ContractFixture().apply { useChatFixture() }
        host.onAction(ActionRequest(host.state.conversationId, host.actions.revision, ActionCommand.Quote, "morgan"))
        host.onEvent(ConversationEvent.DraftChanged(host.state.conversationId, "A reply with an embedded preview"))
        host.onAction(
            ActionRequest(
                host.state.conversationId,
                host.actions.revision,
                ActionCommand.SendQuote,
                "morgan",
                host.state.draft.text,
                host.state.draft.revision,
            ),
        )
        host.accept()
        host.completeDelivery(Delivery.Sent)
        host.receiveMessage()
        compose.setContent { Box(Modifier.size(402.dp, 748.dp)) { ActionContractHarness(host) } }
        compose.onNode(
            hasContentDescription("Reply to Morgan Lee") and hasAnyAncestor(hasTestTag("message-studio-1")),
            useUnmergedTree = true,
        ).performClick()
        if (compose.onAllNodesWithContentDescription("Latest", substring = true).fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithContentDescription("Latest", substring = true).performClick()
        }
        repeat(3) { n ->
            compose.onNodeWithTag("composer").performTextInput("Following message $n. More conversation content for the scrolling journey.")
            compose.onNodeWithContentDescription("Send").performClick()
            compose.runOnIdle { host.accept() }
            compose.waitForIdle()
            compose.runOnIdle { host.completeDelivery(Delivery.Sent) }
            compose.waitForIdle()
            compose.runOnIdle { host.receiveMessage() }
            val bounds = compose.onNodeWithTag("message-${host.state.messages.last().id}").fetchSemanticsNode().boundsInRoot
            val viewport = compose.onNodeWithTag("timeline").fetchSemanticsNode().boundsInRoot
            kotlin.test.assertTrue(bounds.bottom <= viewport.bottom, "Arrivals must remain fully in view after a fitting reply navigation")
        }
    }

    @Test fun editCancelIsLosslessAndFailureDoesNotChangeAuthoritativeMessage() {
        val host = ContractFixture()
        host.onEvent(ConversationEvent.DraftChanged("studio", "Original message"))
        host.onEvent(ConversationEvent.SendDraft("studio", 0, "Original message"))
        host.accept()
        host.completeDelivery(Delivery.Sent)
        host.onEvent(ConversationEvent.DraftChanged("studio", "Unsent draft"))
        compose.setContent { Box(Modifier.width(360.dp).height(800.dp)) { ActionContractHarness(host) } }
        compose.onNodeWithTag("bubble-${host.state.messages.first().id}", useUnmergedTree = true).performTouchInput { longClick() }
        compose.onNodeWithText("Reply").performClick()
        compose.onNodeWithTag("bubble-${host.state.messages.last().id}", useUnmergedTree = true).performScrollTo().performTouchInput {
            longClick()
        }
        compose.onNodeWithText("Edit").performScrollTo().performClick()
        compose.onNodeWithTag("composer").assertTextContains("Original message")
        compose.onNodeWithContentDescription("Cancel edit").performClick()
        compose.onNodeWithTag("composer").assertTextContains("Unsent draft")
        compose.onNodeWithContentDescription("Cancel reply").assertExists()
        compose.onNodeWithTag("bubble-${host.state.messages.last().id}", useUnmergedTree = true).performScrollTo().performTouchInput {
            longClick()
        }
        compose.onNodeWithText("Edit").performScrollTo().performClick()
        compose.onNodeWithTag("composer").performTextReplacement("Revised message")
        compose.onNodeWithText("Save edit").performClick()
        compose.onNodeWithText("Save edit").assertIsNotEnabled().performClick()
        compose.runOnIdle { assertEquals("Original message", host.state.messages.last().text) }
        demo(host, "Fail action")
        compose.onNodeWithText("Couldn't change message. Try again or cancel.").assertExists()
        capture("03-edit-failure")
        compose.onNodeWithText("Save edit").performClick()
        demo(host, "Complete action")
        compose.runOnIdle { assertEquals("Revised message", host.state.messages.last().text) }
        compose.onNodeWithTag("composer").assertTextContains("Unsent draft")
        compose.onNodeWithContentDescription("Cancel reply").assertExists()
    }

    @Test fun deleteRequiresConfirmationAndRevalidatesCapabilityAndTarget() {
        val host = ContractFixture()
        host.onEvent(ConversationEvent.DraftChanged("studio", "Disposable"))
        host.onEvent(ConversationEvent.SendDraft("studio", 0, "Disposable"))
        host.accept()
        host.completeDelivery(Delivery.Sent)
        compose.setContent { Box(Modifier.width(360.dp).height(800.dp)) { ActionContractHarness(host) } }
        compose.onNodeWithTag("bubble-${host.state.messages.first().id}", useUnmergedTree = true).performTouchInput { longClick() }
        compose.onNodeWithText("Edit").assertDoesNotExist()
        compose.onNodeWithText("Delete").assertDoesNotExist()
        compose.onNodeWithText("Close actions").performClick()
        compose.onNodeWithTag("bubble-${host.state.messages.last().id}", useUnmergedTree = true).performScrollTo().performTouchInput {
            longClick()
        }
        compose.onNodeWithText("Delete").performClick()
        compose.runOnIdle { assertEquals(null, host.pendingAction) }
        capture("04-delete-confirmation")
        compose.onNodeWithText("Cancel delete").performClick()
        compose.runOnIdle { assertEquals(2, host.state.messages.size) }
        compose.onNodeWithTag("bubble-${host.state.messages.last().id}", useUnmergedTree = true).performScrollTo().performTouchInput {
            longClick()
        }
        compose.onNodeWithText("Delete").performClick()
        compose.runOnIdle { host.revokeActions("studio-1") }
        compose.onNodeWithText("Confirm delete").assertIsNotEnabled().performClick()
        compose.runOnIdle { assertEquals(null, host.pendingAction) }
        compose.onNodeWithText("Cancel delete").performClick()
        compose.runOnIdle { host.allowActions("studio-1") }
        compose.onNodeWithTag("bubble-${host.state.messages.last().id}", useUnmergedTree = true).performScrollTo().performTouchInput {
            longClick()
        }
        compose.onNodeWithText("Delete").performClick()
        compose.onNodeWithText("Confirm delete").performClick()
        compose.runOnIdle { assertEquals(2, host.state.messages.size) }
        demo(host, "Fail action")
        compose.runOnIdle { assertEquals(2, host.state.messages.size) }
        compose.onNodeWithText("Confirm delete").performClick()
        demo(host, "Complete action")
        compose.runOnIdle { assertEquals(listOf("welcome"), host.state.messages.map { it.id }) }
    }

    @Test fun quotedImageSubmissionPreservesTheSharedDraftSnapshot() {
        val image = Attachment("photo", "image/png", "host-bundle")
        val conversation = ConversationState("room", "me", Draft(attachments = listOf(image)))
        val requests = mutableListOf<ActionRequest>()
        compose.setContent {
            val actions = rememberMessageActions(conversation, ActionState(quoteTargetId = "quote"), requests::add, {})
            Box(Modifier.size(360.dp, 600.dp)) { dev.forntoh.chat.ui.Conversation(conversation, actions::onConversationEvent) }
        }
        compose.onNodeWithText("Send").performClick()
        compose.runOnIdle {
            assertEquals(ActionCommand.SendQuote, requests.single().command)
            assertEquals(listOf(image), requests.single().attachments)
        }
    }
}
