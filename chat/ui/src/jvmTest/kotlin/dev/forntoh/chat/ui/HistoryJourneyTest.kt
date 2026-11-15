package dev.forntoh.chat.ui

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import dev.forntoh.chat.core.*
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HistoryJourneyTest {
    @get:Rule val compose = createComposeRule()

    @Test fun thousandMessageReaderKeepsOffsetAcrossPrependAndArrivalsThenReturnsDeliberately() {
        fun message(n: Int) = Message("m$n", Participant("other", "Avery"), n.toLong(), "09:41", "Message $n", Delivery.Sent)
        val snapshot = mutableStateOf(ConversationState("room", "me", messages = (0..999).map(::message)))
        compose.setContent {
            Conversation(snapshot.value, {}, messageContent = { row, _, _ ->
                BasicText(row.text, Modifier.height(48.dp))
            }, composerContent = {}, latestContent = {
                BasicText(if (it.hasNewMessages) "New messages" else "Latest", Modifier.clickable(onClick = it.onReturnToLatest))
            })
        }
        compose.onNodeWithTag("message-m999").assertIsDisplayed()
        compose.onNodeWithTag("timeline").performScrollToIndex(451)
        compose.onNodeWithTag("timeline").performScrollToIndex(452)
        val top = compose.onNodeWithTag("message-m452").fetchSemanticsNode().boundsInRoot.top
        compose.runOnIdle { snapshot.value = snapshot.value.copy(messages = (-40..999).map(::message)) }
        assertEquals(top, compose.onNodeWithTag("message-m452").fetchSemanticsNode().boundsInRoot.top, 1f)
        compose.runOnIdle { snapshot.value = snapshot.value.copy(messages = snapshot.value.messages + message(1000)) }
        assertEquals(top, compose.onNodeWithTag("message-m452").fetchSemanticsNode().boundsInRoot.top, 1f)
        val anchorTop = compose.onNodeWithTag("message-m451").fetchSemanticsNode().boundsInRoot.top
        compose.runOnIdle {
            snapshot.value = snapshot.value.copy(messages = snapshot.value.messages.filterNot { it.id in (451..454).map { n -> "m$n" } })
        }
        assertEquals(anchorTop, compose.onNodeWithTag("message-m450").fetchSemanticsNode().boundsInRoot.top, 1f)
        compose.onNodeWithText("New messages").performClick()
        compose.onNodeWithTag("message-m1000").assertIsDisplayed()
        compose.runOnIdle { snapshot.value = snapshot.value.copy(messages = snapshot.value.messages + message(1001)) }
        compose.onNodeWithTag("message-m1001").assertIsDisplayed()
        compose.runOnIdle { snapshot.value = ConversationState("other", "me", draft = Draft("Other draft"), messages = listOf(message(7))) }
        compose.onNodeWithTag("message-m7").assertIsDisplayed()
        compose.onNodeWithText("New messages").assertDoesNotExist()
        assertTrue(snapshot.value.draft.text == "Other draft")
    }

    @Test fun startMarkerScrollsAwayAndArrivalsFollowOnlyAtBottom() {
        fun message(n: Int) = Message("m$n", Participant("other", "Avery"), n.toLong(), "09:41", "Message $n", Delivery.Sent)
        val snapshot = mutableStateOf(ConversationState("room", "me", messages = (0..29).map(::message)))
        compose.setContent {
            androidx.compose.foundation.layout.Box(Modifier.height(400.dp)) {
                Conversation(snapshot.value, {}, messageContent = { row, _, _ ->
                    BasicText(row.text, Modifier.height(48.dp))
                }, composerContent = {})
            }
        }
        compose.onNodeWithTag("message-m29").assertIsDisplayed()
        compose.onNodeWithText("Start of conversation").assertDoesNotExist()
        compose.onNodeWithTag("timeline").performScrollToIndex(0)
        compose.onNodeWithText("Start of conversation").assertIsDisplayed()
        compose.onNodeWithText("Latest").performClick()
        compose.onNodeWithTag("message-m29").assertIsDisplayed()
        compose.runOnIdle { snapshot.value = snapshot.value.copy(messages = snapshot.value.messages + (30..34).map(::message)) }
        compose.onNodeWithTag("message-m34").assertIsDisplayed()
        compose.onNodeWithTag("timeline").performScrollToIndex(5)
        val top = compose.onNodeWithTag("message-m5").fetchSemanticsNode().boundsInRoot.top
        compose.runOnIdle { snapshot.value = snapshot.value.copy(messages = snapshot.value.messages + message(35)) }
        assertEquals(top, compose.onNodeWithTag("message-m5").fetchSemanticsNode().boundsInRoot.top, 1f)
    }

    @Test fun replyNavigationCentersLoadedTargetAndOffersTransientHighlightToCustomRows() {
        fun message(n: Int) = Message("m$n", Participant("other", "Avery"), n.toLong(), "09:41", "Message $n", Delivery.Sent)
        val highlighted = mutableSetOf<String>()
        compose.setContent {
            androidx.compose.foundation.layout.Box(Modifier.height(400.dp)) {
                Conversation(ConversationState("room", "me", messages = (0..29).map(::message)), {},
                    slots = ConversationSlots(presentedMessage = { row, _, _, presentation ->
                        androidx.compose.runtime.SideEffect { if (presentation.isHighlighted) highlighted.add(row.id) }
                        BasicText(row.text, Modifier.height(48.dp).clickable { presentation.onNavigateToMessage("m12") })
                    }), composerContent = {})
            }
        }
        compose.onNodeWithText("Message 29").performClick()
        val viewport = compose.onNodeWithTag("timeline").fetchSemanticsNode().boundsInRoot
        val target = compose.onNodeWithTag("message-m12").fetchSemanticsNode().boundsInRoot
        assertEquals(viewport.center.y, target.center.y, 2f)
        compose.runOnIdle { assertTrue("m12" in highlighted) }
        compose.onNodeWithTag("message-m29").assertIsNotDisplayed()
    }

    @Test fun replyNavigationTracksStableTargetWhenHistoryIsPrependedDuringTravel() {
        fun message(n: Int) = Message("m$n", Participant("other", "Avery"), n.toLong(), "09:41", "Message $n", Delivery.Sent)
        val snapshot = mutableStateOf(ConversationState("room", "me", messages = (0..99).map(::message)))
        val highlighted = mutableSetOf<String>()
        compose.setContent {
            androidx.compose.foundation.layout.Box(Modifier.height(400.dp)) {
                Conversation(snapshot.value, {},
                    slots = ConversationSlots(presentedMessage = { row, _, _, presentation ->
                        androidx.compose.runtime.SideEffect { if (presentation.isHighlighted) highlighted.add(row.id) }
                        BasicText(row.text, Modifier.height(48.dp).clickable { presentation.onNavigateToMessage("m12") })
                    }), composerContent = {})
            }
        }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Message 99").performClick()
        repeat(4) { compose.mainClock.advanceTimeByFrame() }
        compose.runOnIdle { snapshot.value = snapshot.value.copy(messages = (-40..99).map(::message)) }
        compose.mainClock.advanceTimeBy(1600)
        val viewport = compose.onNodeWithTag("timeline").fetchSemanticsNode().boundsInRoot
        val target = compose.onNodeWithTag("message-m12").fetchSemanticsNode().boundsInRoot
        assertEquals(viewport.center.y, target.center.y, 2f)
        compose.runOnIdle { assertTrue("m12" in highlighted) }
        compose.mainClock.autoAdvance = true
    }

    @Test fun replyNavigationStopsWhenTargetDisappearsOrConversationChanges() {
        fun message(n: Int) = Message("m$n", Participant("other", "Avery"), n.toLong(), "09:41", "Message $n", Delivery.Sent)
        val snapshot = mutableStateOf(ConversationState("room", "me", messages = (0..99).map(::message)))
        val highlighted = mutableSetOf<String>()
        lateinit var navigate: (String) -> Unit
        compose.setContent {
            androidx.compose.foundation.layout.Box(Modifier.height(400.dp)) {
                Conversation(snapshot.value, {},
                    slots = ConversationSlots(presentedMessage = { row, _, _, presentation ->
                        androidx.compose.runtime.SideEffect {
                            navigate = presentation.onNavigateToMessage
                            if (presentation.isHighlighted) highlighted.add(row.id)
                        }
                        BasicText(row.text, Modifier.height(48.dp))
                    }), composerContent = {})
            }
        }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { navigate("m12") }
        repeat(4) { compose.mainClock.advanceTimeByFrame() }
        compose.runOnIdle { snapshot.value = snapshot.value.copy(messages = snapshot.value.messages.filterNot { it.id == "m12" }) }
        compose.mainClock.advanceTimeBy(1200)
        compose.runOnIdle { assertTrue(highlighted.isEmpty()) }
        // Reintroducing the ID must not restart the cancelled request.
        compose.runOnIdle { snapshot.value = snapshot.value.copy(messages = (0..99).map(::message)) }
        compose.mainClock.advanceTimeBy(1200)
        compose.runOnIdle { assertTrue(highlighted.isEmpty()); navigate("m12") }
        repeat(4) { compose.mainClock.advanceTimeByFrame() }
        compose.runOnIdle { snapshot.value = snapshot.value.copy(conversationId = "other") }
        compose.mainClock.advanceTimeBy(1200)
        compose.onNodeWithTag("message-m99").assertIsDisplayed()
        compose.runOnIdle { assertTrue(highlighted.isEmpty()) }
        compose.mainClock.autoAdvance = true
    }

    @Test fun readerDragCancelsReplyNavigationEvenWhenHistoryThenArrives() {
        fun message(n: Int) = Message("m$n", Participant("other", "Avery"), n.toLong(), "09:41", "Message $n", Delivery.Sent)
        val snapshot = mutableStateOf(ConversationState("room", "me", messages = (0..99).map(::message)))
        val highlighted = mutableSetOf<String>()
        compose.setContent {
            androidx.compose.foundation.layout.Box(Modifier.height(400.dp)) {
                Conversation(snapshot.value, {},
                    slots = ConversationSlots(presentedMessage = { row, _, _, presentation ->
                        androidx.compose.runtime.SideEffect { if (presentation.isHighlighted) highlighted.add(row.id) }
                        BasicText(row.text, Modifier.height(48.dp).clickable { presentation.onNavigateToMessage("m12") })
                    }), composerContent = {})
            }
        }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Message 99").performClick()
        repeat(4) { compose.mainClock.advanceTimeByFrame() }
        compose.onNodeWithTag("timeline").performTouchInput { swipeDown(durationMillis = 400) }
        compose.mainClock.advanceTimeBy(1200)
        compose.runOnIdle { snapshot.value = snapshot.value.copy(messages = (-40..99).map(::message)) }
        compose.mainClock.advanceTimeBy(1200)
        compose.runOnIdle { assertTrue(highlighted.isEmpty()) }
        compose.onNodeWithTag("message-m12").assertDoesNotExist()
        compose.mainClock.autoAdvance = true
    }

    @Test fun latestMessageStaysVisibleThroughoutKeyboardViewportContraction() {
        fun message(n: Int) = Message("m$n", Participant("other", "Avery"), n.toLong(), "09:41", "Message $n", Delivery.Sent)
        val height = mutableStateOf(600.dp)
        compose.setContent {
            androidx.compose.foundation.layout.Box(Modifier.height(height.value)) {
                Conversation(ConversationState("room", "me", messages = (0..39).map(::message)), {},
                    messageContent = { row, _, _ -> BasicText(row.text, Modifier.height(48.dp)) },
                    composerContent = {})
            }
        }
        compose.onNodeWithTag("message-m39").assertIsDisplayed()
        for (pixels in listOf(560, 480, 400, 320, 260)) {
            compose.runOnIdle { height.value = pixels.dp }
            compose.onNodeWithTag("message-m39").assertIsDisplayed()
            val viewport = compose.onNodeWithTag("timeline").fetchSemanticsNode().boundsInRoot
            val last = compose.onNodeWithTag("message-m39").fetchSemanticsNode().boundsInRoot
            assertTrue(last.bottom <= viewport.bottom, "Latest message must remain above the keyboard")
        }
        compose.onNodeWithTag("timeline").performScrollToIndex(10)
        val top = compose.onNodeWithTag("message-m10").fetchSemanticsNode().boundsInRoot.top
        compose.runOnIdle { height.value = 200.dp }
        assertEquals(top, compose.onNodeWithTag("message-m10").fetchSemanticsNode().boundsInRoot.top, 1f)
    }

    @Test fun sendingFromHistoryReturnsToLatestAndFollowsAcceptedMessage() {
        fun message(n: Int, sender: String = "other") = Message("m$n", Participant(sender, sender), n.toLong(), "09:41", "Message $n", Delivery.Sent)
        val snapshot = mutableStateOf(ConversationState("room", "me", draft = Draft("Send from history"), messages = (0..39).map(::message)))
        lateinit var composer: ComposerContent
        val events = mutableListOf<ConversationEvent>()
        compose.setContent {
            androidx.compose.foundation.layout.Box(Modifier.height(400.dp)) {
                Conversation(snapshot.value, events::add,
                    messageContent = { row, _, _ -> BasicText(row.text, Modifier.height(48.dp)) },
                    composerContent = { composer = it })
            }
        }
        compose.onNodeWithTag("timeline").performScrollToIndex(10)
        compose.runOnIdle { composer.onSend(); composer.onSend() }
        compose.onNodeWithTag("message-m39").assertIsDisplayed()
        compose.runOnIdle {
            snapshot.value = snapshot.value.copy(draft = Draft(revision = 1), messages = snapshot.value.messages + message(40, "me"))
        }
        compose.onNodeWithTag("message-m40").assertIsDisplayed()
        compose.runOnIdle { snapshot.value = snapshot.value.copy(messages = snapshot.value.messages + message(41)) }
        compose.onNodeWithTag("message-m41").assertIsDisplayed()
        assertEquals(1, events.filterIsInstance<ConversationEvent.SendDraft>().size)
    }

    @Test fun returnToLatestTravelsContinuouslyWithoutTeleportingAcrossLongHistory() {
        fun message(n: Int) = Message("m$n", Participant("other", "Avery"), n.toLong(), "09:41", "Message $n", Delivery.Sent)
        compose.setContent {
            androidx.compose.foundation.layout.Box(Modifier.height(400.dp)) {
                Conversation(ConversationState("room", "me", messages = (0..999).map(::message)), {},
                    slots = ConversationSlots(messageSpacing = { 0.dp }),
                    messageContent = { row, _, _ -> BasicText(row.text, Modifier.height(48.dp)) },
                    composerContent = {})
            }
        }
        compose.onNodeWithTag("timeline").performScrollToIndex(1)
        compose.mainClock.autoAdvance = false
        fun position(): Float {
            val viewport = compose.onNodeWithTag("timeline").fetchSemanticsNode().boundsInRoot
            val node = compose.onAllNodes(SemanticsMatcher("message row") {
                it.config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.TestTag) { "" }.startsWith("message-m")
            }).fetchSemanticsNodes().first { it.boundsInRoot.bottom > viewport.top }
            val number = node.config[androidx.compose.ui.semantics.SemanticsProperties.TestTag].removePrefix("message-m").toInt()
            // Rows are 48dp, at the test density of 1. The absolute origin is irrelevant.
            return number * 48f - node.positionInRoot.y
        }
        var previous = position()
        var movingFrames = 0
        compose.onNodeWithText("Latest").performClick()
        repeat(240) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            val current = position()
            assertTrue(current >= previous - 1f, "Return travel must not reverse")
            assertTrue(current - previous <= 400f + 1f, "Each frame must measure at most one viewport of travel: ${current - previous}")
            if (current > previous + 1f) movingFrames++
            previous = current
        }
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("message-m999").assertIsDisplayed()
        assertTrue(movingFrames >= 8, "Return travel must include intermediate frames")
    }

    @Test fun oversizedLatestMessageKeepsItsTrailingContentAboveTheKeyboard() {
        val height = mutableStateOf(500.dp)
        compose.setContent {
            androidx.compose.foundation.layout.Box(Modifier.height(height.value)) {
                Conversation(ConversationState("room", "me", messages = listOf(
                    Message("large", Participant("me", "You"), 0, "09:41", "Long message", Delivery.Sent))), {},
                    messageContent = { _, _, _ ->
                        androidx.compose.foundation.layout.Column {
                            BasicText("Top", Modifier.height(700.dp))
                            BasicText("Trailing content")
                        }
                    }, composerContent = {})
            }
        }
        compose.onNodeWithText("Trailing content").assertIsDisplayed()
        compose.runOnIdle { height.value = 250.dp }
        compose.onNodeWithText("Trailing content").assertIsDisplayed()
    }

    @Test fun historyScrollInterruptsAnInFlightArrivalWithoutResumingBottomFollow() {
        fun message(n: Int) = Message("m$n", Participant("other", "Avery"), n.toLong(), "09:41", "Message $n", Delivery.Sent)
        val snapshot = mutableStateOf(ConversationState("room", "me", messages = (0..39).map(::message)))
        compose.setContent {
            androidx.compose.foundation.layout.Box(Modifier.height(400.dp)) {
                Conversation(snapshot.value, {},
                    messageContent = { row, _, _ -> BasicText(row.text, Modifier.height(48.dp)) },
                    composerContent = {})
            }
        }
        compose.onNodeWithTag("message-m39").assertIsDisplayed()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { snapshot.value = snapshot.value.copy(messages = snapshot.value.messages + message(40)) }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("timeline").performScrollToKey("m10")
        compose.mainClock.advanceTimeByFrame()
        val top = compose.onNodeWithTag("message-m10").fetchSemanticsNode().boundsInRoot.top
        compose.runOnIdle { snapshot.value = snapshot.value.copy(messages = snapshot.value.messages + message(41)) }
        compose.mainClock.advanceTimeBy(600)
        compose.mainClock.autoAdvance = true
        assertEquals(top, compose.onNodeWithTag("message-m10").fetchSemanticsNode().boundsInRoot.top, 1f)
    }

    @Test fun boundaryRequestsOnceAndFailureRequiresExplicitRetry() {
        val history = mutableStateOf(HistoryState(HistoryStatus.Idle))
        val requests = mutableListOf<HistoryRequest>()
        lateinit var controls: HistoryContent
        compose.setContent {
            Conversation(ConversationState("room", "me"), {},
                messageContent = { row, _, _ -> BasicText(row.text) }, composerContent = {},
                history = history.value, onLoadEarlier = requests::add,
                historyContent = { controls = it })
        }
        compose.runOnIdle {
            assertEquals(listOf(HistoryRequest("room", 0)), requests)
            controls.onLoadEarlier()
            assertEquals(1, requests.size)
            history.value = HistoryState(HistoryStatus.Error, 1)
        }
        compose.runOnIdle { assertEquals(1, requests.size); controls.onLoadEarlier(); controls.onLoadEarlier() }
        compose.runOnIdle {
            assertEquals(listOf(HistoryRequest("room", 0), HistoryRequest("room", 1)), requests)
            history.value = HistoryState(HistoryStatus.Exhausted, 2)
        }
        compose.runOnIdle { controls.onLoadEarlier(); assertEquals(2, requests.size) }
    }
}
