package dev.chat.sample

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

/** Native desktop verification host; never included in the sample or library artifacts. */
fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Chat RTL verification") {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { SampleApp() }
    }
}
