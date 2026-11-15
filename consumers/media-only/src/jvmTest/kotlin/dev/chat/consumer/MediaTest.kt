package dev.chat.consumer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import dev.forntoh.chat.core.Attachment
import dev.forntoh.chat.core.ConversationEvent
import dev.forntoh.chat.core.ConversationState
import dev.forntoh.chat.core.Draft
import dev.forntoh.chat.media.MediaState
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class MediaTest {
    @get:Rule val compose = createComposeRule()

    @Test fun stagedMediaConsumerSubmitsImageOnlyWithoutActionsOrMaterial3() {
        val image = Attachment("image", "image/png", "host-bundle")
        val events = mutableListOf<ConversationEvent>()
        compose.setContent {
            Box(Modifier.size(360.dp, 600.dp)) {
                MediaConversation(
                    ConversationState("consumer", "me", Draft(attachments = listOf(image))),
                    MediaState(),
                    events::add,
                    {},
                )
            }
        }
        compose.onNodeWithText("Unsupported attachment").assertExists()
        compose.onNodeWithText("Send").performClick().performClick()
        compose.runOnIdle { assertEquals(listOf<ConversationEvent>(ConversationEvent.SendDraft("consumer", 0, "", listOf(image))), events) }
    }
}
