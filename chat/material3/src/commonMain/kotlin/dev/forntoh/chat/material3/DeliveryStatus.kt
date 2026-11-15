package dev.forntoh.chat.material3

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import dev.forntoh.chat.core.Delivery
import dev.forntoh.chat.ui.LocalChatMotion
import dev.forntoh.chat.ui.ChatMotionVisibility

@Composable
internal fun DeliveryStatus(delivery: Delivery) {
    val motion = LocalChatMotion.current
    val measure = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelSmall
    val density = LocalDensity.current
    val width = with(density) {
        maxOf(measure.measure("Sending", style).size.width.toDp(),
            measure.measure("Failed", style).size.width.toDp() + 8.dp)
    }
    ChatMotionVisibility(delivery != Delivery.Sent) {
        Box(Modifier.width(width), contentAlignment = Alignment.CenterEnd) {
            Crossfade(delivery, animationSpec = tween(motion.duration(motion.controlMillis), easing = motion.easing)) { status ->
                when (status) {
                    Delivery.Failed -> Surface(color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer) {
                        Text(status.name, style = style, modifier = Modifier.padding(horizontal = 4.dp))
                    }
                    Delivery.Sending -> Text(status.name, style = style)
                    Delivery.Sent -> Unit
                }
            }
        }
    }
}
