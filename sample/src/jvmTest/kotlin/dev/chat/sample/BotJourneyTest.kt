package dev.chat.sample

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BotJourneyTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun normalLaunchKeepsDraftWhenThemeChangesWithoutDiagnosticControls() {
        compose.setContent { Box(Modifier.size(402.dp, 874.dp)) { SampleApp() } }
        compose.onNodeWithTag("demo-menu").assertDoesNotExist()
        compose.onNodeWithTag("theme-switch").assertIsDisplayed()
        compose.onNodeWithTag("composer").performTextInput("Meet by the lake")
        compose.onNodeWithTag("theme-switch").performClick()
        compose.onNodeWithTag("composer").assertTextContains("Meet by the lake")
    }

    @Test
    fun botAndUserImagesAndCustomContentShareOneConversation() {
        val host = BotHost(42)
        compose.setContent { Box(Modifier.size(402.dp, 874.dp)) { SampleApp(host) } }
        compose.runOnIdle {
            repeat(225) { if (host.suggestions.isEmpty()) host.advanceBy(100) }
            assertTrue(host.suggestions.isNotEmpty())
        }
        compose.onNodeWithTag("trail-suggestion").assertIsDisplayed()
        val imageIndex = host.state.messages.indexOfFirst { it.attachments.isNotEmpty() }
        compose.onNodeWithTag("timeline").performScrollToIndex(imageIndex + 1)
        compose.onNodeWithContentDescription("Mountain morning illustration").assertIsDisplayed().performClick()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithContentDescription("Attach image").performClick()
        compose.onNodeWithText("Attach image").performClick()
        compose.onNodeWithContentDescription("Send").performClick()
        compose.runOnIdle {
            assertEquals("me", host.state.messages.last().sender.id)
            assertEquals(1, host.state.messages.last().attachments.size)
            assertTrue(host.state.messages.any { it.sender.id == "bot" && it.attachments.isNotEmpty() })
        }
        compose.onNodeWithText("Message unavailable").assertDoesNotExist()
        compose.onNodeWithContentDescription("Bundled mountain illustration").assertIsDisplayed().performClick()
        compose.onNodeWithText("Close").assertIsDisplayed().performClick()
    }

    @Test
    fun themeRetainsReadingAnchorAndLeavingCancelsScheduledWork() {
        val host = BotHost(42)
        val shown = mutableStateOf(true)
        compose.setContent { if (shown.value) Box(Modifier.size(402.dp, 874.dp)) { SampleApp(host) } }
        compose.runOnIdle { repeat(3) { host.advanceBy(60_000) } }
        compose.onNodeWithTag("timeline").performScrollToIndex(2)
        val anchor = "message-${host.state.messages[1].id}"
        val before = compose.onNodeWithTag(anchor).fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithTag("theme-switch").performClick()
        val after = compose.onNodeWithTag(anchor).fetchSemanticsNode().boundsInRoot.top
        assertEquals(before, after, 1f)
        compose.runOnIdle { shown.value = false }
        compose.runOnIdle {
            val disposed = host.state
            host.advanceBy(60_000)
            assertEquals(disposed, host.state)
        }
    }
}
