package dev.forntoh.chat.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

/**
 * Space occupied by overlaid host chrome, supplied by a surrounding shell. [Conversation] keeps
 * its timeline behind these areas, adds top clearance to scrollable content and bottom clearance
 * around its composer, and centers reply targets in the remaining visible region. Defaults to
 * zero for standalone use. The shell consumes the corresponding window insets once; hosts must
 * not also pad Conversation with these values. Custom composers may read the bottom value to
 * avoid adding a second decorative margin to an already sufficient system safe-area gap.
 */
val LocalConversationViewportPadding = staticCompositionLocalOf { PaddingValues(0.dp) }
