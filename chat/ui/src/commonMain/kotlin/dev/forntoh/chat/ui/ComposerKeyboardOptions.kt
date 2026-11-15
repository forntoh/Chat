package dev.forntoh.chat.ui

import androidx.compose.foundation.text.KeyboardOptions

/**
 * Keyboard defaults used by the foundation and Material composers. Enables Compose's native
 * UIKit text editing on iOS; other targets retain [KeyboardOptions.Default]. Custom composer
 * fields may reuse these options or copy them to configure their own keyboard behavior.
 * This configures input presentation only; draft changes and submission still belong to the host.
 */
expect val ChatComposerKeyboardOptions: KeyboardOptions
