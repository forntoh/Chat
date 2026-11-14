package dev.chat.sample

import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import dev.forntoh.chat.actions.*
import dev.forntoh.chat.core.*
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.toPixelMap

class ActionSlotsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun defaultReplyDismissUsesIconAndPreservesDraft() {
        val host = FakeHost()
        host.onEvent(ConversationEvent.DraftChanged(host.state.conversationId, "Retain my draft"))
        host.onAction(ActionRequest(host.state.conversationId, host.actions.revision, ActionCommand.Quote, host.state.messages.first().id))
        compose.setContent {
            val actions = rememberMessageActions(host.state, host.actions, host::onAction, host::onEvent)
            Box(Modifier.width(360.dp)) { ActionComposerStatus(actions, ActionSlots()) }
        }
        compose.onNodeWithText("Cancel reply").assertDoesNotExist()
        compose.onNodeWithContentDescription("Cancel reply").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(null, host.actions.quoteTargetId)
            assertEquals("Retain my draft", host.state.draft.text)
        }
    }

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

    @Test fun outgoingFocusGrowsFromRightWithoutActionPadding() = focusKeepsSenderEdge(true)
    @Test fun incomingFocusGrowsFromLeftWithoutActionPadding() = focusKeepsSenderEdge(false)

    @Test fun bothActionSlotsKeepIncomingBubbleInPlace() = focusKeepsSenderEdge(false, true)
    @Test fun bothActionSlotsKeepOutgoingBubbleInPlace() = focusKeepsSenderEdge(true, true)

    @Test fun fullWidthIncomingFocusKeepsBothContentEdgesVisible() = fullWidthFocus(false)
    @Test fun fullWidthOutgoingFocusKeepsBothContentEdgesVisible() = fullWidthFocus(true)

    private fun fullWidthFocus(outgoing: Boolean) {
        val target = Message("m", Participant("me", "You"), 1, "now", "Original", Delivery.Sent)
        val state = mutableStateOf(ActionState(capabilities = mapOf("m" to setOf(MessageAction.Copy))))
        compose.setContent {
            val actions = rememberMessageActions(
                ConversationState("room", if (outgoing) "me" else "other", messages = listOf(target)),
                state.value, {}, {})
            Box(Modifier.fillMaxSize().background(Color.White)) {
                Box(Modifier.offset(y = 120.dp)) {
                    MessageActionFocus(actions, "m", below = { Box(Modifier.size(160.dp, 80.dp)) }) { interaction ->
                        Box(interaction.fillMaxWidth().height(40.dp).background(Color.Magenta)) {
                            Box(Modifier.align(androidx.compose.ui.Alignment.CenterStart).size(8.dp, 40.dp).background(Color.Red))
                            Box(Modifier.align(androidx.compose.ui.Alignment.CenterEnd).size(8.dp, 40.dp).background(Color.Cyan))
                        }
                    }
                }
            }
        }
        fun contentRow(): List<Color> {
            val pixels = compose.onAllNodes(isRoot()).onLast().captureToImage().toPixelMap()
            val y = with(compose.density) { 140.dp.roundToPx() }
            return (0 until pixels.width).map { pixels[it, y] }
        }
        val original = contentRow()
        assertEquals(Color.Red, original.first())
        assertEquals(Color.Cyan, original.last())
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { state.value = state.value.copy(revision = 1, menuTargetId = "m") }
        repeat(16) {
            compose.mainClock.advanceTimeByFrame()
            assertEquals(original, contentRow(), "Full-width content and both sender edges must remain visible during opening")
        }
        compose.runOnIdle { state.value = state.value.copy(revision = 2, menuTargetId = null) }
        repeat(16) {
            compose.mainClock.advanceTimeByFrame()
            assertEquals(original, contentRow(), "Full-width content must return continuously without clipping during closing")
        }
    }

    private fun focusKeepsSenderEdge(outgoing: Boolean, withTop: Boolean = false) {
        val target = Message("m", Participant("me", "You"), 1, "now", "Original", Delivery.Sent)
        val state = mutableStateOf(ActionState(capabilities = mapOf("m" to setOf(MessageAction.Copy))))
        compose.setContent {
            val actions = rememberMessageActions(ConversationState("room", if (outgoing) "me" else "other", messages = listOf(target)), state.value, {
                state.value = state.value.copy(revision = state.value.revision + 1,
                    menuTargetId = if (it.command == ActionCommand.OpenMenu) "m" else null)
            }, {})
            Box(Modifier.fillMaxSize().background(Color.White)) {
                Box(Modifier.align(if (outgoing) androidx.compose.ui.Alignment.TopEnd else androidx.compose.ui.Alignment.TopStart).padding(horizontal = 12.dp).offset(y = 120.dp)) {
                    MessageActionFocus(actions, "m", above = {
                        if (withTop) Box(Modifier.size(160.dp, 30.dp).background(Color.White).testTag("upper action"))
                    }, below = {
                        Box(Modifier.size(160.dp, 80.dp)
                            .shadow(16.dp, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                            .background(Color.White).testTag("lower action"))
                    }) { interaction ->
                        BasicText("Original", interaction.width(200.dp).height(40.dp).background(Color.Magenta))
                    }
                }
            }
        }
        fun coloredRows(): List<Int> {
            val pixels = compose.onAllNodes(isRoot()).onLast().captureToImage().toPixelMap()
            return (0 until pixels.height).filter { y ->
                val c = pixels[if (outgoing) pixels.width - 60 else 60, y]
                c.red > .95f && c.blue > .95f && c.green < .05f
            }
        }
        fun coloredColumns(): List<Int> {
            val pixels = compose.onAllNodes(isRoot()).onLast().captureToImage().toPixelMap()
            val y = with(compose.density) { 140.dp.roundToPx() }
            return (0 until pixels.width).filter { x ->
                val c = pixels[x, y]; c.red > .95f && c.blue > .95f && c.green < .05f
            }
        }
        val originalTop = coloredRows().first()
        val originalColumns = coloredColumns()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { state.value = state.value.copy(revision = 1, menuTargetId = "m") }
        repeat(16) {
            compose.mainClock.advanceTimeByFrame()
            assertTrue(kotlin.math.abs(coloredRows().first() - originalTop) <= 2,
                "Bottom-only actions that fit must not displace or flash the bubble")
            val columns = coloredColumns()
            assertTrue(columns.size >= originalColumns.size - 1, "Focusing must not narrow a wide custom bubble")
            assertTrue(kotlin.math.abs((if (outgoing) columns.last() else columns.first()) -
                (if (outgoing) originalColumns.last() else originalColumns.first())) <= 1,
                "The sender edge must stay anchored throughout opening")
        }
        val expandedColumns = coloredColumns()
        assertTrue(kotlin.math.abs(expandedColumns.size - originalColumns.size * 1.05f) <= 2,
            "Focused bubble must grow by 5%")
        val menu = compose.onNodeWithTag("lower action").fetchSemanticsNode().boundsInRoot
        val density = compose.density.density
        assertTrue(kotlin.math.abs(menu.top - (originalTop + 49 * density)) <= 2,
            "Bottom content keeps an 8dp gap from the scaled bubble edge")
        val expectedLeft = if (outgoing) originalColumns.last() + 1 - menu.width else originalColumns.first().toFloat()
        assertTrue(kotlin.math.abs(menu.left - expectedLeft) <= 2, "Consumer content aligns to the sender edge without horizontal padding")
        if (withTop) {
            val upper = compose.onNodeWithTag("upper action").fetchSemanticsNode().boundsInRoot
            assertTrue(kotlin.math.abs(upper.bottom - (originalTop - 9 * density)) <= 2,
                "Top content keeps an 8dp gap from the scaled bubble edge")
            assertTrue(kotlin.math.abs(upper.left - expectedLeft) <= 2, "Top content shares the sender edge")
        }
        val pixels = compose.onAllNodes(isRoot()).onLast().captureToImage().toPixelMap()
        val x = ((menu.left + menu.right) / 2).toInt()
        // White menu casts a visible shadow below its bottom, over the uniform scrim.
        val scrim = pixels[x, (menu.bottom + 36).toInt()].red
        assertTrue(scrim in .59f.. .61f, "Focused backdrop must use a darker 40% black scrim: $scrim")
        assertTrue((1..6).any { pixels[x, menu.bottom.toInt() + it].red < scrim - .015f },
            "Action shadow must survive its scroll viewport")
        val tail = pixels[x, (menu.bottom + 13 * density).toInt()].red
        assertTrue(tail < scrim - .002f, "The lower shadow must extend beyond 12dp without a hard cutoff: tail=$tail background=$scrim")
        compose.runOnIdle { state.value = state.value.copy(revision = 2, menuTargetId = null) }
        repeat(10) {
            compose.mainClock.advanceTimeByFrame()
            val columns = coloredColumns()
            assertTrue(kotlin.math.abs((if (outgoing) columns.last() else columns.first()) -
                (if (outgoing) originalColumns.last() else originalColumns.first())) <= 1,
                "The sender edge must stay anchored throughout closing")
        }
        compose.mainClock.advanceTimeBy(80)
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
