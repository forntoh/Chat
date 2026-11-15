package dev.chat.sample

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.bottom
import androidx.compose.ui.test.cancel
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.center
import androidx.compose.ui.test.centerY
import androidx.compose.ui.test.click
import androidx.compose.ui.test.down
import androidx.compose.ui.test.height
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.left
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.right
import androidx.compose.ui.test.top
import androidx.compose.ui.test.width
import androidx.compose.ui.unit.dp
import dev.forntoh.chat.core.ConversationEvent
import org.junit.Rule
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

class StaticCompositionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun sampleDefaultAndCustomSlotsKeepMultilineDraftAndSendVisible() {
        val host = ContractFixture().apply { useChatFixture() }
        val width = androidx.compose.runtime.mutableStateOf(360.dp)
        compose.setContent {
            Box(Modifier.size(width.value, 720.dp)) { ActionContractHarness(host) }
        }
        capture("default-conversation")
        compose.runOnIdle { width.value = 1000.dp }
        capture("desktop-default-conversation")
        compose.runOnIdle { width.value = 360.dp }
        compose.onNodeWithTag("composer").performTextInput("First line\nSecond line\nThird line\nFourth line\nFifth line\nSixth line")
        compose.onNodeWithContentDescription("Send").assertIsDisplayed().assertIsEnabled()
        capture("default-multiline")
        compose.runOnIdle { host.custom = true }
        capture("custom-multiline")
        compose.runOnIdle { host.onEvent(ConversationEvent.DraftChanged(host.state.conversationId, "")) }
        capture("custom-conversation")
        compose.runOnIdle { host.custom = false }
        compose.onNodeWithTag("composer").performTextInput("OK")
        compose.onNodeWithContentDescription("Send").performClick()
        compose.runOnIdle { host.accept() }
        compose.onNodeWithTag("timeline").performScrollToIndex(host.state.messages.lastIndex)
        capture("default-short-sending")
        compose.runOnIdle { host.completeDelivery(dev.forntoh.chat.core.Delivery.Failed) }
        compose.onNodeWithTag("timeline").performScrollToIndex(host.state.messages.lastIndex)
        capture("default-short-failed")
        compose.onNodeWithTag("composer").performTextInput("unbroken".repeat(40))
        compose.onNodeWithContentDescription("Send").performClick()
        compose.runOnIdle { host.accept() }
        compose.onNodeWithTag("timeline").performScrollToIndex(host.state.messages.lastIndex)
        capture("default-long-content")
    }

    @Test fun messageActionsAndPressFeedbackAreConfinedToBubble() {
        val host = ContractFixture()
        compose.setContent { Box(Modifier.size(360.dp, 720.dp)) { ActionContractHarness(host) } }
        val message = host.state.messages.first()
        compose.onNodeWithTag("bubble-${message.id}").assertHasClickAction()
        compose.onNodeWithTag("message-${message.id}").performTouchInput {
            click(androidx.compose.ui.geometry.Offset(2f, centerY))
        }
        compose.runOnIdle { kotlin.test.assertEquals(null, host.actions.menuTargetId, "Row gutter must not activate actions") }
        compose.onNodeWithText(message.sender.displayName, useUnmergedTree = true).performTouchInput { click() }
        compose.runOnIdle { kotlin.test.assertEquals(null, host.actions.menuTargetId, "External sender must not activate actions") }
        compose.onNodeWithText(message.text, useUnmergedTree = true).performTouchInput { longClick() }
        compose.onNodeWithText("Reply").assertIsDisplayed()
        compose.onNodeWithText("Close actions").performClick()
        compose.mainClock.advanceTimeBy(300)
        val beforePress = compose.onAllNodes(isRoot()).onFirst().captureToImage().toPixelMap()
        val bubble = compose.onNodeWithTag("bubble-${message.id}", true).fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText(message.text, useUnmergedTree = true).performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(80)
        val pressed = compose.onAllNodes(isRoot()).onFirst().captureToImage().toPixelMap()
        var changedInside = 0
        for (y in 0 until pressed.height) {
            for (x in 0 until pressed.width) {
                if (pressed[x, y].toArgb() != beforePress[x, y].toArgb()) {
                    kotlin.test.assertTrue(
                        x >= bubble.left && x < bubble.right && y >= bubble.top && y < bubble.bottom,
                        "Touch feedback must not draw outside the bubble",
                    )
                    changedInside++
                }
            }
        }
        kotlin.test.assertTrue(changedInside > 0, "Bubble press must show feedback")
        capture("bubble-only-pressed")
        compose.onNodeWithText(message.text, useUnmergedTree = true).performTouchInput { cancel() }
        compose.mainClock.autoAdvance = true
        compose.onNodeWithText(message.text, useUnmergedTree = true).performTouchInput { longClick() }
        compose.onNodeWithText("Reply").assertIsDisplayed()
        capture("bubble-actions-menu")
        compose.onNodeWithText("Close actions").performClick()
        compose.runOnIdle { host.custom = true }
        compose.onNodeWithTag("custom-incoming", true).performTouchInput { longClick() }
        compose.onNodeWithText("Reply").assertIsDisplayed()
    }

    private fun capture(name: String) {
        val pixels = compose.onAllNodes(isRoot()).onFirst().captureToImage().toPixelMap()
        val image = BufferedImage(pixels.width, pixels.height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) image.setRGB(x, y, pixels[x, y].toArgb())
        val directory = File("build/pat267-evidence").apply { mkdirs() }
        ImageIO.write(image, "png", File(directory, "$name.png"))
    }
}
