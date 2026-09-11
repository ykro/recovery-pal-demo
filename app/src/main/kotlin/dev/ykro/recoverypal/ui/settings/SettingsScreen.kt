package dev.ykro.recoverypal.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ykro.recoverypal.R
import dev.ykro.recoverypal.agent.AgentRuntime
import dev.ykro.recoverypal.agent.ModelStore
import dev.ykro.recoverypal.ui.theme.Danger
import dev.ykro.recoverypal.ui.theme.Success
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val fmt: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, HH:mm").withZone(ZoneId.systemDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit, onDataDeleted: () -> Unit) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Settings") },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
      )
    },
    containerColor = MaterialTheme.colorScheme.background,
  ) { padding ->
    Column(Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Section("On-device model (wound photos)") {
        if (state.modelPresent) {
          Text("Ready: ${state.modelFileName}", color = Success)
          Spacer(Modifier.height(8.dp))
          OutlinedButton(onClick = viewModel::deleteModel) { Text("Delete model") }
        } else if (state.downloading) {
          Text("Downloading ${ModelStore.FILE_NAME} (${ModelStore.SIZE_LABEL})…")
          LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
          OutlinedButton(onClick = viewModel::cancelDownload) { Text("Cancel") }
        } else {
          Text("${ModelStore.FILE_NAME} (${ModelStore.SIZE_LABEL}) is not on this device. Photo analysis stays unavailable until it is; nothing falls back to the cloud.")
          state.error?.let { Text(it, color = Danger, style = MaterialTheme.typography.bodySmall) }
          Spacer(Modifier.height(8.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = viewModel::download) { Text("Download over Wi-Fi") }
            OutlinedButton(onClick = viewModel::refreshModel) { Text("Re-check") }
          }
          Text(state.pushCommand, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), modifier = Modifier.padding(top = 8.dp))
        }
      }
      Section("Simulated day (debug)") {
        Text("Calendar says day ${state.calendarDay}. Pick a day to see the agent load a different phase asset.", style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          FilterChip(selected = state.simulatedDay == null, onClick = { viewModel.setSimulatedDay(null) }, label = { Text("Real") })
          listOf(3, 12, 30, 50).forEach { d -> FilterChip(selected = state.simulatedDay == d, onClick = { viewModel.setSimulatedDay(d) }, label = { Text("Day $d") }) }
        }
      }
      Section("Daily reminder") {
        Text("Scheduled with WorkManager at ${state.reminderLabel}. The worker only posts a notification; it never calls the model.", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = viewModel::notifyNow) { Text("Notify now (debug)") }
      }
      Section("Session (Room)") {
        Text("One ADK session for the whole episode: ${state.sessionId}", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
        Text("${state.sessionStats.events} events stored · ${state.sessionStats.compactions} compaction summaries (every ${AgentRuntime.COMPACTION_INTERVAL} user turns)")
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = viewModel::refreshStats) { Text("Refresh") }
      }
      Section("Data that left the device") {
        Image(painterResource(R.drawable.data_privacy), null, Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(14.dp)))
        Spacer(Modifier.height(8.dp))
        Text("Check-in messages go to ${AgentRuntime.CLOUD_MODEL} through Firebase AI Logic (text only). Photos and escalations only with your approval:", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(6.dp))
        if (state.outbound.isEmpty()) Text("Nothing yet.", color = Success)
        state.outbound.forEach { e ->
          Text("${fmt.format(Instant.ofEpochMilli(e.atEpochMs))} · ${e.kind}", style = MaterialTheme.typography.labelLarge, color = if (e.kind.endsWith("REJECTED")) MaterialTheme.colorScheme.onSurfaceVariant else Danger)
          Text(e.summary, style = MaterialTheme.typography.bodySmall)
          Spacer(Modifier.height(6.dp))
        }
      }
      Section("Danger zone") {
        OutlinedButton(onClick = { viewModel.deleteAllData(onDataDeleted) }) { Text("Delete all my data", color = Danger) }
      }
    }
  }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
  Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(0.dp)) {
    Column(Modifier.padding(16.dp)) {
      Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
      Spacer(Modifier.height(8.dp))
      content()
    }
  }
}
