package dev.forntoh.chat.ui

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Ephemeral row presentation, never host message/delivery state. [isHighlighted] is true for
 * 900ms after a reply navigation settles; consumers may animate it or render immediately.
 * [onNavigateToMessage] centers a loaded stable ID, bounded by available scroll range.
 * Missing IDs are ignored: it never fetches history or changes host state. A new activation
 * cancels prior travel/highlight; conversation changes cancel all presentation work.
 * Call navigation only from user input. Do not retain this callback across conversations.
 */
class MessagePresentation(val isHighlighted: Boolean = false, val onNavigateToMessage: (String) -> Unit = {})

/** Also available to existing three-argument row slots and nested optional renderers. */
val LocalMessagePresentation = staticCompositionLocalOf { MessagePresentation() }
