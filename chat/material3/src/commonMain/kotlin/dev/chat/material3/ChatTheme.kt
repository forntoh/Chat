package dev.chat.material3

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import dev.chat.ui.ConversationSlots
import dev.chat.ui.ConversationTheme

/** Portable light reference palette. Pass a host ColorScheme to ChatTheme for other palettes. */
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

/**
 * Reference chat palette and renderers around the one shared Conversation, with Tabler icons.
 * Override colors/typography here and derive [slots] with chatConversationSlots().copy(...).
 * Explicit bundles and renderer arguments at Conversation retain precedence over this provider.
 * No service, actions/media module, navigation or second draft engine is installed. Keep the
 * shared component mounted across theme/title changes to preserve submission/history guards.
 */
@Composable
fun ChatTheme(
    colorScheme: ColorScheme = chatColorScheme(),
    typography: Typography = MaterialTheme.typography,
    slots: ConversationSlots = chatConversationSlots(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(colorScheme = colorScheme, typography = typography) {
        ConversationTheme(slots, content)
    }
}
