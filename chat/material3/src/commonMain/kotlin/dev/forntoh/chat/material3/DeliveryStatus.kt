package dev.forntoh.chat.material3

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import dev.forntoh.chat.core.Delivery
import dev.forntoh.chat.ui.ChatMotionVisibility
import dev.forntoh.chat.ui.LocalChatMotion

@Composable
internal fun DeliveryStatus(delivery: Delivery) {
    val motion = LocalChatMotion.current
    val measure = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelSmall
    val density = LocalDensity.current
    val width = with(density) {
        maxOf(
            measure.measure("Sending", style).size.width.toDp(),
            measure.measure("Failed", style).size.width.toDp() + 8.dp,
        )
    }
    ChatMotionVisibility(delivery.takeUnless { it == Delivery.Sent }) { shown ->
        Box(Modifier.width(width), contentAlignment = Alignment.CenterEnd) {
            Crossfade(shown, animationSpec = tween(motion.duration(motion.controlMillis), easing = motion.easing)) { status ->
                when (status) {
                    Delivery.Failed -> Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ) {
                        Text(status.name, style = style, modifier = Modifier.padding(horizontal = 4.dp))
                    }
                    Delivery.Sending -> Text(status.name, style = style)
                    Delivery.Sent -> Unit
                }
            }
        }
    }
}
