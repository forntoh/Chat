package dev.chat.sample

import androidx.compose.ui.window.ComposeUIViewController

@Suppress("FunctionName", "FunctionNaming") // Swift framework entry point.
fun MainViewController() = ComposeUIViewController { SampleApp() }

@Suppress("FunctionName", "FunctionNaming") // Swift developer-fixture entry point.
fun DiagnosticViewController() = ComposeUIViewController { DiagnosticSampleApp() }
