package dev.chat.sample

import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import dev.forntoh.chat.actions.*
import dev.forntoh.chat.core.*
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class ActionSlotsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun focusAllowsIndependentTopAndBottomContentAndRemovesChangedTargets() {
        val target = Message("m", Participant("me", "You"), 1, "now", "Original", Delivery.Sent)
        val conversation = mutableStateOf(ConversationState("room", "me", messages = listOf(target)))
        val state = mutableStateOf(ActionState(capabilities = mapOf("m" to setOf(MessageAction.Copy))))
        compose.setContent {
            val actions = rememberMessageActions(conversation.value, state.value, { request ->
                state.value = state.value.copy(revision = state.value.revision + 1,
                    menuTargetId = if (request.command == ActionCommand.OpenMenu) "m" else null)
            }, {})
            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.then(
                androidx.compose.ui.Modifier.size(360.dp, 720.dp))) {
                MessageActionFocus(actions, "m", motionEnabled = false,
                    above = { BasicText("Consumer content above") },
                    below = { Column { BasicText("Consumer content below"); MessageActionMenu(actions, "m") } },
                    bubble = { modifier -> BasicText("Original", modifier.padding(20.dp)) })
            }
        }
        compose.onNodeWithText("Original").performTouchInput { longClick() }
        compose.onNodeWithText("Consumer content above").assertIsDisplayed()
        compose.onNodeWithText("Consumer content below").assertIsDisplayed()
        compose.onNodeWithText("Copy").assertIsDisplayed()
        compose.runOnIdle { conversation.value = conversation.value.copy(messages = listOf(target.copy(text = "Changed"))) }
        compose.onNodeWithText("Consumer content above").assertDoesNotExist()
        compose.onNodeWithText("Copy").assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(revision = state.value.revision + 1, menuTargetId = null) }
        compose.onNodeWithText("Original").performTouchInput { longClick() }
        compose.onNodeWithText("Copy").assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(revision = state.value.revision + 1, capabilities = emptyMap()) }
        compose.onNodeWithText("Copy").assertDoesNotExist()
    }

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
