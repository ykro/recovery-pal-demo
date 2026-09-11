package dev.ykro.recoverypal.ui.today

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ykro.recoverypal.R
import dev.ykro.recoverypal.RecoveryPalApp
import dev.ykro.recoverypal.data.CheckinEntity
import dev.ykro.recoverypal.data.Patient
import dev.ykro.recoverypal.ui.components.MarkdownText
import dev.ykro.recoverypal.ui.theme.Peach
import dev.ykro.recoverypal.ui.theme.PeachSoft
import dev.ykro.recoverypal.ui.theme.Teal
import dev.ykro.recoverypal.ui.theme.TealSoft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads the phase asset directly from assets so the home screen renders without calling the model. */
private suspend fun readSkillAsset(context: Context, skill: String, path: String): String =
  withContext(Dispatchers.IO) { runCatching { context.assets.open("skills/$skill/$path").bufferedReader().readText() }.getOrElse { "" } }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(app: RecoveryPalApp, patient: Patient, onCheckin: () -> Unit, onPhoto: () -> Unit, onJournal: () -> Unit, onSettings: () -> Unit) {
  val context = LocalContext.current
  val day = patient.dayToday()
  val phase = patient.phaseToday()
  val checkins by app.database.checkins().observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
  val phaseText by produceState("", phase) { value = readSkillAsset(context, patient.surgery.skillName, phase.assetFile) }
  var expanded by remember { mutableStateOf(false) }
  val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
  LaunchedEffect(Unit) { if (Build.VERSION.SDK_INT >= 33 && !app.reminders.canNotify()) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Today", style = MaterialTheme.typography.titleLarge) },
        actions = {
          IconButton(onClick = onJournal) { Icon(Icons.Outlined.MenuBook, "Journal") }
          IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, "Settings") }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
      )
    },
    containerColor = MaterialTheme.colorScheme.background,
  ) { padding ->
    Column(Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
      Card(colors = CardDefaults.cardColors(containerColor = Teal), elevation = CardDefaults.cardElevation(0.dp)) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
          Column(Modifier.weight(1f)) {
            Text("Day $day", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color.White)
            Text("${patient.surgery.displayName}${if (patient.simulatedDay != null) " · simulated day" else ""}", style = MaterialTheme.typography.bodyMedium, color = TealSoft)
            Spacer(Modifier.height(8.dp))
            Text("Phase ${phase.number}: ${phase.title}", style = MaterialTheme.typography.titleMedium, color = androidx.compose.ui.graphics.Color.White)
            Text(phase.label, style = MaterialTheme.typography.labelMedium, color = TealSoft)
          }
          Image(painterResource(phaseImage(phase.number)), null, Modifier.size(84.dp).clip(RoundedCornerShape(16.dp)))
        }
      }
      Button(onClick = onCheckin, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Do today's check-in", style = MaterialTheme.typography.titleMedium) }
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = onPhoto, modifier = Modifier.weight(1f)) {
          Icon(Icons.Outlined.PhotoCamera, null)
          Spacer(Modifier.size(6.dp))
          Text("Wound photo")
        }
        OutlinedButton(onClick = onJournal, modifier = Modifier.weight(1f)) { Text("Journal") }
      }
      checkins.firstOrNull()?.let { LastCheckinCard(it) }
      Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(0.dp)) {
        Column(Modifier.padding(16.dp)) {
          Text("This phase, from the protocol", style = MaterialTheme.typography.labelLarge, color = Teal)
          Text("Read by the app from the skill asset, no model call.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          Spacer(Modifier.height(8.dp))
          MarkdownText(if (expanded || phaseText.length < 700) phaseText else phaseText.take(700) + "…")
          if (phaseText.length >= 700) TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Show less" else "Read more") }
        }
      }
    }
  }
}

private fun phaseImage(n: Int) = when (n) { 1 -> R.drawable.phase_1; 2 -> R.drawable.phase_2; else -> R.drawable.phase_3 }

@Composable
private fun LastCheckinCard(c: CheckinEntity) {
  Card(colors = CardDefaults.cardColors(containerColor = PeachSoft), elevation = CardDefaults.cardElevation(0.dp)) {
    Column(Modifier.padding(16.dp)) {
      Text("Last check-in · day ${c.dayNumber}", style = MaterialTheme.typography.labelLarge)
      Spacer(Modifier.height(4.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Pain ${c.pain}/10", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        c.temperatureC?.let { Text("$it °C", style = MaterialTheme.typography.titleMedium) }
      }
      c.symptoms?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
      val done = c.exercisesDone.split(',').filter(String::isNotBlank)
      if (done.isNotEmpty()) Text("Exercises: ${done.joinToString()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}
