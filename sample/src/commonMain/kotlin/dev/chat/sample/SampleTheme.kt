package dev.chat.sample

import dev.chat.actions.*
import dev.chat.material3.JetchatTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Portable light themes for the sample. The baseline takes Jetchat's Material3 hierarchy,
 * rounded messages and distinct sender metadata as a visual reference; it does not port its
 * Android dynamic colors, assets or features. [hostTheme] demonstrates host control of colors
 * and typography with system font families, without installing fonts or changing chat state.
 */
@Composable
fun SampleTheme(hostTheme: Boolean, content: @Composable () -> Unit) {
    val colors = if (hostTheme) lightColorScheme(
        primary = Color(0xFF176653), onPrimary = Color.White,
        primaryContainer = Color(0xFFCBF1DF), onPrimaryContainer = Color(0xFF092E24),
        secondaryContainer = Color(0xFFE7EED6), onSecondaryContainer = Color(0xFF252E14),
        surface = Color(0xFFFAFCF5), background = Color(0xFFFAFCF5),
    ) else lightColorScheme(
        primary = Color(0xFF6354A4), onPrimary = Color.White,
        primaryContainer = Color(0xFFE7DEFF), onPrimaryContainer = Color(0xFF251452),
        secondaryContainer = Color(0xFFE8DEF2), onSecondaryContainer = Color(0xFF2D2438),
        surface = Color(0xFFFFFBFF), background = Color(0xFFFFFBFF),
    )
    val type = if (hostTheme) Typography(
        bodyLarge = TextStyle(fontFamily = FontFamily.Serif, fontSize = 18.sp, lineHeight = 25.sp),
        titleLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 24.sp),
    ) else Typography(
        bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 24.sp),
        titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 24.sp),
    )
    MaterialTheme(colorScheme = colors, typography = type) {
        // The sample bridges its optional Material3 theme at the host boundary.
        ActionTheme(
            style = ActionStyle(textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface)),
            slots = LocalActionSlots.current.copy(
                composerStatus = { Column(Modifier.padding(horizontal = 16.dp)) { it() } },
                quote = { quote ->
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                        Text(quote.message?.let { "${it.sender.displayName}: ${it.text}" } ?: "Message unavailable",
                            Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                },
                confirmation = {
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.errorContainer) {
                        Column(Modifier.padding(12.dp)) { it() }
                    }
                },
                status = { status -> Text(when (status.kind) {
                    ActionStatusKind.Editing -> "Editing message"
                    ActionStatusKind.Unavailable -> "This action is no longer available. Cancel and select the message again."
                    ActionStatusKind.Replying -> "Replying to"
                    ActionStatusKind.Pending -> "Request pending…"
                    ActionStatusKind.Error -> status.error.orEmpty()
                    ActionStatusKind.DeletePrompt -> "Delete this message?"
                }, Modifier.padding(vertical = 4.dp), style = MaterialTheme.typography.labelMedium,
                    color = if (status.kind == ActionStatusKind.Error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) },
                control = { button -> TextButton(onClick = button.onClick, enabled = button.enabled) {
                    ActionTheme(style = LocalActionStyle.current.copy(
                        textStyle = MaterialTheme.typography.labelLarge.copy(color = LocalContentColor.current)
                    )) { ActionLabel(button.command) }
                } },
            ),
            content = { JetchatTheme(content = content) },
        )
    }
}
