package dev.chat.sample

import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
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
        val host = FakeHost().apply { useChatFixture() }
        val width = androidx.compose.runtime.mutableStateOf(360.dp)
        compose.setContent {
            Box(Modifier.size(width.value, 720.dp)) { SampleApp(host, manualService = true) }
        }
        capture("default-conversation")
        compose.runOnIdle { width.value = 1000.dp }
        capture("desktop-default-conversation")
        compose.runOnIdle { width.value = 360.dp }
        compose.onNodeWithTag("composer").performTextInput("First line\nSecond line\nThird line\nFourth line\nFifth line\nSixth line")
        compose.onNodeWithContentDescription("Send").assertIsDisplayed().assertIsEnabled()
        capture("default-multiline")
        compose.onNodeWithTag("demo-menu").performClick()
        compose.onNodeWithText("Custom UI").performClick()
        capture("custom-multiline")
        compose.runOnIdle { host.onEvent(ConversationEvent.DraftChanged(host.state.conversationId, "")) }
        capture("custom-conversation")
        compose.onNodeWithTag("demo-menu").performClick()
        compose.onNodeWithText("Material3 UI").performClick()
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
        val host = FakeHost()
        compose.setContent { Box(Modifier.size(360.dp, 720.dp)) { SampleApp(host, manualService = true) } }
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
        for (y in 0 until pressed.height) for (x in 0 until pressed.width) {
            if (pressed[x, y].toArgb() != beforePress[x, y].toArgb()) {
                kotlin.test.assertTrue(x >= bubble.left && x < bubble.right && y >= bubble.top && y < bubble.bottom,
                    "Touch feedback must not draw outside the bubble")
                changedInside++
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
        compose.onNodeWithTag("demo-menu").performClick()
        compose.onNodeWithText("Custom UI").performClick()
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
