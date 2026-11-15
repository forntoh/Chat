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
        first.advanceBy(30_000)
        second.advanceBy(30_000)
        assertEquals(second.state, first.state)
        assertTrue(first.state.messages.any { it.attachments.isNotEmpty() })
        assertTrue(first.state.messages.size in 4..8)
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
}
