package dev.chat.consumer

import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.forntoh.chat.core.*
import com.forntoh.chat.media.*
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class MediaTest {
    @get:Rule val compose = createComposeRule()
    @Test fun stagedMediaConsumerSubmitsImageOnlyWithoutActionsOrMaterial3() {
        val image = Attachment("image", "image/png", "host-bundle")
        val events = mutableListOf<ConversationEvent>()
        compose.setContent { Box(Modifier.size(360.dp, 600.dp)) {
            MediaConversation(ConversationState("consumer", "me", Draft(attachments = listOf(image))),
                MediaState(), events::add, {})
        } }
        compose.onNodeWithText("Unsupported attachment").assertExists()
        compose.onNodeWithText("Send").performClick().performClick()
        compose.runOnIdle { assertEquals(listOf<ConversationEvent>(ConversationEvent.SendDraft("consumer", 0, "", listOf(image))), events) }
    }
}
