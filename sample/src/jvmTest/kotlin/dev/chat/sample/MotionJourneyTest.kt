package dev.chat.sample

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.visible
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.bottom
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.height
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.top
import androidx.compose.ui.test.width
import androidx.compose.ui.unit.dp
import dev.forntoh.chat.core.ConversationEvent
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MotionJourneyTest {
    @get:Rule val compose = createComposeRule()

    @Test fun replyPreviewFadesInAndCancellationRetainsUnrelatedText() {
        val host = ContractFixture().apply { useChatFixture() }
        host.onEvent(ConversationEvent.DraftChanged(host.state.conversationId, "Retained text"))
        compose.setContent {
            SampleTheme(false) {
                Box(Modifier.size(360.dp, 300.dp)) {
                    dev.forntoh.chat.actions.ActionComposerStatus(
                        dev.forntoh.chat.actions.rememberMessageActions(host.state, host.actions, {
                            host.onAction(it)
                        }, host::onEvent),
                    )
                }
            }
        }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle {
            host.onAction(
                dev.forntoh.chat.actions.ActionRequest(
                    host.state.conversationId,
                    host.actions.revision,
                    dev.forntoh.chat.actions.ActionCommand.Quote,
                    host.state.messages.last().id,
                ),
            )
        }
        repeat(3) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        // Crop the preview itself so composer sizing cannot masquerade as an opacity transition.
        fun pixels(): List<Int> {
            val image = compose.onNodeWithContentDescription("Reply to ${host.state.messages.last().sender.displayName}")
                .captureToImage().toPixelMap()
            return (0 until image.height).flatMap { y -> (0 until image.width).map { x -> image[x, y].toArgb() } }
        }
        val intermediate = pixels()
        compose.mainClock.advanceTimeBy(500)
        assertTrue(intermediate != pixels(), "Reply preview must have intermediate opacity")
        compose.onNodeWithContentDescription("Cancel reply").performClick()
        repeat(3) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        compose.onNodeWithContentDescription("Cancel reply").assertDoesNotExist()
        fun rootPixels(): List<Int> {
            val image = compose.onAllNodes(isRoot()).onFirst().captureToImage().toPixelMap()
            return (0 until image.height).flatMap { y -> (0 until image.width).map { x -> image[x, y].toArgb() } }
        }
        val exiting = rootPixels()
        compose.mainClock.advanceTimeBy(600)
        assertTrue(exiting != rootPixels(), "Cancelled reply must retain visible pixels during its exit")
        compose.onNodeWithContentDescription("Cancel reply").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals("Retained text", host.state.draft.text)
            assertEquals(null, host.pendingDraft)
        }
        compose.mainClock.autoAdvance = true
    }

    @Test fun editCancellationRetainsExitPixelsAndDropsThemOnConversationSwitch() {
        val host = ContractFixture().apply { useChatFixture() }
        host.onEvent(ConversationEvent.DraftChanged(host.state.conversationId, "Retained text"))
        compose.setContent {
            SampleTheme(false) {
                Box(Modifier.size(360.dp, 300.dp)) {
                    dev.forntoh.chat.actions.ActionComposerStatus(
                        dev.forntoh.chat.actions.rememberMessageActions(host.state, host.actions, {
                            host.onAction(it)
                        }, host::onEvent),
                    )
                }
            }
        }
        fun pixels(): List<Int> {
            val image = compose.onAllNodes(isRoot()).onFirst().captureToImage().toPixelMap()
            return (0 until image.height).flatMap { y -> (0 until image.width).map { x -> image[x, y].toArgb() } }
        }
        fun beginEdit() {
            host.onAction(
                dev.forntoh.chat.actions.ActionRequest(
                    host.state.conversationId,
                    host.actions.revision,
                    dev.forntoh.chat.actions.ActionCommand.BeginEdit,
                    host.state.messages.last().id,
                ),
            )
        }
        compose.runOnIdle { beginEdit() }
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithContentDescription("Cancel edit").performClick()
        repeat(3) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        compose.onNodeWithContentDescription("Cancel edit").assertDoesNotExist()
        val exiting = pixels()
        compose.mainClock.advanceTimeBy(300)
        val empty = pixels()
        assertTrue(exiting != empty, "Editing label must retain pixels through cancellation")
        assertEquals("Retained text", host.state.draft.text)
        compose.runOnIdle { beginEdit() }
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithContentDescription("Cancel edit").performClick()
        compose.runOnIdle { host.switchRoom() }
        repeat(3) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        assertEquals(empty, pixels(), "Old context cannot survive a conversation switch")
        compose.mainClock.autoAdvance = true
    }

    @Test fun composerGrowthHasIntermediateFramesAndPreservesExactHostDraft() {
        val host = ContractFixture().apply { useChatFixture() }
        compose.setContent { Box(Modifier.size(360.dp, 720.dp)) { ActionContractHarness(host) } }
        val initial = compose.onNodeWithTag("composer-area").fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false
        val text = "First line\nSecond line\nThird line\nFourth line"
        compose.runOnIdle { host.onEvent(ConversationEvent.DraftChanged(host.state.conversationId, text)) }
        val tops = mutableListOf<Float>()
        repeat(20) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            val bounds = compose.onNodeWithTag("composer-area").fetchSemanticsNode().boundsInRoot
            assertEquals(initial.bottom, bounds.bottom, 1f)
            tops += bounds.top
        }
        compose.mainClock.autoAdvance = true
        assertTrue(tops.distinct().size >= 4, "Composer growth must include intermediate heights: $tops")
        assertEquals(text, host.state.draft.text)
        assertEquals(null, host.pendingDraft)
        compose.onNodeWithContentDescription("Send").assertIsDisplayed().assertIsEnabled()
    }
}
