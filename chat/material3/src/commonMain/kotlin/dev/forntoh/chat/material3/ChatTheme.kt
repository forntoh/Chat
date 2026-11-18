package dev.forntoh.chat.material3

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import dev.forntoh.chat.ui.ConversationSlots
import dev.forntoh.chat.ui.ConversationTheme

fun chatColorScheme(): ColorScheme = lightColorScheme(
    primary = Color(color = 0xFF4D5DFF),
    onPrimary = Color.White,
    primaryContainer = Color(color = 0xFFE5E8FF),
    onPrimaryContainer = Color(color = 0xFF152078),
    secondary = Color(color = 0xFF5D6380),
    onSecondary = Color.White,
    secondaryContainer = Color(color = 0xFFE8EAFA),
    onSecondaryContainer = Color(color = 0xFF383E66),
    surface = Color.White,
    onSurface = Color(color = 0xFF202126),
    background = Color.White,
    onBackground = Color(color = 0xFF202126),
    surfaceContainer = Color(color = 0xFFF5F5F6),
    surfaceContainerHighest = Color(color = 0xFFEEEEF0),
    onSurfaceVariant = Color(color = 0xFF71747E),
    outline = Color(color = 0xFF9699A2),
    outlineVariant = Color(color = 0xFFDFE0E5),
)

fun chatColorScheme(darkTheme: Boolean): ColorScheme = if (!darkTheme) {
    chatColorScheme()
} else {
    darkColorScheme(
        primary = Color(color = 0xFFB8C2FF),
        onPrimary = Color(color = 0xFF152078),
        primaryContainer = Color(color = 0xFF303D9C),
        onPrimaryContainer = Color(color = 0xFFE5E8FF),
        secondary = Color(color = 0xFFC0C5E5),
        onSecondary = Color(color = 0xFF292F50),
        secondaryContainer = Color(color = 0xFF383E59),
        onSecondaryContainer = Color(color = 0xFFE8EAFA),
        surface = Color(color = 0xFF121318),
        onSurface = Color(color = 0xFFE4E4EC),
        background = Color(color = 0xFF121318),
        onBackground = Color(color = 0xFFE4E4EC),
        surfaceContainerLow = Color(color = 0xFF1A1B22),
        surfaceContainer = Color(color = 0xFF202129),
        surfaceContainerHigh = Color(color = 0xFF262730),
        surfaceContainerHighest = Color(color = 0xFF30313B),
        onSurfaceVariant = Color(color = 0xFFC4C5D0),
        outline = Color(color = 0xFF8E909D),
        outlineVariant = Color(color = 0xFF454752),
        inversePrimary = Color(color = 0xFF152078),
    )
}

/**
 * Reference chat palette and renderers around the one shared Conversation, with Tabler icons.
 * Override colors/typography here and derive [slots] with chatConversationSlots().copy(...).
 * Explicit bundles and renderer arguments at Conversation retain precedence over this provider.
 * No service, actions/media module, navigation or second draft engine is installed. Keep the
 * shared component mounted across theme/title changes to preserve submission/history guards.
 */
@Composable
fun ChatTheme(
    colorScheme: ColorScheme = chatColorScheme(isSystemInDarkTheme()),
    typography: Typography = MaterialTheme.typography,
    slots: ConversationSlots = chatConversationSlots(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(colorScheme = colorScheme, typography = typography) {
        ConversationTheme(slots, content)
    }
}
