package dev.chat.consumer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import dev.chat.core.*
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

/** Uses staged artifacts only; no producer source, sample renderers or custom conversation slots. */
class DefaultArtifactTest {
    @get:Rule val compose = createComposeRule()

    @Test fun defaultArtifactGroupsAndComposerWorkWithoutConsumerRenderers() {
        val a = Message("a", Participant("avery", "Avery Chen"), 1, "09:41", "Morning! The sketches are ready for our review.", Delivery.Sent)
        val state = mutableStateOf(ConversationState("room", "me", messages = listOf(a,
            a.copy(id = "b", text = "I’ve kept the materials warm and simple."),
            a.copy(id = "c", sender = Participant("me", "You"), text = "Looks great. See you this afternoon!"))))
        val events = mutableListOf<ConversationEvent>()
        compose.setContent { Box(Modifier.size(360.dp, 640.dp)) { MaterialTheme { Surface {
            DefaultConversation(state.value) { event ->
                events += event
                if (event is ConversationEvent.DraftChanged) state.value = state.value.copy(draft = Draft(event.text, state.value.draft.revision + 1))
            }
        } } } }
        compose.onAllNodesWithText("Avery Chen · 09:41", useUnmergedTree = true).assertCountEquals(1)
        capture("default-artifact")
        compose.onNodeWithTag("composer").performTextInput("Exact text\nSecond line  ")
        compose.onNodeWithContentDescription("Send").performClick()
        compose.onNodeWithContentDescription("Send").assertIsNotEnabled().performClick()
        compose.runOnIdle { assertEquals("Exact text\nSecond line  ", events.filterIsInstance<ConversationEvent.SendDraft>().single().text) }
        capture("default-artifact-pending")
    }

    private fun capture(name: String) {
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val raw = BufferedImage(pixels.width, pixels.height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) raw.setRGB(x, y, pixels[x, y].toArgb())
        val directory = File("build/pat254-evidence").apply { mkdirs() }
        ImageIO.write(raw, "png", File(directory, "$name.png"))
    }
}
