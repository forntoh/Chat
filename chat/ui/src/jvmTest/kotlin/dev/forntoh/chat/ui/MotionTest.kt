package dev.forntoh.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.rows
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.visible
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.center
import androidx.compose.ui.test.height
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import dev.forntoh.chat.core.ConversationEvent
import dev.forntoh.chat.core.ConversationState
import dev.forntoh.chat.core.Delivery
import dev.forntoh.chat.core.Draft
import dev.forntoh.chat.core.Message
import dev.forntoh.chat.core.Participant
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertTrue

class MotionTest {
    @get:Rule val compose = createComposeRule()
    private fun row(id: String) = Message(id, Participant("other", "Avery"), 0, "09:41", id, Delivery.Sent)

    @Test fun onlyNewTailRowsFadeAndHistoryAndRemountRemainOpaque() {
        val state = mutableStateOf(ConversationState("room", "me", messages = listOf(row("a"))))
        val mounted = mutableStateOf(true)
        compose.setContent {
            Box(Modifier.size(360.dp, 400.dp).background(Color.White)) {
                if (mounted.value) {
                    Conversation(
                        state.value,
                        {},
                        messageContent = { _, _, _ -> Box(Modifier.size(100.dp, 48.dp).background(Color.Red)) },
                        composerContent = {},
                        historyContent = {},
                    )
                }
            }
        }
        fun green(id: String): Float {
            val bounds = compose.onNodeWithTag("message-$id").fetchSemanticsNode().boundsInRoot
            return compose.onAllNodes(isRoot()).onFirst().captureToImage().toPixelMap()[30, bounds.center.y.toInt()].green
        }
        assertTrue(green("a") < 0.02f, "Initial rows are opaque")
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { state.value = state.value.copy(messages = state.value.messages + row("b")) }
        repeat(3) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        assertTrue(green("b") > 0.1f, "Tail insertion must have a visible intermediate fade")
        compose.mainClock.advanceTimeBy(300)
        assertTrue(green("b") < 0.02f)
        compose.runOnIdle { state.value = state.value.copy(messages = listOf(row("history")) + state.value.messages) }
        repeat(3) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        assertTrue(green("history") < 0.02f, "History must not replay insertion")
        compose.runOnIdle { mounted.value = false }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { mounted.value = true }
        repeat(3) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        assertTrue(green("b") < 0.02f, "Remount must not replay insertion")
        compose.mainClock.autoAdvance = true
    }

    @Test fun hostTimingOverrideCanBeDisabledDuringResizeAndConversationSwitch() {
        val motion = mutableStateOf(ChatMotion(sizeMillis = 960))
        val height = mutableStateOf(48.dp)
        val state = mutableStateOf(ConversationState("one", "me", Draft("Unsent exact text"), listOf(row("a"))))
        val events = mutableListOf<ConversationEvent>()
        compose.setContent {
            CompositionLocalProvider(LocalChatMotion provides motion.value) {
                Box(Modifier.size(360.dp, 400.dp)) {
                    Conversation(
                        state.value,
                        events::add,
                        messageContent = { _, _, _ -> Box(Modifier.size(100.dp, 48.dp)) },
                        composerContent = { Box(Modifier.fillMaxWidth().height(height.value)) },
                    )
                }
            }
        }
        val initial = compose.onNodeWithTag("composer-area").fetchSemanticsNode().boundsInRoot.height
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { height.value = 160.dp }
        compose.mainClock.advanceTimeBy(320)
        compose.waitForIdle()
        val midway = compose.onNodeWithTag("composer-area").fetchSemanticsNode().boundsInRoot.height
        assertTrue(midway > initial && midway < 160f, "Custom duration must still be in progress at 320ms")
        compose.runOnIdle { motion.value = ChatMotion(enabled = false) }
        repeat(3) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        kotlin.test.assertEquals(
            160f,
            compose.onNodeWithTag("composer-area").fetchSemanticsNode().boundsInRoot.height,
            1f,
            "Disabling during an active resize must settle the existing target immediately",
        )
        compose.runOnIdle {
            height.value = 80.dp
            state.value = state.value.copy(conversationId = "two", messages = listOf(row("new")))
        }
        repeat(3) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        kotlin.test.assertEquals(80f, compose.onNodeWithTag("composer-area").fetchSemanticsNode().boundsInRoot.height, 1f)
        compose.onNodeWithTag("message-new").assertIsDisplayed()
        compose.onNodeWithTag("message-a").assertDoesNotExist()
        kotlin.test.assertEquals("Unsent exact text", state.value.draft.text)
        assertTrue(events.isEmpty(), "Presentation changes cannot emit business intents")
        compose.mainClock.autoAdvance = true
    }

