package dev.chat.sample

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.forntoh.chat.actions.*
import com.forntoh.chat.core.*
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class ActionSlotsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun themeSlotsReachNestedSurfacesAndExplicitSlotsOverrideThemWithoutLosingGuards() {
        val target = Message("m", Participant("me", "You"), 1, "now", "Original", Delivery.Sent)
        val state = mutableStateOf(ActionState(capabilities = mapOf("m" to setOf(MessageAction.Copy)), menuTargetId = "m", quoteTargetId = "missing", deleting = target, error = "Refused"))
        val events = mutableListOf<ActionRequest>()
        val style = ActionStyle(textStyle = TextStyle(color = Color.Magenta))
        compose.setContent {
            val actions = rememberMessageActions(ConversationState("room", "me", messages = listOf(target)), state.value, events::add, {})
            ActionTheme(style = style, slots = ActionSlots(
                control = { button -> ActionControl(button) { BasicText("Host "+button.command.name) } },
                status = { BasicText("Host "+(it.error ?: it.kind.name), style = LocalActionStyle.current.textStyle) },
                quote = { BasicText("Quote "+(it.message?.text ?: "Message unavailable")) },
                menu = { Column { BasicText("Menu slot"); it() } },
                composerStatus = { Column { BasicText("Status slot"); it() } },
                confirmation = { Column { BasicText("Confirmation slot"); it() } },
            )) {
                assertEquals(style, LocalActionStyle.current)
                Column {
                    MessageActions(actions, "m")
                    ActionComposerStatus(actions)
                    QuotedMessage(actions, "missing", slots = ActionSlots(quote = { BasicText("Explicit "+(it.message?.text ?: "Message unavailable")) }))
                }
            }
        }
        compose.onNodeWithText("Menu slot").assertExists()
        compose.onNodeWithText("Status slot").assertExists()
        compose.onNodeWithText("Confirmation slot").assertExists()
        compose.onNodeWithText("Host Refused").assertExists()
        compose.onNodeWithText("Quote Message unavailable").assertExists()
        compose.onNodeWithText("Explicit Message unavailable").assertExists()
        compose.onNodeWithText("Host ConfirmDelete").assertIsNotEnabled()
        compose.onNodeWithText("Host Copy").performClick().performClick()
        compose.runOnIdle { assertEquals(listOf(ActionRequest("room", 0, ActionCommand.Copy, "m", "Original")), events) }
        compose.runOnIdle { state.value = state.value.copy(revision = 1, pending = true) }
        compose.onNodeWithText("Host Copy").assertIsNotEnabled()
        compose.onNodeWithText("Host Pending").assertExists()
    }
}
