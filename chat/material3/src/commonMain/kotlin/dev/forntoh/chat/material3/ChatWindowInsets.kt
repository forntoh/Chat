package dev.forntoh.chat.material3

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp

@Composable
internal expect fun defaultChatWindowInsets(): WindowInsets

internal expect fun defaultComposerGutter(): Dp
