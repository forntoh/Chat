package dev.chat.material3

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
import dev.chat.core.*
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

/** Consumer seam: host-authored wording and renderer replacement retain submission guards. */
class ConversationSlotsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun composerSlotOwnsItsWordingAndThemeReplacementRetainsPendingDraft() {
        val snapshot = mutableStateOf(ConversationState("room", "me", Draft("Retained text", 7)))
        val alternate = mutableStateOf(false)
        val events = mutableListOf<ConversationEvent>()
        compose.setContent {
            MaterialTheme {
                ConversationTheme(slots = ConversationSlots(composer = { content ->
                    Row {
                        BasicTextField(content.draft.text, content.onTextChange,
                            enabled = !content.pending, modifier = Modifier.testTag("host-draft"))
                        Button(onClick = content.onSend, enabled = content.canSend) {
                            Text(if (alternate.value) "Submit" else "Post")
                        }
                    }
                })) { Conversation(snapshot.value, events::add) }
            }
        }
        compose.onNodeWithText("Post").performClick()
        compose.runOnIdle { alternate.value = true }
        compose.onNodeWithText("Submit").assertIsNotEnabled().performClick()
        compose.onNodeWithTag("host-draft").assertTextEquals("Retained text")
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
