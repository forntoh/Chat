package dev.forntoh.chat.ui

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.text.input.PlatformImeOptions

@OptIn(ExperimentalComposeUiApi::class)
actual val ChatComposerKeyboardOptions: KeyboardOptions = KeyboardOptions(
    platformImeOptions = PlatformImeOptions { usingNativeTextInput(true) },
)
