package dev.forntoh.chat.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass

/**
 * Presentation-only timing shared by the timeline and optional renderers. Provide [LocalChatMotion]
 * around a conversation (and its optional action/media content) to override or disable motion.
 * Durations are animation-clock milliseconds, also subject to Compose's platform duration scale;
 * a zero system scale or [enabled] = false settles immediately. No completion callback drives host
 * state. [latestMillis] cannot exceed the existing 360ms animation-clock cap.
 *
 * Controls use 180ms; insertion and bounded content sizing use 240ms, with no overshoot. Native
 * window/IME insets sit outside the size animation. Custom renderers may read these tokens;
 * their own animations and platform controls remain their responsibility.
 */
data class ChatMotion(
    val enabled: Boolean = true,
    val controlMillis: Int = 180,
    val sizeMillis: Int = 240,
    val insertionMillis: Int = 240,
    val latestMillis: Int = 360,
    val easing: Easing = FastOutSlowInEasing,
) {
    init {
        require(controlMillis >= 0 && sizeMillis >= 0 && insertionMillis >= 0)
        require(latestMillis in 0..360)
    }
    fun duration(millis: Int): Int = if (enabled) millis else 0
}

val LocalChatMotion = staticCompositionLocalOf { ChatMotion() }

@Composable
fun Modifier.chatContentSize(): Modifier {
    val motion = LocalChatMotion.current
    if (!motion.enabled || motion.sizeMillis == 0) return this
    return animateContentSize(
        animationSpec = tween(motion.duration(motion.sizeMillis), easing = motion.easing),
        alignment = Alignment.BottomStart,
    )
}

/**
 * Finite fade for optional composer context/loading content. Exiting pixels retain their last
 * rendered content until the fade ends, with semantics and pointer activation removed immediately.
 * Supply guarded callbacks (as with ordinary slots); no completion emits an intent. The enclosing
 * Conversation coordinates the resulting height change. Key this subtree by conversation identity
 * when used outside Conversation, so exiting content cannot survive a conversation switch.
 */
@Composable
fun ChatMotionVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val motion = LocalChatMotion.current
    if (!motion.enabled || motion.controlMillis == 0) {
        if (visible) Box(modifier) { content() }
        return
    }
    val retained = remember { MotionContent(content) }
    SideEffect { if (visible) retained.content = content }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(motion.duration(motion.controlMillis), easing = motion.easing)),
        exit = fadeOut(tween(motion.duration(motion.controlMillis), easing = motion.easing)),
    ) {
        Box(
            if (visible) Modifier else Modifier
                .clearAndSetSemantics { }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) awaitPointerEvent(PointerEventPass.Initial)
                            .changes.forEach { it.consume() }
                    }
                }
        ) { if (visible) content() else retained.content() }
    }
}

private class MotionContent(var content: @Composable () -> Unit)
