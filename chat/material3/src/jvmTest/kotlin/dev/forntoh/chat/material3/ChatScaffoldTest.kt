package dev.forntoh.chat.material3

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import kotlin.test.assertTrue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import dev.forntoh.chat.core.*
import dev.forntoh.chat.ui.Conversation
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

/** Public host integration: scaffold chrome never takes ownership of draft submission. */
class ChatScaffoldTest {
    @get:Rule val compose = createComposeRule()

    @Test fun transparentHeaderOverlaysTimelineAndFirstContentStartsBelowIt() {
        val rows = (0..25).map { Message("m$it", Participant("other", "Avery"), 0, "now", "Message $it", Delivery.Sent) }
        compose.setContent { Box(Modifier.size(360.dp, 640.dp)) { ChatTheme {
            ChatScaffold(title = { Text("Conversation", Modifier.testTag("header-title")) }) {
                Conversation(ConversationState("room", "me", messages = rows), {},
                    messageContent = { row, _, _ -> Box(Modifier.fillMaxWidth().height(60.dp).background(Color.Cyan)) { Text(row.text) } })
            }
        } } }
        val timeline = compose.onNodeWithTag("timeline").fetchSemanticsNode().boundsInRoot
        val header = compose.onNodeWithTag("header-title").fetchSemanticsNode().boundsInRoot
        assertTrue(timeline.top < header.top, "Timeline must continue behind the navigation bar")
        compose.onNodeWithTag("timeline").performScrollToIndex(0)
        val first = compose.onNodeWithTag("message-m0").fetchSemanticsNode().boundsInRoot
        assertTrue(first.top > header.bottom, "First message must clear the overlaid header")
        compose.onNodeWithTag("timeline").performScrollToIndex(10).performTouchInput { swipeUp() }
        val pixels = compose.onAllNodes(isRoot()).onFirst().captureToImage().toPixelMap()
        assertTrue((0 until 64).count { pixels[330, it] == Color.Cyan } > 20, "Header must leave timeline content visible through its background")
    }

    @Test fun bottomSafeAreaReplacesDecorativeComposerMargin() {
        compose.setContent { Box(Modifier.size(360.dp, 640.dp)) { ChatTheme {
            ChatScaffold(title = { Text("Conversation") }, contentWindowInsets = WindowInsets(bottom = 34.dp)) {
                Conversation(ConversationState("room", "me"), {})
            }
        } } }
        val field = compose.onNodeWithTag("composer").fetchSemanticsNode().boundsInRoot
        assertEquals(602f, field.bottom, 1f,
            "Only the 34dp safe area and 4dp inner surface inset should separate the field from the bottom")
        val timeline = compose.onNodeWithTag("timeline").fetchSemanticsNode().boundsInRoot
        assertEquals(640f, timeline.bottom, 1f, "Timeline backgrounds extend through the bottom safe area")
    }

    @Test fun hostIdentityChangesKeepConversationMountedAndSubmissionGuarded() {
        val title = mutableStateOf("Avery")
        val customHeader = mutableStateOf(false)
        val events = mutableListOf<ConversationEvent>()
        val state = ConversationState("room", "me", Draft("Hello\nAvery", 7))
        compose.setContent { Box(Modifier.size(360.dp, 640.dp)) { ChatTheme {
            ChatScaffold(title = { Text(title.value) }, avatar = { Text("AC") },
                actions = { TextButton(onClick = { title.value = "Design team"; customHeader.value = true }) { Text("Rename") } },
                topBar = if (customHeader.value) { { Text("Custom ${title.value}") } } else null,
                bottomBar = { Text("Host footer") },
            ) {
                Conversation(state, events::add)
            }
        } } }
        compose.onNodeWithText("Avery").assertIsDisplayed()
        compose.onNodeWithText("AC").assertIsDisplayed()
        compose.onNodeWithContentDescription("Send").performClick()
        compose.onNodeWithText("Rename").performClick()
        compose.onNodeWithText("Custom Design team").assertIsDisplayed()
        compose.onNodeWithText("AC").assertDoesNotExist()
        compose.onNodeWithText("Host footer").assertIsDisplayed()
        compose.onNodeWithContentDescription("Send").assertIsNotEnabled().performClick()
        compose.onNodeWithTag("composer").assertTextContains("Hello\nAvery")
        compose.runOnIdle { assertEquals<List<ConversationEvent>>(listOf(ConversationEvent.SendDraft("room", 7, "Hello\nAvery")), events) }
    }
}
