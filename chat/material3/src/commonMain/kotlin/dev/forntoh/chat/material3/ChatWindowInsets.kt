package dev.forntoh.chat.material3

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable

/** Platform default chrome clearance; hosts may override ChatScaffold.contentWindowInsets. */
@Composable
internal expect fun defaultChatWindowInsets(): WindowInsets

/** Outer composer clearance follows the native screen corner geometry. */
internal expect fun defaultComposerGutter(): androidx.compose.ui.unit.Dp
