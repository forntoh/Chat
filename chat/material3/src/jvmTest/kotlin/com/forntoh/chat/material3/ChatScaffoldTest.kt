package com.forntoh.chat.material3

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.forntoh.chat.core.*
import com.forntoh.chat.ui.Conversation
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

/** Public host integration: scaffold chrome never takes ownership of draft submission. */
class ChatScaffoldTest {
    @get:Rule val compose = createComposeRule()

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
