package dev.chat.sample

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun SimulationPanel(
    host: FakeHost,
    motionLabel: String,
    onMotion: () -> Unit,
    onImages: () -> Unit,
    onDismiss: () -> Unit
) {
    var scenario by remember { mutableStateOf(host.selectedScenario) }
    var seed by remember { mutableStateOf(host.selectedSeed.toString()) }
    var choosing by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Simulation debug panel") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Fixture simulation only · no network or persistence.")
                TextButton(onClick = onMotion) { Text(motionLabel) }
                Box {
                    OutlinedButton(onClick = { choosing = true }) { Text(scenario.label) }
                    DropdownMenu(choosing, onDismissRequest = { choosing = false }) {
                        Scenario.entries.forEach { option ->
                            DropdownMenuItem(text = { Text(option.label) }, onClick = { scenario = option; choosing = false })
                        }
                    }
                }
                Text(scenario.guidance)
                OutlinedTextField(seed, onValueChange = { seed = it }, label = { Text("Seed") }, singleLine = true,
                    isError = seed.toIntOrNull() == null)
                Text("New events: acknowledgement ${host.acknowledgementMillis} ms · delivery ${host.deliveryMillis} ms")
                TextButton(onClick = { host.configureDelays(if (host.acknowledgementMillis == 400L) 1_200 else 400, host.deliveryMillis) }) {
                    Text("Toggle acknowledgement delay")
                }
                TextButton(onClick = { host.configureDelays(host.acknowledgementMillis, if (host.deliveryMillis == 900L) 2_000 else 900) }) {
                    Text("Toggle delivery delay")
                }
                Button(enabled = seed.toIntOrNull() != null, onClick = { host.startSimulation(scenario, seed.toInt()) }) { Text("Start selected scenario") }
                Row {
                    TextButton(onClick = { host.pause(!host.paused) }, enabled = host.simulationEnabled) { Text(if (host.paused) "Resume" else "Pause") }
                    TextButton(onClick = host::step, enabled = host.simulationEnabled) { Text("Step") }
                    TextButton(onClick = host::resetSimulation, enabled = host.simulationEnabled) { Text("Reset") }
                }
                TextButton(onClick = { host.changeConnection(!host.connected) }) { Text(if (host.connected) "Disconnect" else "Reconnect") }
                TextButton(onClick = { host.changeSelectedCapabilities(false) }) { Text("Revoke selected message actions") }
                TextButton(onClick = { host.changeSelectedCapabilities(true) }) { Text("Restore selected message actions") }
                TextButton(onClick = host::removeQuotedTarget) { Text("Remove reply target") }
                TextButton(onClick = host::removeFirstVisibleFixtureRow) { Text("Remove first loaded row") }
                TextButton(onClick = onImages) { Text("Open image simulator") }
                TextButton(onClick = host::switchRoom) { Text("Switch room") }
                Text("${if (host.paused) "Paused" else "Running"} · logical clock ${host.clockMillis} ms")
                Text("Recent fixture trace (max 80; draft text excluded)", style = MaterialTheme.typography.labelMedium)
                host.trace.takeLast(8).forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } })
}
