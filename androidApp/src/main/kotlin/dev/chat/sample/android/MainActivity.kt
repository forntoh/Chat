package dev.chat.sample.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dev.chat.sample.SampleApp
import dev.chat.sample.FakeHost
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class RetainedHost : ViewModel() { val host = FakeHost() }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val host = ViewModelProvider(this)[RetainedHost::class.java].host
        setContent { SampleApp(host) }
    }
}
