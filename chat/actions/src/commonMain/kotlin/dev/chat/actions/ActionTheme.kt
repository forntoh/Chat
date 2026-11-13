package dev.chat.actions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chat.core.Message

/**
 * Foundation defaults for every action surface. Supply a host text style (including color),
 * control background/shape and spacing, or replace renderers in [ActionSlots]. Material3 hosts
 * can map their MaterialTheme values here without adding Material3 to the actions dependency.
 * [minimumControlSize] defaults to a 48 dp target; custom controls own equivalent semantics.
 */
data class ActionStyle(
    val textStyle: TextStyle = TextStyle.Default,
    val controlBackground: Color = Color.Transparent,
    val controlShape: Shape = RectangleShape,
    val controlPadding: PaddingValues = PaddingValues(8.dp),
    val minimumControlSize: Dp = 48.dp,
)

/** Guarded activation for a rendered control. Custom controls must honor [enabled] and call
 * [onClick] only on activation, supplying their own keyboard, focus and accessibility behavior.
 * [label] is already resolved through the caller's [ActionLabels]. */
data class ActionControlContent(val label: String, val enabled: Boolean, val onClick: () -> Unit)

/** Latest quote target, or null if unavailable. [text] includes the sender/excerpt or the host's
 * localized fallback. Keep unavailable quotes visible; rendering must not clear host state. */
data class ActionQuoteContent(val targetId: String, val message: Message?, val text: String)

/**
 * Replaceable presentation for all action surfaces, with foundation defaults. Containers receive
 * their guarded child content and should render it once; they may change layout/decoration.
 * [control] receives labels, disabled state and guarded activation. [text] renders status,
 * pending/error and prompt copy. [quote] receives the resolved target and unavailable fallback.
 * Renderers run during composition and must never dispatch actions at that time. Replacements
 * own semantics and focus; presentation does not change the host's authority or request guards.
 *
 * Set these once with [ActionTheme], or pass an explicit set to an individual surface. A locally
 * supplied set takes precedence; use `LocalActionSlots.current.copy(...)` to replace one slot
 * while retaining the surrounding theme. No slots retain messages or draft state.
 */
data class ActionSlots(
    val control: @Composable (ActionControlContent) -> Unit = { ActionControl(it.label, it.enabled, it.onClick) },
    val text: @Composable (String) -> Unit = { BasicText(it, style = LocalActionStyle.current.textStyle) },
    val quote: @Composable (ActionQuoteContent) -> Unit = { LocalActionSlots.current.text(it.text) },
    val menu: @Composable (@Composable () -> Unit) -> Unit = { Column { it() } },
    val composerStatus: @Composable (@Composable () -> Unit) -> Unit = { Column { it() } },
    val confirmation: @Composable (@Composable () -> Unit) -> Unit = { Column { it() } },
)

/** Foundation style read by default controls/text; nearest [ActionTheme] wins. */
val LocalActionStyle = staticCompositionLocalOf { ActionStyle() }

/** Default renderers inherited by all nested action surfaces, including quotes/confirmation. */
val LocalActionSlots = staticCompositionLocalOf { ActionSlots() }

/**
 * Scoped, optional actions theme, independent of Material3. Unspecified arguments inherit the
 * enclosing actions theme. Theme/slot replacement at the same composition location never emits
 * an intent or resets the host's action/draft state. Explicit surface slots override this theme.
 *
 * ```kotlin
 * ActionTheme(slots = LocalActionSlots.current.copy(control = { button ->
 *     HostButton(button.label, button.enabled, button.onClick)
 * })) { MessageActions(actions, message.id) }
 * ```
 */
@Composable
fun ActionTheme(
    style: ActionStyle = LocalActionStyle.current,
    slots: ActionSlots = LocalActionSlots.current,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalActionStyle provides style, LocalActionSlots provides slots, content = content)
}

/**
 * Foundation default control, usable directly from custom slots without invoking the control
 * slot recursively. Reads [LocalActionStyle]; [label] is the accessible visible button name.
 * Disabled controls suppress activation. Hosts replacing this own equivalent input semantics.
 */
@Composable
fun ActionControl(label: String, enabled: Boolean, onClick: () -> Unit) {
    val style = LocalActionStyle.current
    Box(Modifier.sizeIn(minWidth = style.minimumControlSize, minHeight = style.minimumControlSize)
        .background(style.controlBackground, style.controlShape)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(style.controlPadding)) {
        BasicText(label, style = style.textStyle)
    }
}
