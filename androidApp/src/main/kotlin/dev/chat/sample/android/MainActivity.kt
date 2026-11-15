package dev.chat.sample.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.chat.sample.BotHost
import dev.chat.sample.DiagnosticSampleApp
import dev.chat.sample.FakeHost
import dev.chat.sample.SampleApp

class RetainedHost : ViewModel() {
    val host = BotHost()
    val diagnosticHost by lazy { FakeHost().apply { startSimulation() } }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val retained = ViewModelProvider(this)[RetainedHost::class.java]
        setContent {
            if (intent.getBooleanExtra("diagnostics", false)) {
                DiagnosticSampleApp(retained.diagnosticHost)
            } else {
                SampleApp(retained.host)
            }
        }
    }
}
