package dev.forntoh.chat.material3

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable

/** Keep the floating surface's bottom gutter equal to its sides, above the home indicator.
 * The keyboard remains a separate full-height inset with the composer's explicit gap. */
@Composable
internal actual fun defaultChatWindowInsets(): WindowInsets =
    WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
        .union(WindowInsets(bottom = ChatGeometry.Gutter))
        .union(WindowInsets.ime)
