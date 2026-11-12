package dev.chat.sample

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
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
        titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 22.sp),
    )
    MaterialTheme(colorScheme = colors, typography = type, content = content)
}
