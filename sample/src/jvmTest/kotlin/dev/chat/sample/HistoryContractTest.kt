package dev.chat.sample

import dev.forntoh.chat.ui.HistoryRequest
import dev.forntoh.chat.ui.HistoryStatus
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HistoryContractTest {
    @Test fun overlappingPageMaintainsIdentityAndRejectsOldRoomResults() {
        val fixture = ContractFixture()
        fixture.useHistoryFixture()
        val request = HistoryRequest(fixture.state.conversationId, fixture.history.revision)
        fixture.loadEarlier(request)
        fixture.completeHistory(request)
        assertEquals(100, fixture.state.messages.size)
        assertEquals(100, fixture.state.messages.map { it.id }.distinct().size)
        assertTrue(fixture.state.messages.first().id == "history-900")
        val next = HistoryRequest(fixture.state.conversationId, fixture.history.revision)
        fixture.loadEarlier(next)
        fixture.switchRoom()
        val switched = fixture.state
        fixture.completeHistory(next)
        assertEquals(switched, fixture.state)
    }

    @Test fun historyFailurePreservesRowsAndRetryAdvancesRevision() {
        val fixture = ContractFixture()
        fixture.useHistoryFixture()
        val rows = fixture.state.messages
        fixture.loadEarlier(HistoryRequest(fixture.state.conversationId, fixture.history.revision))
        fixture.failHistory()
        assertEquals(HistoryStatus.Error, fixture.history.status)
        assertEquals(rows, fixture.state.messages)
        fixture.loadEarlier(HistoryRequest(fixture.state.conversationId, fixture.history.revision))
        fixture.completeHistory()
        assertEquals(HistoryStatus.Idle, fixture.history.status)
        assertEquals(100, fixture.state.messages.size)
    }
}
