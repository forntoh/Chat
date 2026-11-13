package dev.forntoh.chat.material3

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import dev.forntoh.chat.core.*
import dev.forntoh.chat.ui.*
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.toArgb
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/** Consumer seam: host-authored wording and renderer replacement retain submission guards. */
class ConversationSlotsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun defaultGroupsUseHostAvatarAndCompactTimeWithoutRoutineDeliveryText() {
        val a = Message("a", Participant("avery", "Avery"), 1, "09:41", "First", Delivery.Sent)
        val rows = listOf(a, a.copy(id = "b", text = "Second"),
            a.copy(id = "c", sender = Participant("me", "You"), text = "Third"))
        compose.setContent { MaterialTheme { JetchatTheme {
            Conversation(ConversationState("room", "me", messages = rows), {},
                slots = jetchatConversationSlots().copy(messageAvatar = { Text("Host avatar ${it.sender.id}") }))
        } } }
        compose.onAllNodesWithText("Host avatar avery", useUnmergedTree = true).assertCountEquals(1)
        compose.onAllNodesWithText("09:41", useUnmergedTree = true).assertCountEquals(3)
        compose.onAllNodesWithText("Sent", useUnmergedTree = true).assertCountEquals(0)
        compose.onNodeWithContentDescription("Avery, Second, 09:41, Sent").assertExists()
    }

    @Test fun referenceLayoutUsesGroupEndPeerAvatarAndOppositeMessageEdges() {
        val peer = Participant("peer", "Avery")
        val rows = listOf(
            Message("first", peer, 1, "09:37", "Hi, buddy!", Delivery.Sent),
            Message("last", peer, 2, "09:38", "What will you do tonight?", Delivery.Sent),
            Message("reply", Participant("me", "You"), 3, "09:41", "Hello", Delivery.Sent),
        )
        compose.setContent { Box(Modifier.size(360.dp, 640.dp)) { MaterialTheme { JetchatTheme {
            Conversation(ConversationState("room", "me", messages = rows), {},
                slots = jetchatConversationSlots().copy(messageAvatar = { Text("Avatar ${it.id}") }))
        } } } }
        compose.onNodeWithText("Avatar first", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Avatar last", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Avatar reply", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("09:38", useUnmergedTree = true).assertExists()
        val incoming = compose.onNodeWithText("Hi, buddy!", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val outgoing = compose.onNodeWithText("Hello", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        kotlin.test.assertTrue(outgoing.left > incoming.right, "Short outgoing reply sits on the opposite edge")
    }

    @Test fun defaultComposerSendsExactMultilineTextAndKeepsPendingInput() {
        val text = "  Hello 🌍\nSecond line  "
        val snapshot = mutableStateOf(ConversationState("room", "me"))
        val events = mutableListOf<ConversationEvent>()
        compose.setContent { Box(Modifier.size(360.dp, 640.dp)) { MaterialTheme { JetchatTheme {
            Conversation(snapshot.value, { event ->
                events += event
                if (event is ConversationEvent.DraftChanged) snapshot.value = snapshot.value.copy(
                    draft = Draft(event.text, snapshot.value.draft.revision + 1))
            })
        } } } }
        compose.onNodeWithContentDescription("Send").assertIsNotEnabled()
        captureComposer("idle")
        compose.onNodeWithTag("composer").performTextInput(text)
        compose.onNodeWithContentDescription("Send").assertIsEnabled().performClick()
        compose.onNodeWithContentDescription("Send").assertIsNotEnabled().performClick()
        compose.onNodeWithTag("composer").assertTextContains(text)
        captureComposer("pending")
        compose.runOnIdle {
            assertEquals(listOf(ConversationEvent.SendDraft("room", snapshot.value.draft.revision, text)),
                events.filterIsInstance<ConversationEvent.SendDraft>())
        }
    }

    private fun captureComposer(name: String) {
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val image = BufferedImage(pixels.width, pixels.height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) image.setRGB(x, y, pixels[x, y].toArgb())
        val directory = File("build/pat254-evidence").apply { mkdirs() }
        ImageIO.write(image, "png", File(directory, "composer-$name.png"))
    }

    @Test fun slotWordingAndNeutralToJetchatThemeChangesRetainPendingDraft() {
        val snapshot = mutableStateOf(ConversationState("room", "me", Draft("Retained text", 7)))
        val alternate = mutableStateOf(false)
        val events = mutableListOf<ConversationEvent>()
        compose.setContent {
            MaterialTheme {
                val base = if (alternate.value) jetchatConversationSlots() else ConversationSlots()
                ConversationTheme(slots = base.copy(composerSend = { content ->
                    Button(onClick = content.onSend, enabled = content.canSend) {
                        Text(if (alternate.value) "Submit" else "Post")
                    }
                })) { Conversation(snapshot.value, events::add) }
            }
        }
        compose.onNodeWithText("Post").performClick()
        compose.runOnIdle { alternate.value = true }
        compose.onNodeWithText("Submit").assertIsNotEnabled().performClick()
        compose.onNodeWithTag("composer").assertTextContains("Retained text")
        compose.runOnIdle {
            assertEquals<List<ConversationEvent>>(listOf(ConversationEvent.SendDraft("room", 7, "Retained text")), events)
            snapshot.value = snapshot.value.copy(draft = Draft("Retained text", 8))
        }
        compose.onNodeWithText("Submit").assertIsEnabled()
    }
    @Test fun explicitBundleAndDirectComposerOverrideAmbientDefaultsWithoutResettingSendLatch() {
        val snapshot = ConversationState("room", "me", Draft("Keep", 2))
        val direct = mutableStateOf(false)
        val events = mutableListOf<ConversationEvent>()
        compose.setContent { MaterialTheme {
            ConversationTheme(slots = ConversationSlots(composerSend = { Text("Ambient") })) {
                Conversation(snapshot, events::add,
                    slots = LocalConversationSlots.current.copy(composerSend = { content ->
                        Button(onClick = content.onSend, enabled = content.canSend) { Text("Bundle") }
                    }),
                    composerContent = if (direct.value) { content ->
                        Button(onClick = content.onSend, enabled = content.canSend) { Text("Direct") }
                    } else null,
                )
            }
        } }
        compose.onNodeWithText("Ambient").assertDoesNotExist()
        compose.onNodeWithText("Bundle").performClick()
        compose.runOnIdle { direct.value = true }
        compose.onNodeWithText("Bundle").assertDoesNotExist()
        compose.onNodeWithText("Direct").assertIsNotEnabled().performClick()
        compose.runOnIdle { assertEquals<List<ConversationEvent>>(
            listOf(ConversationEvent.SendDraft("room", 2, "Keep")), events) }
    }

}
