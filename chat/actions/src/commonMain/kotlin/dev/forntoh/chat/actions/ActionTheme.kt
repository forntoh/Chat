package dev.forntoh.chat.actions

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.visible
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.forntoh.chat.core.Message

/**
 * Foundation defaults for every action surface. Supply a host text style (including color),
 * control background/shape and spacing, or replace renderers in [ActionSlots]. Material3 hosts
 * can map their MaterialTheme values here without adding Material3 to the actions dependency.
 * [minimumControlSize] defaults to a 48 dp target; custom controls own equivalent semantics.
 * [composerPadding] places reply/edit context inside an input header, independently of menu spacing.
 */
data class ActionStyle(
    val textStyle: TextStyle = TextStyle.Default,
    val controlBackground: Color = Color.Transparent,
    val controlShape: Shape = RectangleShape,
    val controlPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    val minimumControlSize: Dp = 48.dp,
    val menuBackground: Color = Color(color = 0xFFF4F5F8),
    val menuShape: Shape = RoundedCornerShape(20.dp),
    val quoteBackground: Color = Color(color = 0x14000000),
    val quoteAccent: Color = Color(color = 0xFF53A6DF),
    val composerPadding: PaddingValues = PaddingValues(start = 16.dp, top = 8.dp, end = 4.dp),
)

/** Guarded explicit action. Custom controls author their label from [command], honor [enabled],
 * and supply focus/keyboard/button semantics. Invoke [onClick] only on activation. */
data class ActionControlContent(
    val command: ActionCommand,
    val enabled: Boolean,
    val onClick: () -> Unit,
)

/** Latest host quote, or null if unavailable. Preserve [targetId] when rendering a fallback;
 * slots own their visible wording and must not clear host state while rendering. */
data class ActionQuoteContent(
    val targetId: String,
    val message: Message?,
    val onClick: (() -> Unit)? = null,
)

/** Presentation feedback category, independent of message delivery state. */
enum class ActionStatusKind { Editing, Unavailable, Replying, Pending, Error, DeletePrompt }

/** Typed feedback. [error] is localized host-supplied operation feedback for Error only. */
data class ActionStatusContent(
    val kind: ActionStatusKind,
    val error: String? = null,
)

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
        content = content,
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
    Box(
        Modifier.sizeIn(minWidth = style.minimumControlSize, minHeight = style.minimumControlSize)
            .background(style.controlBackground, style.controlShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(style.controlPadding),
    ) {
        BasicText(label, style = style.textStyle)
    }
}

@Composable
fun ActionLabel(command: ActionCommand) {
    if (command == ActionCommand.CancelQuote || command == ActionCommand.CancelEdit) {
        val color = LocalActionStyle.current.textStyle.color.takeOrElse { Color.Black }
        Canvas(
            Modifier.size(22.dp).semantics {
                contentDescription = if (command == ActionCommand.CancelQuote) "Cancel reply" else "Cancel edit"
            },
        ) {
            val inset = size.width * 0.25f
            drawLine(color, Offset(inset, inset), Offset(size.width - inset, size.height - inset), 2.dp.toPx(), StrokeCap.Round)
            drawLine(color, Offset(size.width - inset, inset), Offset(inset, size.height - inset), 2.dp.toPx(), StrokeCap.Round)
        }
        return
    }
    BasicText(
        when (command) {
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
        },
        style = LocalActionStyle.current.textStyle,
    )
}

@Composable
fun ActionControl(content: ActionControlContent, label: @Composable () -> Unit = { ActionLabel(content.command) }) {
    val style = LocalActionStyle.current
    Box(
        Modifier.sizeIn(minWidth = style.minimumControlSize, minHeight = style.minimumControlSize)
            .background(style.controlBackground, style.controlShape)
            .clickable(enabled = content.enabled, role = Role.Button, onClick = content.onClick)
            .padding(
                if (content.command == ActionCommand.CancelQuote ||
                    content.command == ActionCommand.CancelEdit
                ) {
                    PaddingValues(0.dp)
                } else {
                    style.controlPadding
                },
            ),
        contentAlignment = Alignment.Center,
    ) { label() }
}

@Composable
fun DefaultActionStatus(content: ActionStatusContent) {
    BasicText(
        when (content.kind) {
            ActionStatusKind.Editing -> "Editing message"
            ActionStatusKind.Unavailable -> "This action is no longer available. Cancel and select the message again."
            ActionStatusKind.Replying -> "Replying to"
            ActionStatusKind.Pending -> "Request pending…"
            ActionStatusKind.Error -> content.error.orEmpty()
            ActionStatusKind.DeletePrompt -> "Delete this message?"
        },
        style = LocalActionStyle.current.textStyle,
    )
}

@Composable
fun DefaultActionQuote(content: ActionQuoteContent) {
    val style = LocalActionStyle.current
    Row(
        Modifier.widthIn(max = 320.dp).clip(RoundedCornerShape(10.dp))
            .background(style.quoteBackground)
            .then(if (content.onClick != null) Modifier.clickable(role = Role.Button, onClick = content.onClick) else Modifier)
            .semantics { contentDescription = content.message?.let { "Reply to ${it.sender.displayName}" } ?: "Message unavailable" }
            .height(IntrinsicSize.Min),
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(style.quoteAccent))
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            content.message?.let {
                BasicText(
                    it.sender.displayName.ifBlank { "Unknown sender" },
                    style = style.textStyle.copy(color = style.quoteAccent, fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicText(
                content.message?.text?.ifBlank { "Message unavailable" } ?: "Message unavailable",
                style = style.textStyle,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
