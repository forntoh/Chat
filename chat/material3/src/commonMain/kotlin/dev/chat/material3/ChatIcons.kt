package dev.chat.material3

import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*

/** Default icons from com.composables:icons-tabler-outline-cmp. Replace via composable slots. */
object ChatIcons {
    val Send: ImageVector get() = Tabler.Outline.Send
    val ArrowLeft: ImageVector get() = Tabler.Outline.ArrowLeft
    val ArrowRight: ImageVector get() = Tabler.Outline.ArrowRight
    val DotsVertical: ImageVector get() = Tabler.Outline.DotsVertical
    val Paperclip: ImageVector get() = Tabler.Outline.Paperclip
}
