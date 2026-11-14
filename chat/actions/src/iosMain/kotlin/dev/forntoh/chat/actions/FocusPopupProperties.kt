package dev.forntoh.chat.actions

import androidx.compose.ui.window.PopupProperties

internal actual fun focusPopupProperties() = PopupProperties(
    focusable = true, clippingEnabled = false, usePlatformInsets = false,
)
