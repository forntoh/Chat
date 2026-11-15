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

    @Test fun startupRepliesAndEveryScenarioAreReproducible() {
        Scenario.entries.forEach { scenario ->
            val first = FakeHost().apply { startSimulation(scenario, 7) }
            val second = FakeHost().apply { startSimulation(scenario, 7) }
            assertEquals(first.state, second.state)
            first.advanceBy(5_000); second.advanceBy(5_000)
            assertEquals(first.state, second.state)
            assertEquals(first.trace, second.trace)
            first.resetSimulation()
            assertEquals(0L, first.clockMillis)
        }
        val host = FakeHost().apply { startSimulation() }
        assertEquals("sketches", host.actions.quotes["response"])
        assertEquals("morgan-2", host.actions.quotes["confirmed"])
    }

    @Test fun burstHistoryFailureEmptyAndDisconnectUsePublicSnapshots() {
        val burst = FakeHost().apply { startSimulation(Scenario.IncomingBurst); advanceBy(2_000) }
        assertEquals(10, burst.state.messages.size)
        val history = FakeHost().apply { startSimulation(Scenario.History) }
        history.loadEarlier(dev.forntoh.chat.ui.HistoryRequest("studio", history.history.revision))
        history.step()
        assertEquals(dev.forntoh.chat.ui.HistoryStatus.Error, history.history.status)
        history.loadEarlier(dev.forntoh.chat.ui.HistoryRequest("studio", history.history.revision))
        history.step()
        assertEquals(100, history.state.messages.size)
        assertEquals(100, history.state.messages.map { it.id }.distinct().size)
        val empty = FakeHost().apply { startSimulation(Scenario.EmptyHistory) }
        empty.loadEarlier(dev.forntoh.chat.ui.HistoryRequest("studio", empty.history.revision)); empty.step()
        assertTrue(empty.state.messages.isEmpty())
        assertEquals(dev.forntoh.chat.ui.HistoryStatus.Exhausted, empty.history.status)
        val disconnected = FakeHost().apply { startSimulation(Scenario.Disconnected) }
        send(disconnected, "Retain until retry")
        disconnected.step(); disconnected.step()
        assertEquals(Delivery.Failed, disconnected.state.messages.last().delivery)
        disconnected.changeConnection(true)
        val id = disconnected.state.messages.last().id
        disconnected.onEvent(ConversationEvent.RetryMessage("studio", id)); disconnected.step()
        assertEquals(Delivery.Sent, disconnected.state.messages.single { it.id == id }.delivery)
    }

    @Test fun mediaFixtureIncludesFallbacksAndProgressResetInvalidatesOldTargets() {
        val host = FakeMediaHost().apply { resetSimulation() }
        assertEquals(setOf(dev.forntoh.chat.media.ImageStatus.Ready, dev.forntoh.chat.media.ImageStatus.Loading,
            dev.forntoh.chat.media.ImageStatus.Missing, dev.forntoh.chat.media.ImageStatus.Unsupported), host.media.images.values.map { it.status }.toSet())
        val row = host.state.messages.first()
        val target = dev.forntoh.chat.media.MediaTarget(host.state.conversationId, row.attachments.first(), message = row)
        host.resetSimulation()
        assertFalse(host.current(target))
        assertEquals(4, host.state.messages.size)
    }

    @Test fun resetRejectsSavedSelectionRemovalUploadAndRenderResultsWhilePreservingNewDraft() {
        val host = FakeMediaHost()
        val selection = dev.forntoh.chat.media.MediaRequest(dev.forntoh.chat.media.MediaCommand.Select,
            host.media.revision, dev.forntoh.chat.media.MediaTarget(host.state.conversationId, draft = host.state.draft))
        host.onRequest(selection)
        host.resolveSelection(choose = true)
        val removal = dev.forntoh.chat.media.MediaRequest(dev.forntoh.chat.media.MediaCommand.Remove,
            host.media.revision, dev.forntoh.chat.media.MediaTarget(host.state.conversationId,
                host.state.draft.attachments.single(), draft = host.state.draft))
        val draft = host.state.draft
        host.onEvent(ConversationEvent.SendDraft(host.state.conversationId, draft.revision, draft.text, draft.attachments))
        host.resolveDraft(true)
        val upload = host.upload!!
        val renderFailure = host.renderFailureCallback(upload)
        host.resetSimulation()
        host.onEvent(ConversationEvent.DraftChanged(host.state.conversationId, "New draft after reset"))
        val expectedState = host.state
        val expectedMedia = host.media
        host.resolveSelection(choose = true, request = selection)
        host.onRequest(removal)
        host.completeUpload(true, upload)
        host.completeUpload(false, upload)
        renderFailure()
        assertEquals(expectedState, host.state)
        assertEquals(expectedMedia, host.media)
        assertNull(host.upload)
        assertNull(host.selection)
    }

    @Test fun renderFailureClosesExactViewerAndLateFailureCannotChangeResetFixture() {
        val host = FakeMediaHost().apply { resetSimulation() }
        val row = host.state.messages.first()
        val target = dev.forntoh.chat.media.MediaTarget(host.state.conversationId, row.attachments.first(), message = row)
        host.onRequest(dev.forntoh.chat.media.MediaRequest(dev.forntoh.chat.media.MediaCommand.View, host.media.revision, target))
        assertEquals(target, host.viewer)
        val renderFailure = host.renderFailureCallback(target)
        renderFailure()
        assertNull(host.viewer)
        assertEquals(row, host.state.messages.first())
        assertEquals(dev.forntoh.chat.media.ImageStatus.Missing, host.media.images[target]?.status)
        host.resetSimulation()
        val expected = host.media
        renderFailure()
        assertEquals(expected, host.media)
    }

    @Test fun returningToRoomRejectsSavedRendererFailureButAllowsCurrentFailure() {
        val host = FakeMediaHost().apply { resetSimulation() }
        val target = host.media.images.entries.first { it.value.status == dev.forntoh.chat.media.ImageStatus.Ready }.key
        val obsoleteFailure = host.renderFailureCallback(target)
        host.switchRoom(); host.switchRoom()
        assertTrue(host.current(target)) // Sent snapshots deliberately survive navigation.
        host.onEvent(ConversationEvent.DraftChanged(host.state.conversationId, "New room session caption"))
        host.onRequest(dev.forntoh.chat.media.MediaRequest(dev.forntoh.chat.media.MediaCommand.View, host.media.revision, target))
        val expectedState = host.state
        val expectedMedia = host.media
        obsoleteFailure()
        assertEquals(expectedMedia, host.media)
        assertEquals(expectedState, host.state)
        assertEquals(target, host.viewer)
        host.failRendering()
        assertEquals(dev.forntoh.chat.media.ImageStatus.Missing, host.media.images[target]?.status)
        assertNull(host.viewer)
        assertEquals(expectedState, host.state)
    }

    @Test fun actionFailureRetriesAndSwitchRejectsLateCompletion() {
        val host = FakeHost().apply { startSimulation(Scenario.Actions) }
        fun request(command: dev.forntoh.chat.actions.ActionCommand) = host.onAction(dev.forntoh.chat.actions.ActionRequest(
            host.state.conversationId, host.actions.revision, command, "confirmed"))
        request(dev.forntoh.chat.actions.ActionCommand.RequestDelete)
        request(dev.forntoh.chat.actions.ActionCommand.ConfirmDelete)
        host.step()
        assertNotNull(host.actions.error)
        assertTrue(host.state.messages.any { it.id == "confirmed" })
        request(dev.forntoh.chat.actions.ActionCommand.ConfirmDelete)
        host.step()
        assertFalse(host.state.messages.any { it.id == "confirmed" })
        host.resetSimulation()
        request(dev.forntoh.chat.actions.ActionCommand.RequestDelete)
        request(dev.forntoh.chat.actions.ActionCommand.ConfirmDelete)
        host.switchRoom(); host.switchRoom(); host.advanceBy(10_000)
        assertNull(host.pendingAction)
        assertFalse(host.actions.pending)
        assertTrue(host.state.messages.any { it.id == "confirmed" })
    }

    @Test fun revokingPendingEditOrDeleteCannotReviveItsResultAfterRestoringCapability() {
        for (deleting in listOf(false, true)) {
            val host = FakeHost().apply { startSimulation(Scenario.Capabilities); pause(true) }
            val original = host.state.messages.last()
            host.onEvent(ConversationEvent.DraftChanged("studio", "Unrelated draft"))
            fun request(command: dev.forntoh.chat.actions.ActionCommand, text: String? = null) =
                host.onAction(dev.forntoh.chat.actions.ActionRequest("studio", host.actions.revision,
                    command, original.id, text, host.state.draft.revision))
            request(if (deleting) dev.forntoh.chat.actions.ActionCommand.RequestDelete else dev.forntoh.chat.actions.ActionCommand.BeginEdit)
            if (!deleting) host.onEvent(ConversationEvent.DraftChanged("studio", "Obsolete edit"))
            request(if (deleting) dev.forntoh.chat.actions.ActionCommand.ConfirmDelete else dev.forntoh.chat.actions.ActionCommand.SubmitEdit,
                if (deleting) null else "Obsolete edit")
            assertTrue(host.actions.pending)
            host.revokeActions(original.id)
            host.allowActions(original.id)
            host.step()
            assertEquals(original, host.state.messages.single { it.id == original.id })
            assertFalse(host.actions.pending)
            assertNotNull(host.actions.error)
            request(if (deleting) dev.forntoh.chat.actions.ActionCommand.CancelDelete else dev.forntoh.chat.actions.ActionCommand.CancelEdit)
            assertEquals("Unrelated draft", host.state.draft.text)
        }
    }

    @Test fun resettingHistoryCannotReuseAnOldRequestWhileNewHistoryIsPending() {
        for (switchBeforeReset in listOf(false, true)) {
            val host = FakeHost().apply { startSimulation(Scenario.History); pause(true) }
            host.loadEarlier(dev.forntoh.chat.ui.HistoryRequest("studio", host.history.revision))
            val old = host.pendingHistory!!
            if (switchBeforeReset) host.switchRoom()
            host.resetSimulation()
            host.pause(true)
            host.onEvent(ConversationEvent.DraftChanged("studio", "New session draft"))
            host.loadEarlier(dev.forntoh.chat.ui.HistoryRequest("studio", host.history.revision))
            val current = host.pendingHistory!!
            host.completeHistory(old)
            assertEquals(50, host.state.messages.size)
            assertEquals(current, host.pendingHistory)
            assertEquals(dev.forntoh.chat.ui.HistoryStatus.Loading, host.history.status)
            assertEquals("New session draft", host.state.draft.text)
            host.step()
            assertEquals(dev.forntoh.chat.ui.HistoryStatus.Error, host.history.status)
            host.loadEarlier(dev.forntoh.chat.ui.HistoryRequest("studio", host.history.revision))
            host.step()
            assertEquals(100, host.state.messages.size)
            assertEquals("New session draft", host.state.draft.text)
        }
    }

    @Test fun overlappingHistoryExhaustsWithoutReorderingOrErasingTheDraft() {
        val host = FakeHost().apply { startSimulation(Scenario.History); pause(true) }
        host.onEvent(ConversationEvent.DraftChanged("studio", "Keep while paging"))
        repeat(20) {
            host.loadEarlier(dev.forntoh.chat.ui.HistoryRequest("studio", host.history.revision))
            host.step()
        }
        assertEquals(dev.forntoh.chat.ui.HistoryStatus.Exhausted, host.history.status)
        assertEquals((0..999).map { "history-$it" }, host.state.messages.map { it.id })
        host.loadEarlier(dev.forntoh.chat.ui.HistoryRequest("studio", host.history.revision))
        assertNull(host.pendingHistory)
        assertEquals("Keep while paging", host.state.draft.text)
        host.startSimulation()
        val originalIds = host.state.messages.map { it.id }
        send(host, "Same timestamp as response")
        host.step()
        val accepted = host.state.messages.last()
        assertEquals(host.state.messages.single { it.id == "response" }.timestampEpochMillis, accepted.timestampEpochMillis)
        assertEquals(originalIds + accepted.id, host.state.messages.map { it.id })
        host.onEvent(ConversationEvent.DraftChanged("studio", "Keep after removal"))
        host.removeMessage("sketches")
        assertEquals((originalIds + accepted.id) - "sketches", host.state.messages.map { it.id })
        assertEquals("Keep after removal", host.state.draft.text)
    }

    @Test fun resetRejectsOldMenuRequestsIncludingThoseRetainedBeforeSwitchingRooms() {
        for (switchBeforeReset in listOf(false, true)) {
            for (command in listOf(dev.forntoh.chat.actions.ActionCommand.RequestDelete, dev.forntoh.chat.actions.ActionCommand.BeginEdit)) {
                val host = FakeHost().apply { startSimulation(Scenario.Capabilities) }
                host.onAction(dev.forntoh.chat.actions.ActionRequest("studio", host.actions.revision,
                    dev.forntoh.chat.actions.ActionCommand.OpenMenu, "confirmed"))
                val old = dev.forntoh.chat.actions.ActionRequest("studio", host.actions.revision, command, "confirmed")
                if (switchBeforeReset) host.switchRoom()
                host.resetSimulation()
                host.onEvent(ConversationEvent.DraftChanged("studio", "New session draft"))
                val snapshot = host.state
                val actions = host.actions
                host.onAction(old)
                assertEquals(snapshot, host.state)
                assertEquals(actions, host.actions)
                assertNull(host.pendingAction)
                host.onAction(old.copy(revision = host.actions.revision))
                if (command == dev.forntoh.chat.actions.ActionCommand.BeginEdit) {
                    assertEquals("confirmed", host.actions.editing?.original?.id)
                    host.onAction(dev.forntoh.chat.actions.ActionRequest("studio", host.actions.revision,
                        dev.forntoh.chat.actions.ActionCommand.CancelEdit))
                } else assertEquals("confirmed", host.actions.deleting?.id)
                assertEquals("New session draft", host.state.draft.text)
            }
        }
    }

}
