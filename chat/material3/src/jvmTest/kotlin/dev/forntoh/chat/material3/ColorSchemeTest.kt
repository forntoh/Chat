package dev.forntoh.chat.material3

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ColorSchemeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun darkPaletteKeepsTextReadableOnChatSurfacesAndBubbles() {
        val colors = chatColorScheme(true)
        assertTrue(colors.surface.luminance() < .02f)
        for ((foreground, background) in listOf(
            colors.onSurface to colors.surface,
            colors.onSurface to colors.surfaceContainerHighest,
            colors.onSurfaceVariant to colors.surfaceContainerHighest,
            colors.onPrimary to colors.primary,
            colors.inversePrimary to colors.primary,
            colors.onSecondaryContainer to colors.secondaryContainer,
        )) {
            val a = foreground.luminance(); val b = background.luminance()
            assertTrue((maxOf(a, b) + .05f) / (minOf(a, b) + .05f) >= 4.5f)
        }
    }

    @Test fun explicitHostSchemeRetainsPrecedence() {
        val host = chatColorScheme(true).copy(surface = Color(0xFF193625))
        var observed = Color.Unspecified
        compose.setContent { ChatTheme(colorScheme = host) {
            val surface = MaterialTheme.colorScheme.surface
            SideEffect { observed = surface }
        } }
        compose.runOnIdle { assertEquals(host.surface, observed) }
    }
}
