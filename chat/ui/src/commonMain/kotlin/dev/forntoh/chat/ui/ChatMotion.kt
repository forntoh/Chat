package dev.forntoh.chat.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics

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
        require(latestMillis in 0..MAX_LATEST_MILLIS)
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

/** Finite fade for content whose presentation does not depend on visibility. For changing
 * presentation data, use the snapshot overload so exiting pixels retain their prior value. */
@Composable
fun ChatMotionVisibility(visible: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) = ChatMotionVisibility(
    value = if (visible) Unit else null,
    modifier = modifier,
    content = { content() },
)

/**
 * Finite fade retaining the last non-null immutable presentation [value] until exit finishes.
 * Render only from the supplied snapshot in [content], rather than a captured live host value.
 * Exiting semantics and pointer activation disappear immediately; callbacks must also retain
 * their ordinary host guards. [initiallyVisible] can be false for a newly inserted identity.
 * No completion emits an intent. Key this subtree by conversation
 * identity outside Conversation. The retained snapshot is released after exit or subtree disposal.
 */
@Composable
fun <T : Any> ChatMotionVisibility(
    value: T?,
    modifier: Modifier = Modifier,
    initiallyVisible: Boolean = value != null,
    content: @Composable (T) -> Unit,
) {
    val motion = LocalChatMotion.current
    if (!motion.enabled || motion.controlMillis == 0) {
        if (value != null) Box(modifier) { content(value) }
        return
    }
    val retained = remember { mutableStateOf(value) }
    val visibility = remember { MutableTransitionState(initiallyVisible) }
    visibility.targetState = value != null
    SideEffect { if (value != null) retained.value = value }
    LaunchedEffect(visibility.isIdle, visibility.currentState) {
        if (visibility.isIdle && !visibility.currentState) retained.value = null
    }
    AnimatedVisibility(
        visibleState = visibility,
        modifier = modifier,
        enter = fadeIn(tween(motion.controlMillis, easing = motion.easing)),
        exit = fadeOut(tween(motion.controlMillis, easing = motion.easing)),
    ) {
        Box(
            if (value != null) {
                Modifier
            } else {
                Modifier
                    .clearAndSetSemantics { }
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent(PointerEventPass.Initial)
                                    .changes.forEach { it.consume() }
                            }
                        }
                    }
            },
        ) { (value ?: retained.value)?.let { content(it) } }
    }
}

private const val MAX_LATEST_MILLIS = 360
