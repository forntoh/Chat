package dev.forntoh.chat.material3

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp

@Composable
internal actual fun defaultChatWindowInsets(): WindowInsets = WindowInsets.safeDrawing

internal actual fun defaultComposerGutter(): Dp = ChatGeometry.Gutter
