package dev.forntoh.chat.material3

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Keep the floating surface's bottom gutter equal to its sides, above the home indicator.
 * The keyboard remains a separate full-height inset with the composer's explicit gap. */
@Composable
internal actual fun defaultChatWindowInsets(): WindowInsets =
    WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
        .union(WindowInsets(bottom = defaultComposerGutter()))
        .union(WindowInsets.ime)

/** 26pt clearance approximates the owner reference around the lower screen corners. */
internal actual fun defaultComposerGutter(): Dp = 26.dp