    @Test fun disablingAnActiveLatestReturnSettlesAtTheTailWithoutAnIntent() {
        val motion = mutableStateOf(ChatMotion())
        val events = mutableListOf<ConversationEvent>()
        compose.setContent {
            CompositionLocalProvider(LocalChatMotion provides motion.value) {
                Box(Modifier.size(360.dp, 400.dp)) {
                    Conversation(
                        ConversationState("one", "me", messages = (0..199).map { row("m$it") }),
                        events::add,
                        messageContent = { _, _, _ -> Box(Modifier.size(100.dp, 48.dp)) },
                        composerContent = {},
                    )
                }
            }
        }
        compose.onNodeWithTag("timeline").performScrollToIndex(1)
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(200)
        compose.onNodeWithText("Latest").performClick()
        repeat(4) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        compose.runOnIdle { motion.value = ChatMotion(enabled = false) }
        repeat(4) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        compose.onNodeWithTag("message-m199").assertIsDisplayed()
        assertTrue(events.isEmpty())
        compose.mainClock.autoAdvance = true
    }

    @Test fun firstArrivalIntoAnEmptyConversationFadesButInitialHistoryResolutionDoesNot() {
        val snapshot = mutableStateOf(ConversationState("empty", "me"))
        val history = mutableStateOf(HistoryState(HistoryStatus.Loading))
        compose.setContent {
            Box(Modifier.size(360.dp, 400.dp).background(Color.White)) {
                Conversation(
                    snapshot.value,
                    {},
                    history = history.value,
                    messageContent = { _, _, _ -> Box(Modifier.size(100.dp, 48.dp).background(Color.Red)) },
                    composerContent = {},
                    historyContent = {},
                )
            }
        }
        fun green(id: String): Float {
            val bounds = compose.onNodeWithTag("message-$id").fetchSemanticsNode().boundsInRoot
            return compose.onAllNodes(isRoot()).onFirst().captureToImage().toPixelMap()[30, bounds.center.y.toInt()].green
        }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle {
            snapshot.value = snapshot.value.copy(messages = listOf(row("initial-page")))
            history.value = HistoryState(HistoryStatus.Exhausted, 1)
        }
        repeat(3) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        assertTrue(green("initial-page") < 0.02f, "Loading resolution cannot replay initial history")
        compose.runOnIdle { snapshot.value = snapshot.value.copy(messages = emptyList()) }
        repeat(3) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        compose.runOnIdle { snapshot.value = snapshot.value.copy(messages = listOf(row("first-arrival"))) }
        repeat(3) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        assertTrue(green("first-arrival") > 0.1f, "An arrival into an already empty conversation must fade")
        compose.mainClock.advanceTimeBy(300)
        assertTrue(green("first-arrival") < 0.02f)
        compose.mainClock.autoAdvance = true
    }

    @Test fun disabledQuoteNavigationPositionsImmediatelyAndDisablingTravelSettles() {
        val motion = mutableStateOf(ChatMotion(enabled = false))
        val events = mutableListOf<ConversationEvent>()
        lateinit var navigate: (String) -> Unit
        compose.setContent {
            CompositionLocalProvider(LocalChatMotion provides motion.value) {
                Box(Modifier.size(360.dp, 400.dp)) {
                    Conversation(
                        ConversationState("one", "me", Draft("Exact text"), (0..199).map { row("m$it") }),
                        events::add,
                        slots = ConversationSlots(presentedMessage = { _, _, _, presentation ->
                            SideEffect { navigate = presentation.onNavigateToMessage }
                            Box(Modifier.size(100.dp, 48.dp))
                        }),
                        composerContent = {},
                    )
                }
            }
        }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { navigate("m12") }
        repeat(4) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        compose.onNodeWithTag("message-m12").assertIsDisplayed()
        compose.runOnIdle { motion.value = ChatMotion() }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { navigate("m180") }
        repeat(3) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        compose.runOnIdle { motion.value = ChatMotion(enabled = false) }
        repeat(4) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        compose.onNodeWithTag("message-m180").assertIsDisplayed()
        assertTrue(events.isEmpty(), "Quote positioning is presentation only")
        compose.mainClock.autoAdvance = true
    }
}
