package dev.chat.sample

import dev.forntoh.chat.core.ConversationEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BotHostTest {
    @Test
    fun acceptedTextReceivesOneDelayedReply() {
        val host = BotHost(42)
        host.start()
        host.onEvent(ConversationEvent.DraftChanged(host.state.conversationId, "Let's go hiking"))
        val event = ConversationEvent.SendDraft(host.state.conversationId, host.state.draft.revision, host.state.draft.text)
        host.onEvent(event)
        host.onEvent(event)
        assertEquals("me", host.state.messages.last().sender.id)
        val acceptedCount = host.state.messages.size
        host.advanceBy(500)
        assertEquals(acceptedCount, host.state.messages.size)
        host.advanceBy(2_000)
        assertEquals(acceptedCount + 1, host.state.messages.size)
        assertEquals("bot", host.state.messages.last().sender.id)
    }

    @Test
    fun autonomousArrivalsAreSeededAndIncludeImagesWithoutUserInput() {
        val first = BotHost(42)
        val second = BotHost(42)
        first.start()
        first.start()
        second.start()
        repeat(2) {
            first.advanceBy(60_000)
            second.advanceBy(60_000)
        }
        assertEquals(second.state, first.state)
        assertTrue(first.state.messages.any { it.attachments.isNotEmpty() })
        assertEquals(4, first.state.messages.size)
        first.advanceBy(60_000)
        assertEquals(4, first.state.messages.size)
        assertEquals(1, first.suggestions.size)
        assertEquals(first.state.messages.size, first.state.messages.map { it.id }.distinct().size)
    }

    @Test
    fun resetRejectsOldDraftsAndDisposalCancelsRepliesAndArrivals() {
        val host = BotHost(42)
        host.start()
        host.onEvent(ConversationEvent.DraftChanged(host.state.conversationId, "Old session"))
        val old = ConversationEvent.SendDraft(host.state.conversationId, host.state.draft.revision, host.state.draft.text)
        host.onEvent(old)
        host.reset()
        assertEquals(1, host.state.messages.size)
        host.onEvent(old)
        host.advanceBy(2_000)
        assertEquals(1, host.state.messages.size)
        assertEquals("", host.state.draft.text)
        host.stop()
        val stopped = host.state
        host.advanceBy(30_000)
        assertEquals(stopped, host.state)
    }

    @Test
    fun pauseCancelsPendingRepliesAndArrivalsButAllowsSending() {
        val host = BotHost(42)
        host.start()
        send(host, "Before pause")
        host.pause()
        val count = host.state.messages.size
        host.advanceBy(60_000)
        assertEquals(count, host.state.messages.size)
        send(host, "While paused")
        assertEquals(count + 1, host.state.messages.size)
        host.advanceBy(60_000)
        assertEquals(count + 1, host.state.messages.size)
        host.resume()
        host.resume()
        host.advanceBy(14_999)
        assertEquals(count + 1, host.state.messages.size)
        repeat(2) { host.advanceBy(60_000) }
        assertEquals(count + 4, host.state.messages.size)
        host.advanceBy(60_000)
        assertEquals(count + 4, host.state.messages.size)
    }

    @Test
    fun userInteractionRestartsOneFiniteBurstAfterIdle() {
        val host = BotHost(42)
        host.start()
        repeat(2) { host.advanceBy(60_000) }
        send(host, "Another idea?")
        host.advanceBy(2_000)
        assertEquals(6, host.state.messages.size)
        repeat(3) { host.advanceBy(60_000) }
        assertEquals(9, host.state.messages.size)
    }

    @Test
    fun unsolicitedArrivalsRespectTheBoundedInterval() {
        val host = BotHost(42)
        host.start()
        repeat(3) { index ->
            host.advanceBy(14_999)
            assertEquals(index + 1, host.state.messages.size)
            // The next arrival must occur by the 30-second upper bound.
            var waited = 14_999L
            while (host.state.messages.size == index + 1 && waited < 30_000) {
                host.advanceBy(1)
                waited++
            }
            assertEquals(index + 2, host.state.messages.size)
        }
        host.advanceBy(60_000)
        assertEquals(4, host.state.messages.size)
    }

    private fun send(host: BotHost, text: String) {
        host.onEvent(ConversationEvent.DraftChanged(host.state.conversationId, text))
        host.onEvent(ConversationEvent.SendDraft(host.state.conversationId, host.state.draft.revision, text))
    }
}
