package dev.chat.sample

import dev.forntoh.chat.actions.*
import dev.forntoh.chat.material3.ChatTheme
import dev.forntoh.chat.material3.chatColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import dev.forntoh.chat.material3.ChatIcons
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.forntoh.chat.material3.ReplyPreview

/**
 * The sample uses the library's default reference palette and renderer theme. [hostTheme] demonstrates host control of colors
 * and typography with system font families, without installing fonts or changing chat state.
 */
@Composable
fun SampleTheme(hostTheme: Boolean, content: @Composable () -> Unit) {
    val colors = if (hostTheme) lightColorScheme(
        primary = Color(0xFF176653), onPrimary = Color.White,
        primaryContainer = Color(0xFFCBF1DF), onPrimaryContainer = Color(0xFF092E24),
        secondaryContainer = Color(0xFFE7EED6), onSecondaryContainer = Color(0xFF252E14),
        surface = Color(0xFFFAFCF5), background = Color(0xFFFAFCF5),
    ) else chatColorScheme()

    val type = if (hostTheme) Typography(
        bodyLarge = TextStyle(fontFamily = FontFamily.Serif, fontSize = 18.sp, lineHeight = 25.sp),
        titleLarge = TextStyle(
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp
        ),
    ) else Typography(
        bodyLarge = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontSize = 16.sp,
            lineHeight = 24.sp
        ),
        titleLarge = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Medium,
            fontSize = 20.sp,
            lineHeight = 24.sp
        ),
    )
    MaterialTheme(colorScheme = colors, typography = type) {
        // The sample bridges its optional Material3 theme at the host boundary.
        ActionTheme(
            style = ActionStyle(
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                menuBackground = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            slots = LocalActionSlots.current.copy(
                composerStatus = { Column(Modifier.padding(horizontal = 16.dp)) { it() } },
                quote = { ReplyPreview(it.message, it.onClick) },
                confirmation = {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Column(Modifier.padding(12.dp)) { it() }
                    }
                },
                status = { status ->
                    Text(
                        when (status.kind) {
                            ActionStatusKind.Editing -> "Editing message"
                            ActionStatusKind.Unavailable -> "This action is no longer available. Cancel and select the message again."
                            ActionStatusKind.Replying -> "Replying to"
                            ActionStatusKind.Pending -> "Request pending…"
                            ActionStatusKind.Error -> status.error.orEmpty()
                            ActionStatusKind.DeletePrompt -> "Delete this message?"
                        },
                        Modifier.padding(vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (status.kind == ActionStatusKind.Error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                control = { button ->
                    ActionTheme(
                        style = LocalActionStyle.current.copy(
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color =
                                    if (button.command == ActionCommand.RequestDelete || button.command == ActionCommand.ConfirmDelete)
                                        MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            )
                        )
                    ) {
                        ActionControl(button) {
                            val icon = when (button.command) {
                                ActionCommand.Copy -> ChatIcons.Copy
                                ActionCommand.Quote -> ChatIcons.Reply
                                ActionCommand.BeginEdit -> ChatIcons.Edit
                                ActionCommand.RequestDelete -> ChatIcons.Delete
                                ActionCommand.CloseMenu -> ChatIcons.Close
                                else -> null
                            }
                            if (icon == null) ActionLabel(button.command) else Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                ActionLabel(button.command)
                                Icon(
                                    icon,
                                    null,
                                    Modifier.size(20.dp),
                                    tint = LocalActionStyle.current.textStyle.color
                                )
                            }
                        }
                    }
                },
            ),
            content = { ChatTheme(colorScheme = colors, typography = type, content = content) },
        )
    }
}

/** Host-owned identity content for the scaffold's avatar slot. */
@Composable
internal fun SampleAvatar(initials: String) {
    Surface(
        shape = androidx.compose.foundation.shape.CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.size(36.dp).clearAndSetSemantics {}) {
        androidx.compose.foundation.layout.Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
            Text(initials, style = MaterialTheme.typography.labelMedium)
        }
    }
}
