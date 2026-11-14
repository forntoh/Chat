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
        compose.onNodeWithTag("timeline").performScrollToIndex(450)
        compose.onNodeWithTag("timeline").performScrollToIndex(451)
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
