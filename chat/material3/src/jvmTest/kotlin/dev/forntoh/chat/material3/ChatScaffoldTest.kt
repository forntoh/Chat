package dev.forntoh.chat.material3

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.rows
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.bottom
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.height
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.top
import androidx.compose.ui.unit.dp
import dev.forntoh.chat.core.ConversationEvent
import dev.forntoh.chat.core.ConversationState
import dev.forntoh.chat.core.Delivery
import dev.forntoh.chat.core.Draft
import dev.forntoh.chat.core.Message
import dev.forntoh.chat.core.Participant
import dev.forntoh.chat.ui.Conversation
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Public host integration: scaffold chrome never takes ownership of draft submission. */
class ChatScaffoldTest {
    @get:Rule val compose = createComposeRule()

    @Test fun materialHeaderOverlaysTimelineAndFirstContentStartsBelowIt() {
        var expectedHeaderColor = Color.Unspecified
        val rows = (0..25).map { Message("m$it", Participant("other", "Avery"), 0, "now", "Message $it", Delivery.Sent) }
        compose.setContent {
            Box(Modifier.size(360.dp, 640.dp)) {
                ChatTheme {
                    expectedHeaderColor = TopAppBarDefaults.topAppBarColors().containerColor
                    ChatScaffold(title = { Text("Conversation", Modifier.testTag("header-title")) }) {
                        Conversation(
                            ConversationState("room", "me", messages = rows),
                            {},
                            messageContent = { row, _, _ ->
                                Box(Modifier.fillMaxWidth().height(60.dp).background(Color.Cyan)) { Text(row.text) }
                            },
                        )
                    }
                }
            }
        }
        val timeline = compose.onNodeWithTag("timeline").fetchSemanticsNode().boundsInRoot
        val header = compose.onNodeWithTag("header-title").fetchSemanticsNode().boundsInRoot
        assertTrue(timeline.top < header.top, "Timeline must continue behind the navigation bar")
        compose.onNodeWithTag("timeline").performScrollToIndex(0)
        val first = compose.onNodeWithTag("message-m0").fetchSemanticsNode().boundsInRoot
        assertTrue(first.top > header.bottom, "First message must clear the overlaid header")
        compose.onNodeWithTag("timeline").performScrollToIndex(10).performTouchInput { swipeUp() }
        val pixels = compose.onAllNodes(isRoot()).onFirst().captureToImage().toPixelMap()
        assertEquals(
            expectedHeaderColor,
            pixels[330, 32],
            "Header must retain the ambient Material background over scrolling content",
        )
    }

    @Test fun bottomSafeAreaReplacesDecorativeComposerMargin() {
        compose.setContent {
            Box(Modifier.size(360.dp, 640.dp)) {
                ChatTheme {
                    ChatScaffold(title = { Text("Conversation") }, contentWindowInsets = WindowInsets(bottom = 34.dp)) {
                        Conversation(ConversationState("room", "me"), {})
                    }
                }
            }
        }
        val field = compose.onNodeWithTag("composer").fetchSemanticsNode().boundsInRoot
        assertEquals(
            602f,
            field.bottom,
            1f,
            "Only the 34dp safe area and 4dp inner surface inset should separate the field from the bottom",
        )
        val timeline = compose.onNodeWithTag("timeline").fetchSemanticsNode().boundsInRoot
        assertEquals(640f, timeline.bottom, 1f, "Timeline backgrounds extend through the bottom safe area")
    }

    @Test fun hostIdentityChangesKeepConversationMountedAndSubmissionGuarded() {
        val title = mutableStateOf("Avery")
        val customHeader = mutableStateOf(false)
        val events = mutableListOf<ConversationEvent>()
        val state = ConversationState("room", "me", Draft("Hello\nAvery", 7))
        compose.setContent {
            Box(Modifier.size(360.dp, 640.dp)) {
                ChatTheme {
                    ChatScaffold(
                        title = { Text(title.value) },
                        avatar = { Text("AC") },
                        actions = {
                            TextButton(onClick = {
                                title.value = "Design team"
                                customHeader.value = true
                            }) { Text("Rename") }
                        },
                        topBar = if (customHeader.value) {
                            { Text("Custom ${title.value}") }
                        } else {
                            null
                        },
                        bottomBar = { Text("Host footer") },
                    ) {
                        Conversation(state, events::add)
                    }
                }
            }
        }
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
