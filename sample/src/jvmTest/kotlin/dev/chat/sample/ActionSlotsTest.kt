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
import kotlin.test.assertTrue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.toPixelMap

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
            Box(
                Modifier.then(
                Modifier.size(360.dp, 720.dp))) {
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

    @Test fun focusedBubbleMovesContinuouslyWithoutLeavingAnotherVisibleBubble() {
        val target = Message("m", Participant("me", "You"), 1, "now", "Original", Delivery.Sent)
        val state = mutableStateOf(ActionState(capabilities = mapOf("m" to setOf(MessageAction.Copy))))
        compose.setContent {
            val actions = rememberMessageActions(ConversationState("room", "me", messages = listOf(target)), state.value, {
                state.value = state.value.copy(revision = state.value.revision + 1,
                    menuTargetId = if (it.command == ActionCommand.OpenMenu) "m" else null)
            }, {})
            Box(Modifier.size(360.dp, 720.dp).background(Color.White)) {
                Box(Modifier.offset(x = 24.dp, y = 580.dp)) {
                    MessageActionFocus(actions, "m",
                        above = { Box(Modifier.size(160.dp, 60.dp)) },
                        below = { if (state.value.menuTargetId != null) Box(Modifier.size(200.dp, 300.dp)) },
                    ) { interaction ->
                        androidx.compose.material3.MaterialTheme(colorScheme = androidx.compose.material3.lightColorScheme(
                            primary = Color.Magenta, onPrimary = Color.Black)) {
                            dev.forntoh.chat.material3.MessageBubble(target, true, null,
                                dev.forntoh.chat.ui.LocalConversationSlots.current.copy(messageBody = {
                                    Box(Modifier.size(80.dp, 20.dp).background(Color.Cyan)) { BasicText(it.text) }
                                }), interaction)
                        }
                    }
                }
            }
        }
        fun magentaRows(root: SemanticsNodeInteraction): List<Int> {
            val pixels = root.captureToImage().toPixelMap()
            return (0 until pixels.height).filter { y -> (0 until pixels.width).any { x ->
                val c = pixels[x, y]; c.red > .95f && c.blue > .95f && c.green < .05f
            } }
        }
        val originalTop = magentaRows(compose.onAllNodes(isRoot()).onFirst()).first()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { state.value = state.value.copy(revision = 1, menuTargetId = "m") }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        val startTop = magentaRows(compose.onAllNodes(isRoot()).onLast()).first()
        assertTrue(kotlin.math.abs(startTop - originalTop) <= 2, "Lift must start at the actual bubble")
        compose.mainClock.advanceTimeBy(80)
        val middleTop = magentaRows(compose.onAllNodes(isRoot()).onLast()).first()
        compose.mainClock.advanceTimeBy(160)
        val endRows = magentaRows(compose.onAllNodes(isRoot()).onLast())
        val endTop = endRows.first()
        assertTrue(endRows.last() - endTop < 80, "Timeline must not leave a second bubble behind")
        val focused = compose.onAllNodes(isRoot()).onLast().captureToImage().toPixelMap()
        assertTrue((endTop until endRows.last()).any { y -> (0 until focused.width).any { x ->
            val c = focused[x, y]; c.red < .05f && c.green > .95f && c.blue > .95f
        } }, "The actual body must travel with the background")
        assertTrue((originalTop until minOf(originalTop + 60, focused.height)).none { y -> (0 until focused.width).any { x ->
            val c = focused[x, y]; c.red < .05f && c.green in .7f.. .85f && c.blue in .7f.. .85f
        } }, "The body must not remain behind under the scrim")
        assertTrue(endTop < middleTop && middleTop < startTop, "Bubble must travel through intermediate positions to make action space")
        compose.runOnIdle { state.value = state.value.copy(revision = 2, menuTargetId = null) }
        compose.mainClock.advanceTimeBy(80)
        val returnTop = magentaRows(compose.onAllNodes(isRoot()).onLast()).first()
        assertTrue(returnTop > endTop && returnTop < originalTop, "Dismissal must animate back")
        compose.mainClock.advanceTimeBy(200)
        assertEquals(originalTop, magentaRows(compose.onAllNodes(isRoot()).onFirst()).first())
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
