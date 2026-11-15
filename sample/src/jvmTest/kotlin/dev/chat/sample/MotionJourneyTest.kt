package dev.chat.sample

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import dev.forntoh.chat.core.ConversationEvent
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MotionJourneyTest {
    @get:Rule val compose = createComposeRule()

    @Test fun replyPreviewFadesInAndCancellationRetainsUnrelatedText() {
        val host = FakeHost().apply { useChatFixture() }
        host.onEvent(ConversationEvent.DraftChanged(host.state.conversationId, "Retained text"))
        compose.setContent {
            SampleTheme(false) {
                Box(Modifier.size(360.dp, 300.dp)) {
                    dev.forntoh.chat.actions.ActionComposerStatus(
                        dev.forntoh.chat.actions.rememberMessageActions(host.state, host.actions, { host.onAction(it) }, host::onEvent))
                }
            }
        }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { host.onAction(dev.forntoh.chat.actions.ActionRequest(
            host.state.conversationId, host.actions.revision, dev.forntoh.chat.actions.ActionCommand.Quote,
            host.state.messages.last().id)) }
        repeat(3) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
        // Crop the preview itself so composer sizing cannot masquerade as an opacity transition.
        fun pixels(): List<Int> {
            val image = compose.onNodeWithContentDescription("Reply to ${host.state.messages.last().sender.displayName}")
                .captureToImage().toPixelMap()
            return (0 until image.height).flatMap { y -> (0 until image.width).map { x -> image[x,y].toArgb() } }
        }
        val intermediate = pixels()
        compose.mainClock.advanceTimeBy(500)
        assertTrue(intermediate != pixels(), "Reply preview must have intermediate opacity")
        compose.onNodeWithContentDescription("Cancel reply").performClick()
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithContentDescription("Cancel reply").assertDoesNotExist()
        compose.runOnIdle { assertEquals("Retained text", host.state.draft.text); assertEquals(null, host.pendingDraft) }
        compose.mainClock.autoAdvance = true
    }

    @Test fun composerGrowthHasIntermediateFramesAndPreservesExactHostDraft() {
        val host = FakeHost().apply { useChatFixture() }
        compose.setContent { Box(Modifier.size(360.dp, 720.dp)) { SampleApp(host, manualService = true) } }
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
