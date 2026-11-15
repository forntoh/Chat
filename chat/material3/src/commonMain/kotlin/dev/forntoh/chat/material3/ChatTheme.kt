package dev.forntoh.chat.material3

import androidx.compose.material3.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import dev.forntoh.chat.ui.ConversationSlots
import dev.forntoh.chat.ui.ConversationTheme

fun chatColorScheme(): ColorScheme = lightColorScheme(
    primary = Color(0xFF4D5DFF), onPrimary = Color.White,
    primaryContainer = Color(0xFFE5E8FF), onPrimaryContainer = Color(0xFF152078),
    secondary = Color(0xFF5D6380), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8EAFA), onSecondaryContainer = Color(0xFF383E66),
    surface = Color.White, onSurface = Color(0xFF202126),
    background = Color.White, onBackground = Color(0xFF202126),
    surfaceContainer = Color(0xFFF5F5F6), surfaceContainerHighest = Color(0xFFEEEEF0),
    onSurfaceVariant = Color(0xFF71747E), outline = Color(0xFF9699A2), outlineVariant = Color(0xFFDFE0E5),
)

fun chatColorScheme(darkTheme: Boolean): ColorScheme = if (!darkTheme) chatColorScheme() else darkColorScheme(
    primary = Color(0xFFB8C2FF), onPrimary = Color(0xFF152078),
    primaryContainer = Color(0xFF303D9C), onPrimaryContainer = Color(0xFFE5E8FF),
    secondary = Color(0xFFC0C5E5), onSecondary = Color(0xFF292F50),
    secondaryContainer = Color(0xFF383E59), onSecondaryContainer = Color(0xFFE8EAFA),
    surface = Color(0xFF121318), onSurface = Color(0xFFE4E4EC),
    background = Color(0xFF121318), onBackground = Color(0xFFE4E4EC),
    surfaceContainerLow = Color(0xFF1A1B22), surfaceContainer = Color(0xFF202129),
    surfaceContainerHigh = Color(0xFF262730), surfaceContainerHighest = Color(0xFF30313B),
    onSurfaceVariant = Color(0xFFC4C5D0), outline = Color(0xFF8E909D), outlineVariant = Color(0xFF454752),
    inversePrimary = Color(0xFF152078),
)

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
