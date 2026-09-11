package dev.ykro.recoverypal.ui.journal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ykro.recoverypal.R
import dev.ykro.recoverypal.RecoveryPalApp
import dev.ykro.recoverypal.data.CheckinEntity
import dev.ykro.recoverypal.data.WoundObservationEntity
import dev.ykro.recoverypal.ui.theme.Peach
import dev.ykro.recoverypal.ui.theme.Teal
import dev.ykro.recoverypal.ui.theme.TealSoft
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val fmt: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, HH:mm").withZone(ZoneId.systemDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalScreen(app: RecoveryPalApp, onBack: () -> Unit) {
  val checkins by app.database.checkins().observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
  val wounds by app.database.wounds().observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Journal") },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
      )
    },
    containerColor = MaterialTheme.colorScheme.background,
  ) { padding ->
    if (checkins.isEmpty() && wounds.isEmpty()) {
      Column(Modifier.fillMaxSize().padding(padding).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Image(painterResource(R.drawable.journal_empty), null, Modifier.size(200.dp))
        Spacer(Modifier.height(12.dp))
        Text("Nothing yet", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Your first check-in will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      return@Scaffold
    }
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      if (checkins.size >= 2) item { PainChart(checkins.sortedBy { it.dayNumber }) }
      items(checkins, key = { "c${it.id}" }) { CheckinCard(it) }
      if (wounds.isNotEmpty()) item { Text("Wound photos (analyzed on device)", style = MaterialTheme.typography.titleMedium) }
      items(wounds, key = { "w${it.id}" }) { WoundCard(it) }
    }
  }
}

@Composable
private fun PainChart(points: List<CheckinEntity>) {
  Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(0.dp)) {
    Column(Modifier.padding(16.dp)) {
      Text("Pain by day", style = MaterialTheme.typography.labelLarge, color = Teal)
      Spacer(Modifier.height(8.dp))
      Canvas(Modifier.fillMaxWidth().height(120.dp)) {
        val minDay = points.first().dayNumber
        val maxDay = maxOf(points.last().dayNumber, minDay + 1)
        fun x(d: Int) = (d - minDay).toFloat() / (maxDay - minDay) * size.width
        fun y(p: Int) = size.height - p / 10f * size.height
        for (level in listOf(0, 5, 10)) drawLine(TealSoft, Offset(0f, y(level)), Offset(size.width, y(level)), strokeWidth = 2f)
        points.zipWithNext().forEach { (a, b) -> drawLine(Teal, Offset(x(a.dayNumber), y(a.pain)), Offset(x(b.dayNumber), y(b.pain)), strokeWidth = 6f, cap = StrokeCap.Round) }
        points.forEach { drawCircle(Peach, radius = 10f, center = Offset(x(it.dayNumber), y(it.pain))) }
      }
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("day ${points.first().dayNumber}", style = MaterialTheme.typography.labelSmall)
        Text("day ${points.last().dayNumber}", style = MaterialTheme.typography.labelSmall)
      }
    }
  }
}

@Composable
private fun CheckinCard(c: CheckinEntity) {
  Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(0.dp)) {
    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Day ${c.dayNumber}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(fmt.format(Instant.ofEpochMilli(c.atEpochMs)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      Text("Pain ${c.pain}/10" + (c.temperatureC?.let { " · $it °C" } ?: ""), style = MaterialTheme.typography.bodyLarge)
      c.symptoms?.takeIf { it.isNotBlank() }?.let { Text("Symptoms: $it", style = MaterialTheme.typography.bodyMedium) }
      c.notes?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
      val done = c.exercisesDone.split(',').filter(String::isNotBlank)
      if (done.isNotEmpty()) Text("Exercises: ${done.joinToString()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

@Composable
private fun WoundCard(w: WoundObservationEntity) {
  Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(0.dp)) {
    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Day ${w.dayNumber} photo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(fmt.format(Instant.ofEpochMilli(w.capturedAtEpochMs)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      Text("Redness ${w.rednessAroundIncision} · discharge ${w.discharge} · edges ${w.edgesClosed} · swelling ${w.swelling}", style = MaterialTheme.typography.bodyMedium)
      if (w.freeText.isNotBlank()) Text(w.freeText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Text("On device in ${w.analysisMs / 1000.0} s" + if (w.analyzedOffline) " · no network" else "", style = MaterialTheme.typography.labelSmall, color = Teal)
    }
  }
}
