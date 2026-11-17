package dev.forntoh.chat.ui

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Ephemeral row presentation, never host message/delivery state. A new activation
 * cancels prior travel/highlight; conversation changes cancel all presentation work.
 * Call navigation only from user input. Do not retain this callback across conversations.
 *
 * @param isHighlighted True for 900ms after a reply navigation settles; consumers may animate it or render immediately.
 * @param onNavigateToMessage Centers a loaded stable ID, bounded by available scroll range.
 * Missing IDs are ignored: it never fetches history or changes host state.
 */
class MessagePresentation(
    val isHighlighted: Boolean = false,
    val onNavigateToMessage: (String) -> Unit = {},
)

val LocalMessagePresentation = staticCompositionLocalOf { MessagePresentation() }
