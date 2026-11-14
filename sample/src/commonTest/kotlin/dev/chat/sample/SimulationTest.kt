package dev.chat.sample

import dev.forntoh.chat.core.*
import kotlin.test.*

/** Checks the approved host event/snapshot seam with logical clock advancement only. */
class SimulationTest {
    private fun send(host: FakeHost, text: String) {
        host.onEvent(ConversationEvent.DraftChanged(host.state.conversationId, text))
        host.onEvent(ConversationEvent.SendDraft(host.state.conversationId, host.state.draft.revision, text))
    }

    @Test fun seededExchangeSeparatesAcknowledgementDeliveryAndIncomingReply() {
        val host = FakeHost().apply { startSimulation(Scenario.Exchange, 42) }
        val initial = host.state.messages.size
        send(host, "See you there")
        assertTrue(host.state.draft.submitting)
        assertEquals(initial, host.state.messages.size)
        host.step()
        assertEquals("", host.state.draft.text)
        assertEquals(Delivery.Sending, host.state.messages.last().delivery)
        host.step()
        assertEquals(Delivery.Sent, host.state.messages.last().delivery)
        host.step()
        assertEquals(initial + 2, host.state.messages.size)
        assertEquals("avery", host.state.messages.last().sender.id)
        val repeated = FakeHost().apply { startSimulation(Scenario.Exchange, 42) }
        send(repeated, "See you there")
        repeat(3) { repeated.step() }
        assertEquals(host.state, repeated.state)
        assertEquals(host.trace, repeated.trace)
    }
    @Test fun refusalRetainsExactDraftAndRepeatedPendingSendIsIgnored() {
        val host = FakeHost().apply { startSimulation(Scenario.Refusal) }
        send(host, "  Keep\nthis draft 🌍  ")
        val pending = host.pendingDraft!!
        host.onEvent(pending)
        host.step()
        assertEquals(pending.text, host.state.draft.text)
        assertEquals(pending.attachments, host.state.draft.attachments)
        assertFalse(host.state.draft.submitting)
        assertTrue(host.state.draft.revision > pending.revision)
        assertEquals(6, host.state.messages.size)
        send(host, "Next attempt")
        host.step()
        assertEquals(7, host.state.messages.size)
    }

    @Test fun failureRetriesOneLogicalRowAndRejectsRepeatedRetry() {
        val host = FakeHost().apply { startSimulation(Scenario.Failure) }
        send(host, "Try again")
        host.step()
        val id = host.state.messages.last().id
        host.step()
        assertEquals(Delivery.Failed, host.state.messages.last().delivery)
        val retry = ConversationEvent.RetryMessage("studio", id)
        host.onEvent(retry)
        host.onEvent(retry)
        assertEquals(Delivery.Sending, host.state.messages.last().delivery)
        host.step()
        assertEquals(Delivery.Sent, host.state.messages.single { it.id == id }.delivery)
        host.step()
        host.step()
        assertEquals(8, host.state.messages.size)
        assertEquals(1, host.state.messages.count { it.id == id })
    }

    @Test fun resetAndRoundTripSwitchCancelAcknowledgementsDeliveryAndRetry() {
        val host = FakeHost().apply { startSimulation(Scenario.Failure) }
        send(host, "Obsolete submission")
        host.resetSimulation()
        host.advanceBy(10_000)
        assertEquals(6, host.state.messages.size)
        assertEquals("", host.state.draft.text)
        send(host, "Retained studio draft")
        host.switchRoom()
        send(host, "Other room")
        host.switchRoom()
        host.advanceBy(10_000)
        assertEquals("Retained studio draft", host.state.draft.text)
        assertFalse(host.state.draft.submitting)
        assertEquals(6, host.state.messages.size)
        send(host, "Fail then retry")
        host.step()
        host.step()
        host.onEvent(ConversationEvent.RetryMessage("studio", host.state.messages.last().id))
        host.resetSimulation()
        send(host, "New session")
        host.advanceBy(10_000)
        assertEquals(7, host.state.messages.size)
        assertEquals("New session", host.state.messages.last().text)
        assertEquals(Delivery.Failed, host.state.messages.last().delivery)
    }

    @Test fun pausedClockStepsEqualDeadlinesAndOldAcknowledgementCannotClearNewDraft() {
        val host = FakeHost().apply { startSimulation(); configureDelays(0, 0) }
        host.pause(true)
        send(host, "First")
        val old = host.pendingDraft!!
        host.advanceBy(5_000)
        assertTrue(host.state.draft.submitting)
        assertEquals(0L, host.clockMillis)
        host.step()
        send(host, "Second")
        host.accept(old)
        assertEquals("Second", host.state.draft.text)
        host.step() // first delivery, inserted before the second acknowledgement
        host.step() // second acknowledgement
        assertEquals(listOf("First", "Second"), host.state.messages.takeLast(2).map { it.text })
        host.pause(false)
        host.advanceBy(2_000)
        assertEquals(10, host.state.messages.size)
        assertEquals(10, host.state.messages.map { it.id }.distinct().size)
        assertTrue(host.state.messages.all { it.delivery == Delivery.Sent })
    }

    @Test fun configuredDelaysAndTraceStayBoundedWithFixtureDataOnly() {
        val host = FakeHost().apply { startSimulation(); configureDelays(1_200, 2_000) }
        send(host, "PRIVATE DRAFT MUST NOT ENTER TRACE")
        host.advanceBy(1_199)
        assertTrue(host.state.draft.submitting)
        host.advanceBy(1)
        assertEquals(Delivery.Sending, host.state.messages.last().delivery)
        host.advanceBy(1_999)
        assertEquals(Delivery.Sending, host.state.messages.last().delivery)
        host.advanceBy(1)
        assertEquals(Delivery.Sent, host.state.messages.last().delivery)
        repeat(30) { send(host, "fixture"); host.step(); host.step() }
        assertTrue(host.trace.size <= 80)
        assertFalse(host.trace.any { "PRIVATE" in it || "fixture" in it })
    }

}
