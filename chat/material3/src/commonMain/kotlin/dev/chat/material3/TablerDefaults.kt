// Tabler Icons v3.46.0, Copyright (c) 2020-2026 Paweł Kuna. MIT license.
// https://github.com/tabler/tabler-icons/tree/v3.46.0
package dev.chat.material3

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

internal object TablerDefaults {
    val Send: ImageVector = ImageVector.Builder("Tabler.Send", 24.dp, 24.dp, 24f, 24f).apply {
        addPath(PathParser().parsePathString("M10 14l11 -11").toNodes(), stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
        addPath(PathParser().parsePathString("M21 3l-6.5 18a.55 .55 0 0 1 -1 0l-3.5 -7l-7 -3.5a.55 .55 0 0 1 0 -1l18 -6.5").toNodes(), stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
    }.build()
}
