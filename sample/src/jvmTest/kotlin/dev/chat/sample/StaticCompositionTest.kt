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

    private fun capture(name: String) {
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val image = BufferedImage(pixels.width, pixels.height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) image.setRGB(x, y, pixels[x, y].toArgb())
        val directory = File("build/pat267-evidence").apply { mkdirs() }
        ImageIO.write(image, "png", File(directory, "$name.png"))
    }
}
