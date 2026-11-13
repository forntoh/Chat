package com.forntoh.chat.actions

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
import com.forntoh.chat.core.Message

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

/** Guarded explicit action. Custom controls author their label from [command], honor [enabled],
 * and supply focus/keyboard/button semantics. Invoke [onClick] only on activation. */
data class ActionControlContent(val command: ActionCommand, val enabled: Boolean, val onClick: () -> Unit)

/** Latest host quote, or null if unavailable. Preserve [targetId] when rendering a fallback;
 * slots own their visible wording and must not clear host state while rendering. */
data class ActionQuoteContent(val targetId: String, val message: Message?)

/** Presentation feedback category, independent of message delivery state. */
enum class ActionStatusKind { Editing, Unavailable, Replying, Pending, Error, DeletePrompt }

/** Typed feedback. [error] is localized host-supplied operation feedback for Error only. */
data class ActionStatusContent(val kind: ActionStatusKind, val error: String? = null)

/**
 * Replaceable presentation for all action surfaces, with foundation defaults. Containers receive
 * their guarded child content and should render it once; they may change layout/decoration.
 * [control] receives a typed command, disabled state and guarded activation. [status] receives
 * typed pending/error/prompt state. [quote] receives the resolved nullable target.
 * Renderers run during composition and must never dispatch actions at that time. Replacements
 * own semantics and focus; presentation does not change the host's authority or request guards.
 *
 * Set these once with [ActionTheme], or pass an explicit set to an individual surface. A locally
 * supplied set takes precedence; use `LocalActionSlots.current.copy(...)` to replace one slot
 * while retaining the surrounding theme. No slots retain messages or draft state.
 */
data class ActionSlots(
    val control: @Composable (ActionControlContent) -> Unit = { ActionControl(it) },
    val status: @Composable (ActionStatusContent) -> Unit = { DefaultActionStatus(it) },
    val quote: @Composable (ActionQuoteContent) -> Unit = { DefaultActionQuote(it) },
    val menu: @Composable (@Composable () -> Unit) -> Unit = { Column { it() } },
    val composerStatus: @Composable (@Composable () -> Unit) -> Unit = { Column { it() } },
    val confirmation: @Composable (@Composable () -> Unit) -> Unit = { Column { it() } },
)

val LocalActionStyle = staticCompositionLocalOf { ActionStyle() }

val LocalActionSlots = staticCompositionLocalOf { ActionSlots() }

/**
 * Scoped, optional actions theme, independent of Material3. Unspecified arguments inherit the
 * enclosing actions theme. Theme/slot replacement at the same composition location never emits
 * an intent or resets the host's action/draft state. Explicit surface slots override this theme.
 *
 * ```kotlin
 * ActionTheme(slots = LocalActionSlots.current.copy(control = { button ->
 *     HostActionButton(button.command, button.enabled, button.onClick)
 * })) { MessageActions(actions, message.id) }
 * ```
 */
@Composable
fun ActionTheme(
    style: ActionStyle = LocalActionStyle.current,
    slots: ActionSlots = LocalActionSlots.current,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalActionStyle provides style,
        LocalActionSlots provides slots,
        content = content
    )
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

@Composable
fun ActionLabel(command: ActionCommand) {
    BasicText(when (command) {
        ActionCommand.OpenMenu -> "Actions"
        ActionCommand.CloseMenu -> "Close actions"
        ActionCommand.Copy -> "Copy"
        ActionCommand.Quote -> "Reply"
        ActionCommand.CancelQuote -> "Cancel reply"
        ActionCommand.SendQuote -> "Send"
        ActionCommand.BeginEdit -> "Edit"
        ActionCommand.CancelEdit -> "Cancel edit"
        ActionCommand.SubmitEdit -> "Save edit"
        ActionCommand.RequestDelete -> "Delete"
        ActionCommand.CancelDelete -> "Cancel delete"
        ActionCommand.ConfirmDelete -> "Confirm delete"
    }, style = LocalActionStyle.current.textStyle)
}

@Composable
fun ActionControl(content: ActionControlContent, label: @Composable () -> Unit = { ActionLabel(content.command) }) {
    val style = LocalActionStyle.current
    Box(Modifier.sizeIn(minWidth = style.minimumControlSize, minHeight = style.minimumControlSize)
        .background(style.controlBackground, style.controlShape)
        .clickable(enabled = content.enabled, role = Role.Button, onClick = content.onClick).padding(style.controlPadding)) { label() }
}

@Composable
fun DefaultActionStatus(content: ActionStatusContent) {
    BasicText(when (content.kind) {
        ActionStatusKind.Editing -> "Editing message"
        ActionStatusKind.Unavailable -> "This action is no longer available. Cancel and select the message again."
        ActionStatusKind.Replying -> "Replying to"
        ActionStatusKind.Pending -> "Request pending…"
        ActionStatusKind.Error -> content.error.orEmpty()
        ActionStatusKind.DeletePrompt -> "Delete this message?"
    }, style = LocalActionStyle.current.textStyle)
}

@Composable
fun DefaultActionQuote(content: ActionQuoteContent) {
    BasicText(content.message?.let { "${it.sender.displayName}: ${it.text}" } ?: "Message unavailable",
        style = LocalActionStyle.current.textStyle)
}
